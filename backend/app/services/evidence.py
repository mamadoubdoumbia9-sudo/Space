"""Validation RÉELLE des preuves. Une preuve invalide ne part jamais vers WhatsApp.

Ce module ne se contente pas de vérifier une extension : il ouvre le fichier,
vérifie sa signature binaire (magic bytes), le décode, mesure des indicateurs
d'authenticité et calcule une empreinte perceptuelle pour détecter les captures
d'écran réutilisées d'un signalement à l'autre (premier signal d'abus).
"""
from __future__ import annotations

import hashlib
import io
import json
import re
import zipfile
from dataclasses import dataclass, field
from datetime import datetime, timezone

from ..constants import EvidenceKind
from ..models import Evidence
from ..security import sha256_hex
from .storage import get_storage, make_key

MAGIC = {
    b"\x89PNG\r\n\x1a\n": "image/png",
    b"\xff\xd8\xff": "image/jpeg",
    b"RIFF": "image/webp",
    b"PK\x03\x04": "application/zip",
    b"%PDF": "application/pdf",
}

# Format d'export de conversation WhatsApp :
#   [12/03/2026, 14:22:05] +223 61 23 45 67: Bonjour ...
#   12/03/2026, 14:22 - Nom: message
WA_LINE = re.compile(
    r"^\[?(?P<d>\d{1,2}[/.-]\d{1,2}[/.-]\d{2,4}),?\s+(?P<t>\d{1,2}:\d{2}(?::\d{2})?)\]?\s*(?:-\s*)?(?P<who>[^:]{1,80}):\s?(?P<msg>.*)$"
)
# Identifiant de message WhatsApp : true_/false_<jid>@c.us_<clé>, ou identifiant opaque.
WA_MSG_ID = re.compile(r"^[A-Za-z0-9_@.+\-]{8,120}$")
E164_IN_TEXT = re.compile(r"\+?(\d[\d\s().-]{6,17}\d)")

MIN_IMAGE_W, MIN_IMAGE_H = 200, 200
MAX_LINES_CHECK = 20000


@dataclass
class ValidationResult:
    ok: bool
    kind: str
    mime: str
    size: int
    sha256: str
    detail: dict = field(default_factory=dict)
    errors: list[str] = field(default_factory=list)
    perceptual_hash: str | None = None

    def detail_json(self) -> str:
        payload = dict(self.detail)
        if self.perceptual_hash:
            payload["perceptual_hash"] = self.perceptual_hash
        payload["errors"] = self.errors
        return json.dumps(payload, ensure_ascii=False)


def _detect_mime(data: bytes) -> str | None:
    for magic, mime in MAGIC.items():
        if data.startswith(magic):
            return mime
    # texte brut (export .txt) : décodable et sans octet de contrôle
    try:
        sample = data[:4096].decode("utf-8")
        if sum(1 for c in sample if ord(c) < 9) / max(len(sample), 1) < 0.01:
            return "text/plain"
    except UnicodeDecodeError:
        return None
    return None


def _ahash(image_bytes: bytes) -> str | None:
    """Empreinte perceptuelle 8x8 (détection de réutilisation d'image)."""
    try:
        from PIL import Image
    except ImportError:  # pragma: no cover
        return None
    try:
        img = Image.open(io.BytesIO(image_bytes)).convert("L").resize((8, 8))
    except Exception:  # noqa: BLE001
        return None
    pixels = list(img.getdata())
    avg = sum(pixels) / len(pixels)
    bits = "".join("1" if p >= avg else "0" for p in pixels)
    return f"{int(bits, 2):016x}"


def _hamming(a: str, b: str) -> int:
    return bin(int(a, 16) ^ int(b, 16)).count("1")


