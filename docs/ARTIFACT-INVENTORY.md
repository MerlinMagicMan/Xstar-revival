# Artifact Inventory and Preservation Policy

This file records artifacts that can anchor reproducible X-Star research without redistributing proprietary binaries in the public repository.

## A. Preserved Starlink APK

```yaml
artifact: Starlink Android APK
filename_received: android-comautelmaxlink-V20320.apk
package: com.autel.maxlink
reported_version: 2.0.3.20
sha256: 01d6aba3ebbb1e1672273e20dfe4fb44bfaf0a6c1c10499ed57c08e4f2e34702
redistribution: not committed to public repo
research_status: static inventory completed; deeper call-graph analysis pending
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
```

## B. Legacy Tablet — Pending Recovery

Desired artifact set:

```yaml
device_photos: pending
manufacturer_model: pending
android_version: pending
build_fingerprint: pending
installed_starlink_version: pending
installed_apk_sha256: pending
certificate_fingerprint: pending
controller_usb_descriptors: pending
starlink_logcat_baseline: pending
accessible_shared_files: pending
flight_logs: pending
firmware_versions: pending
```

See `LEGACY-TABLET-PRESERVATION.md` before modifying the device.

## C. Flight Logs — Pending

Private source archive should retain:

- original binary log;
- acquisition method;
- aircraft firmware;
- remote firmware;
- camera/gimbal firmware;
- date/time and privacy classification;
- bundled parser/script, if any;
- SHA-256 for every file.

Only sanitized fixtures should be public.

## D. Firmware — Metadata First

Frequently referenced final X-Star Premium filename:

```text
X3P_FW_900M_V2.0.12.bin
```

Pending work:

- locate copies from more than one source;
- compare hashes;
- identify official provenance;
- inventory archive/container structure;
- extract component/version manifest without flashing;
- determine redistribution rights;
- record known update failures and compatibility requirements.

Do not commit firmware binaries to the public repository unless rights and provenance are clear.

## E. Historical Starlink Versions

Versions worth locating for signed differential analysis include at least:

```text
2.0.2.30
2.0.3.19
2.0.3.20
```

For every copy:

1. verify package name;
2. verify signing certificate against the known Autel certificate;
3. calculate SHA-256;
4. record minimum/target SDK and ABI inventory;
5. compare Java/native package trees;
6. diff endpoint strings and JNI symbols;
7. do not execute an unverified copy on production hardware.

Historical release notes suggest that 2.0.3.19 added offline mission planning and Find My X-Star. Version diffs may reveal where mission/camera/transport behavior changed.

## F. FCC and Official Documentation

Primary/public references to preserve by URL and title:

```text
Autel X-Star Downloads
https://shop.autelrobotics.com/pages/x-star-downloads

Aircraft FCC ID 2AGNTAC5809A
https://fcc.report/FCC-ID/2AGNTAC5809A

Remote FCC ID 2AGNTRC5809A
https://fcc.report/FCC-ID/2AGNTRC5809A

TI bq3055
https://www.ti.com/product/BQ3055

PX4 MAVLink Shell
https://docs.px4.io/main/en/debug/mavlink_shell.html

X-Star Log Viewer source
https://github.com/tomSny/XStarLogViewer

X-Star Log Viewer documentation
https://xslogs.weebly.com/docs.html
```

Where allowed, retain local private copies with source URL, retrieval date and SHA-256. Public repo entries should prefer links and metadata.

## G. Physical Hardware Inventory — Pending

Record each item without publishing full serial numbers:

```text
X-Star Premium aircraft
remote controller
camera/gimbal
charger
batteries (each separately)
legacy tablet
Galaxy S20 test device
Galaxy S25 Ultra target device
USB cables/adapters
microSD cards
```

For batteries, assign internal IDs such as `PACK-01` and keep the serial-number mapping private.

## H. Capture Naming Convention

```text
YYYYMMDD-HHMM_<device>_<experiment>_<sequence>.<ext>
```

Examples:

```text
20260828-1430_tablet_usb-attach_logcat.txt
20260828-1430_remote_usb-descriptors.json
20260828-1505_aircraft_idle-flightlog.bin
20260828-1510_pack-01_sbs-readonly.json
```

## I. Integrity Manifest

Every private research session should produce a machine-readable manifest:

```yaml
capture_id: value
created_utc: value
operator: value
hardware:
  aircraft_firmware: value
  remote_firmware: value
  camera_firmware: value
  mobile_device: value
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

Never overwrite original captures. Derivations should reference the source artifact hash.
