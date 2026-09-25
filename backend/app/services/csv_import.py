"""Import en masse CSV / Excel avec refus de toute ligne sans preuve ni catégorie.

Règle non négociable : une ligne sans catégorie d'infraction ET sans référence de
preuve est rejetée. Un import partiel n'est jamais accepté en silence : les lignes
rejetées sont affichées avec leur motif et ne créent aucun signalement.
"""
from __future__ import annotations

import csv
import io
import json
import re
from dataclasses import dataclass, field
from datetime import datetime, timezone

from ..constants import InfractionCategory

COLUMN_ALIASES: dict[str, str] = {
    "numero": "target_phone", "numéro": "target_phone", "phone": "target_phone",
    "telephone": "target_phone", "téléphone": "target_phone", "target": "target_phone",
    "numero_cible": "target_phone", "numéro_cible": "target_phone", "msisdn": "target_phone",
    "categorie": "category", "catégorie": "category", "category": "category", "infraction": "category",
    "type": "category", "motif": "category",
    "date": "occurred_at", "date_infraction": "occurred_at", "occurred_at": "occurred_at",
    "date_heure": "occurred_at", "horodatage": "occurred_at", "timestamp": "occurred_at",
    "description": "description", "details": "description", "détails": "description",
    "commentaire": "description", "fait": "description", "faits": "description",
    "preuve": "evidence_ref", "evidence": "evidence_ref", "fichier": "evidence_ref",
    "capture": "evidence_ref", "chemin_preuve": "evidence_ref", "proof": "evidence_ref",
    "message_id": "message_ids", "message_ids": "message_ids", "ids": "message_ids",
    "id_message": "message_ids", "wa_message_id": "message_ids",
    "peut_contacter": "contact_ok", "contact_ok": "contact_ok",
    # Variantes courantes observées dans de vrais tableurs francophones.
    "date_reception": "occurred_at", "date_de_reception": "occurred_at", "recu_le": "occurred_at",
    "date_recue": "occurred_at", "date_et_heure": "occurred_at", "date_du_message": "occurred_at",
    "numero_whatsapp": "target_phone", "num_whatsapp": "target_phone", "numero_de_telephone": "target_phone",
    "numero_signale": "target_phone", "compte_signale": "target_phone",
    "categorie_infraction": "category", "type_infraction": "category", "nature": "category",
    "resume": "description", "commentaires": "description", "faits_constates": "description",
    "preuves": "evidence_ref", "piece_jointe": "evidence_ref", "pieces_jointes": "evidence_ref",
    "pj": "evidence_ref", "capture_ecran": "evidence_ref", "nom_fichier": "evidence_ref",
    "identifiant_message": "message_ids", "ids_messages": "message_ids",
}

CATEGORY_ALIASES: dict[str, str] = {}
for _c in InfractionCategory:
    CATEGORY_ALIASES[_c.value] = _c.value
    CATEGORY_ALIASES[_c.value.replace("_", " ")] = _c.value
CATEGORY_ALIASES.update(
    {
        "arnaque financiere": InfractionCategory.FINANCIAL_SCAM,
        "discours haineux": InfractionCategory.HATE_SPEECH,
        "contenu illegal": InfractionCategory.ILLEGAL_CONTENT,
        "harcelement": InfractionCategory.HARASSMENT,
        "arnaque": InfractionCategory.FINANCIAL_SCAM,
        "arnaque financiere": InfractionCategory.FINANCIAL_SCAM,
        "fraude": InfractionCategory.FINANCIAL_SCAM,
        "escroquerie": InfractionCategory.FINANCIAL_SCAM,
        "usurpation": InfractionCategory.IMPERSONATION,
        "faux profil": InfractionCategory.IMPERSONATION,
        "harcelement": InfractionCategory.HARASSMENT,
        "menace": InfractionCategory.HARASSMENT,
        "haine": InfractionCategory.HATE_SPEECH,
        "illegal": InfractionCategory.ILLEGAL_CONTENT,
        "pub": InfractionCategory.SPAM,
        "publicite": InfractionCategory.SPAM,
    }
)

