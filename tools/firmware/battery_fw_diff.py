#!/usr/bin/env python3
"""Differential analyzer for equal-length X-Star battery firmware images.

Read-only research utility. It never writes firmware or communicates with hardware.

Current uses:
- compare aligned 8-byte blocks between historical battery firmware versions;
- locate long equal runs and map them onto a candidate MSP430 address range;
- inventory repeated ciphertext blocks;
- test standard TEA/XTEA/XXTEA transforms against likely erased-flash plaintext;
- optionally mine printable strings from a preserved Starlink APK as candidate keys;
- optionally test DES/3DES if Python's cryptography package is installed.

A failed candidate-key test does NOT disprove a cipher family. The actual key may be
binary, derived at runtime, transformed, or the implementation may differ from the
standard algorithms tested here.
"""

from __future__ import annotations

import argparse
import collections
import hashlib
import re
import struct
import zipfile
from pathlib import Path

BLOCK = 8
KNOWN_ERASED_CIPHER = bytes.fromhex("EA9C70A3322C1D86")


def hx(value: bytes) -> str:
    return value.hex().upper()


def tea_encrypt(block: bytes, key: bytes, endian: str) -> bytes:
    v0, v1 = struct.unpack(endian + "2I", block)
    k = struct.unpack(endian + "4I", key)
    total = 0
    delta = 0x9E3779B9
    for _ in range(32):
        total = (total + delta) & 0xFFFFFFFF
        v0 = (v0 + (((v1 << 4) + k[0]) ^ (v1 + total) ^ ((v1 >> 5) + k[1]))) & 0xFFFFFFFF
        v1 = (v1 + (((v0 << 4) + k[2]) ^ (v0 + total) ^ ((v0 >> 5) + k[3]))) & 0xFFFFFFFF
    return struct.pack(endian + "2I", v0, v1)


def xtea_encrypt(block: bytes, key: bytes, endian: str) -> bytes:
    v0, v1 = struct.unpack(endian + "2I", block)
    k = struct.unpack(endian + "4I", key)
    delta = 0x9E3779B9
    total = 0
    for _ in range(32):
        v0 = (v0 + ((((v1 << 4) ^ (v1 >> 5)) + v1) ^ (total + k[total & 3]))) & 0xFFFFFFFF
        total = (total + delta) & 0xFFFFFFFF
        v1 = (v1 + ((((v0 << 4) ^ (v0 >> 5)) + v0) ^ (total + k[(total >> 11) & 3]))) & 0xFFFFFFFF
    return struct.pack(endian + "2I", v0, v1)


def xxtea_encrypt(block: bytes, key: bytes, endian: str) -> bytes:
    """Standard Corrected Block TEA for a two-word (64-bit) block."""
    values = list(struct.unpack(endian + "2I", block))
    keys = list(struct.unpack(endian + "4I", key))
    n = 2
    delta = 0x9E3779B9
    rounds = 6 + 52 // n
    total = 0
    z = values[-1]

    for _ in range(rounds):
        total = (total + delta) & 0xFFFFFFFF
        e = (total >> 2) & 3

        y = values[1]
        mx = (
            (((z >> 5) ^ (y << 2)) + ((y >> 3) ^ (z << 4)))
            ^ ((total ^ y) + (keys[e] ^ z))
        ) & 0xFFFFFFFF
        values[0] = (values[0] + mx) & 0xFFFFFFFF
        z = values[0]

        y = values[0]
        mx = (
            (((z >> 5) ^ (y << 2)) + ((y >> 3) ^ (z << 4)))
            ^ ((total ^ y) + (keys[1 ^ e] ^ z))
        ) & 0xFFFFFFFF
        values[1] = (values[1] + mx) & 0xFFFFFFFF
        z = values[1]

    return struct.pack(endian + "2I", *values)


