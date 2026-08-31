# X-Star Battery MCU Structure After V5.21/V6.07 Decode

## Status

The Battery V5.21 and V6.07 component transform is solved as a fixed repeating XOR mask. After decoding, both 14,336-byte images contain strong MSP430-family structure. This document records what is proven and what remains uncertain before assigning a final linker/memory map.

No proprietary firmware image is committed to this repository.

## Reproducible decode

`tools/firmware/decode_battery_firmware.py` removes the repeating mask:

```text
15 63 8F 5C CD D3 E2 79
```

The formerly repeated ciphertext block:

```text
EA 9C 70 A3 32 2C 1D 86
```

therefore becomes eight erased-flash bytes:

```text
FF FF FF FF FF FF FF FF
```

This is direct evidence that the inner transform is XOR obfuscation, not TEA/XXTEA/DES encryption.

## MSP430 instruction evidence

The decoded images contain many occurrences of canonical MSP430 instruction words. Most useful is:

```text
0x4130  MOV @SP+,PC  ; canonical RET emulation
```

Observed counts:

| Image | `0x4130` occurrences |
|---|---:|
| Battery V5.21 | 94 |
| Battery V6.07 | 86 |

The density and distribution of this instruction, together with community PCB evidence of a TI MSP430 on the battery board, strongly confirms that the decoded payload is MSP430-family application code/data rather than bq3055 ROM firmware.

## Erased high-image region

The largest contiguous `0xFF` runs are:

| Image | Start offset | Length |
|---|---:|---:|
| V5.21 | `0x366C` | 354 bytes |
| V6.07 | `0x3644` | 394 bytes |

Both runs terminate immediately before a compact structured tail near the end of the `0x3800`-byte image.

The later V6.07 build therefore leaves slightly more erased space in this image region than V5.21 despite broad code changes elsewhere.

## Structured 50-byte tail

The final 50 bytes of both decoded images have the same shape:

```text
12 x [ 0x010C, 16-bit value ]
1  x [ final 16-bit value ]
```

V5.21:

```text
010C:FB04
010C:BB1F
010C:1FAE
010C:0304
010C:ABBF
010C:1FAE
010C:FB91
010C:1FB9
010C:1FAE
010C:1FAE
010C:670D
010C:1FAE
final: FB09
```

V6.07:

```text
010C:8F9E
010C:8FA6
010C:1F31
010C:0304
010C:0F22
010C:1F31
010C:8FBB
010C:1F9C
010C:1F31
010C:1F31
010C:DB3C
010C:1F31
final: CF20
```

This is clearly intentional linker/update structure, but it should **not yet be labeled the native MSP430 interrupt vector table**. TI MSP430 hardware interrupt vectors are fixed 16-bit entries; the repeated 4-byte `0x010C:value` records may instead be proxy/trampoline metadata, 20-bit-address packing, a loader-specific table, or another compiler/linker construct.

Earlier research treated `0xC800-0xFFFF` as a likely direct flash mapping because `0x3800` bytes exactly fill that range. The decoded tail shows that this is still a useful hypothesis but **not proven enough to use as the canonical address map**.

## Shared 256-byte linear lookup table

A byte-identical 256-byte lookup table survives between releases but moves in the image:

```text
V5.21 offset: 0x34C2
V6.07 offset: 0x344E
```

The first bytes are:

```text
00 D0 B0 60 34 E4 84 54 2C FC 9C 4C 18 C8 A8 78 ...
```

Properties reproduced from the decoded images:

- exactly 256 bytes;
- all 256 byte values occur exactly once;
- `table[a] XOR table[b] == table[a XOR b]` for all byte pairs tested;
- therefore the table is a linear GF(2) transform/permutation, not an arbitrary nonlinear S-box.

Its exact purpose is not yet identified. It may be a checksum/codec/bit-mixing helper and is a useful anchor for function matching across firmware generations.

## Community hardware evidence

Independent battery-board work documents:

- TI bq3055 fuel gauge;
- a separate TI MSP430 MCU;
- the MSP430 continuously accesses the bq3055 as SMBus master;
- holding the MSP430 in reset releases the bus and allows direct bq3055 service access;
- the MSP430 performs the X-Star pack's 1:2 current/capacity scaling before reporting values to the aircraft.

Relevant source thread:

- https://www.laptopu.ro/community/reset-and-repair-dji-drone-battery/unsealing-a-ti-bq3055-chip-in-a-nother-drone-lipo-battery-pack/

This division of responsibilities is consistent with Autel Battery V5.21/V6.07 being MSP430 application firmware while the bq3055 separately reports TI firmware v0.04.

## New analysis tool

`tools/firmware/analyze_decoded_battery.py` reports:

- image hash and size;
- canonical `RET` word count;
- erased-flash runs;
- final-word value;
- the 12-record `0x010C:value` tail shape.

It intentionally avoids assigning physical addresses until the exact MSP430 part/linker map is established.

## Immediate next targets

1. Identify the exact MSP430 marking from high-resolution battery PCB photography or a donor board.
2. Obtain a proper MSP430X disassembly of both decoded images.
3. Treat `0x4130` returns, branches/calls, the shared 256-byte linear table, and the structured tail as anchors for function recovery.
4. Build a function-level V5.21 -> V6.07 diff and correlate changed routines with Autel's documented battery behavior changes.
5. Locate SMBus access routines by their peripheral/register and bq3055 command constants.
6. Locate the aircraft-facing communications path and determine whether it is UART, SMBus/I2C, or a translated internal interface at the battery PCB boundary.
7. Determine the exact meaning of the `0x010C:value` records before treating the final word as a reset vector.

## Confidence summary

| Finding | Confidence |
|---|---|
| V5.21/V6.07 transform is repeating XOR | Reproduced / very high |
| decoded payload contains MSP430-family code | Very high |
| payload targets separate battery MSP430, not bq3055 ROM | High |
| 256-byte linear table is shared utility data | Reproduced / high |
| final 50 bytes form deliberate 12-record table + word | Reproduced / very high |
| image maps directly at `0xC800-0xFFFF` | Hypothesis only |
| final word is native reset vector | Hypothesis only |
| exact MSP430 part | Unknown |
