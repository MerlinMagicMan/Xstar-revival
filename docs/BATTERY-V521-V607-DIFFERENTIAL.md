# Battery V5.21 vs V6.07 Differential Analysis

## Scope

This document compares two independently extracted X-Star Premium battery firmware components and records the now-solved inner transform.

| Property | Battery V5.21 | Battery V6.07 |
|---|---|---|
| Parent package | X-Star Premium V1.1.3 | X-Star Premium V2.0.12 |
| Component filename | `X3P_BATTERY41_v5.21_20160324.bin` | `X3P_BATTERY41_V6.07_20170627.BIN` |
| Length | 14,336 (`0x3800`) | 14,336 (`0x3800`) |
| MD5 | `D02AC39F837B572437C1EFD3E1344334` | `2C4AAD6D78B12152C5DDF3FB4EDC2CC9` |
| SHA-256 | `f3236905978d352b4a73be3f28d066a7f89910f182fcbb0c863581ee9e706816` | `cfba99aef0bab724105ef1e1b914903d09432f58944fcd293b19650cbb6f73c7` |

Both parent packages were decoded and all components verified against Autel's embedded MD5 manifests using `tools/firmware/extract_xstar_firmware.py`.

No proprietary firmware binaries are committed to the public repository.

## Major result: inner transform solved

The Battery V5.21/V6.07 payload is **not protected by TEA, XTEA, XXTEA, DES, or another avalanche-style 64-bit block cipher** at this layer.

It uses a fixed eight-byte repeating XOR mask:

```text
15 63 8F 5C CD D3 E2 79
```

The strongest known-plaintext observation is the repeated ciphertext block:

```text
EA 9C 70 A3 32 2C 1D 86
```

which occurs 43 times in V5.21 and 48 times in V6.07. XORing it with the recovered mask yields:

```text
FF FF FF FF FF FF FF FF
```

exactly matching erased flash.

Because the mask repeats every eight bytes, decoding is simply:

```text
plaintext[i] = ciphertext[i] XOR mask[i mod 8]
```

and the same operation re-encodes the image.

## Reproducible decoder

Use:

```bash
python tools/firmware/decode_battery_firmware.py \
  X3P_BATTERY41_V6.07_20170627.BIN \
  -o battery-v607.decoded.bin
```

The tool is offline and does not communicate with hardware. It contains no proprietary firmware.

## Why the XOR result is compelling

Applying the mask to the complete payload produces several mutually reinforcing results:

1. the long high-address repeated region becomes literal `0xFF` erased flash;
2. the beginning of each image becomes structured, code-like binary rather than high-entropy ciphertext;
3. corresponding changed regions remain localized instead of exhibiting block-cipher avalanche;
4. the tail transitions from erased flash into structured version-specific data at the same candidate high-memory region in both releases.

For example, the V6.07 head begins after decoding as:

```text
FFFF22E2D090FFFFFFFF5041FFFFFFFF
F084B08470843084E0844C02B00083C1...
```

while the formerly repeated high-flash ciphertext decodes to long runs of:

```text
FFFFFFFFFFFFFFFF
```

This is far more consistent with a lightly obfuscated MCU firmware image than encrypted firmware.

## Differential result

Before decoding, the two payloads show:

```text
length=0x3800 (14336)
equal_8byte_blocks=46/1792 (2.57%)
longest_equal_run_blocks=44
longest_equal_run_offset=0x3670-0x37CF
```

A candidate image base of `0xC800` maps the 14 KiB payload exactly through `0xFFFF`:

```text
0xC800 + 0x3800 = 0x10000
```

Under that hypothesis the longest equal run maps to:

```text
0xFE70-0xFFCF
```

and, after XOR decoding, that region is overwhelmingly erased `0xFF` flash.

The exact MSP430 part and exact load address still require hardware-backed confirmation, but the image geometry and decoded flash structure strongly reinforce the MSP430 application-firmware hypothesis.

## Tail after decoding

The decoded V5.21 tail includes:

```text
FFC0  FFFFFFFFFFFFFFFF
FFC8  FFFFFFFFFFFF0C01
FFD0  04FB0C011FBB0C01
FFD8  AE1F0C0104030C01
FFE0  BFAB0C01AE1F0C01
FFE8  91FB0C01B91F0C01
FFF0  AE1F0C01AE1F0C01
FFF8  0D670C01AE1F09FB
```

V6.07 includes:

```text
FFC0  FFFFFFFFFFFFFFFF
FFC8  FFFFFFFFFFFF0C01
FFD0  9E8F0C01A68F0C01
FFD8  311F0C0104030C01
FFE0  220F0C01311F0C01
FFE8  BB8F0C019C1F0C01
FFF0  311F0C01311F0C01
FFF8  3CDB0C01311F20CF
```

The repeated structured values at the very top of memory are compatible with a vector/loader structure, though exact decoding of those entries remains a separate reverse-engineering task.

## Correction: Starlink `XXTEA.java` is unrelated

Earlier work noted that the preserved Starlink APK contains the source-file string `XXTEA.java`. A direct DEX class-def/source-file trace now resolves that marker to:

```text
Lcom/amap/api/services/a/ai;
```

which belongs to the bundled **AMap mapping library**, not the Autel firmware updater.

Therefore the prior XXTEA clue is superseded. No updater-specific XXTEA call site has been established, and XXTEA is unnecessary to explain Battery V5.21/V6.07 because the payload transform is now reproduced exactly with the repeating XOR mask.

See `docs/STARLINK-FIRMWARE-UPDATER-CALLGRAPH.md`.

## Starlink updater path observed

Static analysis identifies:

```text
AutelFirmUpBinParseTools.getSubBin(String)
    -> Gson manifest deserialization

SubBin / SubBinData
    -> id, type, inversion, filename, version, length, md5, crc32

AutelUpdateVersionCompare.isAllFileNewest(String)
    -> parse manifest
    -> inspect component type/version
```

Both historical Battery component manifests use:

```text
type = 9
inversion = 0
```

The Android application path inspected so far handles metadata/version comparison rather than applying the battery XOR transform itself. This suggests the corresponding encode/decode knowledge likely lives downstream in the aircraft transfer/update layer or battery-side loader.

## What remains to solve

### Exact MSP430 target and disassembly

Identify the battery MCU part from high-resolution board evidence or a donor pack, confirm flash geometry, map the decoded image to the correct address, and disassemble the now-readable firmware.

### Battery update transport

Trace manifest component `type = 9` through the aircraft updater / `TRANSFER` component / MAVLink or UAVCAN path to determine exactly where the XOR is removed and how flash records are delivered to the battery MCU.

### V5.21 vs V6.07 semantic diff

Once the correct MSP430 processor profile and memory mapping are established, perform function-level diffing of the decoded firmware to identify the code changes behind the later battery behavior.

### Hardware validation

A read-only MSP430 flash dump from a real V5.21 or V6.07 pack would confirm whether the decoded payload is the direct flash image or passes through one additional packing/load-address layer.

## Current conclusion

The Battery V5.21 and V6.07 update images have now crossed an important threshold: the inner transform is reproducibly solved. The fixed repeating mask is:

```text
15 63 8F 5C CD D3 E2 79
```

and the result exposes erased flash plus structured MCU code/data. The research problem is no longer cryptanalysis; it is now conventional embedded-firmware reverse engineering: exact MSP430 identification, memory mapping, disassembly, semantic diffing, and update-protocol tracing.
