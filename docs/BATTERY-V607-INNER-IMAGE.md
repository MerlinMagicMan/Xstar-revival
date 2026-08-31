# Battery V6.07 Inner Image Analysis

## Status

**Source-verified static analysis; inner transform not yet solved.**

This document continues the V2.0.12 aggregate-firmware work after extracting and MD5-verifying the battery component:

`X3P_BATTERY41_V6.07_20170627.BIN`

- Manifest type: `9`
- Length: `14,336` bytes = `0x3800`
- MD5: `2C4AAD6D78B12152C5DDF3FB4EDC2CC9`
- SHA-256: `cfba99aef0bab724105ef1e1b914903d09432f58944fcd293b19650cbb6f73c7`

The proprietary binary itself is not committed to this repository.

## Key Finding

The extracted battery image is very likely **not compressed data** and does not resemble a normal plaintext MCU image. Its entropy is about **7.51 bits/byte** and it contains no useful plaintext strings.

However, its structure is highly non-random.

### Exact 14 KiB size

The component length is exactly:

```text
0x3800 bytes = 14 KiB
```

A particularly interesting candidate mapping is:

```text
0xC800 + 0x3800 = 0x10000
```

In other words, if this payload represents a contiguous MSP430 flash image loaded at `0xC800`, it spans exactly:

```text
0xC800 - 0xFFFF
```

That hypothesis matters because the upper end of the classic MSP430 16-bit address space contains the interrupt-vector table.

This is not yet proof of the load address or exact MSP430 part, but it is a strong architectural fit with independent battery-PCB evidence reporting an MSP430 application MCU alongside the TI bq3055 gauge.

## 8-byte block behavior

The strongest inner-format clue is a long sequence of identical **8-byte aligned blocks** near the end of the image.

The repeated block is:

```text
ea 9c 70 a3 32 2c 1d 86
```

It occurs 48 times at aligned offsets, with a long contiguous run beginning at approximately `0x3648`.

Representative tail data:

```text
file 0x3648: ea9c70a3322c1d86 ea9c70a3322c1d86
...
file 0x37B8: ea9c70a3322c1d86 ea9c70a3322c1d86
file 0x37C0: ea9c70a3322c1d86 ea9c70a3322cee78
file 0x37C8: ea9c70a3322cee78 8bec835d6b5cee78
...
file 0x37F8: 29b8835dfcccc2b6
```

If mapped at candidate base `0xC800`, these become:

```text
0xFE48 ... repeated blocks ...
0xFFB8 ... repeated blocks ...
0xFFC0 transition
0xFFC8 changing data
...
0xFFF8 final 8 bytes
```

That is an unusually good fit for firmware containing a large erased/unused flash region immediately before the MCU's interrupt-vector area.

## What the repeated blocks imply

A compressed stream should not ordinarily turn a long run of identical plaintext bytes into dozens of identical aligned 8-byte output blocks.

The observed behavior is instead consistent with an **ECB-like block transform with an 8-byte block size**, where identical erased flash blocks produce identical encoded blocks.

Possible families include 64-bit block ciphers or proprietary 64-bit transforms. At this stage we **do not claim DES, 3DES, TEA/XTEA, or any other specific algorithm**.

The important evidence is the block size and repetition behavior.

### Why AES is now less likely

Initial visual inspection suggested encryption generally, but the repeating ciphertext itself consists of an 8-byte pattern repeated independently on 8-byte boundaries. That makes a native 8-byte transform a better current hypothesis than AES-ECB's 16-byte block size.

## MSP430 hypothesis

Independent board research reports an MSP430 MCU communicating continuously with the TI bq3055 gauge. Autel separately reports the battery as firmware version `V6.07`, while the bq3055 has its own TI firmware/revision identity.

The current working architecture is therefore:

```text
4S LiPo cells
     |
 TI bq3055
 gauge/protection/data flash
     |
 battery-local bus
     |
 MSP430 application MCU
 Autel Battery V6.07 candidate target
     |
 aircraft BATI2C / update path
     |
 flight controller
```

The `0x3800` image size plus the vector-region behavior materially strengthens the hypothesis that the extracted V6.07 payload is transformed MSP430 application firmware rather than bq3055 ROM firmware.

## Release behavior supports an application-controller target

The X-Star V6.07 release changed high-level battery behavior, including:

- improved self-discharge control based on temperature;
- power-drop prevention for long-stored batteries.

Those are exactly the kinds of policy/application changes that can plausibly live in a battery-side application MCU while retaining the bq3055 as gauge/protection hardware.

This is contextual support, not proof of division of responsibility.

## What is now ruled out or weakened

### Plain raw firmware

Ruled out in its current extracted form. The image does not expose a normal plaintext MSP430 vector table or useful strings.

### Ordinary compression as the only transform

Weakened substantially. The aligned repeated 8-byte output blocks are not typical of a normal compressed stream.

### Battery V6.07 being merely the bq3055's reported TI firmware revision

Strongly weakened. Autel treats Battery V6.07 as an independently updateable component, while board research identifies the bq3055 and a separate MSP430 MCU.

## Unsolved questions

1. What exact MSP430 part is used?
2. Is the candidate load base really `0xC800`?
3. What 8-byte block transform protects the image?
4. What key or key derivation is used?
5. Is there an additional checksum/header before or after decryption?
6. Does the aircraft decrypt before sending data to the pack, or does the battery bootloader decrypt locally?
7. What transport carries component type `9` across BATI2C/SMBus?
8. What bootloader/recovery mechanism exists if an update fails?
9. Does V6.07 update only MSP430 code, or also write bq3055 Data Flash values after installation?

## Next reverse-engineering targets

### 1. Find an older battery image

A second known battery firmware image is extremely valuable. Differential analysis between two encrypted versions can reveal block boundaries, unchanged regions, metadata and possibly the transform.

Priority target: a pre-V6.07 firmware package, particularly one containing battery firmware at or below V5.22 because Starlink contains explicit `isBatteryVersionAboveV522` compatibility logic.

### 2. Trace component type 9 in updater firmware

The aggregate manifest labels the battery as component type `9`. Search the flight-controller/transfer/updater implementation for dispatch logic for type 9 and identify:

- destination bus/address;
- packetization;
- decrypt/transform location;
- update-mode command;
- integrity verification;
- reboot/version verification.

### 3. Passive BATI2C capture

When hardware is available, capture the bus during normal battery operation first. Do not perform an update until the protocol and recovery route are understood.

A later controlled update capture using a sacrificial non-flight pack could reveal whether ciphertext or plaintext blocks cross the bus.

### 4. Exact battery PCB inventory

Identify the MSP430 part marking and trace its connection to:

- bq3055 SMBus/I2C;
- aircraft connector;
- programming/test pads;
- flash/update pins.

Exact MCU identification will establish the expected flash map and vector table, which can confirm or reject the `0xC800` hypothesis.

## Reproducibility

Run:

```bash
python tools/firmware/analyze_battery_v607.py \
  X3P_BATTERY41_V6.07_20170627.BIN
```

The analyzer performs only offline reads and reports hashes, entropy, aligned block repetition and the candidate address mapping.

## Safety boundary

This phase is static/offline research only.

Do **not** flash the extracted battery image, issue battery bootloader commands, or write bq3055/MSP430 state until the target, transport, integrity checks and recovery path are understood. A failed battery update can disable protection or make a pack unsafe even if the cells themselves remain electrically charged.
