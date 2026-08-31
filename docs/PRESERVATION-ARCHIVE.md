# X-Star Revival Preservation Archive

## Purpose

The X-Star Revival project preserves both the surviving historical record of the Autel X-Star/X-Star Premium ecosystem and independently derived interoperability knowledge.

The goal is not merely to make one aircraft fly again. It is to leave a durable, reproducible technical record so future owners, repairers, developers, archivists and researchers do not have to rediscover the platform from disappearing forums, dead download servers and aging hardware.

## Preservation principles

1. **Preserve originals unchanged.** Never overwrite a recovered APK, firmware package, manual, SDK package, flight log or capture.
2. **Hash everything.** Record byte length, MD5 where historically useful, and SHA-256 for integrity.
3. **Record provenance.** Keep source URL/archive, recovery date, filename as received and any evidence supporting authenticity.
4. **Separate originals from derivations.** Extracted manifests, hashes, protocol notes, parsers and clean-room specifications must reference the hash of the source artifact they came from.
5. **Do not assume abandonment removes copyright.** Public Git history should contain our research, metadata and tools, not proprietary Autel binaries unless redistribution rights are established.
6. **Preserve failed experiments.** An unsuccessful battery substitution, protocol attempt or firmware update can still be valuable evidence.
7. **Track confidence.** Findings should be labeled `reported`, `corroborated`, `source-verified`, or `reproduced`.
8. **Prefer reproducibility.** A future researcher should be able to take a matching source artifact and reproduce our extraction/analysis with public tools in this repository.

## Archive model

The recommended private archival layout is:

```text
xstar-archive/
├── 01_original_artifacts/
│   ├── firmware/
│   ├── starlink/
│   ├── sdk/
│   ├── manuals/
│   └── release-notes/
├── 02_extracted_metadata/
│   ├── firmware-manifests/
│   ├── component-hashes/
│   ├── version-timeline/
│   └── provenance/
├── 03_protocol-captures/
│   ├── usb/
│   ├── mavlink/
│   ├── bati2c-smbus/
│   ├── can/
│   ├── camera-video/
│   └── starpoint/
├── 04_hardware/
│   ├── battery/
│   ├── charger/
│   ├── flight-controller/
│   ├── esc/
│   ├── starpoint/
│   ├── radio/
│   └── remote/
├── 05_community-research/
├── 06_tools/
└── 07_clean-room-specs/
```

The public repository mirrors the *knowledge* and tooling portions of that archive but does not automatically mirror proprietary source binaries.

## Canonical source record

Each original artifact should have a record containing at least:

```yaml
artifact_id: xstar-fw-v1.1.3
artifact_type: firmware
product: X-Star Premium
filename_received: X3P_FW_900M_V1.1.3.bin
reported_version: V1.1.3
size_bytes: 60760988
md5: 48e0a68ed22ab25d1711950c0d1c5fa1
sha256: 63096382d1cb252edc18efc67065e1a16f37b2f445a2eca08863a1bbd30f5d2b
source: surviving third-party firmware archive
recovered_utc: 2026-08
redistribution: metadata only pending rights determination
research_status: container decoded and manifest/components verified
```

The canonical artifact inventory lives in [`ARTIFACT-INVENTORY.md`](ARTIFACT-INVENTORY.md).

## Derivation chain

Derived files must identify their parent source hash whenever practical.

Example:

```text
X3P_FW_900M_V1.1.3.bin
SHA-256 63096382...
        |
        +-- XOR 0xC8 decode
        |
        +-- manifest extraction
        |
        +-- X3P_BATTERY41_v5.21_20160324.bin
            SHA-256 f3236905...
                |
                +-- differential analysis vs Battery V6.07
```

This makes results independently auditable even when the original proprietary artifact cannot be placed in the public repository.

## Community research preservation

Forum and community discoveries should be indexed by:

- title and URL;
- forum/site;
- author or handle when useful;
- date;
- claim/finding;
- evidence type;
- current confidence;
- whether X-Star Revival reproduced the finding;
- related hardware/software area;
- notes about images or attachments that may disappear.

See [`COMMUNITY-RESEARCH-INDEX.md`](COMMUNITY-RESEARCH-INDEX.md).

## Confidence vocabulary

### REPORTED
A single community/source claim that has not been independently confirmed.

### CORROBORATED
Multiple independent sources or artifacts agree, but X-Star Revival has not directly reproduced the result.

### SOURCE-VERIFIED
The behavior/data is directly supported by an official artifact such as firmware, SDK documentation, manual, APK or hardware marking.

### REPRODUCED
X-Star Revival independently repeated the experiment or derived the same result from preserved artifacts/hardware.

These labels describe evidence quality, not importance.

## Public/private boundary

### Suitable for the public repository

- hashes and provenance;
- extracted manifests and version metadata;
- clean-room protocol specifications;
- original research notes;
- diagrams created by the project;
- read-only extraction/analysis utilities;
- sanitized captures and synthetic fixtures;
- links and citations to public sources;
- interoperability code.

### Keep in the private archive unless rights are established

- Autel firmware binaries;
- Starlink APKs;
- compiled Autel SDK binaries;
- copied proprietary source/decompiled implementation code;
- copyrighted manuals where redistribution is not clearly authorized;
- unsanitized flight logs containing private location data.

## Capture convention

```text
YYYYMMDD-HHMM_<device>_<experiment>_<sequence>.<ext>
```

Examples:

```text
20260831-0210_pack-02_bq3055-readonly_001.json
20260831-0220_remote_usb-descriptors_001.json
20260831-0235_starpoint-passive-uart_001.bin
```

Every research session should emit an integrity manifest containing source hashes, hardware/firmware versions, safety state and notes.

## Preservation priorities

1. Recover remaining historical firmware releases, especially V1.2.8 and intermediate battery versions.
2. Recover additional signed Starlink APK versions.
3. Preserve the official SDK AAR and API documentation privately with hashes.
4. Archive useful X-Star community research before attachments and external mirrors disappear.
5. Capture every original battery board before modifications: photographs, markings, BQ3055 dump, MSP430 markings and connector topology.
6. Preserve original charger/connector electrical mapping.
7. Preserve live USB, BATI2C/SMBus, CAN and Starpoint UART captures once hardware testing begins.

## Long-term objective

The archive should make the repository a durable answer to:

> What is currently known about the Autel X-Star platform, what evidence supports it, and how can another researcher reproduce it?
