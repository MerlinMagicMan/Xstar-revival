# X-Star Community Research Index

This index preserves high-value community findings and distinguishes claims from evidence reproduced by X-Star Revival.

## Evidence labels

- `REPORTED` — one community report; not independently confirmed.
- `CORROBORATED` — multiple independent reports/artifacts agree.
- `SOURCE-VERIFIED` — supported directly by an official artifact/manual/SDK/APK/firmware/hardware marking.
- `REPRODUCED` — independently reproduced by X-Star Revival.

## Battery / charger

### Battery Hack — successful modern recell and BQ3055 service access

- Source: https://autelpilots.com/threads/battery-hack.12990/
- Area: battery, BQ3055, MSP430, replacement cells
- Status: `CORROBORATED`
- High-value findings:
  - replacement cells can be used while retaining the original X-Star smart-battery electronics;
  - successful flight was reported after BMS health/capacity recommissioning;
  - direct BQ3055 communication was achieved with Raspberry Pi and Bus Pirate class hardware;
  - the battery-side MCU must be held in reset to avoid bus contention while servicing the BQ3055;
  - community-reported candidate keys include unseal `0414 3672` and full-access `FFFF FFFF`.
- Revival status: not yet reproduced on physical hardware.
- Caution: credentials and write procedures remain community-reported until verified against a donor pack with a complete read-only backup.

### BQ3055 X-Star repair / unsealing research

- Source: https://www.laptopu.ro/community/reset-and-repair-dji-drone-battery/unsealing-a-ti-bq3055-chip-in-a-nother-drone-lipo-battery-pack/
- Area: BQ3055, MSP430, SMBus/I2C, Qmax, capacity scaling, replacement cells
- Status: `CORROBORATED`
- High-value findings:
  - X-Star battery board includes a TI BQ3055 and a separate MSP430-class application MCU;
  - direct gauge access is possible through service pads when the application MCU is prevented from contending for the bus;
  - X-Star pack current/capacity reporting uses a roughly 1:2 scaling relationship associated with the current-sense arrangement;
  - Qmax/FCC/learned state matters after cell replacement;
  - 5100 mAh LiHV replacement-cell experiments achieved flight endurance approaching the original pack when correctly charged/commissioned.
- Revival status: architecture is strongly consistent with recovered firmware evidence; physical reproduction pending.

### X-Star battery rebuild threads

Representative sources:

- https://autelpilots.com/threads/x-star-battery-rebuild.7063/
- https://autelpilots.com/threads/x-star-battery-rebuild.12960/
- https://autelpilots.com/threads/x-star-batteries.13012/
- Area: recelling, fitment, capacity reporting, UNKNOWN BATTERY behavior
- Status: `CORROBORATED`
- High-value findings:
  - physical cell fit/energy density has historically been a major rebuild constraint;
  - cell replacement without correct smart-gauge recommissioning can produce incorrect reported capacity or later compatibility problems;
  - merely providing compatible pack voltage does not reproduce the X-Star smart-battery interface.

### Charger teardown

- Source: https://muhammadrawi.com/partial-teardown-autel-x-star-premium/
- Area: charger, balance connector, battery interface
- Status: `CORROBORATED`
- High-value findings:
  - factory charger head contains active balancing/charge-control hardware rather than being a passive plug;
  - four balancing networks are visible;
  - aircraft teardown exposes a battery I2C-labelled path consistent with smart-battery communication.
- Revival status: electrical pinout not yet physically reproduced.

## Aircraft / protocol / firmware

### Proposal for reverse-engineering forum

- Source: https://autelpilots.com/threads/proposal-for-reverse-engineering-forum.1596/
- Area: firmware, USB, NuttX/PX4, MAVLink, UAVCAN, flight logs
- Status: `CORROBORATED`
- High-value findings:
  - prior researchers found compressed/encoded structures inside historical X-Star firmware;
  - front USB exposes useful debugging/logging behavior;
  - NuttX/PX4 lineage, MAVLink and UAVCAN activity were observed;
  - flight-log work established MAVLink-related structure.
- Revival status: aggregate V1.1.3 and V2.0.12 container formats are now independently `REPRODUCED` and extracted by project tooling.

### Partial X-Star Premium teardown

- Source: https://muhammadrawi.com/partial-teardown-autel-x-star-premium/
- Area: flight controller, radio, Starpoint, ESC, battery bus
- Status: `CORROBORATED`
- High-value findings:
  - 900 MHz video/radio hardware and separate control/telemetry architecture;
  - Starpoint contains separate processing for optical flow and sonar and exposes UART/test points;
  - flight controller/ESC/current-sense hardware details;
  - battery connector path labelled BATI2C.
- Revival status: used as a hardware-research roadmap; physical confirmation pending.

## Original software / preservation

### Starlink APK preservation

- Artifact: `android-comautelmaxlink-V20320.apk`
- Version: 2.0.3.20
- Status: `SOURCE-VERIFIED`
- Revival findings include:
  - MAVLink and Autel transport packages;
  - USB proxy/framing code;
  - H.264/RTSP and camera HTTP/event interfaces;
  - firmware updater/parser classes;
  - `XXTEA.java` is present in the APK's class/string inventory, making XXTEA a concrete candidate worth testing against battery firmware transformations.

## Historical firmware recovery

### X-Star Premium V1.1.3

- Recovered file: `X3P_FW_900M_V1.1.3.bin`
- Size: 60,760,988 bytes
- MD5: `48e0a68ed22ab25d1711950c0d1c5fa1`
- SHA-256: `63096382d1cb252edc18efc67065e1a16f37b2f445a2eca08863a1bbd30f5d2b`
- Status: `REPRODUCED`
- Battery component: `X3P_BATTERY41_v5.21_20160324.bin`
- Battery MD5: `D02AC39F837B572437C1EFD3E1344334`

### X-Star Premium V2.0.12

- Recovered file: `X3P_FW_900M_V2.0.12.bin`
- Size: 59,778,215 bytes
- MD5: `c67b603ab5604f8633e04916dc190989`
- SHA-256: `fe6c66bed25ac01395f3b9082accddf2989cd7a91e848fecb61e82d9b82a64d7`
- Status: `REPRODUCED`
- Battery component: `X3P_BATTERY41_V6.07_20170627.BIN`
- Battery MD5: `2C4AAD6D78B12152C5DDF3FB4EDC2CC9`

## Research backlog

High-value community material to continue preserving:

1. Historical firmware mirrors: V1.2.8, V1.3.x/beta packages and any intermediate battery firmware.
2. Additional Starlink APK versions, especially 2.0.2.30 and 2.0.3.19.
3. Battery-board photographs showing exact MSP430 part number and service-pad labels.
4. Charger-board photographs/traces sufficient to reconstruct the 12-pin balance connector pinout.
5. Original flight logs from multiple firmware generations.
6. Starpoint UART captures or firmware updater details.
7. Remote CAN teaching-mode captures.

When a useful forum attachment/image is found, record the post URL and preserve a private archival copy where legally appropriate because external image hosts and old forum attachments are particularly fragile.
