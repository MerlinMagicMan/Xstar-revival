# Battery V5.21 vs V6.07 Differential Analysis

## Scope

This document compares two independently extracted X-Star Premium battery firmware components:

| Property | Battery V5.21 | Battery V6.07 |
|---|---|---|
| Parent package | X-Star Premium V1.1.3 | X-Star Premium V2.0.12 |
| Component filename | `X3P_BATTERY41_v5.21_20160324.bin` | `X3P_BATTERY41_V6.07_20170627.BIN` |
| Length | 14,336 (`0x3800`) | 14,336 (`0x3800`) |
| MD5 | `D02AC39F837B572437C1EFD3E1344334` | `2C4AAD6D78B12152C5DDF3FB4EDC2CC9` |
| SHA-256 | `f3236905978d352b4a73be3f28d066a7f89910f182fcbb0c863581ee9e706816` | `cfba99aef0bab724105ef1e1b914903d09432f58944fcd293b19650cbb6f73c7` |

Both parent packages were decoded and their component payloads verified against Autel's own embedded MD5 manifest using `tools/firmware/extract_xstar_firmware.py`.

No proprietary firmware binaries are committed to this repository.

## Reproducible analyzer

Use:

```bash
python tools/firmware/battery_fw_diff.py \
  X3P_BATTERY41_v5.21_20160324.bin \
  X3P_BATTERY41_V6.07_20170627.BIN \
  --apk android-comautelmaxlink-V20320.apk
```

The APK is optional. If supplied it is only scanned for printable candidate key material and the presence of relevant class-name strings; it is not modified or executed.

## Reproduced differential result

```text
length=0x3800 (14336)
equal_8byte_blocks=46/1792 (2.57%)
longest_equal_run_blocks=44
longest_equal_run_offset=0x3670-0x37CF
candidate_address=0xFE70-0xFFCF

old repeated EA9C70A3322C1D86: 43 occurrences
new repeated EA9C70A3322C1D86: 48 occurrences
```

The 44-block equal run corresponds to 352 consecutive bytes shared at identical aligned offsets in both versions.

## Candidate MSP430 mapping

Both images have exact length `0x3800`. A candidate base address of `0xC800` maps the image exactly through `0xFFFF`:

```text
0xC800 + 0x3800 = 0x10000
```

Under this hypothesis the longest identical aligned run maps to:

```text
0xFE70-0xFFCF
```

That is notable because it approaches the upper interrupt-vector/reset-vector region of classic 16-bit-address-space MSP430 devices.

This mapping is **strong evidence, not yet proof**. Exact MSP430 part identification and decrypted vector values are still required.

## Repeated 64-bit block

The block:

```text
EA 9C 70 A3 32 2C 1D 86
```

occurs 43 times in V5.21 and 48 times in V6.07.

A large concentration of identical 8-byte blocks over the candidate high-flash/unused region is consistent with deterministic transformation of repeated plaintext such as erased flash (`FF FF FF FF FF FF FF FF`).

The fact that corresponding identical regions generate corresponding identical blocks is evidence for an independently transformed/block-aligned scheme (ECB-like behavior) rather than CBC-style chaining with changing prior-block state.

This does **not** by itself identify the cipher.

## Important Starlink clue: XXTEA is present

Static string/class inventory of the preserved Starlink 2.0.3.20 APK contains:

```text
XXTEA.java
Encrypt.java
decrypt
encrypt
```

This makes XXTEA materially more interesting than it was when the analysis was based only on ciphertext block size.

However, presence in the APK does not prove that Battery V5.21/V6.07 uses that class. Starlink contains many third-party and unrelated utilities, so a call-graph or updater-specific cross-reference is required.

## Candidate transform tests performed

The analyzer tested the presumed repeated plaintext candidates:

```text
FF FF FF FF FF FF FF FF
00 00 00 00 00 00 00 00
```

against the observed repeated ciphertext block, including straightforward byte/reversal variants, with standard implementations of:

- TEA, big- and little-endian;
- XTEA, big- and little-endian;
- XXTEA/Corrected Block TEA for a two-word block, big- and little-endian;
- DES/degenerate 8-byte TripleDES when the `cryptography` package is available.

When the Starlink APK was supplied, candidate keys were generated from its printable DEX strings plus common/default values. The reproduced run tested:

```text
32,857 16-byte candidates
40,415 8-byte candidates
```

Result:

```text
candidate_matches=[]
```

### What that result means

It rules out only those **specific standard algorithm + tested plaintext + tested key + byte-order combinations**.

It does **not** rule out XXTEA, TEA, XTEA, DES-family transforms or another block cipher in general because:

- the real key may be binary rather than printable;
- the key may be derived at runtime;
- the firmware payload may include whitening/XOR/permutation before or after encryption;
- erased flash plaintext may not actually be all `0xFF` at the transformed stage;
- Autel may use a modified algorithm;
- the 8-byte pattern may reflect a record/packing transform rather than direct block-cipher output.

## Structural observations

Only 46 of 1,792 aligned 8-byte blocks are identical between V5.21 and V6.07, indicating substantial application-code change between versions while preserving a small common high-flash region.

The strongest equal run ends at candidate address `0xFFCF`. Data immediately after that region changes between firmware versions, which is consistent with version-specific interrupt/vector targets if the MSP430 mapping is correct.

The combination of:

1. exact `0x3800` length;
2. candidate `0xC800-0xFFFF` mapping;
3. repeated aligned 8-byte block in the high-flash region;
4. corresponding equal run across two historical releases;
5. separate battery-side MSP430 evidence from community board research;

strengthens the hypothesis that these components are transformed MSP430 application images rather than BQ3055 ROM firmware.

## Next cryptanalytic targets

### 1. Trace Starlink `XXTEA` call sites

Determine whether `AutelFirmUpBinParseTools`, `SubBin`, firmware updater classes, battery type `9`, or native libraries reference the XXTEA implementation.

### 2. Recover another intermediate battery image

V5.22, V5.x or an intermediate V6.x would allow three-way block classification:

```text
unchanged across all versions
changed once
changed incrementally
version-specific vectors/data
```

### 3. Recover exact MSP430 part number

Board photography or service evidence can determine actual flash geometry and interrupt vector layout, converting the current `0xC800` hypothesis into a hardware-backed memory map.

### 4. Obtain a read-only MSP430 flash dump from a real pack

A direct hardware dump from a battery reporting V5.21 or V6.07 would be the highest-value known-plaintext artifact. Comparing plaintext flash to the corresponding update payload could solve the transform immediately.

### 5. Trace battery update transport

The aircraft/Starlink update path for manifest type `9` can reveal whether decryption happens:

- in Starlink;
- in the aircraft flight controller;
- in a transfer-loader component;
- in the battery MSP430 bootloader itself.

The location of that transform determines where the key/algorithm must exist.

## Current conclusion

The V5.21 recovery materially strengthens the battery-firmware model. The two images exhibit deterministic 8-byte block behavior over a candidate MSP430 high-flash region, and the preserved Starlink APK independently contains an `XXTEA.java` implementation. Standard XXTEA/TEA/XTEA tests with tens of thousands of obvious/APK-derived printable key candidates did not reproduce the repeated block, so the next step should be **call-graph/key derivation analysis and hardware-derived known plaintext**, not blind brute force.
