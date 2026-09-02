# X-Star Remote-Controller Firmware

## Preserved source packages

Two independently recovered X-Star Premium aggregate packages provide a
controller-firmware comparison set:

| Aggregate package | SHA-256 | RC component | RC version |
|---|---|---|---|
| `X3P_FW_900M_V1.1.3.bin` | `63096382d1cb252edc18efc67065e1a16f37b2f445a2eca08863a1bbd30f5d2b` | `X3P_RC_V1.0.0.37_20160406.bin` | V1.0.0.37 |
| `X3P_FW_900M_V2.0.12.bin` | `fe6c66bed25ac01395f3b9082accddf2989cd7a91e848fecb61e82d9b82a64d7` | `X3P_RC_V1.0.1.5_20170713.BIN` | V1.0.1.5 |

Both aggregate packages pass all 17 component MD5 checks with
`tools/firmware/extract_xstar_firmware.py`. The second package confirms that the
known final X-Star Premium remote-controller version is V1.0.1.5. This is also
the version named by the preserved SDK capability record.

The proprietary package and extracted component binaries remain in the private,
Git-ignored research archive. They are not committed to the public repository.

## RC-PRO wrapper

Both extracted controller images use the same wrapper:

```text
offset  size  observed meaning
0x00    4     02 AA 55 AA magic
0x1C    4     version, little-endian packed as V1.0.1.5
0x20    4     unpadded inner-payload length
0x24    4     STM32 non-reflected word CRC-32 of decoded payload
0x28    4     target value 7 in both recovered images
0x2C    4     duplicate version field
0x30    ...   NUL-terminated product tag "RC-PRO"
0xF0    ...   inner payload, rounded up to a 16-byte boundary
```

The declared inner length is exact: rounding it up to the next 16-byte boundary
and adding the `0xF0` wrapper reproduces the full component length in both
releases.

## Decoded inner payload

The inner payload is AES-128-ECB using this fixed key:

```text
2B 7E 15 16 28 AE D2 A6 AB F7 15 88 09 CF 4F 3C
```

The most common ciphertext block in both releases is:

```text
7D F7 6B 0C 1A B8 99 B3 3E 42 F0 47 B9 1B 54 6F
```

