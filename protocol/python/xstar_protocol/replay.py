from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Iterable, Iterator, Optional
import time


@dataclass(frozen=True)
class ReplayChunk:
    offset: int
    data: bytes


def chunks(data: bytes, chunk_size: int = 512) -> Iterator[ReplayChunk]:
    if chunk_size <= 0:
        raise ValueError("chunk_size must be > 0")
    for offset in range(0, len(data), chunk_size):
        yield ReplayChunk(offset=offset, data=data[offset : offset + chunk_size])


def replay_bytes(
    data: bytes,
    sink: Callable[[ReplayChunk], None],
    *,
    chunk_size: int = 512,
    delay_seconds: float = 0.0,
) -> None:
    """Replay a capture into a parser/consumer without any hardware dependency."""
    for chunk in chunks(data, chunk_size=chunk_size):
        sink(chunk)
        if delay_seconds > 0:
            time.sleep(delay_seconds)


def replay_file(
    path: str | Path,
    sink: Callable[[ReplayChunk], None],
    *,
    chunk_size: int = 512,
    delay_seconds: float = 0.0,
) -> None:
    replay_bytes(
        Path(path).read_bytes(),
        sink,
        chunk_size=chunk_size,
        delay_seconds=delay_seconds,
    )
