# Legal and Project Posture

## Engineering Position

X-Star Revival treats the Autel X-Star and X-Star Premium as **orphaned platforms** for project-planning purposes.

That means:

- Autel cooperation is not a prerequisite.
- Current Autel SDK authentication or product whitelisting will not be a foundational dependency.
- The primary product path is an independently implemented compatibility stack.
- Official SDKs, legacy APKs, firmware and working legacy devices are research references and behavioral oracles.
- Long-term preservation takes priority over short-term dependence on a vendor service that may disappear.

## What the Project Intends to Build

The project may pursue original implementations of:

- Android ground-control software;
- USB transport and packet framing;
- MAVLink/Autel protocol support;
- camera, gimbal, telemetry and video interfaces;
- flight-log parsers;
- battery diagnostics and service tooling;
- replacement battery assemblies and electronics;
- repair documentation and parts interchange;
- desktop and mobile diagnostic utilities; and
- eventually independently developed firmware or replacement electronics, subject to separate safety and legal review.

## Interoperability and Repair Basis

The project is centered on interoperability with lawfully acquired hardware and on diagnosis, maintenance and repair.

17 U.S.C. § 1201(f) permits certain reverse engineering and circumvention undertaken to identify elements necessary for interoperability of an independently created computer program, subject to the statute's conditions. It also permits certain interoperability information and means to be shared for that purpose.

The current U.S. Copyright Office exemption for consumer devices permits circumvention that is necessary for diagnosis, maintenance or repair of a lawfully acquired consumer device, within the exemption's limits.

These rules support a disciplined approach based on:

1. lawful possession of the hardware/software being studied;
2. identifying interfaces and functional behavior;
3. writing original replacement code;
4. sharing protocol knowledge for interoperability and repair; and
5. avoiding access to unrelated copyrighted works or services.

This document is a project policy, not a substitute for legal advice before commercial release.

## Boundaries That Remain

Unsupported does not mean public domain and does not waive Autel's copyrights, trademarks or other rights.

The public project will therefore avoid:

- copying or publishing Autel source code;
- copying substantial decompiled implementation code;
- redistributing Autel APKs, SDK binaries or firmware without established rights;
- using Autel logos, artwork or product presentation in a way that suggests official sponsorship;
- calling the application `Autel Starlink`;
- bypassing protections for unrelated account access, cloud services, paid content or copyrighted works;
- publishing secrets, credentials, serial numbers or private flight locations; and
- presenting experimental battery or flight-control procedures as safe before validation.

## Branding Position

The product should have an independent name and visual identity.

Permitted compatibility wording should be factual and non-confusing, such as:

> Compatible with Autel X-Star and X-Star Premium aircraft.

A prominent disclaimer should state that the project is independent and is not affiliated with, endorsed by or sponsored by Autel Robotics.

## Firmware Policy

The project may:

- inventory firmware;
- calculate hashes;
- document versions and container structure;
- analyze interfaces and behavior;
- develop original interoperable software; and
- maintain private research copies lawfully obtained by contributors.

The project will not publicly redistribute Autel firmware merely because the product is unsupported. Public archival work should begin with metadata, hashes, official-source references and independently developed tooling.

## Official SDK Policy

The official Autel SDK is optional research infrastructure, not the project's foundation.

It may be used to:

- discover documented semantics;
- observe expected behavior;
- compare telemetry and video output;
- accelerate a read-only proof of concept; and
- identify component boundaries.

The shipping architecture must retain an independent adapter path:

```text
X-Star Revival App
        |
XStarPlatform
        |
        +-- OpenXStarAdapter          primary preservation path
        |
        +-- OfficialAutelSdkAdapter   optional research/transition path
```

## Safety and Regulatory Separation

Intellectual-property freedom does not remove aviation, radio, product-safety or liability obligations.

Project safety rules therefore remain mandatory:

- props removed for early powered bench work;
- read-only first;
- factory remote authority preserved;
- no undocumented flight commands during protocol discovery;
- staged testing before flight;
- conservative battery validation;
- clear experimental-build warnings; and
- compliance with applicable operating rules by users and testers.

## Commercialization Gate

Before charging for an application, hardware kit, battery rebuild service or firmware product, complete a focused review covering:

- branding and trademark language;
- third-party library licenses;
- SDK redistribution/app-key terms;
- firmware and binary distribution;
- patent exposure for replacement hardware or flight features;
- product liability and warnings;
- privacy treatment of flight logs and locations; and
- aviation/regulatory representations.

## Bottom Line

The project will behave as though the platform is technically abandoned, while preserving a clean legal and engineering record.

We do not need Autel's participation to begin building original interoperable software and repair tools. We also do not need to copy or redistribute Autel's protected material to accomplish the mission.
