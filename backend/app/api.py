"""Dépendances d'API : authentification, rôles, extraction de contexte requête."""
from __future__ import annotations

from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy import select
from sqlalchemy.orm import Session

from .constants import Role, UserStatus
from .db import get_db
from .models import User
from .security import decode_token

bearer = HTTPBearer(auto_error=False)


class AuthError(HTTPException):
    def __init__(self, detail: str, code: int = status.HTTP_401_UNAUTHORIZED):
        super().__init__(status_code=code, detail=detail)


def client_ip(request: Request) -> str | None:
    fwd = request.headers.get("x-forwarded-for")
    if fwd:
        return fwd.split(",")[0].strip()
    return request.client.host if request.client else None


def current_user(
    request: Request,
    creds: HTTPAuthorizationCredentials | None = Depends(bearer),
    db: Session = Depends(get_db),
) -> User:
    if creds is None or not creds.credentials:
        raise AuthError("Authentification requise.")
    try:
        payload = decode_token(creds.credentials)
    except Exception as exc:  # noqa: BLE001
        raise AuthError("Session expirée ou jeton invalide. Reconnectez-vous.") from exc
    if payload.get("typ") != "access":
        raise AuthError("Jeton d'accès attendu.")
    user = db.get(User, int(payload["sub"]))
    if user is None:
        raise AuthError("Compte introuvable.")
    if user.status == UserStatus.BANNED:
        raise AuthError(
            "Votre compte est banni définitivement : des signalements abusifs ont été confirmés "
            "après vérification. Contactez le support pour contester cette décision.",
            code=status.HTTP_403_FORBIDDEN,
        )
    request.state.user_id = user.id
    return user


def active_user(user: User = Depends(current_user)) -> User:
    if user.status != UserStatus.ACTIVE or not user.is_verified:
        raise AuthError(
            "Compte non vérifié : confirmez le code envoyé par email ou SMS pour accéder à cette fonctionnalité.",
            code=status.HTTP_403_FORBIDDEN,
        )
    return user


def moderator_user(user: User = Depends(active_user)) -> User:
    if user.role not in (Role.MODERATOR, Role.ADMIN):
        raise AuthError("Réservé aux modérateurs.", code=status.HTTP_403_FORBIDDEN)
    return user


def admin_user(user: User = Depends(active_user)) -> User:
    if user.role != Role.ADMIN:
        raise AuthError("Réservé aux administrateurs.", code=status.HTTP_403_FORBIDDEN)
    return user


def find_user_by_email(db: Session, email: str) -> User | None:
    from .security import fingerprint

    return db.execute(select(User).where(User.email_fp == fingerprint(email))).scalar_one_or_none()


def find_user_by_phone(db: Session, phone: str) -> User | None:
    from .security import fingerprint

    return db.execute(select(User).where(User.phone_fp == fingerprint(phone))).scalar_one_or_none()
