from __future__ import annotations

from collections import Counter
from pathlib import Path
from typing import Any

from .analyze import analyze


def _counter_from_message_ids(result: dict) -> Counter:
    return Counter({int(k): v for k, v in result["mavlink"]["message_ids"].items()})


def compare_bytes(a: bytes, b: bytes) -> dict[str, Any]:
    left = analyze(a)
    right = analyze(b)
    left_ids = _counter_from_message_ids(left)
    right_ids = _counter_from_message_ids(right)
    return {
        "left_bytes": len(a),
        "right_bytes": len(b),
        "mavlink_count_delta": right["mavlink"]["count"] - left["mavlink"]["count"],
        "message_id_delta": dict(sorted((right_ids - left_ids).items())),
        "message_ids_removed": dict(sorted((left_ids - right_ids).items())),
        "left_signature_kinds": sorted({h["kind"] for h in left["signatures"]}),
        "right_signature_kinds": sorted({h["kind"] for h in right["signatures"]}),
        "new_signature_kinds": sorted(
            {h["kind"] for h in right["signatures"]}
            - {h["kind"] for h in left["signatures"]}
        ),
    }


def compare_files(left: str | Path, right: str | Path) -> dict[str, Any]:
    return compare_bytes(Path(left).read_bytes(), Path(right).read_bytes())
