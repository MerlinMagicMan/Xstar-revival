from __future__ import annotations

from dataclasses import dataclass
from typing import Iterable, List, Optional


@dataclass(frozen=True)
class MavlinkFrame:
    offset: int
    version: int
    payload_length: int
    sequence: int
    system_id: int
    component_id: int
    message_id: int
    incompat_flags: Optional[int]
    compat_flags: Optional[int]
    signed: bool
    frame_length: int
    raw: bytes


MAVLINK_V1_MAGIC = 0xFE
MAVLINK_V2_MAGIC = 0xFD


def _scan_v1(data: bytes, offset: int) -> Optional[MavlinkFrame]:
    # v1: magic,len,seq,sysid,compid,msgid,payload...,crc(2)
    if offset + 8 > len(data):
        return None
    payload_len = data[offset + 1]
    frame_len = 8 + payload_len
    end = offset + frame_len
    if end > len(data):
        return None
    return MavlinkFrame(
        offset=offset,
        version=1,
        payload_length=payload_len,
        sequence=data[offset + 2],
        system_id=data[offset + 3],
        component_id=data[offset + 4],
        message_id=data[offset + 5],
        incompat_flags=None,
        compat_flags=None,
        signed=False,
        frame_length=frame_len,
        raw=data[offset:end],
    )


def _scan_v2(data: bytes, offset: int) -> Optional[MavlinkFrame]:
    # v2: magic,len,incompat,compat,seq,sysid,compid,msgid[3],payload,crc[2],signature?[13]
    if offset + 12 > len(data):
        return None
    payload_len = data[offset + 1]
    incompat = data[offset + 2]
    compat = data[offset + 3]
    signed = bool(incompat & 0x01)
    frame_len = 12 + payload_len + (13 if signed else 0)
    end = offset + frame_len
    if end > len(data):
        return None
    msgid = data[offset + 7] | (data[offset + 8] << 8) | (data[offset + 9] << 16)
    return MavlinkFrame(
        offset=offset,
        version=2,
        payload_length=payload_len,
        sequence=data[offset + 4],
        system_id=data[offset + 5],
        component_id=data[offset + 6],
        message_id=msgid,
        incompat_flags=incompat,
        compat_flags=compat,
        signed=signed,
        frame_length=frame_len,
        raw=data[offset:end],
    )


def scan_mavlink(data: bytes) -> List[MavlinkFrame]:
    """Find structurally plausible MAVLink v1/v2 frames.

    This intentionally does not validate CRC yet because Autel may use a custom
    dialect whose CRC-extra table is not known. Structural detection is useful
    for first captures without falsely claiming semantic validation.
    """
    frames: List[MavlinkFrame] = []
    i = 0
    while i < len(data):
        magic = data[i]
        frame: Optional[MavlinkFrame] = None
        if magic == MAVLINK_V1_MAGIC:
            frame = _scan_v1(data, i)
        elif magic == MAVLINK_V2_MAGIC:
            frame = _scan_v2(data, i)

        if frame is None:
            i += 1
            continue

        frames.append(frame)
        i += frame.frame_length
    return frames


def summarize(frames: Iterable[MavlinkFrame]) -> dict:
    frames = list(frames)
    by_version: dict[int, int] = {}
    by_message: dict[int, int] = {}
    by_system: dict[tuple[int, int], int] = {}
    for frame in frames:
        by_version[frame.version] = by_version.get(frame.version, 0) + 1
        by_message[frame.message_id] = by_message.get(frame.message_id, 0) + 1
        key = (frame.system_id, frame.component_id)
        by_system[key] = by_system.get(key, 0) + 1
    return {
        "count": len(frames),
        "versions": by_version,
        "message_ids": by_message,
        "systems": {f"{k[0]}:{k[1]}": v for k, v in by_system.items()},
    }