PHONE_DIGITS = re.compile(r"\D+")
MESSAGE_ID_SPLIT = re.compile(r"[;,\s|]+")


@dataclass
class ImportRow:
    line: int
    raw: dict[str, str]
    target_phone: str | None = None
    category: str | None = None
    occurred_at: datetime | None = None
    description: str = ""
    evidence_ref: str = ""
    message_ids: list[str] = field(default_factory=list)
    errors: list[str] = field(default_factory=list)

    @property
    def valid(self) -> bool:
        return not self.errors


def _canonical(header: str) -> str | None:
    """Normalise un en-tête de colonne : accents, casse, espaces et tirets ignorés.

    « Date de réception », « date_recu_le » et « DATE-RECEPTION » désignent donc la
    même colonne : un fichier réel écrit par un utilisateur francophone est reconnu
    au lieu d'être rejeté ligne par ligne.
    """
    import unicodedata

    raw = (header or "").strip().lstrip("\ufeff")
    norm = unicodedata.normalize("NFKD", raw)
    norm = "".join(ch for ch in norm if not unicodedata.combining(ch))
    norm = norm.lower().replace("-", "_").replace(" ", "_")
    norm = re.sub(r"_+", "_", norm).strip("_")
    return COLUMN_ALIASES.get(norm)


def detect_columns(headers: list[str]) -> dict[str, str]:
    mapping: dict[str, str] = {}
    for h in headers:
        canon = _canonical(h)
        if canon and canon not in mapping.values():
            mapping[h] = canon
    return mapping


def _parse_category(value: str) -> str | None:
    import unicodedata

    v = unicodedata.normalize("NFKD", (value or "").strip().lower())
    v = "".join(ch for ch in v if not unicodedata.combining(ch))
    v = re.sub(r"\s+", " ", v.replace("_", " "))
    return CATEGORY_ALIASES.get(v) or (v if v in {c.value for c in InfractionCategory} else None)


def _parse_date(value: str) -> datetime | None:
    from .evidence import parse_occurred_at

    return parse_occurred_at(value)


def read_rows(filename: str, data: bytes) -> tuple[list[str], list[dict[str, str]]]:
    """Lit un CSV ou un XLSX (openpyxl) et renvoie (en-têtes, lignes)."""
    lower = filename.lower()
    if lower.endswith((".xlsx", ".xlsm")):
        try:
            import openpyxl
        except ImportError as exc:  # pragma: no cover
            raise ValueError(
                "Import Excel indisponible : le module openpyxl n'est pas installé sur le serveur. "
                "Convertissez le fichier en CSV."
            ) from exc
        wb = openpyxl.load_workbook(io.BytesIO(data), read_only=True, data_only=True)
        ws = wb.active
        rows = list(ws.iter_rows(values_only=True))
        if not rows:
            return [], []
        headers = [str(c) if c is not None else "" for c in rows[0]]
        out = []
        for r in rows[1:]:
            if r is None or all(c is None or str(c).strip() == "" for c in r):
                continue
            out.append({headers[i]: ("" if r[i] is None else str(r[i]).strip()) for i in range(min(len(headers), len(r)))})
        wb.close()
        return headers, out

    text = None
    for enc in ("utf-8-sig", "utf-8", "latin-1"):
        try:
            text = data.decode(enc)
            break
        except UnicodeDecodeError:
            continue
    if text is None:
        raise ValueError("Fichier illisible : encodage non reconnu (utilisez UTF-8).")

    sample = text[:4096]
    try:
        dialect = csv.Sniffer().sniff(sample, delimiters=",;\t|")
    except csv.Error:
        dialect = csv.excel
    reader = csv.DictReader(io.StringIO(text), dialect=dialect)
    headers = [h for h in (reader.fieldnames or []) if h is not None]
    return headers, [{(k or ""): (v or "").strip() for k, v in row.items()} for row in reader]