It occurs 8,233 times in V1.0.0.37 and 5,747 times in V1.0.1.5. This is the
published [NIST AES/OMAC test-vector](https://csrc.nist.gov/CSRC/media/Projects/Block-Cipher-Techniques/documents/BCM/proposed-modes/omac/omac-ad.pdf)
result for an all-zero block under that key. Full decryption independently
confirms the identification: both outputs have exact `0xFF` padding, valid
STM32 SRAM stack pointers, Thumb reset vectors in flash and 58 plausible
interrupt vectors.

| RC version | Decoded bytes | Decoded SHA-256 | Initial SP | Reset vector |
|---|---:|---|---|---|
| V1.0.0.37 | 410,252 | `32673d1bd2aebd83a5d4c48cb0daeff186f02f9e8095746e9040aada80087039` | `0x20006E00` | `0x08013165` |
| V1.0.1.5 | 380,228 | `3a7180278ed9e4046ed57d188e09d5168ae8b61c29381c4d9869e83f258ae718` | `0x20007200` | `0x08013165` |

The application is an STM32 Cortex-M Thumb image mapped from `0x08013000`.
The reset entry `0x08013165` maps to file offset `0x164`, where valid startup
code begins, and all 58 plausible interrupt vectors map into the packaged
application. Absolute control addresses therefore use file offset plus
`0x08013000`. The decoder enforces that the reset handler maps into the image,
preventing an incorrect base address from passing validation.
The wrapper check is now identified. It is the STM32 hardware-style,
non-reflected CRC-32 with polynomial `0x04C11DB7`, initialized to zero. The
decoded payload is padded to a 32-bit boundary with `0xFF` and fed as
little-endian 32-bit words. This reproduces `0x90CBCC63` and `0x54A06F0A` for
the two RC releases. It also reproduces the wrapper checks for every available
OPTICAL, RFRX, RFTX, SONAR and TRANSFER component using the same wrapper, making
the identification independent of the two RC samples.

The aggregate manifest's component check is understood separately. Its `crc32`
value is JAMCRC (the bitwise complement of the conventional CRC-32 result) over
the complete RC component, including the wrapper and encrypted payload. This
reproduces `0xC340559D` for V1.0.0.37 and `0x1B6C66B4` for V1.0.1.5. The wrapper's
own check word at offset `0x24` can therefore be rebuilt after an offline
application change.

## Aggregate header comparison

The two recovered aggregate generations make several decoded header fields
reproducible rather than speculative:

| Offset | V1.1.3 | V2.0.12 | Supported interpretation |
|---:|---:|---:|---|
| `0x00` | `AT` | `AT` | aggregate magic |
| `0x07` | `FC C2 86 09` | `B0 1A D1 0D` | opaque four-byte package field |
| `0x0B` | `60,760,988` | `59,778,215` | exact total aggregate byte length |
| `0x13` | `0x570899A4` | `0x59CC4C99` | Unix build timestamp |
| `0x17` | `2` | `2` | constant field; meaning not assigned |
| `0x1B` | `X-Star Premium` | `X-Star Premium` | product string |
| `0x5B` | `Autel Robotics Co.,Ltd.` | same | vendor string |
| `0x101` | `B1 C0` | `B1 C0` | pre-manifest marker |
| `0x103` | `0x0BAC` | `0x10B7` | exact JSON manifest length |

The timestamps decode to 2016-04-09 05:56:52 UTC and 2017-09-28 01:12:57
UTC. Conventional CRC-32, JAMCRC, Adler-32, STM32 word CRC, digest fragments,
word sums and component-field reductions did not reproduce the two values at
`0x07`. There is consequently no evidence that this field is an integrity
check. The aggregate rebuilder preserves it byte-for-byte and labels it
**opaque**, without assigning security or update semantics.

Reproduce the structured header comparison without extracting or modifying the
package:

```bash
python tools/firmware/analyze_xstar_aggregate_header.py \
  X3P_FW_900M_V1.1.3.bin
python tools/firmware/analyze_xstar_aggregate_header.py \
  X3P_FW_900M_V2.0.12.bin
```

## Reproducing the result

After extracting both aggregate packages into private directories, inspect the
wrappers and compare ciphertext blocks:

```bash
python tools/firmware/analyze_rc_firmware.py \
  X3P_RC_V1.0.0.37_20160406.bin \
  --compare X3P_RC_V1.0.1.5_20170713.BIN
```

Decode a local extracted component to a new file:

```bash
python tools/firmware/decode_rc_firmware.py \
  X3P_RC_V1.0.1.5_20170713.BIN \
  -o X3P_RC_V1.0.1.5.decoded.bin
```

Then reproduce the controller landmark map:

```bash
python tools/firmware/map_rc_control_landmarks.py \
  X3P_RC_V1.0.1.5.decoded.bin
```

Verify the recovery-relevant application, flash and GM8136 landmarks:

```bash
python tools/firmware/map_rc_recovery_landmarks.py \
  X3P_RC_V1.0.1.5.decoded.bin
```

The analyzer, decoder and landmark mappers operate only on local files. The
decoder refuses to overwrite an output and writes it only after the plaintext
passes padding and STM32 vector-table checks. None of these tools opens a USB
device, sends a controller command or performs a firmware update.

## Offline simulator-only patch experiment

`tools/firmware/patch_rc_simulator_firmware.py` builds a tightly bounded
research variant from the exact decoded V1.0.1.5 image. It replaces only the
52-byte selector `0x210` callback. The replacement:

1. rejects a control payload longer than 124 bytes;
2. uses the stock `0x08028058` routine to create the existing `0xAA`, channel 3
   stick frame in the stock 128-byte buffer;
3. passes that complete frame to the stock `0x080280E0` `0xA5`/USART1 sender;
4. does not set the CAN queue length, so this simulator variant does not send
   stick frames toward the aircraft CAN path; and
5. leaves the existing selector `0x81` button callback unchanged.

The tool requires both the exact decoded-image SHA-256 and the exact original
callback bytes before it will write a new file. It refuses to overwrite an
output, revalidates the STM32 vector table after patching, and verifies that no
bytes outside the callback changed.

```bash
python tools/firmware/patch_rc_simulator_firmware.py \
  X3P_RC_V1.0.1.5.decoded.bin \
  -o X3P_RC_V1.0.1.5.simulator-only.decoded.bin
```

This output is deliberately **not flashable** because it is decoded application
data rather than an RC-PRO component. The wrapper check algorithm is now solved,
and `tools/firmware/rebuild_rc_simulator_component.py` can restore the exact
stock wrapper and AES packaging around the verified simulator image. That
rebuilder accepts only the exact stock and patched hashes, verifies the wrapper
CRC and performs a complete encryption/decryption round trip. Its output is
then accepted by `tools/firmware/rebuild_xstar_simulator_aggregate.py`, which
replaces only component type 8 in the exact preserved V2.0.12 aggregate and
updates its manifest MD5 and JAMCRC. A complete re-extraction verifies all 17
component MD5 values. The aggregate header's opaque four-byte field at offset
`0x07` is preserved, so the aggregate remains a research artifact rather than
an approved update. A tested controller
backup/recovery path is also not yet available. The separate USB/video
processor still has to be tested to determine whether it forwards the new
USART1 payload to Android.

```bash
python tools/firmware/rebuild_rc_simulator_component.py \
  X3P_RC_V1.0.1.5_20170713.BIN \
  X3P_RC_V1.0.1.5.simulator-only.decoded.bin \
  -o X3P_RC_SIMULATOR.BIN

python tools/firmware/rebuild_xstar_simulator_aggregate.py \
  X3P_FW_900M_V2.0.12.bin \
  X3P_RC_SIMULATOR.BIN \
  -o X3P_FW_900M_V2.0.12_SIMULATOR.bin
```

Both rebuilders refuse to overwrite an output and contain no hardware access
or update path.

## Physical-control path in V1.0.1.5

Static disassembly provides the first concrete physical-button map. Function
`0x0801BF9C` monitors five values and submits an 8-byte event through the common
dispatch routine at `0x0802C558`. The dispatch selector is `0x81`; byte zero
is the event ID and byte one is the state value.

| Event ID | Firmware debug label | Physical meaning |
|---:|---|---|
| 1 | `CANKNOB` | rear knob/wheel |
| 2 | `CANRECKEY` | record key |
| 3 | `CANSETKEY` | settings key |
| 4 | `CANPHKEY` | photo key |
| 5 | `CANSELKEY` | selector key |
| 6 | `FLYSTICK` | flight-stick mode/state event |

The much larger routine beginning at `0x08030074` is the main control update.
It samples four joystick axes through `0x0801D478`, using channel indices 0-3,
and stores the normalized values in consecutive 16-bit fields. Its control-data
builder submits a variable-length buffer with dispatch selector `0x210` through
the same `0x0802C558` routine. The dispatcher walks a RAM table of registered
selector/callback pairs and invokes every match. Selector `0x81` is registered
to callback `0x08027BD8`; selector `0x210` is registered to callback
`0x080277C4`.

The two callbacks lead to distinct transports:

- Selector `0x81` wraps each 8-byte control event in an inner `0xFE` frame with
  a sequence/type field and CRC-16. It then adds an outer `0xA5`, channel 3,
  length and checksum frame. The transmit queue at `0x0802E56C` ultimately
  writes the bytes to `0x40013804`, the STM32F1 USART1 data register. USART1 is
  initialized at 115,200 baud on PA9/TX and PA10/RX.
- Selector `0x210` wraps the variable-length flight-control data in an `0xAA`,
  channel 3, length and checksum frame. It places the result in a bounded
  128-byte buffer. The link state machine beginning at `0x08019204` drains that
  buffer through `0x0802C4B0`, which splits it into 8-byte CAN frames.
  `0x080187D0` selects a transmit mailbox and writes the identifier, data length
  and two 32-bit payload words into the bxCAN transmit registers. The hard-coded
  peripheral base is `0x40006400`, identifying the physical endpoint as CAN1.
  The firmware enables the CAN1 remap and configures PB8 as CAN_RX and PB9 as
  CAN_TX. It writes CAN bit-timing value `0x00450002`. The startup clock code
  runs the core at 72 MHz with a 36 MHz APB1 clock, making this a 1 Mbit/s bus.

USART1 here is an internal MCU hardware path; this finding does not identify it
as the Mac-visible Micro-USB port. The live Micro-USB CDC tests remain valid:
that exposed service port produced no controller stream with the aircraft off.

## Recovery-path findings

The V1.0.1.5 image now provides three independently checkable architectural
boundaries:

1. The packaged component begins with an application vector table for
   `0x08013000`; it contains no bytes for the preceding `0x13000`-byte STM32
   flash span.
2. Startup code disables interrupts and writes `0x08013000` to the Cortex-M
   System Control Block VTOR register at `0xE000ED08`, then enables interrupts
   and initializes the application. This confirms the link/run address in
   executable code.
3. The application's verified direct flash-program call paths target
   `0x08012800` and `0x0807F800`, both outside the packaged application range.
   No direct application self-reflash path was found in that map.

The image also registers callback `0x08033FF8`. It accepts device selector 7
and an exact ten-byte payload, parses request/device/status/percent/retry/version
fields and logs `GM8136 UPGRADE PARSE`. This establishes that the STM32
application receives upgrade progress from the controller's GM8136-side
processor. It does not establish which processor owns the loader or prove that
either processor can restore a damaged STM32 application.

These findings reduce the risk of an application-only update overwriting every
loader-related byte, but they are not a recovery procedure. The absent
pre-application flash span could contain a bootloader, protected configuration,
or both; its contents and behavior have not been read back. The tool reports
`bootloader_recovery_path=NOT_PROVEN` for that reason.

Autel's documented stock workflow reinforces the architectural separation:
the all-in-one package is placed on the camera SD card, the aircraft and remote
controller are powered together, and the aircraft distributes the component
updates. See the [X-Star user manual](https://fcc.report/FCC-ID/2AGNTRC5809A/2883864.pdf)
and the [official X-Star downloads page](https://shop.autelrobotics.com/pages/x-star-downloads).
The controller alone at the office cannot exercise that intended update path.

## Controller-board evidence

The controller's FCC filing is [2AGNTRC5809A, model
RC5809A](https://fccid.io/2AGNTRC5809A). Its public [internal-photo
exhibit](https://fccid.io/2AGNTRC5809A/Internal-Photos/Int-Photos-2883859.pdf)
shows the main processing assembly with its lower shield removed. The board
legend visible in the exhibit is `EF3S_MAIN_HD_V7`. Three exposed round gold
pads are visible along the left edge of that assembly, and multiple removable
board connectors are present. The office bench controller's rear label was
confirmed to match FCC ID `2AGNTRC5809A` on 2026-09-02.

Those photographs are a location guide, not a pinout. Their resolution does
not establish which nets reach the three pads, whether a CAN transceiver sits
between the MCU and a connector, or the voltage levels on any candidate point.
The FCC page lists schematics and a block diagram as metadata only, so neither
is available to resolve the nets. Before a passive capture, confirm that the
physical controller carries FCC ID `2AGNTRC5809A` and the same board revision,
then document both board faces sharply and identify ground and signal levels
without injecting power or commands.

For a passive button capture, PA9/TX plus ground is sufficient; do not connect
to PA10/RX or transmit toward the controller. Confirm the voltage first and use
a high-impedance receiver. For the stick stream, locate the CAN transceiver and
capture CAN-H/CAN-L on its bus side with a galvanically isolated adapter in
listen-only mode. Do not attach a USB CAN device directly to the MCU's PB8/PB9
logic pins, and do not add termination until the existing bus resistance has
been measured with the controller unpowered.

The image also contains calibration handlers and strings for `CANMODE`,
`RFMODE`, `DEBUGROCKER`, `DEBUGKEY`, `PhoneSet:CanMode`, `SIMULATED FLIGHT`,
`Use the command sticks`, `Command sticks error` and `App-controlled stick
disabled`. Direct code references confirm the calibration, CAN button and
`FLYSTICK` strings. The simulated-flight phrases live in the controller UI
resource region; their presence proves that the release contains that UI, but
does not by itself prove that the Micro-USB port can stream controls.

## Simulator relevance

The V1.0.1.5 image is the correct target for controller interoperability
research. The software-only route now changes the priorities to:

1. retain the byte-for-byte verified callback replacement and hash-locked
   component/aggregate rebuilders as offline-only tools;
2. retain the aggregate header field at offset `0x07` as opaque and preserved,
   and document a full backup/recovery procedure before any controller write;
3. only after those safeguards, test whether the controller's separate
   USB/video processor forwards the rerouted USART1 stick frames to Android;
4. capture one control at a time and map all axis ranges, centers, directions
   and event debounce behavior; and
5. translate the observed frames into the simulator input model while retaining
   the external isolated adapter as a fallback.

The offline modification is safe to construct and inspect. A readback of the
pre-application flash span, loader/recovery characterization, and a reversible
bench procedure remain prerequisites before any write to the physical
controller.

## Optional hardware evidence route

The FCC main-board view remains useful if a later passive hardware trace is
chosen, but opening the controller is not required for the current software-only
work. The board photographs are treated as the matching hardware reference; no
unverified trace or pad function is inferred from them.
