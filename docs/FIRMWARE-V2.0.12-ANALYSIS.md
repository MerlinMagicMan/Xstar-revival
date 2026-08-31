# X-Star Premium V2.0.12 Firmware Analysis

## Artifact

```text
Filename: X3P_FW_900M_V2.0.12.bin
Size:     59,778,215 bytes
SHA-256:  fe6c66bed25ac01395f3b9082accddf2989cd7a91e848fecb61e82d9b82a64d7
```

The binary itself is not committed to the public repository. This document records interoperability findings and reproducible extraction details.

## Major Finding: Outer Container Obfuscation

The complete aggregate firmware file is bytewise XOR-obfuscated with the constant `0xC8`.

Applying:

```text
decoded_byte = encoded_byte XOR 0xC8
```

immediately reveals the plaintext product header and embedded manifest, including:

```text
X-Star Premium
Autel Robotics Co.,Ltd.
```

This is simple obfuscation, not cryptographic protection.

## Embedded Manifest

The decoded package contains a JSON manifest beginning at offset `0x107` and ending at `0x11BE`.

It describes 17 component images and records, for each component:

- numeric ID;
- component type;
- filename;
- firmware version;
- hardware compatibility range;
- payload length;
- MD5;
- a firmware-specific CRC32 field.

All 17 extracted payloads have been independently verified against the manifest MD5 values.

## Component Inventory

| ID | Type | Component image | Version | Length |
|---:|---:|---|---|---:|
| 0 | 9 | `X3P_BATTERY41_V6.07_20170627.BIN` | V6.07 | 14,336 |
| 1 | 6 | `X3P_CAMERA_V0.0.0.053_20170615.BIN` | V0.0.0.053 | 45,025,324 |
| 2 | 3 | `X3P_DSP_V0.01.60_20170502.IMG` | V0.01.60 | 9,791,334 |
| 3 | 0 | `X3P_FC_V2.00.38_20170928.BIN` | V2.00.38 | 687,764 |
| 4 | 5 | `X3P_GIMBAL_V2.0.3.5_20170819.BIN` | V2.0.3.5 | 81,304 |
| 5 | 15 | `X3P_GROUND_V1.01.50_20170328.IMG` | V1.01.50 | 3,245,072 |
| 6 | 11 | `X3P_OPTICAL_V0.06.08_20170417.UPG` | V0.06.08 | 132,656 |
| 7 | 21 | `X3P_RCLANGLIB_V1.0.0.15_20170620.BIN` | V1.0.0.15 | 121,856 |
| 8 | 8 | `X3P_RC_V1.0.1.5_20170713.BIN` | V1.0.1.5 | 380,480 |
| 9 | 13 | `X3P_RFRX_V1.0.2.5_20160224.BIN` | V1.0.2.5 | 19,168 |
| 10 | 10 | `X3P_RFTX_V1.0.2.5_20160224.BIN` | V1.0.2.5 | 18,816 |
| 11 | 12 | `X3P_SONAR_V0.1.0.6_20170417.UPG` | V0.1.0.6 | 22,224 |
| 12 | 14 | `X3P_TRANSFER_V1.0.2.5_20160219.BIN` | V1.0.2.5 | 35,728 |
| 13 | 16 | `X3P_UAVESC0_V0.0.6.2_20160315.UPG` | V0.0.6.2 | 49,376 |
| 14 | 17 | `X3P_UAVESC1_V0.0.6.2_20160315.UPG` | V0.0.6.2 | 49,376 |
| 15 | 18 | `X3P_UAVESC2_V0.0.6.2_20160315.UPG` | V0.0.6.2 | 49,376 |
| 16 | 19 | `X3P_UAVESC3_V0.0.6.2_20160315.UPG` | V0.0.6.2 | 49,376 |

This inventory independently matches the component/version table in Autel's V2.0.12 release notes.

## Container Layout

For this package:

```text
0x00000000   decoded package header
0x00000107   JSON manifest begins
0x000011BE   JSON manifest ends
0x000011C5   component 0 begins
```

Seven non-payload bytes occur between the end of the manifest and the first component image.

Between component payloads is a six-byte marker:

```text
E1 E0 <next-component-id as uint32 little-endian>
```

Examples:

```text
E1 E0 01 00 00 00
E1 E0 02 00 00 00
...
E1 E0 10 00 00 00
```

The package ends with:

```text
E1 D1 55 55
```

The extractor in `tools/firmware/extract_xstar_firmware.py` validates these markers rather than assuming raw concatenation.

## Verified Component Offsets

