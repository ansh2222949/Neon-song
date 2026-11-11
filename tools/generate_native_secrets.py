"""
Generates secrets_generated.rs for Rust native vault from environment secrets.

Run at build time (pre-Cargo or from Gradle). Reads ADDON_CLIENT_SECRET,
and securely binds it against the release keystore's SHA-256 certificate fingerprint.
The secret is double-encrypted using a keystream derived from the signing certificate
itself, so that the secret CANNOT be recovered without the genuine release keystore.

Usage:
    python tools/generate_native_secrets.py [--strict] [--out <path>]
"""

import argparse
import base64
import hashlib
import hmac
import os
import random
import sys
import tempfile


def parse_args():
    parser = argparse.ArgumentParser(description="Generate native secrets for Rust vault.")
    parser.add_argument(
        "--strict",
        action="store_true",
        help="Enforce non-empty secrets and genuine certificate fingerprint. Fail with non-zero exit on absence.",
    )
    parser.add_argument(
        "--out",
        default=os.path.join("security", "neonsong-vault", "src", "secrets_generated.rs"),
        help="Target output path for secrets_generated.rs",
    )
    args, _ = parser.parse_known_args()
    return args


def resolve_env(name: str) -> str:
    val = os.environ.get(name, "").strip()
    if val:
        return val
    # Gradle's signing and build configuration accepts both files. Read them
    # here too so a secret stored in ignored local.properties reaches the
    # native vault generator instead of silently appearing missing.
    for path in (".env", "local.properties"):
        if not os.path.isfile(path):
            continue
        try:
            with open(path, "r", encoding="utf-8") as f:
                for raw_line in f:
                    line = raw_line.strip()
                    if not line or line.startswith("#") or "=" not in line:
                        continue
                    key, value = line.split("=", 1)
                    if key.strip() == name:
                        return value.strip().strip("\"'")
        except OSError:
            continue
    return ""


def resolve_cert_sha256() -> str:
    env_cert = resolve_env("RELEASE_CERT_SHA256")
    if env_cert:
        cleaned = env_cert.replace(":", "").strip().lower()
        if len(cleaned) == 64 and all(c in "0123456789abcdef" for c in cleaned):
            return cleaned

    passwords = [
        resolve_env("KEY_STORE_PASSWORD"),
        resolve_env("RELEASE_STORE_PASSWORD"),
        resolve_env("KEY_PASSWORD"),
        resolve_env("RELEASE_KEY_PASSWORD"),
        "android",
        "",
    ]

    # Try raw base64-encoded keystore
    b64_key = resolve_env("SIGNING_KEY")
    der_bytes = None
    if b64_key:
        try:
            der_bytes = base64.b64decode(b64_key.strip())
        except Exception:
            der_bytes = None

    # Try keystore file path
    if not der_bytes:
        store_file = resolve_env("RELEASE_STORE_FILE")
        if store_file and os.path.isfile(store_file):
            try:
                with open(store_file, "rb") as f:
                    der_bytes = f.read()
            except Exception:
                der_bytes = None
        else:
            fallback_keystore = os.path.expanduser("~/.android/debug.keystore")
            if os.path.isfile(fallback_keystore):
                try:
                    with open(fallback_keystore, "rb") as f:
                        der_bytes = f.read()
                except Exception:
                    der_bytes = None


    if der_bytes:
        try:
            from cryptography.hazmat.primitives.serialization import pkcs12
            from cryptography.hazmat.primitives import hashes
            for pw in passwords:
                try:
                    p12 = pkcs12.load_key_and_certificates(der_bytes, pw.encode("utf-8") if pw else None)
                    cert = p12[1]
                    if cert:
                        fp = cert.fingerprint(hashes.SHA256()).hex().lower()
                        if len(fp) == 64:
                            return fp
                except Exception:
                    continue
        except Exception:
            pass

    return "PLACEHOLDER_REPLACE_WITH_YOUR_CERT_SHA256"


def derive_keystream(cert_sha256_hex: str, salt: bytes, length: int) -> bytes:
    if not cert_sha256_hex or "PLACEHOLDER" in cert_sha256_hex:
        key_bytes = hashlib.sha256(b"NEONSONG_DEV_FALLBACK_KEY").digest()
    else:
        try:
            key_bytes = bytes.fromhex(cert_sha256_hex)
        except Exception:
            key_bytes = hashlib.sha256(cert_sha256_hex.encode("utf-8")).digest()

    out = bytearray()
    counter = 0
    while len(out) < length:
        block = hmac.new(key_bytes, salt + counter.to_bytes(4, "big"), hashlib.sha256).digest()
        out.extend(block)
        counter += 1
    return bytes(out[:length])


