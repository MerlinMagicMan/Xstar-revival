# Starlink Firmware Updater Call-Graph Findings

## Scope

This document records static analysis of the preserved Starlink 2.0.3.20 APK, focused on whether the Android application transforms/encrypts X-Star component firmware before delivery and whether the APK's `XXTEA.java` string is relevant to the X-Star firmware updater.

## Key conclusion

The current static evidence does **not** show Starlink applying XXTEA/TEA/XTEA/DES to X-Star firmware components.

The `XXTEA.java` source-file marker in the APK belongs to the bundled AMap mapping library class:

```text
Lcom/amap/api/services/a/ai;
```

It is therefore third-party map-library code, not an Autel firmware-updater class. Earlier research that treated the mere presence of `XXTEA.java` as a meaningful Autel updater clue was too weak and is superseded by this call-site/class-origin analysis.

## Starlink aggregate-package handling

The Autel updater-side classes identified in the APK include:

```text
com.autel.starlink.aircraft.upgrade.engine.AutelFirmwareConst
com.autel.starlink.aircraft.upgrade.engine.SubBin
com.autel.starlink.aircraft.upgrade.engine.SubBinData
com.autel.starlink.aircraft.upgrade.utils.AutelFirmUpBinParseTools
com.autel.starlink.aircraft.upgrade.utils.AutelFirmwareConfig
com.autel.starlink.aircraft.upgrade.utils.AutelFirmwareUtils
com.autel.starlink.aircraft.upgrade.utils.AutelUpdateVersionCompare
```

`AutelFirmUpBinParseTools.getSubBin(String)` performs Gson deserialization of the JSON manifest into the `SubBin` / `SubBinData` model. `SubBinData` carries the fields:

```text
id
inversion
type
filename
version
length
md5
crc32
```

The recovered V1.1.3 and V2.0.12 manifests both set the Battery component (`type = 9`) `inversion` field to `0`.

## Version-comparison path

`AutelUpdateVersionCompare.isAllFileNewest(String)` calls:

```text
AutelFirmUpBinParseTools.getSubBin(...)
SubBin.getData()
```

and then compares component metadata using `SubBinData.getType()` and `SubBinData.getVersion()`.

This path is concerned with deciding whether installed component versions are current; it is not evidence of payload transformation.

## Firmware network path

`AutelFirmwareUtils` contains the historical Autel firmware update service endpoint:

```text
http://app.autelrobotics.com:8080/AutelUpdate/FirmwareUpdateAddrServlet/
```

and parses server-returned metadata including:

```text
version
downurl
md5
summary
file-size
```

Again, this is download/version metadata handling rather than component cryptography.

## Implication for Battery V5.21 / V6.07

The Battery component payloads are already transformed when embedded in the aggregate `.bin`. Because the Android updater path does not currently show a corresponding battery-specific decrypt/transform call, the repeating-XOR decoding discovered independently is more likely to be reversed/applied by:

1. the aircraft firmware-update/transfer layer;
2. a component-specific loader; or
3. the battery-side MSP430 bootloader/application updater.

The exact consumer still requires transport/loader tracing.

## Corrected interpretation of `XXTEA.java`

The APK contains the source-file string `XXTEA.java`, but class-def/source-file mapping resolves it to:

```text
Lcom/amap/api/services/a/ai;
```

which is under AMap, the mapping/navigation dependency bundled with Starlink.

Therefore:

- `XXTEA.java` presence in the APK is **not** evidence that X-Star battery firmware uses XXTEA;
- no updater-specific XXTEA call site has been established;
- the battery transform has now been solved independently as a repeating XOR mask, making the XXTEA hypothesis unnecessary for V5.21/V6.07 decoding.

## Next updater target

Continue from the Android metadata path into the aircraft-side update transport, especially component `type = 9`, the `TRANSFER` component, MAVLink/UAVCAN update messages, and any bootloader protocol that writes the battery MSP430.
