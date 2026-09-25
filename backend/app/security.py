"""Sécurité : mots de passe, jetons, chiffrement au repos, index aveugles.

- Mots de passe / codes : bcrypt.
- Jetons : JWT HS256 (accès court + rafraîchissement).
- Numéros de téléphone et données sensibles : AES-256-GCM au repos.
  La recherche d'un numéro se fait via un index aveugle HMAC-SHA256
  (le numéro n'est jamais stocké en clair, il reste recherchable).
"""
from __future__ import annotations

import base64
import hashlib
import hmac
import os
import secrets
import time
from datetime import datetime, timedelta, timezone
from typing import Any

import bcrypt
import jwt
from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

from .config import get_settings

ALGO = "HS256"


# --- Mots de passe ---------------------------------------------------------
def hash_password(password: str) -> str:
    return bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt(rounds=12)).decode()


def verify_password(password: str, hashed: str) -> bool:
    try:
        return bcrypt.checkpw(password.encode("utf-8"), hashed.encode())
    except (ValueError, TypeError):
        return False


def generate_code(n_digits: int = 6) -> str:
    return "".join(secrets.choice("0123456789") for _ in range(n_digits))


def hash_code(code: str, purpose: str) -> str:
    """Les codes de vérification ne sont jamais stockés en clair."""
    s = get_settings()
    return hmac.new(s.fingerprint_key.encode(), f"{purpose}:{code}".encode(), hashlib.sha256).hexdigest()


# --- Jetons -----------------------------------------------------------------
def _secret() -> str:
    return get_settings().jwt_secret


def create_token(subject: str, token_type: str, ttl: timedelta, **claims: Any) -> str:
    now = datetime.now(timezone.utc)
    payload = {
        "sub": subject,
        "typ": token_type,
        "iat": int(now.timestamp()),
        "exp": int((now + ttl).timestamp()),
        "jti": secrets.token_hex(8),
        **claims,
    }
    return jwt.encode(payload, _secret(), algorithm=ALGO)


def create_access_token(user_id: int, role: str) -> str:
    s = get_settings()
    return create_token(str(user_id), "access", timedelta(minutes=s.jwt_access_ttl_min), role=role)


def create_refresh_token(user_id: int) -> str:
    s = get_settings()
    return create_token(str(user_id), "refresh", timedelta(days=s.jwt_refresh_ttl_days))


def decode_token(token: str) -> dict[str, Any]:
    return jwt.decode(token, _secret(), algorithms=[ALGO])


# --- Chiffrement des données au repos --------------------------------------
def _aes() -> AESGCM:
    return AESGCM(get_settings().field_key)


def encrypt_str(plaintext: str | None) -> str | None:
    if plaintext is None:
        return None
    nonce = os.urandom(12)
    ct = _aes().encrypt(nonce, plaintext.encode("utf-8"), b"signalpro")
    return "v1:" + base64.urlsafe_b64encode(nonce + ct).decode()


def decrypt_str(ciphertext: str | None) -> str | None:
    if ciphertext is None:
        return None
    if not ciphertext.startswith("v1:"):
        # Valeur héritée non chiffrée : on la renvoie telle quelle (migration douce).
        return ciphertext
    blob = base64.urlsafe_b64decode(ciphertext[3:].encode())
    try:
        return _aes().decrypt(blob[:12], blob[12:], b"signalpro").decode("utf-8")
    except InvalidTag as exc:  # clé changée ou donnée corrompue
        raise ValueError("Donnée chiffrée illisible (clé FIELD_KEY_B64 différente ?)") from exc


def encrypt_bytes(data: bytes) -> bytes:
    nonce = os.urandom(12)
    return nonce + _aes().encrypt(nonce, data, b"signalpro-evidence")


def decrypt_bytes(blob: bytes) -> bytes:
    return _aes().decrypt(blob[:12], blob[12:], b"signalpro-evidence")


# --- Index aveugles (recherche sans stockage en clair) ---------------------
def fingerprint(value: str) -> str:
    """Index aveugle HMAC : identifie une valeur sans jamais la stocker en clair.

    Les numéros sont normalisés en E.164 (+223 61… == 22361…), les autres valeurs
    (emails, identifiants WhatsApp) sont normalisées en minuscules. Sans cette
    distinction, deux emails distincts produiraient la même empreinte.
    """
    s = get_settings()
    raw = (value or "").strip()
    looks_like_phone = bool(raw) and all(ch.isdigit() or ch in "+ .-()" for ch in raw)
    normalized = normalize_phone(raw) if looks_like_phone else raw.lower()
    return hmac.new(s.fingerprint_key.encode(), normalized.encode(), hashlib.sha256).hexdigest()


def normalize_phone(value: str) -> str:
    """Normalise un numéro E.164 : +2236XXXXXXXX -> 2236XXXXXXXX."""
    cleaned = "".join(ch for ch in value.strip() if ch.isdigit() or ch == "+")
    if cleaned.startswith("+"):
        cleaned = cleaned[1:]
    return cleaned


def is_valid_e164(value: str) -> bool:
    v = normalize_phone(value)
    return 8 <= len(v) <= 15 and v.isdigit()


def sha256_hex(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


# --- Signature des appels à la passerelle locale ---------------------------
def sign_connector_request(method: str, path: str, body: bytes, ts: int | None = None) -> dict[str, str]:
    s = get_settings()
    ts = ts or int(time.time())
    mac = hmac.new(
        s.connector_shared_secret.encode(), b"%s\n%s\n%d\n" % (method.encode(), path.encode(), ts) + body, hashlib.sha256
    ).hexdigest()
    return {"X-SignalPro-Timestamp": str(ts), "X-SignalPro-Signature": mac}


def verify_connector_signature(
    secret: str, method: str, path: str, body: bytes, ts: str, signature: str, max_skew: int = 300
) -> bool:
    try:
        ts_i = int(ts)
    except (TypeError, ValueError):
        return False
    if abs(int(time.time()) - ts_i) > max_skew:
        return False
    expected = hmac.new(
        secret.encode(), b"%s\n%s\n%d\n" % (method.encode(), path.encode(), ts_i) + body, hashlib.sha256
    ).hexdigest()
    return hmac.compare_digest(expected, signature or "")
