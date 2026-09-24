"""Journal d'audit (append-only) ; toute action sensible passe par ici."""
from __future__ import annotations

import json
from typing import Any

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..models import AuditLog


def log(
    db: Session,
    *,
    action: str,
    actor_user_id: int | None = None,
    actor_role: str | None = None,
    entity_type: str | None = None,
    entity_id: int | None = None,
    ip: str | None = None,
    user_agent: str | None = None,
    detail: dict[str, Any] | str | None = None,
) -> AuditLog:
    entry = AuditLog(
        actor_user_id=actor_user_id,
        actor_role=actor_role,
        action=action,
        entity_type=entity_type,
        entity_id=entity_id,
        ip=ip,
        user_agent=(user_agent or "")[:255] or None,
        detail=json.dumps(detail, ensure_ascii=False, default=str) if isinstance(detail, dict) else detail,
    )
    db.add(entry)
    db.flush()
    return entry


def recent(db: Session, limit: int = 200, action: str | None = None, entity_id: int | None = None) -> list[AuditLog]:
    stmt = select(AuditLog).order_by(AuditLog.id.desc()).limit(min(limit, 1000))
    if action:
        stmt = stmt.where(AuditLog.action == action)
    if entity_id is not None:
        stmt = stmt.where(AuditLog.entity_id == entity_id)
    return list(db.execute(stmt).scalars())
