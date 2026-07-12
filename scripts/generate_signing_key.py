#!/usr/bin/env python3
"""Create a reproducible development signing key for this permission-free offline app.
The key and certificate are deterministic so future sideloaded v3 builds can update.
It is not intended for Play Store production distribution.
"""
import datetime
import hashlib
import sys
from pathlib import Path
from Crypto.PublicKey import RSA
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.serialization import pkcs12
from cryptography.x509.oid import NameOID

out = Path(sys.argv[1])
password = sys.argv[2].encode("utf-8")
seed = hashlib.sha256(b"groen-op-de-balans-v3-stable-development-signing-key").digest()

class DeterministicRandom:
    def __init__(self, seed_bytes):
        self.seed = seed_bytes
        self.counter = 0
    def __call__(self, n):
        data = bytearray()
        while len(data) < n:
            data.extend(hashlib.sha512(self.seed + self.counter.to_bytes(8, "big")).digest())
            self.counter += 1
        return bytes(data[:n])

rsa_key = RSA.generate(2048, randfunc=DeterministicRandom(seed), e=65537)
key = serialization.load_pem_private_key(rsa_key.export_key(format="PEM"), password=None)
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
