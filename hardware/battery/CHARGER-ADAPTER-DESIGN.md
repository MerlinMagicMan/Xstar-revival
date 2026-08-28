# Revival Charge Adapter v1

**Status:** Engineering design / pinout verification pending  
**Target:** Original Autel X-Star and X-Star Premium 14.8 V / 4900 mAh battery packs  
**Lab charger:** HOTA D6 Pro or another genuine 4S-capable balance charger  
**Safety classification:** High-energy lithium battery equipment; no charging is authorized until the connector map is independently verified

## 1. Decision

The correct workaround is **not** a two-wire 16.8 V power adapter.

The factory X-Star charger contains important electronics inside the fan-equipped battery connector:

- two high-current charge contacts;
- a custom 12-pin auxiliary connector;
- four cell-balancing resistor networks, one per cell;
- balancing switches;
- a charge-control MOSFET; and
- temperature-controlled cooling.

Therefore, Revival Charge Adapter v1 will expose the X-Star pack as a conventional **4S LiPo pack with a main power connection plus individual cell taps** so that a modern RC balance charger can perform CC/CV charging and cell balancing.

The architecture is technically credible, but the exact physical pin positions on the custom 12-pin connector are not published in the sources found. They must be mapped on real hardware before the adapter is energized.

## 2. Hard Electrical Conclusions

### 2.1 Original pack chemistry

The original pack is:

```text
Chemistry:          standard lithium-polymer (LiPo)
Nominal voltage:    14.8 V
Capacity:           4900 mAh
Topology:           4 cells in series (4S)
Nominal cell value: 3.7 V
Normal full charge: 4.20 V/cell, 16.80 V total
```

The manual's statement that protection stops charging at approximately 17 V is treated as an **overcharge-protection ceiling**, not the normal charger setpoint.

### 2.2 Do not use LiHV mode on an original pack

The factory 14.8 V pack is a standard 4S LiPo pack. It must be charged using **LiPo**, not LiHV, unless a documented rebuild uses genuine 4.35 V-class cells and the BMS configuration has been deliberately changed and validated.

Community references to LiHV charging concern modified packs using replacement high-voltage cells. They do not establish LiHV as correct for the original X-Star cells.

### 2.3 Balance topology

The teardown confirms four independently switched bleed-resistor networks in the OEM charger head. Each network is approximately 10 ohms and corresponds to one cell.

A community rebuilder further reports that the battery connector exposes four cell-node leads and uses the main negative terminal as the fifth reference needed for a 4S balance connection.

The expected electrical topology is therefore:

```text
B0 = pack negative / cell 1 negative
B1 = cell 1 positive
B2 = cell 2 positive (cumulative)
B3 = cell 3 positive (cumulative)
B4 = cell 4 positive / pack positive
```

**This topology is strongly supported; the physical 12-pin locations are still unknown.**

## 3. Adapter Architecture

```text
                     HOTA D6 PRO

                Main channel output
                         |
                       XT60
                         |
                  inline protection
                         |
         +---------------+---------------+
         |                               |
      PACK+                           PACK-
         |                               |
         +------- X-STAR BATTERY --------+
                         |
          verified cell-node conductors
                         |
              JST-XH 5-pin, 4S order
                         |
              B0  B1  B2  B3  B4
                         |
               charger balance port
```

### 3.1 Required outputs

Revival Charge Adapter v1 should provide:

1. **Main power output** to the charger's channel port.
2. **Standard 4S balance output** in `B0, B1, B2, B3, B4` order.
3. **Isolated diagnostic test points** for every X-Star auxiliary pin.
4. **No connection** to unidentified control/data pins.

### 3.2 Preferred mechanical implementation

There are three implementation paths, in priority order.

#### Path A — OEM charger-head donor

Use the battery-side head from an original charger as the mating connector and mechanical cradle.

Advantages:

- exact factory fit;
- correct high-current contacts;
- correct 12-pin spring/contact geometry;
- keyed insertion;
- no risk of an inaccurate 3D-printed contact interface;
- provides the OEM board for independent trace verification.

