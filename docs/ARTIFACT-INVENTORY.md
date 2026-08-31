# Artifact Inventory and Preservation Policy

This file records artifacts that anchor reproducible X-Star research without redistributing proprietary binaries in the public repository. See [`PRESERVATION-ARCHIVE.md`](PRESERVATION-ARCHIVE.md) for the complete archive policy.

## A. Preserved Starlink APK

```yaml
artifact_id: starlink-android-2.0.3.20
artifact: Starlink Android APK
filename_received: android-comautelmaxlink-V20320.apk
package: com.autel.maxlink
reported_version: 2.0.3.20
sha256: 01d6aba3ebbb1e1672273e20dfe4fb44bfaf0a6c1c10499ed57c08e4f2e34702
redistribution: not committed to public repo
research_status: static inventory completed; firmware/protocol call-graph analysis ongoing
```

### Signing certificate

```text
Subject/issuer:
C=86, ST=Guangdong, L=Shenzhen, O=Autel, OU=autel, CN=autel

Validity:
2015-04-30 through 2042-09-16

SHA-256 fingerprint:
8D:BF:01:90:69:6A:D8:50:A5:34:5E:6A:2D:1E:E6:B4:7F:53:0C:06:15:64:CD:ED:CB:30:58:DC:E2:28:DD:C2
```

### Native ABI inventory

The preserved APK contains only legacy `armeabi` native libraries:

```text
lib/armeabi/libAutelPlayer.so
lib/armeabi/libNetWorkProxy.so
lib/armeabi/libgdinamapv4sdk752.so
lib/armeabi/libgdinamapv4sdk752ex.so
lib/armeabi/libpl_droidsonroids_gif.so
lib/armeabi/libpl_droidsonroids_gif_surface.so
lib/armeabi/libpldroidplayer.so
lib/armeabi/libtencentloc.so
```

No `arm64-v8a` libraries are present. This is a major reason a clean modern implementation is preferable to repackaging the old application.

### High-value static findings

```text
MAVLink packages and parser/connection classes
AutelMavlinkCore packages
Android USB host classes and bulkTransfer
UsbPkt_* native framing functions
NetworkProxy JNI entry points
H.264 RTSP endpoint
camera and event HTTP endpoints
flight controller, remote, mission, gimbal, camera and battery modules
firmware parser/updater classes
XXTEA.java / Encrypt.java class-name evidence
```

## B. Official SDK/API Documentation

```yaml
artifact_id: autel-sdk-api-2.0.11.79
filename_received: SDK Api_V2.0.11.79.zip
size_bytes: 2281080
status: preserved
content: complete Javadoc/API documentation corpus
redistribution: original archive not committed pending rights determination
```

Related official release notes are also preserved and establish X-Star/X-Star Premium support plus expected final component versions.

## C. X-Star Premium V1.1.3 Firmware — RECOVERED

```yaml
artifact_id: xstar-premium-fw-v1.1.3
filename_received: X3P_FW_900M_V1.1.3.bin
size_bytes: 60760988
md5: 48e0a68ed22ab25d1711950c0d1c5fa1
sha256: 63096382d1cb252edc18efc67065e1a16f37b2f445a2eca08863a1bbd30f5d2b
status: recovered-and-verified
redistribution: not committed pending rights determination
container: aggregate XOR 0xC8 format
component_count: 17
```

The decoded Autel manifest contains:

```text
X3P_BATTERY41_v5.21_20160324.bin
Battery V5.21
length 14336 (0x3800)
MD5 D02AC39F837B572437C1EFD3E1344334
SHA-256 f3236905978d352b4a73be3f28d066a7f89910f182fcbb0c863581ee9e706816
```

This recovery is especially important because Starlink contains version-boundary logic around Battery V5.22 and V5.21 provides a historical comparison image for V6.07.

## D. X-Star Premium V2.0.12 Firmware — RECOVERED

```yaml
artifact_id: xstar-premium-fw-v2.0.12
filename_received: X3P_FW_900M_V2.0.12.bin
size_bytes: 59778215
md5: c67b603ab5604f8633e04916dc190989
sha256: fe6c66bed25ac01395f3b9082accddf2989cd7a91e848fecb61e82d9b82a64d7
status: recovered-and-verified
redistribution: not committed pending rights determination
container: aggregate XOR 0xC8 format
component_count: 17
```

Battery component:

