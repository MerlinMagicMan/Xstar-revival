# Battery MSP430X Disassembly Workflow

## Status

The Battery V5.21/V6.07 inner transform is solved and both decoded images contain strong MSP430/MSP430X signatures. This pass wires the project to a real architecture-aware disassembler rather than continuing with byte-pattern heuristics alone.

The chosen research dependency is `python-msp430-tools`, whose BSD-licensed disassembler explicitly supports classic MSP430 and MSP430X extension words/instructions, including `mova`, `calla`, `pushm`, `pop`, and extension-word decoding.

## Toolchain

```bash
python tools/firmware/decode_battery_firmware.py X3P_BATTERY41_V6.07_20170627.BIN -o v607.dec.bin
python tools/firmware/disassemble_battery_msp430x.py v607.dec.bin -o v607.asm --base 0xC800
python tools/firmware/find_smart_battery_callers.py v607.asm > v607-smart-battery-leads.txt
```

Repeat for V5.21 and compare the resulting call sites and normalized function bodies.

## Why the base address remains explicit

`0xC800` remains a useful candidate because the decoded image length is exactly `0x3800`, but the final image trailer does not resemble a normal native vector table strongly enough to promote the mapping to fact. The wrapper therefore requires the address to remain a visible research parameter instead of baking the hypothesis into a proprietary image conversion.

## Current target: identify the SMBus primitive

The decoded image contains literals equal to standard Smart Battery command IDs. Those constants by themselves are weak evidence. The next promotion criterion is instruction-level structure:

1. a command literal is loaded into a register or stack argument;
2. control flows into the same small set of call/calla targets;
3. the target accesses the same peripheral/register or shared-bus state;
4. multiple standard battery commands converge on that target; and
5. the target has homologous code in both V5.21 and V6.07.

`find_smart_battery_callers.py` performs only the first triage step: it finds decoded command immediates near calls. It deliberately does not name the called routine `SMBusRead`/`SMBusWrite` until the peripheral accesses prove that interpretation.

## Function matching strategy

A direct same-address diff is misleading because more than 93% of raw bytes move/change between V5.21 and V6.07. The preferred matching order is:

- exact moved byte regions already discovered;
- normalized instruction sequences with addresses/immediates masked where appropriate;
- common call targets/call-graph neighborhoods;
- references to stable lookup/data tables;
- Smart Battery command dispatch behavior.

Once the common BQ3055 transaction primitive is identified, trace all of its callers and classify them by command number. That will produce a real battery-service function map and let us isolate which routines changed in V6.07.

## Research boundary

This work is static/offline only. No flash/write path is added. The repository does not contain Autel firmware binaries; users supply locally preserved, hash-verified artifacts.
