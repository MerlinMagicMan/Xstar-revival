# Legacy Starlink Tablet Preservation and Capture Plan

A tablet that already runs Starlink is a **golden reference device**. Preserve it before changing anything.

## Do Not Do These Things First

- Do not factory-reset the tablet.
- Do not update Android or system apps.
- Do not clear Starlink storage/cache.
- Do not uninstall or reinstall Starlink.
- Do not run storage-cleaner or device-optimization tools.
- Do not connect it to an untrusted computer.
- Do not power the aircraft with propellers installed during bench work.

Keep the tablet charged and, if possible, leave it in airplane mode except when a particular test requires connectivity.

## Phase 1 — Photograph and Inventory

Before enabling developer settings, photograph or record:

- tablet make/model and model number;
- Android version/build number;
- Starlink version from Android App Info and the app's About screen;
- free/used storage;
- USB connector type;
- controller firmware;
- aircraft firmware;
- camera/gimbal firmware;
- remote-controller serial/model labels;
- aircraft and battery labels;
- every Starlink settings screen that exposes versions or connection state.

Do not publish serial numbers or personal flight locations without redaction.

## Phase 2 — Make an ADB Preservation Folder

Install Android Platform Tools on a computer, then create a timestamped directory such as:

```text
xstar-capture/
├── tablet/
├── apk/
├── starlink-files/
├── flight-logs/
├── logcat/
├── usb/
└── notes/
```

Enable **Developer options** and **USB debugging** on the tablet only after the initial inventory.

Verify the device:

```bash
adb devices -l
```

Record system properties and installed-package metadata:

```bash
adb shell getprop > tablet/getprop.txt
adb shell dumpsys package com.autel.maxlink > tablet/starlink-package.txt
adb shell pm path com.autel.maxlink > tablet/starlink-apk-path.txt
adb shell dumpsys usb > usb/dumpsys-usb-before-controller.txt
```

On Linux/macOS, locate related packages with:

```bash
adb shell pm list packages | grep -i -E 'autel|maxlink|maxifly|starlink'
```

On Windows PowerShell or Command Prompt:

```bat
adb shell pm list packages | findstr /I "autel maxlink maxifly starlink"
```

## Phase 3 — Pull the Installed APK

First retrieve the path:

```bash
adb shell pm path com.autel.maxlink
```

It will normally return a line similar to:

```text
package:/data/app/.../base.apk
```

Pull the exact returned path:

```bash
adb pull /data/app/.../base.apk apk/starlink-tablet-base.apk
```

Calculate hashes without modifying the file:

Linux/macOS:

```bash
sha256sum apk/starlink-tablet-base.apk > apk/starlink-tablet-base.sha256
```

Windows PowerShell:

```powershell
Get-FileHash .\apk\starlink-tablet-base.apk -Algorithm SHA256 |
  Format-List | Out-File .\apk\starlink-tablet-base.sha256.txt
```

Compare its package version, certificate and hash with the already preserved 2.0.3.20 APK. A different signed version could be especially valuable for differential analysis.

## Phase 4 — Preserve Accessible Starlink Data

Start by listing likely shared-storage paths rather than deleting or moving anything:

```bash
adb shell find /sdcard -maxdepth 4 -iname '*autel*' -o -iname '*starlink*' 2>/dev/null
```

Common paths may vary by version. Pull any discovered Autel/Starlink directory as a complete tree, for example:

```bash
adb pull /sdcard/Autel starlink-files/Autel
adb pull /sdcard/Starlink starlink-files/Starlink
```

Also inspect common Android shared-data locations:

```bash
adb shell ls -la /sdcard/Android/data/com.autel.maxlink
adb shell ls -la /storage/emulated/0/Android/data/com.autel.maxlink
```

Access restrictions vary by Android version. Failure to read private app data does **not** mean it is absent. Do not root the tablet merely to obtain it; first exhaust normal ADB, shared-storage and backup options.

Potentially valuable files include:

- flight records/logs;
- cached maps;
- camera/media metadata;
- crash logs;
- configuration files;
- firmware packages;
- downloaded manuals;
- database files accessible in shared storage.

