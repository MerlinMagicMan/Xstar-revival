# Local Autel SDK binary

Place the official Autel Mobile SDK AAR here as:

```text
autel-sdk-release.aar
```

The AAR is intentionally **not committed** to X-Star Revival.

Before use, record its provenance, version and SHA-256 in the private research manifest. Prefer an artifact obtained directly from Autel's official SDK repository/distribution.

The first binary-analysis task is to inventory:

- `arm64-v8a` native libraries
- `armeabi-v7a` native libraries
- package/class surface
- embedded manifests/resources
- native dependencies
- SDK version metadata

Do not substitute APK-extracted Starlink native libraries for this file.
