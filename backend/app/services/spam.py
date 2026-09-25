"""Détection de spam / arnaque — moteur réel exécuté localement.

Deux usages :
1. côté appareil : l'application Android télécharge les signatures (`/detect/signatures`)
   et analyse les conversations localement ;
2. côté serveur : analyse d'un extrait que l'utilisateur soumet explicitement
   (`/detect/scan`) — jamais un scraping silencieux de ses conversations.

Les correspondances de numéros se font par empreinte HMAC : la liste des numéros
signalés ne circule jamais en clair vers les clients.
"""
from __future__ import annotations

import hashlib
import re
import unicodedata

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from ..constants import TargetStatus
from ..models import SpamSignature, Target
from ..security import fingerprint

# --- Signatures de départ (arnaques fréquentes en Afrique de l'Ouest / francophone)
SEED_KEYWORDS: list[tuple[str, int, str]] = [
    ("votre colis est bloque", 3, "financial_scam"),
    ("frais de douane", 3, "financial_scam"),
    ("vous avez gagne", 2, "financial_scam"),
    ("vous etes le gagnant", 3, "financial_scam"),
    ("gagnez un iphone", 2, "financial_scam"),
    ("cliquez ici pour recevoir", 3, "financial_scam"),
    ("compte suspendu verifiez", 3, "impersonation"),
    ("verifiez votre compte", 2, "impersonation"),
    ("mot de passe expire", 3, "impersonation"),
    ("code de verification", 2, "impersonation"),
    ("je suis votre fils", 3, "impersonation"),
    ("maman jai un probleme", 3, "impersonation"),
    ("papa envoie moi de largent", 3, "impersonation"),
    ("transfert mobile money", 2, "financial_scam"),
    ("orange money", 2, "financial_scam"),
    ("wave transfert", 2, "financial_scam"),
    ("moov money", 2, "financial_scam"),
    ("bitcoin double", 3, "financial_scam"),
    ("investissement garanti", 3, "financial_scam"),
    ("doublez votre argent", 3, "financial_scam"),
    ("profit garanti", 3, "financial_scam"),
    ("crypto gratuit", 2, "financial_scam"),
    ("pret rapide sans garantie", 2, "financial_scam"),
    ("offre d emploi urgente", 2, "financial_scam"),
    ("travail a domicile paye", 2, "financial_scam"),
    ("salaire 500000", 2, "financial_scam"),
    ("envoyez votre numero de carte", 3, "financial_scam"),
    ("code pin", 3, "financial_scam"),
    ("numero de compte bancaire", 3, "financial_scam"),
    ("je vais te tuer", 3, "harassment"),
    ("tu vas le regretter", 3, "harassment"),
    ("je connais ta famille", 3, "harassment"),
    ("photo intime", 3, "harassment"),
    ("je vais publier tes photos", 3, "harassment"),
]

SEED_DOMAINS: list[str] = [
    "bit.ly", "tinyurl.com", "cutt.ly", "shorturl.at", "t.ly",
    "wa-link.info", "whatsapp-verify.net", "whatsapp-secure-login.com",
    "free-recharge.top", "crypto-gift.xyz", "promo-mali.xyz",
]

SEED_PATTERNS: list[tuple[str, int, str]] = [
    (r"wa\.me/\+?\d{6,15}", 1, "spam"),
    (r"\bhttps?://[a-z0-9\-]{1,40}\.(?:tk|ml|ga|cf|gq|xyz|top|cfd|sbs)\b", 3, "financial_scam"),
    (r"\b\+?\d{8,15}\b.{0,40}(?:appel|appelez|whatsapp)", 2, "spam"),
    (r"(?:gagne|remporte|clique).{0,30}(?:lien|link|ici)", 2, "spam"),
    (r"\d+\s*(?:%|pour cent).{0,20}(?:par (?:jour|semaine|mois)|profit|rendement)", 3, "financial_scam"),
]


def normalize_text(text: str) -> str:
    t = unicodedata.normalize("NFKD", text or "").encode("ascii", "ignore").decode().lower()
    return re.sub(r"\s+", " ", t)


def seed_signatures(db: Session) -> int:
    """Installe/maintient les signatures de base (idempotent)."""
    created = 0
    existing = {
        (k, h) for k, h in db.execute(select(SpamSignature.kind, SpamSignature.value_hash)).all()
    }
    for value, severity, category in SEED_KEYWORDS:
        h = hashlib.sha256(normalize_text(value).encode()).hexdigest()
        if ("keyword", h) in existing:
            continue
        db.add(
            SpamSignature(
                kind="keyword", value_hash=h, value_display=value, severity=severity, category=category, source="seed"
            )
        )
        created += 1
    for domain in SEED_DOMAINS:
        h = hashlib.sha256(domain.encode()).hexdigest()
        if ("domain", h) in existing:
            continue
        db.add(
            SpamSignature(
                kind="domain", value_hash=h, value_display=domain, severity=3, category="financial_scam", source="seed"
            )
        )
        created += 1
    for pattern, severity, category in SEED_PATTERNS:
        h = hashlib.sha256(pattern.encode()).hexdigest()
        if ("regex", h) in existing:
            continue
        db.add(
            SpamSignature(
                kind="regex", value_hash=h, value_display=pattern, severity=severity, category=category, source="seed"
            )
        )
        created += 1
    db.flush()
    return created


