# Battery V5.21 → V6.07 MSP430X Structural / Function-Diff Pass

## Status

This is the next layer after solving the battery-image XOR transform. It operates only on the decoded 14,336-byte images and is intentionally conservative about symbol names until the exact MCU/linker layout and a full MSP430X disassembler are established.

## Stronger architecture evidence

The decoded images contain both classic MSP430 and MSP430X signatures:

| Signal | V5.21 | V6.07 |
|---|---:|---:|
| canonical `RET` (`0x4130`) | 94 | 86 |
| words matching MSP430X extension-word class (`0x18xx` mask) | 319 | 314 |
| largest literal `0xFF` erased run | offset `0x366C`, 354 bytes | offset `0x3644`, 394 bytes |

The hundreds of MSP430X extension-word candidates materially strengthen the conclusion that the decoded payload is MSP430-family executable content. Exact processor identification is still pending.

## How much changed

V5.21 and V6.07 have equal length (`0x3800`), but 13,361 of 14,336 bytes differ (`93.20%`). This does **not** mean 93% of source-level behavior was rewritten; compiler/linker relocation can cause large binary changes after comparatively small source edits.

Therefore byte-at-identical-offset comparison is the wrong tool for the next phase.

## Relocated common regions

A sequence-matching pass over the decoded images finds multiple substantial blocks that are byte-identical but moved between builds. Largest examples include:

```text
V5.21 old offset   V6.07 new offset   length
0x3669             0x3641             357
0x34C2             0x344E             262
0x218C             0x2C8C              94
0x235A             0x2E60              90
0x3090             0x336A              64
0x2EF4             0x329A              56
0x35E6             0x3596              53
0x1124             0x0F6A              50
```

This establishes a practical route to **function matching by content rather than address**. The already-known 256-byte linear GF(2) table is contained in the 262-byte moved region and is a particularly strong anchor.

## BQ3055 / Smart Battery leads

The BQ3055 is a Smart Battery System fuel gauge, and community hardware work confirms the battery-side MSP430 communicates continuously with it over SMBus. Forum work further reports that the MSP430 applies the X-Star pack's 1:2 current/capacity scaling before reporting values onward to the drone.

The decoded firmware contains aligned 16-bit literals corresponding to several standard Smart Battery command numbers, including candidates for:

- `0x08` Temperature
- `0x09` Voltage
- `0x0A` Current
- `0x0D` Relative State of Charge
- `0x0F` Remaining Capacity
- `0x10` Full Charge Capacity
- `0x14` Charging Current
- `0x15` Charging Voltage (present in V6.07 candidate set)
- `0x18` Design Capacity
- `0x19` Design Voltage
- `0x1B` Manufacture Date
- `0x1C` Serial Number (present in V6.07 candidate set)
- `0x23` Manufacturer Data (present in V6.07 candidate set)

These literal hits are **leads, not yet proof of command dispatch**, because small constants occur naturally in embedded code. They become high-confidence only when cross-referenced with instruction decoding and calls to a common SMBus read/write routine.

## Important hardware corroboration

The battery-repair research thread independently reports:

1. the X-Star uses a BQ3055 with firmware v0.04;
2. a separate MSP430 is on the same bus and continuously talks to the gauge;
3. SDA2/SCL2 test points lead to the BQ3055 bus;
4. the MSP430 must be silenced/held in reset for reliable direct external gauge servicing;
5. the pack uses a 1:2 current/capacity ratio due to dual parallel sense resistors; and
6. those calculations are reportedly performed by the MSP430 before values are sent onward to the aircraft.

That makes BQ command-dispatch identification the highest-value target in the decoded firmware.

## New reproducible tool

`tools/firmware/diff_decoded_battery_structure.py` performs a read-only analysis of two decoded images and reports:

- hashes and sizes;
- MSP430 `RET` signatures;
- MSP430X extension-word candidates;
- erased-flash regions;
- aligned Smart Battery command-literal leads;
- long common/moved regions; and
- raw changed-byte ratio.

It deliberately avoids assigning function names until instruction-level evidence exists.

## Next pass

The next concrete work is:

1. wire in a real MSP430X disassembler (the BSD-licensed `python-msp430-tools` disassembler is a suitable research dependency/reference);
2. seed recursive analysis from repeated call/return patterns and linker metadata rather than blindly decoding the whole image as code;
3. identify functions that access a common SMBus peripheral/register sequence;
4. trace callers that load standard Smart Battery command values before those functions;
5. match those functions between V5.21 and V6.07 using normalized instruction sequences; and
6. isolate the behavior changes relevant to the V6.07 battery release notes (especially self-discharge/long-storage policy).

## Interpretation discipline

At this stage the following are strong:

- the repeating-XOR transform is solved;
- the decoded image contains substantial MSP430/MSP430X-looking executable structure;
- the X-Star battery PCB contains an MSP430 and BQ3055 on the same bus;
- common firmware regions can be tracked despite relocation.

The following remain unproven and should not be presented as fact yet:

- the exact MSP430 part number;
- the decoded image's absolute load address;
- the meaning of the final `0x010C/value` records;
- which literal occurrences are actual BQ3055 command dispatches;
- the exact function-level changes from V5.21 to V6.07.