def validate_upload(
    *,
    kind: str,
    filename: str,
    data: bytes,
    declared_mime: str | None,
    message_ids: list[str] | None = None,
    target_phone_digits: str = "",
) -> ValidationResult:
    size = len(data)
    digest = sha256_hex(data)
    mime = declared_mime or _detect_mime(data) or "application/octet-stream"
    detected = _detect_mime(data)
    errors: list[str] = []
    detail: dict = {"declared_mime": declared_mime, "detected_mime": detected, "filename": filename}

    if size == 0:
        errors.append("Fichier vide.")
    if detected is None:
        errors.append("Type de fichier non reconnu (contenu illisible).")
    if declared_mime and detected and declared_mime.split(";")[0] != detected and not (
        declared_mime.startswith("image/") and detected.startswith("image/")
    ):
        detail["mime_mismatch"] = True

    phash: str | None = None

    if kind == EvidenceKind.SCREENSHOT:
        if detected not in ("image/png", "image/jpeg", "image/webp"):
            errors.append("Une capture d'écran doit être une image PNG, JPEG ou WebP.")
        else:
            try:
                from PIL import Image, ImageStat

                img = Image.open(io.BytesIO(data))
                img.load()
                detail["width"], detail["height"] = img.size
                if img.size[0] < MIN_IMAGE_W or img.size[1] < MIN_IMAGE_H:
                    errors.append(
                        f"Image trop petite ({img.size[0]}x{img.size[1]} px) : une capture lisible est exigée."
                    )
                stat = ImageStat.Stat(img.convert("L"))
                detail["stddev"] = round(stat.stddev[0], 2)
                if stat.stddev[0] < 4:
                    errors.append("Image quasi uniforme : capture illisible ou vide.")
                exif_software = None
                try:
                    exif = img.getexif()
                    exif_software = exif.get(305) or exif.get(271)
                except Exception:  # noqa: BLE001
                    pass
                if exif_software:
                    detail["image_software"] = str(exif_software)[:80]
                phash = _ahash(data)
            except Exception as exc:  # noqa: BLE001
                errors.append(f"Image illisible : {exc}")

    elif kind == EvidenceKind.CHAT_EXPORT:
        text = _extract_text(data, detected)
        if text is None:
            errors.append("Export de conversation illisible : fournissez le .txt exporté par WhatsApp (ou un .zip le contenant).")
        else:
            lines = text.splitlines()[:MAX_LINES_CHECK]
            parsed = [m.groupdict() for m in (WA_LINE.match(ln.strip()) for ln in lines) if m]
            detail["total_lines"] = len(lines)
            detail["parsed_messages"] = len(parsed)
            if len(parsed) < 1:
                errors.append(
                    "Aucun message reconnu : l'export ne correspond pas au format WhatsApp "
                    "([jj/mm/aaaa, hh:mm:ss] Numéro: message)."
                )
            else:
                senders = {p["who"].strip() for p in parsed}
                detail["distinct_senders"] = len(senders)
                detail["date_range"] = [parsed[0]["d"] + " " + parsed[0]["t"], parsed[-1]["d"] + " " + parsed[-1]["t"]]
                if target_phone_digits:
                    found = any(target_phone_digits[-9:] in re.sub(r"\D", "", s) for s in senders)
                    detail["target_appears_as_sender"] = found
                    if not found:
                        errors.append(
                            "Le numéro signalé n'apparaît pas comme expéditeur dans l'export fourni."
                        )
                detail["sample_redacted"] = (
                    f"{len(parsed)} messages, premiers expéditeurs: "
                    + ", ".join(sorted({s[:6] + '…' for s in senders})[:3])
                )

    elif kind == EvidenceKind.MESSAGE_ID:
        ids = [i.strip() for i in (message_ids or []) if i and i.strip()]
        if not ids:
            errors.append("Aucun identifiant de message fourni.")
        bad = [i for i in ids if not WA_MSG_ID.match(i)]
        if bad:
            errors.append(f"{len(bad)} identifiant(s) invalide(s) (format WhatsApp attendu).")
        detail["message_ids_count"] = len(ids)
        detail["message_ids"] = ids[:50]

    elif kind == EvidenceKind.HEADER_DUMP:
        text = _extract_text(data, detected)
        if text is None:
            errors.append("En-tête technique illisible.")
        else:
            try:
                parsed_json = json.loads(text)
                detail["json_keys"] = sorted(parsed_json.keys())[:30] if isinstance(parsed_json, dict) else ["array"]
            except json.JSONDecodeError:
                kv = dict(
                    ln.split(":", 1) for ln in text.splitlines() if ":" in ln and len(ln.split(":", 1)) == 2
                )
                detail["fields"] = sorted(k.strip() for k in kv)[:30]
                if len(kv) < 2:
                    errors.append("En-tête non exploitable (JSON ou paires clé:valeur attendues).")
    else:
        errors.append(f"Type de preuve inconnu : {kind}")

    return ValidationResult(
        ok=not errors, kind=kind, mime=mime, size=size, sha256=digest, detail=detail, errors=errors,
        perceptual_hash=phash,
    )