def apk_candidate_keys(apk: Path | None):
    common16 = {
        b"\x00" * 16,
        b"\xFF" * 16,
        b"0123456789abcdef",
        b"1234567890abcdef",
        b"autelrobotics1234"[:16],
        b"AutelRobotics123"[:16],
        b"XStarPremium1234"[:16],
    }
    if not apk:
        return common16, set(), False

    strings: set[bytes] = set()
    xxtea_present = False
    with zipfile.ZipFile(apk) as archive:
        for name in archive.namelist():
            if not name.endswith(".dex"):
                continue
            data = archive.read(name)
            if b"XXTEA.java" in data:
                xxtea_present = True
            strings.update(match.group() for match in re.finditer(rb"[\x20-\x7e]{4,64}", data))

    keys16 = {value[:16] for value in strings if len(value) >= 16}
    keys8 = {value[:8] for value in strings if len(value) >= 8}
    keywords = (b"autel", b"xstar", b"battery", b"firm", b"encrypt", b"decrypt", b"update")

    for value in strings:
        if not any(keyword in value.lower() for keyword in keywords):
            continue
        if 1 <= len(value) <= 16:
            keys16.update(
                {
                    value.ljust(16, b"\x00"),
                    value.ljust(16, b"\xFF"),
                    (value * (16 // len(value) + 1))[:16],
                }
            )
        if 1 <= len(value) <= 8:
            keys8.update(
                {
                    value.ljust(8, b"\x00"),
                    value.ljust(8, b"\xFF"),
                    (value * (8 // len(value) + 1))[:8],
                }
            )

    keys16 |= common16
    return keys16, keys8, xxtea_present


def candidate_tests(target: bytes, apk: Path | None):
    target_variants = {
        target,
        target[::-1],
        target[4:] + target[:4],
        target[3::-1] + target[7:3:-1],
    }
    plaintexts = [b"\xFF" * 8, b"\x00" * 8]
    keys16, keys8, xxtea_present = apk_candidate_keys(apk)
    matches = []

    for key in keys16:
        for plaintext in plaintexts:
            tests = (
                ("TEA-BE", tea_encrypt(plaintext, key, ">")),
                ("TEA-LE", tea_encrypt(plaintext, key, "<")),
                ("XTEA-BE", xtea_encrypt(plaintext, key, ">")),
                ("XTEA-LE", xtea_encrypt(plaintext, key, "<")),
                ("XXTEA-BE", xxtea_encrypt(plaintext, key, ">")),
                ("XXTEA-LE", xxtea_encrypt(plaintext, key, "<")),
            )
            for name, ciphertext in tests:
                if ciphertext in target_variants:
                    matches.append((name, hx(plaintext), repr(key)))

    # DES/3DES is optional so the analyzer stays dependency-light.
    try:
        from cryptography.hazmat.decrepit.ciphers import algorithms
        from cryptography.hazmat.primitives.ciphers import Cipher, modes

        for key in keys8:
            try:
                encryptor = Cipher(algorithms.TripleDES(key), modes.ECB()).encryptor()
                for plaintext in plaintexts:
                    if encryptor.update(plaintext) in target_variants:
                        matches.append(("DES/3DES-8", hx(plaintext), repr(key)))
            except Exception:
                pass
    except Exception:
        pass

    return {
        "keys16": len(keys16),
        "keys8": len(keys8),
        "matches": matches,
        "xxtea_present": xxtea_present,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("old", type=Path)
    parser.add_argument("new", type=Path)
    parser.add_argument("--apk", type=Path, help="optional Starlink APK used only to mine candidate key strings")
    parser.add_argument("--base-address", type=lambda text: int(text, 0), default=0xC800)
    args = parser.parse_args()

    old = args.old.read_bytes()
    new = args.new.read_bytes()
    if len(old) != len(new):
        raise SystemExit("images must have equal length")

    print("old_sha256=" + hashlib.sha256(old).hexdigest())
    print("new_sha256=" + hashlib.sha256(new).hexdigest())
    print(f"length=0x{len(old):X} ({len(old)})")

    equal = [old[offset : offset + BLOCK] == new[offset : offset + BLOCK] for offset in range(0, len(old), BLOCK)]
    same = sum(equal)
    print(f"equal_8byte_blocks={same}/{len(equal)} ({same / len(equal):.2%})")

    best_start = best_count = current_start = current_count = 0
    for index, is_equal in enumerate(equal):
        if is_equal:
            if current_count == 0:
                current_start = index
            current_count += 1
            if current_count > best_count:
                best_start, best_count = current_start, current_count
        else:
            current_count = 0

    start_offset = best_start * BLOCK
    end_offset = (best_start + best_count) * BLOCK - 1
    print(f"longest_equal_run_blocks={best_count}")
    print(f"longest_equal_run_offset=0x{start_offset:X}-0x{end_offset:X}")
    print(
        f"candidate_address=0x{args.base_address + start_offset:X}-"
        f"0x{args.base_address + end_offset:X}"
    )

    old_blocks = collections.Counter(old[offset : offset + BLOCK] for offset in range(0, len(old), BLOCK))
    new_blocks = collections.Counter(new[offset : offset + BLOCK] for offset in range(0, len(new), BLOCK))
    print("old_top_blocks=" + ",".join(f"{hx(value)}:{count}" for value, count in old_blocks.most_common(5)))
    print("new_top_blocks=" + ",".join(f"{hx(value)}:{count}" for value, count in new_blocks.most_common(5)))
    print(f"known_repeated_block_old={old_blocks[KNOWN_ERASED_CIPHER]}")
    print(f"known_repeated_block_new={new_blocks[KNOWN_ERASED_CIPHER]}")

    tests = candidate_tests(KNOWN_ERASED_CIPHER, args.apk)
    print(f"candidate_16byte_keys_tested={tests['keys16']}")
    print(f"candidate_8byte_keys_tested={tests['keys8']}")
    print("candidate_matches=" + repr(tests["matches"]))
    if args.apk:
        print("apk_contains_XXTEA_java=" + str(tests["xxtea_present"]))


if __name__ == "__main__":
    main()
