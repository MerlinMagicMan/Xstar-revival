#!/usr/bin/env python3
"""Inspect an Autel X-Star RC-PRO firmware component without modifying it.

The aggregate X-Star firmware extractor must be run first. This tool parses the
observed 0xF0-byte RC-PRO wrapper and reports structural evidence about the
AES-128-ECB payload. It deliberately does not write output or attempt a
firmware update. Use decode_rc_firmware.py for validated offline decoding.
"""

from __future__ import annotations

import argparse
import binascii
import hashlib
import math
import struct
from collections import Counter
from dataclasses import dataclass
from pathlib import Path


MAGIC = bytes.fromhex("02 AA 55 AA")
HEADER_SIZE = 0xF0
BLOCK_SIZE = 16
AES_ZERO_BLOCK_CIPHERTEXT = bytes.fromhex(
    "7D F7 6B 0C 1A B8 99 B3 3E 42 F0 47 B9 1B 54 6F"
)


@dataclass(frozen=True)
class RcImage:
    path: Path
    data: bytes
    version_word: int
    declared_payload_length: int
    check_word: int
    target_word: int
    product: str
    payload: bytes

    @property
    def version(self) -> str:
        octets = self.version_word.to_bytes(4, "big")
        return "V" + ".".join(str(value) for value in octets)


def load_image(path: Path) -> RcImage:
    data = path.read_bytes()
    if len(data) < HEADER_SIZE:
        raise ValueError(f"{path}: shorter than the 0x{HEADER_SIZE:X}-byte header")
    if data[:4] != MAGIC:
        raise ValueError(f"{path}: RC-PRO magic not found")

    version_word = struct.unpack_from("<I", data, 0x1C)[0]
    duplicate_version = struct.unpack_from("<I", data, 0x2C)[0]
    if duplicate_version != version_word:
        raise ValueError(
            f"{path}: duplicated version fields differ "
            f"(0x{version_word:08X} != 0x{duplicate_version:08X})"
        )

    declared_length, check_word, target_word = struct.unpack_from("<III", data, 0x20)
    product = data[0x30:0xD0].split(b"\0", 1)[0].decode("ascii", errors="replace")
    payload = data[HEADER_SIZE:]

    padded_length = math.ceil(declared_length / BLOCK_SIZE) * BLOCK_SIZE
    if len(payload) != padded_length:
        raise ValueError(
            f"{path}: payload size {len(payload)} does not match declared length "
            f"{declared_length} rounded to {padded_length}"
        )

    return RcImage(
        path=path,
        data=data,
        version_word=version_word,
        declared_payload_length=declared_length,
        check_word=check_word,
        target_word=target_word,
        product=product,
        payload=payload,
    )


def shannon_entropy(data: bytes) -> float:
    counts = Counter(data)
    total = len(data)
    return -sum((count / total) * math.log2(count / total) for count in counts.values())


def block_counts(payload: bytes) -> Counter[bytes]:
    return Counter(
        payload[offset : offset + BLOCK_SIZE]
        for offset in range(0, len(payload), BLOCK_SIZE)
    )


def describe(image: RcImage) -> None:
    counts = block_counts(image.payload)
    total_blocks = sum(counts.values())
    repeated_blocks = sum(count - 1 for count in counts.values() if count > 1)

    print(f"file={image.path}")
    print(f"sha256={hashlib.sha256(image.data).hexdigest()}")
    print(f"component_jamcrc=0x{(~binascii.crc32(image.data) & 0xFFFFFFFF):08X}")
    print(f"size={len(image.data)}")
    print(f"header_size=0x{HEADER_SIZE:X}")
    print(f"product={image.product}")
    print(f"version={image.version}")
    print(f"version_word=0x{image.version_word:08X}")
    print(f"declared_payload_length={image.declared_payload_length}")
    print(f"padded_payload_length={len(image.payload)}")
    print(f"check_word=0x{image.check_word:08X}")
    print(f"target_word=0x{image.target_word:08X}")
    print(f"payload_entropy_bits_per_byte={shannon_entropy(image.payload):.6f}")
    print(f"block_size={BLOCK_SIZE}")
    print(f"total_blocks={total_blocks}")
    print(f"unique_blocks={len(counts)}")
    print(f"repeated_block_instances={repeated_blocks}")
    print(f"repeated_block_ratio={repeated_blocks / total_blocks:.6f}")
    print("block_transform_evidence=" + ("STRONG" if repeated_blocks >= 32 else "LIMITED"))
    print(f"aes_128_ecb_zero_block_count={counts[AES_ZERO_BLOCK_CIPHERTEXT]}")
    print("cipher_identification=AES-128-ECB")
    for rank, (block, count) in enumerate(counts.most_common(8), start=1):
        print(f"common_block_{rank}={count}\t{block.hex()}")


def compare(left: RcImage, right: RcImage) -> None:
    left_counts = block_counts(left.payload)
    right_counts = block_counts(right.payload)
    common_values = left_counts.keys() & right_counts.keys()
    common_instances = sum(min(left_counts[value], right_counts[value]) for value in common_values)

    print(f"compare_left={left.path}")
    print(f"compare_right={right.path}")
    print(f"common_block_values={len(common_values)}")
    print(f"common_block_instances={common_instances}")
    for rank, value in enumerate(
        sorted(common_values, key=lambda item: min(left_counts[item], right_counts[item]), reverse=True)[:8],
        start=1,
    ):
        print(
            f"shared_block_{rank}={left_counts[value]}\t{right_counts[value]}\t{value.hex()}"
        )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("firmware", type=Path, help="extracted X3P_RC_*.BIN component")
    parser.add_argument(
        "--compare",
        type=Path,
        help="optional second extracted RC component for block-level comparison",
    )
    args = parser.parse_args()

    image = load_image(args.firmware)
    describe(image)
    if args.compare:
        compare(image, load_image(args.compare))


if __name__ == "__main__":
    main()
