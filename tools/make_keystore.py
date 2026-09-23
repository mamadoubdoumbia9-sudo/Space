#!/usr/bin/env python3
"""Génère une clé RSA 2048 + certificat auto-signé (PEM) pour signer l'APK.
   build/keys/release.key.pem / release.cert.pem (ignorés par git). Pour une clé de production,
   déposer vos propres fichiers PEM au même endroit (ou pointer RELEASE_KEY / RELEASE_CERT)."""
import datetime, os, sys
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KEYS = os.path.join(ROOT, "build", "keys")


def ensure():
    os.makedirs(KEYS, exist_ok=True)
    kp = os.environ.get("RELEASE_KEY", os.path.join(KEYS, "release.key.pem"))
    cp = os.environ.get("RELEASE_CERT", os.path.join(KEYS, "release.cert.pem"))
    if os.path.exists(kp) and os.path.exists(cp):
        return kp, cp
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    name = x509.Name([
        x509.NameAttribute(NameOID.COUNTRY_NAME, "FR"),
        x509.NameAttribute(NameOID.ORGANIZATION_NAME, "Atelier Marée Basse"),
        x509.NameAttribute(NameOID.COMMON_NAME, "La Cartographie des Absents — release"),
    ])
    now = datetime.datetime.now(datetime.timezone.utc)
    cert = (x509.CertificateBuilder().subject_name(name).issuer_name(name).public_key(key.public_key())
            .serial_number(x509.random_serial_number()).not_valid_before(now - datetime.timedelta(days=1))
            .not_valid_after(now + datetime.timedelta(days=365 * 30)).sign(key, hashes.SHA256()))
    with open(kp, "wb") as f:
        f.write(key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
    with open(cp, "wb") as f:
        f.write(cert.public_bytes(serialization.Encoding.PEM))
    print("clé de signature générée :", kp)
    return kp, cp


if __name__ == "__main__":
    ensure()
