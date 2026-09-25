"""Application des limites anti-abus — NON désactivables, appliquées côté serveur.

Les compteurs vivent en base (`usage_counters`) : redémarrer l'API ou changer de
client ne remet aucun quota à zéro. Chaque limite refusée est journalisée.
"""
from __future__ import annotations

import datetime as dt

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..config import get_settings
from ..models import UsageCounter


class RateLimitExceeded(Exception):
    def __init__(self, limit: int, window: str, action: str, retry_after_s: int):
        self.limit = limit
        self.window = window
        self.action = action
        self.retry_after_s = retry_after_s
        super().__init__(
            f"Limite atteinte : {limit} {action} par {window}. Réessayez dans {retry_after_s // 60 + 1} minute(s)."
        )


def _window_key(window: str, now: dt.datetime) -> tuple[str, int]:
    if window == "minute":
        key = now.strftime("%Y-%m-%dT%H:%M")
        retry = 60 - now.second
    elif window == "hour":
        key = now.strftime("%Y-%m-%dT%H")
        retry = 3600 - (now.minute * 60 + now.second)
    elif window == "day":
        key = now.strftime("%Y-%m-%d")
        nxt = (now + dt.timedelta(days=1)).replace(hour=0, minute=0, second=0, microsecond=0)
        retry = int((nxt - now).total_seconds())
    else:  # pragma: no cover
        raise ValueError(window)
    return key, retry


def peek(db: Session, user_id: int, action: str, window: str) -> int:
    key, _ = _window_key(window, dt.datetime.now(dt.timezone.utc))
    row = db.execute(
        select(UsageCounter).where(
            UsageCounter.user_id == user_id, UsageCounter.action == action, UsageCounter.window_key == key
        )
    ).scalar_one_or_none()
    return row.count if row else 0


def consume(db: Session, user_id: int, action: str, window: str, limit: int, cost: int = 1) -> int:
    """Consomme `cost` unités ou lève RateLimitExceeded. Retourne le total après consommation."""
    now = dt.datetime.now(dt.timezone.utc)
    key, retry = _window_key(window, now)
    row = db.execute(
        select(UsageCounter).where(
            UsageCounter.user_id == user_id, UsageCounter.action == action, UsageCounter.window_key == key
        )
    ).scalar_one_or_none()
    if row is None:
        row = UsageCounter(user_id=user_id, action=action, window_key=key, count=0)
        db.add(row)
    if row.count + cost > limit:
        db.flush()
        raise RateLimitExceeded(limit=limit, window=window, action=action, retry_after_s=retry)
    row.count += cost
    row.updated_at = now
    db.flush()
    return row.count


# --------------------------------------------------------------------------
# Politiques nommées (une seule source de vérité, réutilisée par tous les routeurs)
# --------------------------------------------------------------------------
def consume_report_creation(db: Session, user_id: int, count: int = 1) -> dict[str, int]:
    """Au plus 5 signalements/heure et 20/jour — plafonds durs."""
    s = get_settings()
    hourly = consume(db, user_id, "report_create", "hour", s.max_reports_per_hour_user, cost=count)
    daily = consume(db, user_id, "report_create", "day", s.max_reports_per_day_user, cost=count)
    return {"hourly": hourly, "daily": daily}


def consume_action(db: Session, user_id: int, action: str, cost: int = 1) -> int:
    """10 actions/minute maximum par compte utilisateur (contrainte explicite du projet)."""
    s = get_settings()
    return consume(db, user_id, f"action:{action}", "minute", s.max_actions_per_minute_user, cost=cost)


def consume_campaign(db: Session, user_id: int) -> int:
    s = get_settings()
    return consume(db, user_id, "campaign_create", "day", s.campaign_max_targets_per_day)


def consume_pairing(db: Session, user_id: int) -> int:
    s = get_settings()
    return consume(db, user_id, "pairing", "day", s.max_pairing_per_day)


def usage_snapshot(db: Session, user_id: int, strikes: int, status: str) -> dict[str, object]:
    s = get_settings()
    hour = peek(db, user_id, "report_create", "hour")
    day = peek(db, user_id, "report_create", "day")
    return {
        "reports_last_hour": hour,
        "reports_last_day": day,
        "hourly_limit": s.max_reports_per_hour_user,
        "daily_limit": s.max_reports_per_day_user,
        "remaining_today": max(0, s.max_reports_per_day_user - day),
        "strikes": strikes,
        "status": status,
        "ban_threshold": s.max_abusive_strikes,
    }