The OEM electronics should first be documented intact. A donor should not be cut apart until photographs, continuity maps, component markings and connector traces are preserved.

#### Path B — Custom Revival cradle

Create a purpose-built keyed cradle using:

- two rated high-current spring contacts;
- twelve individually replaceable pogo/spring contacts;
- a mechanically constrained battery pocket;
- a protective shutter or recessed contact design;
- strain relief; and
- an enclosure that prevents reversed or partial insertion.

This is the preferred commercial design but requires accurate dimensional capture and contact-current validation.

#### Path C — Internal pigtail on a research pack

A sacrificial or rebuilt pack may be fitted with a direct main connector and balance lead for laboratory characterization.

This does **not** solve the consumer adapter problem and is not appropriate for an intact flight pack. It is useful only for controlled battery-development work.

## 4. Prototype Hardware

### 4.1 Mapper fixture

Before building a charging adapter, build a connector-mapping fixture.

Recommended features:

- OEM donor head or nonconductive custom cradle;
- every small connector pin routed to a numbered test point;
- two large contacts routed to separately protected test points;
- removable zero-ohm links or jumpers between the connector and downstream circuit;
- no charger connection during mapping;
- shrouded probes and recessed test points;
- a printed connector-orientation diagram permanently attached to the fixture.

### 4.2 Charge adapter v1 BOM direction

| Function | Prototype direction |
|---|---|
| Main charge conductors | 16–18 AWG high-flex silicone wire |
| Balance conductors | 22–26 AWG silicone or equivalent |
| Main charger connector | Mating XT60 lead for selected charger channel |
| Balance connector | JST-XH-compatible 5-pin 4S lead |
| Main overcurrent protection | Replaceable fuse sized for the validated charge-current ceiling |
| Prototype charge limit | Begin at 0.5 A; do not exceed 1.0 A during first validation |
| Balance-current ceiling | Begin near 0.3–0.4 A, comparable to the OEM 10-ohm bleed networks |
| Temperature observation | Independent thermocouple or temperature probe against pack case |
| Enclosure | Flame-retardant, keyed, strain-relieved, no exposed live contacts |
| Test points | Pack voltage plus all mapped cell nodes and isolated unknown pins |
| Polarity protection | Mechanical keying first; electrical reverse-polarity protection where practical |

The final fuse value and conductor/contact rating must be based on the validated charge-current target and measured contact heating. Do not select a high-current fuse merely because the charger can supply high current.

## 5. Connector Mapping Procedure

This procedure maps the external connector without assuming any physical pin assignment.

### 5.1 Establish a permanent numbering convention

1. Fix the battery in a defined orientation.
2. Photograph or draw the connector from the mating side.
3. Mark the two high-current contacts separately.
4. Number the twelve small positions left-to-right and row-by-row.
5. State explicitly whether the drawing is the battery view or charger view.

Never reuse a diagram without its viewing direction.

### 5.2 Identify main polarity

Using a high-impedance multimeter and insulated probes:

1. Measure across the two large contacts.
2. Identify main positive and main negative.
3. Repeat with the battery off and on.
4. Record whether the BMS switches either main terminal.
5. Do not proceed if polarity or battery condition is uncertain.

### 5.3 Survey all auxiliary pins

Measure each small pin relative to verified main negative with the battery off, then on.

Classify observations as:

```text
0 V / ground candidate
cumulative cell-node voltage
logic/data idle voltage
open/floating
state-dependent/control
unknown
```

Expected cell-node candidates should form a monotonic cumulative ladder. For a partially charged 4S pack, an illustrative pattern might resemble:

```text
B0    0.0 V
B1   ~3.x–4.2 V
B2   ~6.x–8.4 V
B3   ~9.x–12.6 V
B4  ~12.x–16.8 V
```

The exact values depend on state of charge. The requirement is the arithmetic relationship, not a particular voltage.

### 5.4 Validate adjacent-cell arithmetic

For the proposed ordering, calculate:

```text
Cell 1 = B1 - B0
Cell 2 = B2 - B1
Cell 3 = B3 - B2
Cell 4 = B4 - B3
Pack   = B4 - B0
```

