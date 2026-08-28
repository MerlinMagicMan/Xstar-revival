# X-Star Battery Research

## Active Engineering Tracks

- [`CHARGER-ADAPTER-DESIGN.md`](CHARGER-ADAPTER-DESIGN.md) — Revival Charge Adapter v1 architecture, connector-mapping procedure, HOTA validation plan, safety gates and future smart-charger path
- [`BQ3055-RESEARCH.md`](BQ3055-RESEARCH.md) — smart-BMS identification, read-only diagnostics, rebuild research and validation ladder

## Research Questions

- Exact series cell configuration?
- Original nominal voltage/capacity?
- Cell dimensions and chemistry?
- Continuous/peak current requirement?
- BMS controller IC?
- Balance tap arrangement?
- Exact 12-pin charger connector map?
- Is a charger-present or activation signal required?
- Temperature sensor type/location?
- Pack authentication, if any?
- What state does the BMS retain after deep discharge?
- What happens after cell replacement?
- Which telemetry values are exposed to the aircraft/app?
- Can capacity/health be recalibrated?
- Which failure modes are cells vs BMS vs charger?

## Validation Categories

### Ordinary charging

Cells and BMS are healthy; the objective is to reproduce the factory main-power, balancing and protection interface with a verified adapter or replacement charger.

### Recovery

Cells remain electrically healthy but the normal charger/BMS path refuses operation.

### Rebuild

Cells are degraded or unsafe and are replaced while preserving validated pack-management behavior.

### Replacement

A new compatible pack or electronics solution is engineered and validated.

These categories must not be conflated. This directory documents research; it is not an endorsement of unverified battery recovery techniques.