```text
X3P_BATTERY41_V6.07_20170627.BIN
Battery V6.07
length 14336 (0x3800)
MD5 2C4AAD6D78B12152C5DDF3FB4EDC2CC9
SHA-256 cfba99aef0bab724105ef1e1b914903d09432f58944fcd293b19650cbb6f73c7
```

The public read-only extractor at `tools/firmware/extract_xstar_firmware.py` reproduces the container decode, extracts all 17 components and validates every component against the MD5 embedded by Autel in the aggregate manifest.

## E. Battery Firmware Differential Dataset

The pair:

```text
Battery V5.21 SHA-256 f3236905...
Battery V6.07 SHA-256 cfba99ae...
```

is the current canonical battery-firmware comparison dataset.

Public tools and analysis:

- `tools/firmware/battery_fw_diff.py`
- `docs/BATTERY-V521-V607-DIFFERENTIAL.md`
- `docs/BATTERY-V607-INNER-IMAGE.md`

The original component binaries remain private/proprietary artifacts; the hashes, analysis and reproducible tooling are public.

## F. User Manual / Official Documentation

Preserved official manual:

```text
X-Star_Premium__User_Manual_EN.pdf
```

Use official documents as the primary source for intended operating limits, hardware behavior and safety guidance. Community research should be clearly distinguished from official specifications.

## G. Historical Firmware Still Wanted

Priority recovery targets:

```text
X3P_FW_900M_V1.2.8.bin
V1.3.x / beta packages
Battery V5.22 or any intermediate V5.x/V6.x image
```

Every recovered firmware copy should be hashed before analysis. Do not flash unverified historical firmware to production hardware merely to identify it.

## H. Historical Starlink Versions Still Wanted

Versions worth locating for signed differential analysis include at least:

```text
2.0.2.30
2.0.3.19
2.0.3.20 (preserved)
```

For every copy:

1. verify package name;
2. verify signing certificate against the preserved Autel certificate;
3. calculate SHA-256;
4. record minimum/target SDK and ABI inventory;
5. compare Java/native package trees;
6. diff endpoint strings and JNI symbols;
7. do not execute an unverified copy on production hardware.

## I. Legacy Tablet — Optional/Pending

If recovered, preserve it as a golden behavioral reference without updating/resetting it. Desired records include Android/build version, installed APK hash, USB descriptors, Starlink logs, flight logs and firmware versions.

The project no longer depends on finding this tablet.

## J. Flight Logs / Live Captures — Pending Hardware Access

Private source archive should retain:

- original binary capture/log;
- acquisition method;
- aircraft, remote, camera/gimbal and battery firmware versions;
- date/time and privacy classification;
- bundled parser/script, if any;
- SHA-256 for every file.

Only sanitized fixtures should be public.

## K. Community Research

The source-of-truth index is [`COMMUNITY-RESEARCH-INDEX.md`](COMMUNITY-RESEARCH-INDEX.md). It records forum discoveries with confidence labels and reproduction status rather than treating every community post as established fact.

## L. Physical Hardware Inventory

Record each physical item without publishing full serial numbers:

```text
X-Star Premium aircraft
remote controller
camera/gimbal
factory charger
batteries (each separately)
mobile test devices
USB cables/adapters
microSD cards
battery service/programming interfaces
```

For batteries, assign internal IDs such as `PACK-01`; keep serial-number mapping private.

Before modifying a donor battery PCB, preserve board photography, component markings and a complete read-only BQ3055 dump wherever possible.

## M. Capture Naming Convention

```text
YYYYMMDD-HHMM_<device>_<experiment>_<sequence>.<ext>
```

Examples:

```text
20260831-0210_pack-02_bq3055-readonly_001.json
20260831-0220_remote_usb-descriptors_001.json
20260831-0235_starpoint-passive-uart_001.bin
```

## N. Integrity Manifest

Every private research session should produce a machine-readable manifest:

```yaml
capture_id: value
created_utc: value
operator: value
sources:
  - sha256: value
    filename: value
hardware:
  aircraft_firmware: value
  remote_firmware: value
  battery_firmware: value
safety:
  props_removed: true
  battery_condition_checked: true
artifacts:
  - path: value
    size: value
    sha256: value
    privacy: private|sanitized|public
    source: value
notes: value
```

Never overwrite original captures. Every derivation should reference the parent artifact hash.