def active_signatures(db: Session) -> list[SpamSignature]:
    return list(db.execute(select(SpamSignature).where(SpamSignature.active.is_(True))).scalars())


def signatures_fingerprint(db: Session) -> str:
    rows = db.execute(
        select(SpamSignature.kind, SpamSignature.value_hash, SpamSignature.active).order_by(SpamSignature.id)
    ).all()
    h = hashlib.sha256()
    for kind, value_hash, active in rows:
        h.update(f"{kind}:{value_hash}:{int(active)};".encode())
    return h.hexdigest()[:16]


def scan_text(db: Session, text: str, peer_phone: str | None = None) -> dict:
    """Analyse un texte fourni par l'utilisateur et renvoie les correspondances réelles."""
    normalized = normalize_text(text)
    matches: list[dict] = []
    score = 0.0

    for sig in active_signatures(db):
        if sig.kind == "keyword":
            needle = sig.value_display
            if needle and needle in normalized:
                matches.append(
                    {"kind": "keyword", "value": needle, "severity": sig.severity, "category": sig.category}
                )
                score += 2.0 * sig.severity
        elif sig.kind == "domain":
            if sig.value_display in text.lower():
                matches.append(
                    {"kind": "domain", "value": sig.value_display, "severity": sig.severity, "category": sig.category}
                )
                score += 3.0 * sig.severity
        elif sig.kind == "regex":
            if re.search(sig.value_display, text, re.IGNORECASE):
                matches.append(
                    {"kind": "pattern", "value": sig.value_display[:60], "severity": sig.severity, "category": sig.category}
                )
                score += 2.5 * sig.severity

    # Numéros cités dans le message déjà signalés par la communauté
    phones = set()
    for raw in re.findall(r"\+?\d[\d\s().-]{7,17}\d", text):
        digits = re.sub(r"\D", "", raw)
        if 8 <= len(digits) <= 15:
            phones.add(digits)
    if peer_phone:
        phones.add(re.sub(r"\D", "", peer_phone))
    for digits in phones:
        target = db.execute(
            select(Target).where(Target.phone_fp == fingerprint(digits))
        ).scalar_one_or_none()
        if target and target.verified_reports >= 1:
            matches.append(
                {
                    "kind": "reported_number",
                    "value": "+" + digits[:3] + "…" + digits[-2:],
                    "severity": 3,
                    "category": target.main_category or "spam",
                    "verified_reports": target.verified_reports,
                }
            )
            score += 6.0 + min(target.verified_reports, 5)

    is_suspicious = score >= 4.0 or any(m["kind"] == "reported_number" for m in matches)
    peer_known_malicious = False
    peer_reported_count = 0
    if peer_phone:
        tgt = db.execute(select(Target).where(Target.phone_fp == fingerprint(peer_phone))).scalar_one_or_none()
        if tgt:
            peer_known_malicious = tgt.verified_reports > 0 and tgt.status == TargetStatus.CONFIRMED_MALICIOUS
            peer_reported_count = tgt.verified_reports

    advice: list[str] = []
    if is_suspicious:
        advice.append("Ne cliquez sur aucun lien et n'envoyez aucun code de vérification ni argent.")
        advice.append("Conservez la conversation : elle sert de preuve si vous signalez ce numéro.")
        if peer_known_malicious:
            advice.append("Ce numéro est déjà confirmé malveillant par la communauté : bloquez-le dès maintenant.")
    return {
        "score": round(score, 2),
        "is_suspicious": is_suspicious,
        "matches": matches[:40],
        "peer_known_malicious": peer_known_malicious,
        "peer_reported_count": peer_reported_count,
        "advice": advice,
    }


def community_stats(db: Session) -> dict:
    total_targets = db.execute(select(func.count(Target.id))).scalar_one()
    confirmed = db.execute(
        select(func.count(Target.id)).where(Target.verified_reports > 0)
    ).scalar_one()
    suspended = db.execute(
        select(func.count(Target.id)).where(Target.suspension_status == "suspended_confirmed")
    ).scalar_one()
    return {"targets_total": total_targets, "targets_verified": confirmed, "suspensions_confirmed": suspended}