| ID | Decoded offset |
|---:|---:|
| 0 | `0x11C5` |
| 1 | `0x49CB` |
| 2 | `0x2AF51FD` |
| 3 | `0x344B969` |
| 4 | `0x34F3803` |
| 5 | `0x35075A1` |
| 6 | `0x381F9B7` |
| 7 | `0x383FFED` |
| 8 | `0x385DBF3` |
| 9 | `0x38BAA39` |
| 10 | `0x38BF51F` |
| 11 | `0x38C3EA5` |
| 12 | `0x38C957B` |
| 13 | `0x38D2111` |
| 14 | `0x38DE1F7` |
| 15 | `0x38EA2DD` |
| 16 | `0x38F63C3` |

## Battery V6.07

The critical battery payload has now been isolated exactly:

```text
X3P_BATTERY41_V6.07_20170627.BIN
Length: 14,336 bytes
MD5:    2C4AAD6D78B12152C5DDF3FB4EDC2CC9
```

This confirms that `Battery V6.07` is a distinct update image in Autel's aggregate package and is not merely a version label reported by Starlink.

### Hardware context

Independent battery-board research reports two important ICs on the original X-Star battery PCB:

- TI `bq3055` smart fuel gauge / pack manager;
- an MSP430 microcontroller communicating on the same smart-battery bus.

That distinction is important. The BQ3055 itself reports its own TI firmware revision (community measurements report BQ3055 FW v0.04), while Autel's package separately delivers a 14 KB `Battery V6.07` image. The leading hypothesis is therefore that Autel V6.07 targets the battery-side MSP430 or another programmable layer around the gauge rather than replacing the BQ3055's internal ROM firmware.

This is a hypothesis, not yet proven from the payload contents.

### Inner encoding status

Unlike several `.UPG` payloads in the package, which expose recognizable Autel update headers and plaintext vendor strings immediately, the Battery V6.07 payload does not currently present a conventional raw MSP430 vector table or obvious plaintext firmware header.

Its byte distribution and repeated word patterns indicate that the battery sub-image itself is likely encoded, transformed, encrypted, or packaged in a battery-specific format.

Therefore:

```text
Outer aggregate encoding: SOLVED (XOR 0xC8)
Battery sub-image extraction: SOLVED + MD5 verified
Battery inner image format: OPEN
Battery target MCU: strong MSP430 hypothesis; needs confirmation
```

## Other Immediate Findings

### Sonar / optical-flow / RC update headers

Several component images begin with an Autel-style update header containing values such as:

```text
02 AA 55 AA
...
Autel Intelli...
```

The sonar and optical-flow images are therefore now independently extractable research targets for Starpoint reverse engineering.

### Camera image

The camera payload exposes extensive Ambarella bootloader/system strings after outer decoding. This confirms that the simple XOR transform was applied across the full aggregate and that extracted payloads preserve their native internal formats.

### ESC images

All four ESC payloads are byte-identical and share the same manifest MD5, consistent with one firmware image being deployed independently to four ESC targets.

## Battery-Replacement Implications

The extracted V6.07 image materially improves the Revival Battery program.

We can now investigate three separate compatibility layers instead of treating the battery as one black box:

```text
4S cell pack
   |
TI BQ3055
  - cell voltage/current/temp
  - gauging/data flash
  - protection state
   |
Autel battery MCU / V6.07 layer
  - pack identity / aircraft protocol
  - update handling
  - possible self-discharge policy
   |
BATI2C / aircraft
```

Autel's V6.07 release notes specifically mention improved battery self-discharging behavior and power-drop prevention for long-stored batteries, which is consistent with V6.07 containing active battery-side control behavior rather than being merely static capacity data.

## Reproducible Extraction

Run:

```bash
python tools/firmware/extract_xstar_firmware.py \
  X3P_FW_900M_V2.0.12.bin \
  --output xstar-fw-extracted
```

Expected final line:

```text
verification=ALL_COMPONENT_MD5_MATCH
```

Do not commit extracted proprietary firmware payloads to the public repository.

## Next Research Steps

1. Reverse the Battery V6.07 inner transform/container.
2. Identify the exact battery-side MSP430 part from board photographs or a donor PCB.
3. Trace Starlink/aircraft update routing for component type `9`.
4. Determine the battery bootloader protocol over BATI2C/SMBus.
5. Compare V6.07 with an older battery image if an earlier aggregate firmware package can be recovered.
6. Determine whether battery firmware update and BQ3055 data-flash update are independent operations.
7. Extract and document Starpoint optical/sonar `.UPG` headers for future raw-sensor work.
8. Build a read-only battery-bus capture tool before attempting any firmware write.

## Safety Boundary

Firmware extraction is offline and non-destructive. No extracted image should be written to a battery, aircraft, ESC, or sensor module until the bootloader protocol, target identity, integrity checks, rollback behavior, and recovery path are understood.