Acceptance conditions before connecting a balance charger:

- all four adjacent differences are plausible single-cell voltages;
- the four values are mutually credible for the pack's state;
- `B4 - B0` agrees with the two large main-contact voltage;
- no data or control line has been mistaken for a cell node;
- the result is reproducible across more than one measurement session.

### 5.5 Independently verify through OEM charger-board tracing

Trace the four 10-ohm balancing networks in the factory charger head back to the 12-pin connector.

The battery-side voltage survey and charger-board trace must agree before the mapping is promoted from hypothesis to confirmed pinout.

### 5.6 Isolate likely data/control pins

The remaining pins may include:

- balance-switch control;
- charger-present or system-present detection;
- BMS communication;
- temperature or identification;
- duplicated contacts; or
- unused positions.

Do not ground, pull up, pulse or connect any unidentified pin to the RC charger.

## 6. Unknown Factory-Charger Handshake

The teardown shows a charge-control MOSFET in the OEM charger head, and the battery contains its own smart BMS. It is not yet proven whether an original pack will permit charging solely from:

```text
main positive + main negative + verified cell taps
```

Possible outcomes:

### Outcome A — Passive adapter works

The HOTA sees the correct 4S cell count, the pack's charge FET permits current and balance charging proceeds normally.

### Outcome B — BMS blocks the main charge path

A factory charger-present or activation signal may be required. In that case:

- stop the experiment;
- inspect OEM charger-head logic and pack BMS status;
- identify the signal electrically and semantically;
- implement a separately reviewed activation circuit only after validation.

### Outcome C — Main terminals are switched but cell taps remain visible

Use the balance values diagnostically, but do not attempt to force current through balance leads or bypass BMS switching.

**No unidentified pin should ever be blindly tied to ground or supply in an attempt to wake the pack.**

## 7. First HOTA D6 Pro Validation Sequence

The HOTA supports 1–6S LiPo packs, adjustable charge current and adjustable balance current. Its high maximum capability is useful for the laboratory but must be deliberately limited for this legacy pack.

### Gate 1 — Meter-only

- no charger connected;
- verify pack and cell-node voltages using the mapper fixture;
- inspect pack for swelling, damage, odor, corrosion or abnormal heating;
- reject packs that are physically compromised.

### Gate 2 — Charger monitor only

- connect main and balance leads with the charger idle;
- verify that the HOTA independently detects 4S;
- compare every displayed cell voltage with the calibrated multimeter;
- require close agreement before starting any task;
- disconnect immediately if the charger reports the wrong cell count, reverse polarity or inconsistent voltages.

### Gate 3 — Balance-only observation

Where the charger supports it, begin with a low balance-current setting and observe whether the cell readings remain stable.

Do not use the HOTA's full 1.6 A balance capability on the first prototype. The OEM charger's approximately 10-ohm bleed paths imply roughly 0.4 A at a full cell, so an initial ceiling near 0.3–0.4 A is the conservative reference point.

### Gate 4 — First charge

Initial profile:

```text
Battery type:       LiPo
Cell count:         4S
End voltage:        4.20 V/cell
Pack end voltage:   16.80 V
Task:               Balance Charge
Charge current:     0.5 A initially
Balance current:    approximately 0.3 A initially
```

Run the first test:

- in a LiPo-safe, nonflammable containment area;
- attended continuously;
- with an independent temperature probe;
- away from the aircraft and occupied living areas;
- with clear emergency disconnect access.

### Gate 5 — 1.0 A validation

Only after a successful low-current charge:

- inspect contacts and wiring;
- compare pre/post cell balance;
- review BMS telemetry;
- measure connector temperature rise; and
- repeat at no more than 1.0 A.

Higher-current charging is a later validation program, not part of adapter bring-up.

## 8. Immediate Stop Conditions

Stop and isolate the pack if any of the following occurs:

- swelling, hissing, smoke, electrolyte odor or visible damage;
- rapid or localized heating;
- any cell voltage rises unexpectedly relative to the others;
- charger and multimeter disagree materially;
- cell count changes during connection;
- a cell is severely low or significantly imbalanced;
- BMS reports charge, safety or permanent-failure errors;
- main current flows when the charger is idle;
- connector contacts become warm at low current;
- the pack repeatedly disconnects or is recognized intermittently.

Do not attempt deep-discharge recovery as part of the adapter validation. Recovery, rebuild and ordinary charging are separate procedures.

## 9. Recommended Development Strategy

### Fastest reliable path

1. Buy or borrow one working OEM X-Star charger if immediate flying is important.
2. Use it as the electrical reference and known-good control.
3. Acquire a second damaged or surplus charger head as the donor/mapping fixture.
4. Buy the HOTA D6 Pro as the long-term battery laboratory charger.
5. Map the 12-pin connector independently from battery voltages and charger PCB traces.
6. Build a passive main-plus-balance breakout.
7. Validate at 0.5 A, then 1.0 A.
8. Determine whether a charger-present signal is required.
9. Convert the verified mapping into a keyed custom cradle.

A currently available used OEM charger may cost roughly the same as a quality RC charger, but it remains valuable because it immediately restores factory charging and gives the project the only known-good electrical/mechanical reference.

## 10. Product Roadmap

### Revival Charge Adapter v1

Passive, serviceable adapter for a modern RC balance charger:

```text
X-Star battery cradle
main XT60 output
4S JST-XH balance output
fuse
independent temperature probe
protected test points
```

### Revival Battery Analyzer v1

Add read-only BMS access:

```text
pack and cell voltages
current
temperature
remaining/full/design capacity
cycle count
state of health
safety and permanent-failure flags
serial/firmware
charge-session log
```

### Revival Smart Charger v2

Purpose-built replacement charger:

```text
USB-C PD or DC input
4S LiPo CC/CV power stage
individual cell balancing
pack temperature monitoring
BMS/SMBus diagnostics
keyed X-Star cradle
charge history
phone/desktop diagnostics
```

### Revival Battery commercial program

The eventual aftermarket battery and charger program should include:

- original-pack diagnostic/rebuild service;
- verified replacement-cell assemblies;
- new smart packs;
- replacement chargers and adapters;
- serialized test reports;
- capacity and load validation;
- traceable cell lots;
- shipping/compliance review; and
- product-liability and certification planning.

## 11. Evidence Status

| Claim | Status |
|---|---|
| Pack is 14.8 V / 4900 mAh LiPo | Confirmed by official manual |
| Pack is 4S | Confirmed by physical charger teardown; consistent with official voltage |
| Normal stock-pack full voltage is 16.8 V | Engineering conclusion from standard 4S LiPo chemistry |
| Manual's 17 V figure is a protection ceiling | Strong interpretation of official protection description |
| OEM charger head contains four balancing networks | Confirmed by physical teardown |
| OEM head contains the charge-control MOSFET | Confirmed by physical teardown |
| Four small leads plus main negative form the five balance nodes | Community-reported; must be verified on our hardware |
| Exact physical 12-pin map | Unknown / not found publicly |
| Passive RC-charger adapter will open stock BMS charge path | Plausible but unverified |
| Factory charger-present/activation signal is required | Unknown |
| HOTA can perform controlled 4S balance charging | Confirmed charger capability |

## 12. Definition of Done for v1

Revival Charge Adapter v1 is complete only when:

- connector orientation and pin numbering are published;
- main polarity is independently verified;
- `B0–B4` are mapped and mathematically validated;
- OEM charger-board tracing confirms the same mapping;
- unidentified pins remain isolated;
- charger monitor mode reports the same cell voltages as a calibrated meter;
- a 0.5 A balance charge completes without abnormal temperature or errors;
- a repeated 1.0 A charge completes consistently;
- BMS and application telemetry remain coherent;
- contact temperature rise is acceptable;
- the enclosure prevents reverse insertion and exposed live contacts;
- the test report and schematic are committed to the repository.

Until those gates are complete, this document is a design and research plan—not a finished charging instruction.