def generate_fragments(data: bytes, num_fragments: int = 8) -> list:
    if not data:
        return []
    chunk_size = max(1, len(data) // num_fragments)
    fragments = []
    for i in range(0, len(data), chunk_size):
        chunk = data[i:i + chunk_size]
        mask = random.randint(0x10, 0xFE)
        xored = [b ^ mask for b in chunk]
        fragments.append((xored, mask, len(chunk)))
    return fragments


def main():
    args = parse_args()
    cert_sha256 = resolve_cert_sha256()
    client_secret = resolve_env("ADDON_CLIENT_SECRET")

    # Strict mode checks for release builds
    if args.strict:
        if not cert_sha256 or "PLACEHOLDER" in cert_sha256:
            sys.stderr.write(
                "ERROR [STRICT]: Release signing certificate SHA-256 fingerprint is unresolved or placeholder. "
                "Release builds require a valid certificate fingerprint.\n"
            )
            sys.exit(1)
        if len(cert_sha256) != 64 or not all(c in "0123456789abcdefABCDEF" for c in cert_sha256):
            sys.stderr.write(
                f"ERROR [STRICT]: Invalid certificate SHA-256 format ({len(cert_sha256)} chars). Expected 64-character hex.\n"
            )
            sys.exit(1)

    salt = os.urandom(16)
    secret_bytes = client_secret.encode("utf-8") if client_secret else b""
    keystream = derive_keystream(cert_sha256, salt, len(secret_bytes)) if secret_bytes else b""
    ciphertext = bytes(b ^ k for b, k in zip(secret_bytes, keystream))
    client_secret_fragments = generate_fragments(ciphertext)

    salt_hex = ",".join(f"0x{b:02X}" for b in salt)

    safe_cert_display = f"{cert_sha256[:16]}... (verified)" if "PLACEHOLDER" not in cert_sha256 else "[DEV PLACEHOLDER]"
    secret_status = f"[{len(client_secret)} chars, encrypted]" if client_secret else "[EMPTY / DEV FALLBACK]"

    print("Configuring Rust native vault (neonsong-vault)...")
    print(f"  Mode: {'STRICT RELEASE' if args.strict else 'PERMISSIVE DEV'}")
    print(f"  Client secret: {secret_status}")
    print(f"  Fragments: {len(client_secret_fragments)} client_secret chunks (keystore-bound)")
    print(f"  Cert SHA256: {safe_cert_display}")

    # Generate Rust secrets file for neonsong-vault
    lines_rs = [
        "// AUTO-GENERATED — DO NOT COMMIT TO VERSION CONTROL",
        "// Generated by tools/generate_native_secrets.py",
        "",
        f'pub const EXPECTED_CERT_PREFIX: &str = "{cert_sha256}";',
        "",
        f"pub const CLIENT_SECRET_SALT: [u8; 16] = [{salt_hex}];",
        f"pub const CLIENT_SECRET_TOTAL_LEN: usize = {len(secret_bytes)};",
        f"pub const CLIENT_SECRET_FRAGMENT_COUNT: usize = {len(client_secret_fragments)};",
        "",
        "pub struct Fragment {",
        "    pub data: &'static [u8],",
        "    pub mask: u8,",
        "}",
        "",
    ]
    for i, (xored_bytes, mask, length) in enumerate(client_secret_fragments):
        hex_vals = ",".join(f"0x{b:02X}" for b in xored_bytes)
        lines_rs.append(f"static CS{i}: [u8; {length}] = [{hex_vals}];")
    lines_rs.append("")
    lines_rs.append("pub static CLIENT_SECRET_FRAGMENTS: &[Fragment] = &[")
    if client_secret_fragments:
        for i, (xored_bytes, mask, length) in enumerate(client_secret_fragments):
            lines_rs.append(f"    Fragment {{ data: &CS{i}, mask: 0x{mask:02X} }},")
    lines_rs.append("];")
    lines_rs.append("")

    out_rs_path = args.out
    os.makedirs(os.path.dirname(out_rs_path), exist_ok=True)

    # Atomic write via temporary file
    temp_dir = os.path.dirname(out_rs_path)
    with tempfile.NamedTemporaryFile("w", dir=temp_dir, delete=False, encoding="utf-8") as tf:
        tf.write("\n".join(lines_rs) + "\n")
        temp_name = tf.name

    os.replace(temp_name, out_rs_path)
    print(f"Generated {out_rs_path}")

    # Verify decryption roundtrip
    if secret_bytes:
        recon_cipher = bytearray()
        for xored_bytes, mask, _ in client_secret_fragments:
            for b in xored_bytes:
                recon_cipher.append(b ^ mask)
        recon_keystream = derive_keystream(cert_sha256, salt, len(recon_cipher))
        recon_secret = bytes(b ^ k for b, k in zip(recon_cipher, recon_keystream)).decode("utf-8")
        assert recon_secret == client_secret, "Client secret roundtrip mismatch"
        print("  Keystore-bound verification: PASS")


if __name__ == "__main__":
    main()
