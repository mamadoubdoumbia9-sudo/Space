"""Stockage des preuves, chiffré au repos (AES-256-GCM), avec abstraction S3.

- `local` : répertoire sur disque (dev, instance unique, ou volume monté).
- `s3`    : stockage objet compatible S3 / MinIO / Firebase-équivalent.

Dans les deux cas, le contenu est chiffré AVANT écriture : même si le bucket ou
le disque fuite, les captures d'écran et exports de conversations restent illisibles.
"""
from __future__ import annotations

import os
from pathlib import Path
from typing import Protocol

from ..config import get_settings
from ..security import decrypt_bytes, encrypt_bytes, sha256_hex


class Storage(Protocol):
    def put(self, key: str, data: bytes) -> str: ...
    def get(self, key: str) -> bytes: ...
    def delete(self, key: str) -> None: ...
    def exists(self, key: str) -> bool: ...


class LocalEncryptedStorage:
    name = "local-aesgcm"

    def __init__(self, root: str | None = None):
        self.root = Path(root or get_settings().storage_dir)
        self.root.mkdir(parents=True, exist_ok=True)

    def _path(self, key: str) -> Path:
        safe = key.replace("..", "_").lstrip("/")
        p = self.root / safe
        p.parent.mkdir(parents=True, exist_ok=True)
        return p

    def put(self, key: str, data: bytes) -> str:
        blob = encrypt_bytes(data)
        path = self._path(key)
        tmp = path.with_suffix(path.suffix + ".tmp")
        tmp.write_bytes(blob)
        os.replace(tmp, path)
        # vérification d'intégrité après écriture (lecture réelle)
        if sha256_hex(self.get(key)) != sha256_hex(data):
            raise IOError("Échec de vérification d'intégrité après écriture")
        return key

    def get(self, key: str) -> bytes:
        return decrypt_bytes(self._path(key).read_bytes())

    def delete(self, key: str) -> None:
        p = self._path(key)
        if p.exists():
            p.unlink()

    def exists(self, key: str) -> bool:
        return self._path(key).exists()


class S3EncryptedStorage:
    name = "s3-aesgcm"

    def __init__(self) -> None:
        try:
            import boto3  # noqa: PLC0415
        except ImportError as exc:  # pragma: no cover
            raise RuntimeError("boto3 requis pour STORAGE_BACKEND=s3 (pip install boto3)") from exc
        s = get_settings()
        self.bucket = s.s3_bucket
        self.client = boto3.client(
            "s3",
            region_name=s.s3_region or None,
            endpoint_url=s.s3_endpoint or None,
            aws_access_key_id=s.s3_access_key or None,
            aws_secret_access_key=s.s3_secret_key or None,
        )

    def put(self, key: str, data: bytes) -> str:
        self.client.put_object(Bucket=self.bucket, Key=key, Body=encrypt_bytes(data))
        return key

    def get(self, key: str) -> bytes:
        obj = self.client.get_object(Bucket=self.bucket, Key=key)
        return decrypt_bytes(obj["Body"].read())

    def delete(self, key: str) -> None:
        self.client.delete_object(Bucket=self.bucket, Key=key)

    def exists(self, key: str) -> bool:
        try:
            self.client.head_object(Bucket=self.bucket, Key=key)
            return True
        except Exception:  # noqa: BLE001
            return False


_storage: Storage | None = None


def get_storage() -> Storage:
    global _storage
    if _storage is None:
        s = get_settings()
        _storage = S3EncryptedStorage() if s.storage_backend == "s3" else LocalEncryptedStorage()
    return _storage


def make_key(user_id: int, report_ref: str, filename: str) -> str:
    import uuid

    ext = Path(filename or "preuve.bin").suffix.lower()[:10]
    return f"evidence/{user_id}/{report_ref}/{uuid.uuid4().hex}{ext}"
