from __future__ import annotations

from dataclasses import dataclass
from typing import List


@dataclass(frozen=True)
class SignatureHit:
    kind: str
    offset: int
    detail: str


SIGNATURES = [
    (b"RTSP/1.0", "rtsp", "RTSP response"),
    (b"OPTIONS rtsp://", "rtsp", "RTSP OPTIONS request"),
    (b"DESCRIBE rtsp://", "rtsp", "RTSP DESCRIBE request"),
    (b"SETUP rtsp://", "rtsp", "RTSP SETUP request"),
    (b"PLAY rtsp://", "rtsp", "RTSP PLAY request"),
    (b"HTTP/1.1", "http", "HTTP response"),
    (b"GET /", "http", "HTTP GET request"),
    (b"POST /", "http", "HTTP POST request"),
    (b"\x00\x00\x00\x01\x67", "h264", "H.264 SPS start code"),
    (b"\x00\x00\x00\x01\x68", "h264", "H.264 PPS start code"),
    (b"\x00\x00\x00\x01\x65", "h264", "H.264 IDR start code"),
    (b"\x00\x00\x01\x67", "h264", "H.264 SPS 3-byte start code"),
    (b"\x00\x00\x01\x68", "h264", "H.264 PPS 3-byte start code"),
    (b"\x00\x00\x01\x65", "h264", "H.264 IDR 3-byte start code"),
]


def scan_signatures(data: bytes) -> List[SignatureHit]:
    hits: List[SignatureHit] = []
    for needle, kind, detail in SIGNATURES:
        start = 0
        while True:
            idx = data.find(needle, start)
            if idx < 0:
                break
            # A four-byte Annex-B start code also contains the three-byte
            # form beginning at its second zero. Report the NAL once, at the
            # beginning of the complete start code.
            if kind == "h264" and needle.startswith(b"\x00\x00\x01") and idx > 0 and data[idx - 1] == 0:
                start = idx + 1
                continue
            hits.append(SignatureHit(kind=kind, offset=idx, detail=detail))
            start = idx + 1
    hits.sort(key=lambda h: h.offset)
    return hits
