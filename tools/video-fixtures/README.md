# Video fixtures

`generate_h264_demo.py` creates the original synthetic landscape replay bundled
with the Android app. It produces a raw H.264 Annex-B stream with access-unit
delimiters and repeated parameter sets so the replay exercises the same scanner
and decoder boundary intended for future receive-only camera data.

The generator requires Python, Pillow and an ffmpeg build with `libx264`:

```bash
python generate_h264_demo.py \
  ../../software/android-app/app/src/main/res/raw/xstar_synthetic_fpv.h264 \
  --ffmpeg /path/to/ffmpeg
```

The fixture is synthetic and contains no Autel footage or proprietary data.