def validate_rows(headers: list[str], rows: list[dict[str, str]]) -> list[ImportRow]:
    mapping = detect_columns(headers)
    if "target_phone" not in mapping.values():
        raise ValueError(
            "Colonne « numéro » introuvable. En-têtes attendus : numero, categorie, date, description, preuve."
        )
    if "category" not in mapping.values():
        raise ValueError("Colonne « categorie » introuvable : l'import sans catégorie d'infraction est refusé.")
    if "evidence_ref" not in mapping.values() and "message_ids" not in mapping.values():
        raise ValueError(
            "Aucune colonne de preuve (preuve / message_id) : l'import sans preuve est refusé par principe."
        )

    out: list[ImportRow] = []
    for idx, raw in enumerate(rows, start=2):
        row = ImportRow(line=idx, raw=raw)
        for header, canon in mapping.items():
            value = raw.get(header, "")
            if canon == "target_phone":
                digits = PHONE_DIGITS.sub("", value)
                if not digits:
                    row.errors.append("Numéro manquant." if not value.strip() else f"Numéro invalide « {value} ».")
                elif not 8 <= len(digits) <= 15:
                    row.errors.append(f"Numéro invalide « {value} » (8 à 15 chiffres attendus).")
                else:
                    row.target_phone = "+" + digits
            elif canon == "category":
                cat = _parse_category(value)
                if not cat:
                    row.errors.append(
                        f"Catégorie d'infraction manquante ou inconnue « {value} » : "
                        "spam, arnaque financière, usurpation d'identité, harcèlement, discours haineux, contenu illégal."
                    )
                else:
                    row.category = cat
            elif canon == "occurred_at":
                if value:
                    parsed = _parse_date(value)
                    if parsed is None:
                        row.errors.append(f"Date illisible « {value} » (ex. 2026-03-12 14:22).")
                    else:
                        row.occurred_at = parsed
            elif canon == "description":
                row.description = value
            elif canon == "evidence_ref":
                row.evidence_ref = value
            elif canon == "message_ids":
                row.message_ids = [p for p in MESSAGE_ID_SPLIT.split(value) if p]
            elif canon == "contact_ok":
                if value.strip().lower() in ("non", "no", "0", "false", "faux"):
                    row.errors.append("L'utilisateur déclare ne pas avoir été contacté par ce numéro.")

        if not row.evidence_ref and not row.message_ids:
            row.errors.append(
                "Preuve manquante : renseignez la colonne « preuve » (nom du fichier justificatif à joindre) "
                "ou « message_id ». Aucun signalement n'est envoyé sans preuve."
            )
        if not row.description:
            row.errors.append("Description courte manquante.")
        elif len(row.description) < 10:
            row.errors.append("Description trop courte (10 caractères minimum).")
        if row.occurred_at is None and not any("Date" in e for e in row.errors):
            row.errors.append("Date et heure de réception manquantes.")
        if row.occurred_at and row.occurred_at > datetime.now(timezone.utc):
            row.errors.append("Date d'infraction dans le futur.")

        out.append(row)
    return out


def template_csv() -> str:
    header = "numero,categorie,date,description,preuve,message_id"
    examples = [
        '+22361234567,arnaque financière,2026-03-12 14:22,"Faux vendeur : demande d\'acompte Wave pour un colis inexistant",capture_20260312.png,false_22361234567@c.us_3EB0A1B2C3D4',
        '+22367788990,spam,2026-03-14 09:05,"Envoi massif de messages publicitaires non sollicités",export_conversation.txt,',
    ]
    return header + "\n" + "\n".join(examples) + "\n"


def rows_to_json(rows: list[ImportRow]) -> list[dict]:
    return [
        {
            "line": r.line,
            "target_phone": r.target_phone,
            "category": r.category,
            "occurred_at": r.occurred_at.isoformat() if r.occurred_at else None,
            "description": r.description,
            "evidence_ref": r.evidence_ref or None,
            "message_ids": r.message_ids,
            "valid": r.valid,
            "errors": r.errors,
        }
        for r in rows
    ]


def to_json(rows: list[ImportRow]) -> str:
    return json.dumps(rows_to_json(rows), ensure_ascii=False)