def _extract_text(data: bytes, detected: str | None) -> str | None:
    if detected == "application/zip":
        try:
            with zipfile.ZipFile(io.BytesIO(data)) as zf:
                candidates = [n for n in zf.namelist() if n.lower().endswith((".txt", ".json", ".csv"))]
                if not candidates:
                    return None
                with zf.open(candidates[0]) as fh:
                    return fh.read().decode("utf-8", errors="replace")
        except zipfile.BadZipFile:
            return None
    if detected in ("text/plain", "application/json", "text/csv"):
        return data.decode("utf-8", errors="replace")
    return None


def find_duplicate(db, result: ValidationResult, exclude_report_id: int | None = None) -> Evidence | None:
    """Retourne une preuve identique (même empreinte exacte) déjà déposée ailleurs."""
    from sqlalchemy import select

    stmt = select(Evidence).where(Evidence.sha256 == result.sha256)
    if exclude_report_id is not None:
        stmt = stmt.where(Evidence.report_id != exclude_report_id)
    return db.execute(stmt.limit(1)).scalar_one_or_none()


def find_near_duplicate_image(db, perceptual_hash: str, exclude_report_id: int | None = None) -> int | None:
    """Détecte une capture d'écran quasi identique (distance de Hamming <= 3)."""
    from sqlalchemy import select

    stmt = select(Evidence.id, Evidence.report_id, Evidence.validation_detail).where(
        Evidence.kind == EvidenceKind.SCREENSHOT
    )
    if exclude_report_id is not None:
        stmt = stmt.where(Evidence.report_id != exclude_report_id)
    for eid, rid, detail_json in db.execute(stmt.limit(2000)).all():
        if not detail_json:
            continue
        try:
            other = json.loads(detail_json).get("perceptual_hash")
        except json.JSONDecodeError:
            continue
        if other and _hamming(perceptual_hash, other) <= 3:
            return rid
    return None


def store_evidence_bytes(user_id: int, report_ref: str, filename: str, data: bytes) -> str:
    key = make_key(user_id, report_ref, filename)
    get_storage().put(key, data)
    return key


def load_evidence_bytes(storage_key: str) -> bytes:
    return get_storage().get(storage_key)


def compute_hash(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()[:16]


def parse_occurred_at(value: str) -> datetime | None:
    """Analyse tolérante des dates d'infraction (formats locaux Mali/France + ISO)."""
    value = (value or "").strip()
    if not value:
        return None
    formats = (
        "%Y-%m-%d %H:%M:%S",
        "%Y-%m-%d %H:%M",
        "%Y-%m-%dT%H:%M:%S",
        "%Y-%m-%dT%H:%M",
        "%Y-%m-%d",
        "%d/%m/%Y %H:%M:%S",
        "%d/%m/%Y %H:%M",
        "%d/%m/%Y",
        "%d-%m-%Y %H:%M",
        "%d-%m-%Y",
        "%d.%m.%Y %H:%M",
        "%d.%m.%Y",
    )
    for fmt in formats:
        try:
            return datetime.strptime(value, fmt).replace(tzinfo=timezone.utc)
        except ValueError:
            continue
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return None
