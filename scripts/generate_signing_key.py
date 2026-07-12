#!/usr/bin/env python3
"""Create a reproducible development signing key for this permission-free offline app.
The key is intentionally deterministic so future sideloaded v3 builds can update.
It is not intended for Play Store production distribution.
"""
import datetime
import hashlib
import sys
from pathlib import Path
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.serialization import pkcs12
from cryptography.x509.oid import NameOID

out = Path(sys.argv[1])
password = sys.argv[2].encode("utf-8")
seed = hashlib.sha256(b"groen-op-de-balans-v3-stable-development-signing-key").digest()
order = int("FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551", 16)
scalar = (int.from_bytes(seed, "big") % (order - 1)) + 1
key = ec.derive_private_key(scalar, ec.SECP256R1())
name = x509.Name([
    x509.NameAttribute(NameOID.COUNTRY_NAME, "NL"),
    x509.NameAttribute(NameOID.ORGANIZATION_NAME, "Groen op de Balans offline app"),
    x509.NameAttribute(NameOID.COMMON_NAME, "Groen op de Balans v3"),
])
serial = int.from_bytes(hashlib.sha256(b"groen-op-de-balans-v3-cert").digest()[:20], "big") >> 1
not_before = datetime.datetime(2025, 1, 1, tzinfo=datetime.timezone.utc)
not_after = datetime.datetime(2050, 1, 1, tzinfo=datetime.timezone.utc)
cert = (
    x509.CertificateBuilder()
    .subject_name(name)
    .issuer_name(name)
    .public_key(key.public_key())
    .serial_number(serial)
    .not_valid_before(not_before)
    .not_valid_after(not_after)
    .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
    .sign(key, hashes.SHA256())
)
out.write_bytes(pkcs12.serialize_key_and_certificates(
    name=b"groenbalans",
    key=key,
    cert=cert,
    cas=None,
    encryption_algorithm=serialization.BestAvailableEncryption(password),
))
print(f"Created stable development signing key: {out}")
