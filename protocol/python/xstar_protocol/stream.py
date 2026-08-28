from __future__ import annotations

from dataclasses import dataclass, field
from typing import List

from .mavlink import MavlinkFrame, MAVLINK_V1_MAGIC, MAVLINK_V2_MAGIC


@dataclass
class MavlinkStreamScanner:
    """Incrementally extracts structurally plausible MAVLink frames.

    USB reads can split a frame at arbitrary byte boundaries. This scanner keeps
    incomplete tails until the next read arrives. It still intentionally does not
    validate CRC-extra until the Autel dialect is known.
    """

    buffer: bytearray = field(default_factory=bytearray)
    absolute_offset: int = 0

    def feed(self, chunk: bytes) -> List[MavlinkFrame]:
        self.buffer.extend(chunk)
        frames: List[MavlinkFrame] = []
        i = 0

        while i < len(self.buffer):
            magic = self.buffer[i]
            if magic not in (MAVLINK_V1_MAGIC, MAVLINK_V2_MAGIC):
                i += 1
                continue

            if magic == MAVLINK_V1_MAGIC:
                if len(self.buffer) - i < 2:
                    break
                payload_len = self.buffer[i + 1]
                frame_len = 8 + payload_len
                if len(self.buffer) - i < frame_len:
                    break
                raw = bytes(self.buffer[i : i + frame_len])
                frames.append(
                    MavlinkFrame(
                        offset=self.absolute_offset + i,
                        version=1,
                        payload_length=payload_len,
                        sequence=raw[2],
                        system_id=raw[3],
                        component_id=raw[4],
                        message_id=raw[5],
                        incompat_flags=None,
                        compat_flags=None,
                        signed=False,
                        frame_length=frame_len,
                        raw=raw,
                    )
                )
                i += frame_len
                continue

            if len(self.buffer) - i < 3:
                break
            payload_len = self.buffer[i + 1]
            incompat = self.buffer[i + 2]
            signed = bool(incompat & 0x01)
            frame_len = 12 + payload_len + (13 if signed else 0)
            if len(self.buffer) - i < frame_len:
                break
            raw = bytes(self.buffer[i : i + frame_len])
            msgid = raw[7] | (raw[8] << 8) | (raw[9] << 16)
            frames.append(
                MavlinkFrame(
                    offset=self.absolute_offset + i,
                    version=2,
                    payload_length=payload_len,
                    sequence=raw[4],
                    system_id=raw[5],
                    component_id=raw[6],
                    message_id=msgid,
                    incompat_flags=raw[2],
                    compat_flags=raw[3],
                    signed=signed,
                    frame_length=frame_len,
                    raw=raw,
                )
            )
            i += frame_len

        if i:
            del self.buffer[:i]
            self.absolute_offset += i

        # Avoid unbounded noise retention. Keep only a small suffix that could
        # plausibly contain a partial header when no valid magic was found.
        if len(self.buffer) > 65536:
            keep = bytes(self.buffer[-32:])
            dropped = len(self.buffer) - len(keep)
            self.buffer[:] = keep
            self.absolute_offset += dropped

        return frames
