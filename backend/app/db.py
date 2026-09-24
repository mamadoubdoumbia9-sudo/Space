"""Accès base de données (SQLAlchemy 2.0). SQLite en dev/test, PostgreSQL en production."""
from __future__ import annotations

import os
from collections.abc import Iterator

from sqlalchemy import create_engine, event
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from .config import get_settings


class Base(DeclarativeBase):
    pass


def _engine_kwargs(url: str) -> dict:
    if url.startswith("sqlite"):
        return {"connect_args": {"check_same_thread": False}, "pool_pre_ping": True}
    return {"pool_pre_ping": True, "pool_size": 10, "max_overflow": 20}


_settings = get_settings()
_url = os.environ.get("DATABASE_URL", _settings.database_url)
engine = create_engine(_url, future=True, **_engine_kwargs(_url))
SessionLocal = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False, class_=Session)

if _url.startswith("sqlite"):
    @event.listens_for(engine, "connect")
    def _sqlite_pragmas(dbapi_conn, _rec):  # pragma: no cover - spécifique sqlite
        cur = dbapi_conn.cursor()
        cur.execute("PRAGMA foreign_keys=ON")
        cur.execute("PRAGMA journal_mode=WAL")
        cur.close()


def get_db() -> Iterator[Session]:
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def init_db() -> None:
    from . import models  # noqa: F401  (enregistre les tables)

    Base.metadata.create_all(bind=engine)
