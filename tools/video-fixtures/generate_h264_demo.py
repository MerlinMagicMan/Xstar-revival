#!/usr/bin/env python3
"""Generate the original, synthetic H.264 replay fixture used by the Android app."""

from __future__ import annotations

import argparse
import math
import subprocess
from pathlib import Path

from PIL import Image, ImageDraw


WIDTH = 640
HEIGHT = 360
FPS = 15
SECONDS = 4


def frame_at(index: int) -> Image.Image:
    phase = index / (FPS * SECONDS)
    image = Image.new("RGB", (WIDTH, HEIGHT))
    pixels = image.load()

    horizon = 178 + round(math.sin(phase * math.tau) * 7)
    for y in range(HEIGHT):
        if y < horizon:
            mix = y / max(horizon, 1)
            color = (round(35 + 62 * mix), round(83 + 75 * mix), round(130 + 68 * mix))
        else:
            mix = (y - horizon) / max(HEIGHT - horizon, 1)
            color = (round(59 - 25 * mix), round(76 - 34 * mix), round(43 - 20 * mix))
        for x in range(WIDTH):
            pixels[x, y] = color

    draw = ImageDraw.Draw(image)
    drift = math.sin(phase * math.tau) * 22
    mountains = [
        (-80, horizon, 85 + drift, 92),
        (20, horizon, 210 + drift, 112),
        (155, horizon, 340 + drift, 76),
        (305, horizon, 500 + drift, 108),
        (450, horizon, 710 + drift, 86),
    ]
    for left, base, peak_x, peak_y in mountains:
        draw.polygon([(left, base), (peak_x, peak_y), (peak_x + 180, base)], fill=(47, 70, 65))

    road_center = WIDTH / 2 + math.sin(phase * math.tau * 1.5) * 35
    draw.polygon(
        [(road_center - 18, horizon), (road_center + 18, horizon), (road_center + 155, HEIGHT), (road_center - 155, HEIGHT)],
        fill=(49, 52, 50),
    )
    for marker in range(7):
        depth = (marker / 7 + phase * 1.8) % 1.0
        y = horizon + (depth**2) * (HEIGHT - horizon)
        width = 2 + depth * 14
        height = 3 + depth * 22
        draw.rectangle((road_center - width / 2, y, road_center + width / 2, y + height), fill=(221, 202, 116))

    for side in (-1, 1):
        start_x = road_center + side * 32
        end_x = road_center + side * 200
        draw.line((start_x, horizon, end_x, HEIGHT), fill=(91, 116, 72), width=2)

    return image


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("output", type=Path)
    parser.add_argument("--ffmpeg", required=True, type=Path)
    args = parser.parse_args()
    args.output.parent.mkdir(parents=True, exist_ok=True)

    command = [
        str(args.ffmpeg), "-hide_banner", "-loglevel", "error", "-y",
        "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{WIDTH}x{HEIGHT}", "-r", str(FPS), "-i", "-",
        "-an", "-c:v", "libx264", "-preset", "veryslow", "-tune", "zerolatency",
        "-profile:v", "baseline", "-level", "3.0", "-pix_fmt", "yuv420p",
        "-x264-params", f"keyint={FPS}:min-keyint={FPS}:scenecut=0:aud=1:repeat-headers=1",
        "-f", "h264", str(args.output),
    ]
    process = subprocess.Popen(command, stdin=subprocess.PIPE)
    assert process.stdin is not None
    try:
        for index in range(FPS * SECONDS):
            process.stdin.write(frame_at(index).tobytes())
    finally:
        process.stdin.close()
    if process.wait() != 0:
        raise SystemExit("ffmpeg failed")


if __name__ == "__main__":
    main()