Preserve originals and work from copies.

## Phase 5 — Baseline Connection Capture

### Hardware safety

- Remove all propellers.
- Place the aircraft on a clear bench.
- Use a known-good battery that is not swollen, damaged or severely imbalanced.
- Keep the factory remote controller available and powered normally.
- Do not issue motor, arming, takeoff, mission or firmware commands.

### Capture tablet logs

Clear only the transient Android logcat buffer—not app data—then start recording:

```bash
adb logcat -c
adb logcat -v threadtime > logcat/starlink-connect.txt
```

While recording, perform a scripted sequence and note timestamps:

1. Starlink closed; controller disconnected.
2. Open Starlink.
3. Connect the tablet to the remote.
4. Accept the Android USB permission prompt, if shown.
5. Power the remote.
6. Power the aircraft with props removed.
7. Wait for telemetry/camera/video.
8. Open one screen at a time: aircraft status, battery, camera, map, settings.
9. Stop without changing settings.

After connection, save new USB state:

```bash
adb shell dumpsys usb > usb/dumpsys-usb-connected.txt
adb shell dumpsys package com.autel.maxlink > tablet/starlink-package-connected.txt
```

If permissions allow, record sockets/routes:

```bash
adb shell ip addr > tablet/ip-addr-connected.txt
adb shell ip route > tablet/ip-route-connected.txt
adb shell cat /proc/net/tcp > tablet/proc-net-tcp-connected.txt
adb shell cat /proc/net/udp > tablet/proc-net-udp-connected.txt
adb shell cat /proc/net/tcp6 > tablet/proc-net-tcp6-connected.txt
adb shell cat /proc/net/udp6 > tablet/proc-net-udp6-connected.txt
```

Some Android builds restrict these files. Record the failure rather than altering security controls.

## Phase 6 — USB Descriptor Capture

The first objective is to identify:

- USB vendor ID and product ID;
- device class/subclass/protocol;
- interface count and classes;
- endpoint addresses;
- bulk/control/interrupt endpoint types;
- maximum packet sizes;
- device/manufacturer/product strings;
- attach intent and permission behavior.

Useful host-side tools include:

Linux:

```bash
lsusb
lsusb -v -d VVVV:PPPP
```

Windows:

- USB Device Tree Viewer
- USBPcap with Wireshark, if packet capture becomes necessary

macOS:

```bash
system_profiler SPUSBDataType
```

A passive descriptor dump is preferred before attempting full packet capture.

## Phase 7 — Controlled UI Correlation

Once baseline connection works, repeat one action per capture session:

- open camera status;
- switch photo/video mode without recording;
- open battery detail;
- open aircraft status;
- move between map and FPV views;
- tilt the aircraft by hand with motors disabled and observe attitude telemetry;
- rotate the gimbal using the physical controller only.

Record exact timestamps so logcat/USB/network events can be correlated. Avoid write-heavy settings or autonomous-flight functions until the protocol is understood.

## Evidence Manifest

For every captured artifact record:

```yaml
capture_id: YYYYMMDD-HHMM-description
operator: initials
props_removed: true
android_device: manufacturer/model
android_version: value
starlink_version: value
controller_firmware: value
aircraft_firmware: value
camera_firmware: value
battery_id_redacted: true
sequence: short description
files:
  - path: relative/path
    sha256: value
notes: observations/errors
```

## Privacy and Repository Rules

Do not commit raw private flight locations, device serials, account tokens, private app databases, APKs, firmware binaries or unredacted captures to the public repository.

The public repo should contain:

- hashes and metadata;
- redacted/sanitized fixtures;
- protocol documentation;
- reproducible scripts;
- observations that do not expose personal data or proprietary binaries.

## Success Criteria

This preservation task is complete when we have:

1. an independently hashed copy of the installed Starlink APK;
2. a full device/app/firmware inventory;
3. accessible Starlink files and at least one flight log;
4. controller USB descriptors;
5. a clean logcat connection trace;
6. a timestamped baseline session manifest; and
7. no changes that reduce the tablet's ability to run the original app.
