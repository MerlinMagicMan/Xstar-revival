# X-Star Hardware Capability Matrix

> Status: Living engineering reference  
> Purpose: Map every known X-Star/X-Star Premium hardware capability to its documented operating behavior, known software accessibility, roadmap dependencies, and remaining reverse-engineering work.

## Evidence Levels

- **Official manual** — directly documented in the X-Star/X-Star Premium User Manual.
- **Official SDK** — exposed by Autel Mobile SDK 2.0 / X-Star-specific API documentation or release notes.
- **Static implementation evidence** — observed in the preserved Starlink APK/native libraries.
- **Community / research evidence** — credible external teardown or reverse-engineering work, not yet reproduced by this project.
- **Hypothesis** — plausible but not yet demonstrated.

The matrix deliberately distinguishes **physical capability** from **public API access**. A sensor may exist and be used internally by the flight controller without exposing raw data to the mobile application.

---

# 1. Executive Summary

The X-Star Premium contains more useful sensing and control hardware than its original mobile application exposed as a coherent system. The documented platform includes:

- GNSS receiver using GPS/GLONASS;
- IMU with 3-axis gyroscope and 3-axis accelerometer;
- compass / magnetometer;
- barometer;
- Starpoint positioning system consisting of one downward monochrome camera and two ultrasonic sensors;
- 4K R12 main camera;
- 3-axis stabilized gimbal;
- intelligent battery with cell balancing and telemetry;
- remote-controller state and physical inputs;
- separate command and HD-video RF links;
- onboard/aircraft flight-data storage accessible over USB;
- autonomous Orbit, Follow and Waypoint mission primitives;
- a CAN-bus remote-controller teaching interface;
- physical and software failsafe / Go Home / automatic-landing logic.

The highest-value engineering opportunity is to build a **Revival Sensor Fusion & Perception layer** above these components rather than treating each sensor as an isolated display value.

```text
                    X-STAR HARDWARE

  4K Camera      Starpoint Camera      Ultrasonic
      |                 |                  |
      +-----------------+------------------+
                        |
               IMU / GNSS / Compass
                        |
                    Barometer
                        |
                Battery / RC / RF
                        |
                        v
             REVIVAL SENSOR FUSION
                        |
      +-----------------+------------------+
      |                 |                  |
   Landing           Vision           Navigation
      |                 |                  |
      +------------- Health / AI ---------+
```

---

# 2. Core Navigation Sensors

## 2.1 GNSS Receiver — GPS / GLONASS

### Physical capability

**Official manual:** The GNSS receiver determines aircraft latitude, longitude and altitude. GPS mode requires at least six satellite signals for normal position-hold behavior.

### Known software access

**Official SDK:** Flight-controller state APIs expose GPS/navigation state and related aircraft position information.

**Original UI:** Starlink displayed GPS signal strength, aircraft position, distance from home, map location and flight route.

### Useful derived features

- modern map/FPV overlay;
- advanced Flight Planner;
- Ghost Flight;
- flight logs and black-box replay;
- geospatial property intelligence;
- repeated inspection routes;
- visual/GNSS cross-checking;
- RF heat maps;
- multi-aircraft deconfliction;
- 3D reconstruction georeferencing;
- AR aircraft overlay;
- sensor-confidence scoring.

### Limitations / cautions

- GNSS altitude should not be treated as ground-relative altitude without fusion;
- multipath and poor satellite geometry remain possible;
- the aircraft's factory GPS/no-GPS behavior must remain authoritative until control logic is thoroughly validated.

### Research status

**Access confidence: HIGH**  
Raw message identity/scaling in the independent open protocol remains to be validated against live captures.

---

## 2.2 IMU — 3-Axis Gyroscope + 3-Axis Accelerometer

### Physical capability

**Official manual:** The IMU consists of a 3-axis gyroscope and a 3-axis accelerometer measuring angular rates and acceleration. Automatic IMU calibration occurs at aircraft startup.

### Known software access

**Official SDK:** Aircraft attitude and flight-controller information are exposed through X-Star flight-controller callbacks.

**Flight-log evidence:** Existing X-Star/PX4-derived log tooling includes attitude and inertial records.

### Useful derived features

- aircraft attitude HUD;
- precise touchdown / settle detection;
- tip-over-risk detection;
- wind estimation;
- propulsion imbalance analysis;
- visual-inertial odometry;
- camera-pose reconstruction;
- crash/incident reconstruction;
- Ghost Flight trajectory comparison;
- flight simulator model fitting;
- sensor anomaly detection.

### Especially important for Landing Assistant

Possible touchdown sequence:

```text
vertical velocity decreases
        +
ultrasonic range approaches zero
        +
vertical acceleration transient
        +
roll/pitch stabilize
        v
TOUCHDOWN CONFIDENCE
```

This can reduce ambiguity between hovering centimeters above the surface and actual ground contact.

### Research status

**Access confidence: HIGH for processed attitude; MEDIUM for raw IMU values through mobile path.**

---

## 2.3 Compass / Magnetometer

### Physical capability

**Official manual:** Measures the geomagnetic field and provides aircraft heading reference. The aircraft has explicit compass-interference warnings and calibration procedures.

### Known software access

Flight heading/yaw is available in normal telemetry. Exact raw magnetometer-field accessibility through the mobile SDK has not yet been confirmed.

### Useful derived features

- heading/HUD;
- map orientation;
- sensor-confidence engine;
- compass anomaly detection by comparing magnetic heading with visual/GNSS course;
- experimental relative magnetic-anomaly mapping if raw field data becomes accessible.

### Experimental concept — magnetic anomaly map

If raw calibrated field magnitude/vector data can be obtained, repeated grid flights could test whether large ferrous structures produce reproducible anomalies. Aircraft motor/current interference must be characterized first.

### Research status

**Processed heading: HIGH. Raw magnetometer data: UNKNOWN.**

---

## 2.4 Barometer

### Physical capability

**Official manual:** Measures atmospheric pressure to determine pressure altitude.

### Known software access

Aircraft altitude is available through telemetry; exact separation of barometric vs fused altitude in the public mobile path must be mapped.

### Useful derived features

- altitude stabilization visualization;
- terrain-relative estimates when fused with sonar/terrain data;
- pressure-drift diagnostics;
- vertical wind/weather inference experiments;
- flight replay;
- Ghost Flight height matching;
- landing-state fusion.

### Research status

**Access confidence: HIGH for fused altitude; MEDIUM for raw pressure/barometric altitude.**

---

# 3. Starpoint Downward Perception System

The Starpoint subsystem is one of the highest-value targets for X-Star Revival.

The manual explicitly documents:

- **one monocular downward camera**;
- **two ultrasonic sensors**;
- ultrasound determines current aircraft height above the surface;
- image analysis estimates x/y aircraft movement relative to the ground;
- system works in both GPS and ATTI modes;
- operating envelope is approximately **0.30 m to 3.00 m above the surface**.

The manual also documents conditions that degrade Starpoint performance:

- monochrome or highly reflective surfaces;
- water or transparent surfaces;
- extremely dark/bright or rapidly changing illumination;
- surfaces with weak or highly repetitive texture;
- sound-absorbing/deflecting surfaces such as thick carpet;
- moving surfaces;
- excessive speed at low altitude.

These limitations are themselves useful signals for a Revival confidence model.

## 3.1 Ultrasonic Sensors

### Physical capability

Two downward ultrasound sensors estimate surface distance.

### Official operating range

Starpoint valid range: approximately **30 cm–300 cm AGL**.

The manual warns against interference from other **40 kHz ultrasonic devices** near the aircraft.

### Known software access

**Official SDK:** X-Star flight-controller interface exposes a real-time ultrasonic-height listener.

This is a major confirmed capability.

### Immediate product uses

#### Precision Landing / Anti-Tip Landing

- detect entry into final three-meter landing envelope;
- monitor true AGL independently of home-point altitude;
- detect unexpected ground-rise during descent;
- verify stable ground range after touchdown;
- combine with attitude/acceleration to estimate settle confidence;
- detect bouncing or one-leg contact;
- guide repositioning before committing to automatic landing.

#### Low-Altitude Terrain Following

Within valid sonar range:

```text
commanded AGL
   versus
measured surface distance
```

This could support advisory terrain-following first, and only later validated automated control.

#### Micro-Terrain Mapping

Fuse:

```text
GNSS position
+ barometric altitude
+ sonar AGL
```

to generate rough relative ground-elevation profiles. This is **not survey-grade** and should be labeled accordingly.

#### Surface / Echo Research

If raw echo quality or return statistics are exposed internally, future experiments could investigate surface classification. The public documentation currently supports height data, not raw acoustic waveform access.

### Research status

**Processed ultrasonic height: CONFIRMED / HIGH. Raw echo waveform: UNKNOWN.**

---

## 3.2 Downward Monochrome Optical-Flow Camera

### Physical capability

**Official manual:** Tracks x/y aircraft motion relative to the ground through image analysis.

### Firmware evidence

The official Mobile SDK release notes identify **Optical Flow V0.6.8.0** as an independently versioned X-Star/X-Star Premium firmware component.

### Known software access

The public X-Star mobile API clearly exposes Starpoint/ultrasonic state, but the project has **not yet confirmed a public API for raw downward camera frames or raw optical-flow vectors**.

### High-value uses if raw/processed flow can be unlocked

- precision hover visualization;
- landing drift detection;
- final-meter visual odometry;
- landing-pad marker recognition;
- low-altitude GPS-independent position hold augmentation;
- visual breadcrumb navigation;
- local landing-zone reconstruction;
- visual/GNSS sensor-confidence comparison;
- optical-flow quality score based on surface texture and lighting;
- indoor/poor-GPS positioning experiments.

### Precision Landing Marker

A printed Revival landing pad could provide a strong visual target if raw downward imagery becomes available.

```text
known fiducial marker
       +
downward camera pose
       +
ultrasonic AGL
       v
pad offset + yaw error + scale
```

Initially this could provide pilot guidance. Automatic correction would require a later validated control gate.

### Reverse-engineering priority

**TOP SENSOR RESEARCH TARGET.** Determine whether:

1. raw downward images traverse an internal bus;
2. optical-flow x/y vectors are exposed through MAVLink/UAVCAN/custom Autel messages;
3. Starpoint quality/confidence fields exist;
4. optical-flow firmware communicates with the main flight controller over an observable channel.

### Research status

**Physical capability: CONFIRMED. Raw mobile access: UNKNOWN.**

---

# 4. Main R12 4K Camera

## Physical capability

The X-Star/X-Star Premium uses the Xteady R12 camera/gimbal system.

Official manual capabilities include:

- UHD up to 4096×2160 24/25 fps;
- 3840×2160 up to 30 fps;
- 2704×1520 up to 60 fps;
- 1080p up to 120 fps;
- 720p up to 240 fps;
- approximately 108° maximum field of view;
- single shot, burst, AEB and timelapse;
- RAW/DNG-class still-image workflows;
- MOV/MP4 video;
- manual ISO/shutter controls;
- histogram;
- overexposure warning;
- grids/diagonals/center point;
- multiple color/style modes including Log-style output.

## Known software access

**Official SDK:** R12 camera state and controls are exposed through the camera module.

**Static APK:** Known H.264/RTSP paths and camera/event HTTP endpoints exist in Starlink.

## Immediate roadmap uses

### Vision Copilot

Run object detection / segmentation on the live video stream:

- buildings;
- vehicles;
- people;
- animals;
- roofs;
- roads;
- trees;
- water;
- fences;
- property structures.

### Visual Subject Lock

Track a user-selected target through successive video frames. Initial implementation should be **advisory/gimbal-oriented** before influencing aircraft trajectory.

### Visual Obstacle Detection

Use monocular computer vision and image expansion / optical flow / known aircraft movement to estimate time-to-collision.

Initial output:

```text
possible obstacle
bearing
relative expansion rate
time-to-collision estimate
confidence
```

No avoidance control until extensive validation.

### Visual Return-to-Home

Use outbound imagery to build visual landmarks and compare them during return. This should augment—not replace—factory GPS RTH initially.

### 3D World Reconstruction / Photogrammetry

Fuse imagery with:

- GNSS;
- attitude;
- gimbal pose;
- timestamps;
- repeated mission geometry.

Outputs can include:

- sparse/dense point clouds;
- textured meshes;
- orthomosaics;
- property models;
- measurement tools;
- temporal comparisons.

### Property Intelligence

Derived RGB analysis may support approximate observations such as:

- structure detection;
- roof-area estimation;
- canopy coverage;
- fence/road/path extraction;
- standing-water candidates;
- visible change detection;
- construction progress.

These must be described as estimates/observations unless professionally validated.

### RGB Vegetation Analytics

The R12 is not a multispectral/NDVI sensor. However, RGB imagery can support relative vegetation indices, canopy coverage, missing-plant detection and temporal comparison.

### Shadow-Based Measurement

Known sun position + image geometry + geolocation may help estimate approximate object height. Photogrammetry should remain the primary measurement path.

## Research status

**Video access confidence: HIGH. Full camera control: HIGH. Computer-vision features: software-development task rather than hardware-access blocker.**

---

# 5. 3-Axis Gimbal

## Physical capability

Official manual:

- pitch, roll and yaw axes;
- Stabilized and FPV modes;
- reported control accuracy approximately ±0.015° on each axis;
- controllable pitch 0°–90°;
- high angular-rate capability.

## Known software access

Official SDK exposes X-Star Premium gimbal interfaces/state/control.

## Product uses

- Subject Lock framing;
- cinematic rails;
- Ghost Flight camera-pose reproduction;
- photogrammetry camera-pose constraints;
- stabilized visual obstacle/landmark analysis;
- 3D model capture profiles;
- automated inspection viewpoints;
- simulator camera replication;
- multi-camera choreography.

## Research questions

- exact telemetry rate/resolution of gimbal angles;
- whether absolute yaw relative to aircraft is exposed consistently;
- latency between command and measured pose;
- repeatability after power cycle.

## Research status

**Access confidence: HIGH.**

---

# 6. Intelligent Battery

## Physical capability

Official manual baseline:

```text
Chemistry: LiPo
Nominal voltage: 14.8 V
Capacity: 4900 mAh
```

Documented smart functions include:

- individual-cell balancing;
- battery level;
- current;
- voltage;
- battery life/health information;
- temperature;
- overcharge/over-discharge protection;
- short-circuit protection;
- charging temperature protection;
- automatic power saving.

Manual protection values include approximately:

- 17 V charge stop;
- 10.8 V discharge stop.

## Known software access

**Official SDK X-Star battery API** exposes or documents values including:

- individual cell voltages;
- pack voltage;
- current;
- temperature;
- design capacity;
- full-charge capacity;
- remaining capacity/percentage;
- discharge count/cycle information;
- battery serial/version information.

## Product uses

### Battery Intelligence

- cell-delta monitoring;
- health/capacity estimation;
- sag-under-load analysis;
- temperature trend analysis;
- cycle history;
- pack-specific mission recommendation;
- retirement / local-flight-only advisories;
- historical comparison.

### Battery-Aware Mission Planning

Factory X-Star already estimates the battery required to return home. Revival can extend this using:

```text
route geometry
+ pack health
+ observed current draw
+ wind
+ altitude changes
+ temperature
+ historical consumption
```

### Aftermarket Battery Program — TOP COMMERCIAL PRIORITY

Potential track:

1. reverse-engineer original pack mechanics and electrical interface;
2. confirm BMS/gauge architecture;
3. characterize aircraft authentication/recognition behavior;
4. select modern high-quality cells with adequate current capability;
5. design validated BMS/pack architecture;
6. mechanical enclosure strategy;
7. charging compatibility;
8. thermal and abuse testing;
9. cycle-life testing;
10. production QC / serialization;
11. service/rebuild pathway;
12. liability, shipping and regulatory review.

The app can become the diagnostic front end for both original and Revival batteries.

## Research status

**Aircraft-visible telemetry: HIGH. Direct BMS service path: active research. Aftermarket pack: strategic hardware program.**

---

# 7. Remote Controller Inputs and State

## Physical capability

Remote includes:

- dual command sticks;
- gimbal pitch dial;
- flight-mode switch;
- motor-start button;
- takeoff/landing button;
- Go Home button;
- Pause button;
- shutter/record controls;
- camera settings dial;
- onboard status display;
- USB mobile-device port;
- CAN-bus teaching port;
- built-in battery.

## Known software access

Official SDK provides remote-controller state/input interfaces. Existing flight logs also document RC-channel records.

## Product uses

- configurable Glass Cockpit control visualization;
- RC diagnostics/calibration;
- input recording for Ghost Flight and simulator training;
- incident reconstruction;
- Bring Your Own Remote mapping layer;
- instructor/learner mode research;
- redundant controller architectures.

---

# 8. CAN-Bus Teaching Interface

## Physical capability

The manual documents a **CAN-Bus port** connecting two factory remotes for Teaching Mode.

In Teaching Mode:

- one remote is Instructor;
- one remote is Learner;
- Instructor can gain control;
- Learner gimbal pitch remains independently usable in documented behavior.

## Why this matters

This may provide the shortest hardware path toward:

### Revival Link / Bring Your Own Remote

Potential architecture:

```text
Third-party controller
       |
Revival Link adapter
       |
factory-compatible CAN semantics
       |
X-Star remote / controller chain
```

Whether this is feasible depends on the exact CAN protocol and safety semantics.

## Research priorities

- passive CAN capture during Instructor/Learner operation;
- bus bitrate and physical-layer confirmation;
- message IDs and timing;
- control ownership arbitration;
- keep-alives/watchdogs;
- failsafe behavior if either controller disappears;
- whether a non-factory device can participate safely.

No control injection should occur until passive capture and full arbitration behavior are understood.

## Research status

**Physical interface: CONFIRMED. Protocol: UNKNOWN / HIGH-PRIORITY RESEARCH.**

---

# 9. Command and Video RF Systems

## Documented architecture

The manual distinguishes:

- control/flight-information link in the ~5.8 GHz band;
- X-Star Premium HD video downlink in the 902–928 MHz region in the U.S.;
- USB connection from mobile device to X-Star Premium remote.

The remote antennas have differentiated roles for command/telemetry and video reception.

## Known software access

Original Starlink provides video-link signal status. The manual also documents a Video Link Settings screen for X-Star Premium where the user can inspect channel noise and choose a lower-noise channel.

Official SDK DSP/RF APIs expose frequency/signal-strength information.

## Product uses

### RF Spectrum Display

Display channel-by-channel RF conditions rather than a single signal-strength number.

### Geospatial RF Heat Map

Log:

```text
position
altitude
frequency/channel
RF strength/noise
video quality
RC link quality
```

and visualize weak/noisy regions.

### Mission Risk Integration

Planner can warn:

> Prior flights show repeated video-link degradation behind this structure.

### Multi-Aircraft RF Coordination

Future fleet operation could consider channel usage and interference when multiple X-Stars operate nearby.

## Research status

**Signal/noise access: strong official evidence. Exact mobile telemetry/API behavior to validate live.**

---

# 10. Flight Data / Onboard Logging

## Physical capability

The manual explicitly provides a **Read Flight Data** function using the aircraft USB connection to a computer.

Existing community tooling maps X-Star logs to PX4-like attitude, GPS, battery, RC and other records.

## Product uses

- Black Box Flight Recorder;
- Flight Replay;
- Post-Flight AI Engineer;
- simulator training data;
- aircraft-specific performance baselines;
- crash/incident reconstruction;
- battery sag and propulsion analysis;
- Ghost Flight path extraction;
- protocol validation against live telemetry.

## Research status

**Existence/access path: CONFIRMED. Complete format mapping: active research.**

---

# 11. Factory Mission / Autonomy Primitives

## Orbit

Documented capabilities include:

- POI selection;
- radius roughly 10–100 m;
- CW/CCW direction;
- lap count;
- face-POI behavior;
- completion action;
- pilot adjustment and Pause support.

### Revival opportunities

- cinematic orbit templates;
- structure scanning;
- photogrammetry capture;
- Subject Lock integration.

---

## Follow

Factory Follow mode tracks the mobile-device location and adjusts aircraft behavior around that target.

### Revival opportunities

Potential future bridge:

```text
vision target
   -> estimated geolocation / motion
   -> validated target update
   -> factory Follow primitive
```

This must not be attempted until target-update semantics and failsafe behavior are proven.

---

## Waypoint

Documented Starlink supports:

- routes created by aircraft position;
- route drawing on the map;
- point-on-map placement;
- up to 15 waypoints in the documented original UI;
- waypoint altitude;
- hover duration;
- route save/favorites;
- speed adjustment during mission;
- Pause/resume;
- completion action.

### Revival opportunities

Our advanced Flight Planner can initially compile sophisticated user plans into conservative factory-compatible mission primitives before more advanced direct-control features exist.

---

# 12. Landing and Motor Shutdown

## Factory behavior

The manual documents three landing pathways:

- manual landing;
- automatic landing;
- failsafe/passive landing.

Automatic landing:

1. starts from a hover;
2. descends automatically;
3. allows pitch/roll/yaw adjustment during descent;
4. lands;
5. shuts the motors off automatically.

Manual shutdown requires holding the descent stick fully down for approximately two seconds after proper landing. The manual explicitly warns that the emergency toe-in motor-stop gesture can shut motors down in flight.

## Revival Landing Assistant Strategy

**Preferred approach: augment factory landing rather than invent a new motor-kill path.**

### Stage A — advisory

- classify/score landing area from main camera;
- use sonar in final 3 m;
- monitor drift, roll/pitch and vertical speed;
- warn on uneven/unsafe conditions;
- guide pilot into stable hover;
- recommend factory auto-land.

### Stage B — touchdown confidence

Fuse:

- sonar;
- attitude;
- acceleration;
- vertical velocity;
- optical flow if available;
- motor/flight state.

Output:

```text
DESCENDING
SURFACE ACQUIRED
TOUCHDOWN SUSPECTED
SETTLING
TOUCHDOWN CONFIRMED
MOTORS STOPPING
SAFE
```

### Stage C — validated assistance

Only after exhaustive bench/field testing should software influence landing commands or motor-stop timing.

## Primary user problem addressed

Reduce the time the X-Star sits on the ground with spinning propellers, where an uneven landing or small lateral movement can cause tip-over.

---

# 13. Visual Obstacle Avoidance

The manual explicitly states that factory Autopilot **cannot autonomously avoid obstacles**.

That makes obstacle perception a true new Revival capability.

## Potential sensor inputs

- main R12 camera;
- aircraft attitude;
- gimbal pose;
- GPS velocity;
- IMU;
- existing route/mission geometry.

## Initial implementation

Advisory-only collision prediction:

```text
object segmentation / feature flow
+ frame-to-frame expansion
+ known aircraft velocity
       v
relative hazard / time-to-collision
```

## Later stages

- trajectory collision bubble;
- suggested evasive direction;
- planner route rejection;
- validated automatic avoidance only after control/safety gates.

## Limitation

A forward-facing monocular camera does not provide the same redundancy or coverage as modern multi-directional stereo/depth systems. Revival must communicate confidence and blind spots clearly.

---

# 14. Visual Navigation / Visual RTH

## Concept

Use camera imagery as an independent navigation aid rather than relying exclusively on GNSS.

### Visual breadcrumbs

During outbound flight, retain lightweight landmark descriptors tied to:

- position;
- heading;
- camera/gimbal pose;
- altitude;
- timestamp.

During return:

- recognize previously seen landmarks;
- compare visual direction against GNSS return direction;
- flag disagreement;
- provide pilot guidance.

### Sensor fusion

```text
GPS
+ IMU
+ compass
+ barometer
+ main camera
+ Starpoint where available
       v
navigation confidence
```

Factory RTH remains the safety-authoritative mechanism until any visual-control path is extensively validated.

---

# 15. Sensor Confidence Engine

This should become a core software service.

Every measurement carries:

- value;
- timestamp;
- source;
- expected range;
- freshness;
- confidence/quality;
- agreement with other sensors.

Example:

```text
GNSS altitude       118 m
barometric/fused    120 m
visual estimate     119 m

consensus           119.2 m
confidence          HIGH
```

Or:

```text
magnetic heading    214°
GPS track           184°
visual heading      181°

COMPASS DISAGREEMENT
```

This architecture underpins:

- Landing Assistant;
- obstacle avoidance;
- Visual RTH;
- Flight Risk Score;
- Post-Flight AI Engineer;
- predictive maintenance;
- future Guardian AI.

---

# 16. Wind Estimation

The X-Star has no documented dedicated wind sensor, but wind may be estimated by combining:

- attitude required to hold position;
- GPS ground velocity;
- commanded/actual movement;
- flight mode;
- motor/actuator output if available.

Potential outputs:

```text
estimated wind direction
estimated horizontal speed
confidence
altitude band
```

Use cases:

- battery prediction;
- mission feasibility;
- RTH reserve;
- property scan planning;
- multi-aircraft atmospheric profile.

With two or three aircraft at different altitudes, Revival could experimentally create a temporary vertical wind profile.

---

# 17. Propulsion / ESC Health

## Known hardware evidence

The aircraft clearly has four independently driven motors/ESCs. Existing flight-log ecosystems include actuator-output records.

Community teardown research suggests board-level current-sensing hardware may exist, but per-motor current visibility has **not yet been confirmed by this project**.

## Potential features

If per-motor or detailed actuator data becomes accessible:

- compare motor command balance in stable hover;
- detect persistent asymmetric correction;
- identify increasing compensation over time;
- correlate motor loading with vibration/attitude anomalies;
- flag propeller or motor degradation candidates.

Example:

> Motor 3 requires consistently greater command to maintain level hover than the aircraft's historical baseline.

This must be framed as diagnostic evidence—not definitive failure diagnosis—without direct RPM/current sensing validation.

## Research status

**Actuator output: likely accessible through logs. Per-motor current: UNCONFIRMED.**

---

# 18. Multi-Aircraft Sensor Fusion

The roadmap should assume multiple X-Stars from the architecture level.

## Cooperative vision

Two aircraft viewing the same object from different known positions create a large stereo baseline that can improve triangulation and 3D reconstruction.

## Multi-camera production

Synchronize:

- camera feeds;
- timestamps;
- aircraft pose;
- gimbal pose.

Possible roles:

```text
Aircraft A — wide establishing view
Aircraft B — orbit/tracking view
Aircraft C — overhead
```

## Cooperative search

Divide a property/area into sectors and run local or centralized visual detection, with all detections requiring human confirmation.

## Atmospheric sensing

Place multiple aircraft at different altitudes and compare inferred wind/pressure conditions.

## RF awareness

Fleet planner can consider link quality/interference histories when assigning routes.

## Safety gate

Multi-aircraft control introduces substantial collision/deconfliction risk. Early versions should provide fleet visualization and mission separation before coordinated autonomy.

---

# 19. Flight Simulator Inputs

The simulator can use actual X-Star data rather than generic quadcopter assumptions.

Potential training sources:

- stick inputs;
- attitude response;
- GPS velocity;
- vertical response;
- battery/current response;
- mission behavior;
- gimbal behavior;
- failsafe events;
- wind estimates;
- flight logs.

The long-term goal is a simulator whose behavior increasingly matches real X-Star flight logs.

---

# 20. AR Flight / AR Mission Planning

Sensor requirements:

- aircraft GNSS;
- aircraft attitude/heading;
- phone GNSS/IMU/camera;
- mission route;
- terrain/map data.

Potential overlays:

- aircraft direction/location;
- home direction;
- waypoints;
- return corridor;
- geofences;
- POIs;
- predicted flight path;
- obstacle warnings.

AR mission planning could allow a pilot to visually select a real-world target and translate it into a mission proposal, subject to map/position confidence and explicit review.

---

# 21. Capability-to-Roadmap Matrix

| Hardware / Data | Confirmed capability | Software access today | Primary roadmap features | Reverse engineering needed? |
|---|---|---|---|---|
| GNSS GPS/GLONASS | Position/navigation | Strong | Planner, Ghost Flight, replay, AR, 3D, fleet | Open-protocol mapping only |
| IMU | Gyro + accelerometer | Processed attitude strong | Landing, VIO, health, simulator | Raw IMU may require work |
| Compass | Heading | Processed heading strong | Navigation, anomaly detection | Raw field data unknown |
| Barometer | Pressure altitude | Fused altitude strong | Landing, replay, terrain fusion | Raw pressure uncertain |
| Ultrasonic pair | 0.3–3 m ground range via Starpoint | **Official listener confirmed** | Landing, low-altitude terrain, confidence | Raw echo unavailable/unknown |
| Downward monochrome camera | Ground x/y motion via image analysis | Raw access not found | Precision landing, VIO, local navigation | **Yes — priority target** |
| Main R12 camera | 4K/high-FPS RGB | Strong | Vision, obstacle detection, 3D, property AI | No major access blocker |
| Gimbal | 3-axis stabilized pose | Strong | Subject lock, rails, 3D, Ghost Flight | Latency/repeatability characterization |
| Intelligent battery | Cells/current/temp/capacity | Strong SDK evidence | Battery Intelligence, planner, aftermarket pack | BMS service/auth needs work |
| RC controls | Sticks/buttons/dial/modes | Strong | diagnostics, simulator, BYO remote | Open packet mapping needed |
| CAN teaching port | dual-controller hardware path | No decoded protocol yet | Revival Link / BYO remote | **Yes — priority target** |
| Video RF/DSP | channel/noise/link data | Strong evidence | RF maps, risk planning | Live API validation |
| Flight logs | onboard flight data | Confirmed path | Replay, AI engineer, simulator | Format completion |
| Factory Waypoint | mission primitive | Known | Flight Planner | Write-path validation |
| Factory Orbit | mission primitive | Known | cinematic/3D | Write-path validation |
| Factory Follow | mobile-location target | Known | vision-follow bridge | Update/control semantics validation |
| Factory auto-land | descend + motor shutdown | Known | Landing Assistant | Safe command semantics validation |

---

# 22. Highest-Priority Sensor Research Questions

## Priority 1 — Raw Starpoint / Optical Flow

Determine whether we can retrieve:

- raw monochrome frames;
- optical-flow x/y vectors;
- feature count / quality;
- Starpoint confidence/state;
- ultrasound sensor values independently vs fused value.

This unlocks the largest number of unique perception features.

## Priority 2 — Official SDK Capability Matrix

Systematically inventory every X-Star/X-Star Premium API:

- telemetry getter;
- callback;
- writable setting;
- mission command;
- camera control;
- RF/DSP capability;
- battery field;
- remote state;
- gimbal state;
- error/status enum.

## Priority 3 — Flight Log Field Dictionary

Map actual logs from firmware V2.0.12 against SDK values and live telemetry.

## Priority 4 — CAN Teaching Bus

Passive capture of dual-controller behavior to determine whether Revival Link is practical.

## Priority 5 — Propulsion Data

Determine availability of:

- actuator commands;
- ESC status;
- RPM;
- per-motor current;
- aggregate motor current;
- fault flags.

## Priority 6 — Sensor Timing

Measure telemetry rates and timestamp behavior for:

- attitude;
- GNSS;
- sonar;
- battery;
- gimbal;
- video;
- RC inputs.

Sensor fusion is only reliable if latency and update rates are characterized.

---

# 23. Proposed Revival Sensor API

The application should normalize all available sensor information independently of whether it came from Autel SDK, open USB protocol or replay data.

Conceptual model:

```kotlin
data class SensorSample<T>(
    val value: T,
    val timestampNanos: Long,
    val source: SensorSource,
    val confidence: Double?,
    val qualityFlags: Set<SensorQualityFlag>
)

interface XStarSensorPlatform {
    val attitude: Flow<SensorSample<Attitude>>
    val position: Flow<SensorSample<GeoPosition>>
    val altitude: Flow<SensorSample<AltitudeState>>
    val ultrasonicHeight: Flow<SensorSample<Double>>
    val battery: Flow<SensorSample<BatteryState>>
    val gimbal: Flow<SensorSample<GimbalState>>
    val remote: Flow<SensorSample<RemoteState>>
    val rf: Flow<SensorSample<RfState>>
    val video: Flow<VideoFrame>
}
```

Raw Starpoint/optical-flow access can be added without changing higher-level perception services if discovered later.

---

# 24. Safety Architecture

Sensor-derived features must progress through explicit capability levels.

## Level 0 — Display

Show raw/processed values only.

## Level 1 — Advisory

Generate warnings/recommendations without commanding the aircraft.

Examples:

- obstacle warning;
- unsafe landing-surface warning;
- battery return recommendation;
- sensor disagreement.

## Level 2 — Pilot-Confirmed Assistance

System proposes an action and requires explicit user approval.

Examples:

- adjust mission path;
- skip waypoint;
- begin factory auto-land;
- return home.

## Level 3 — Constrained Automation

Only after protocol validation, simulation, props-off testing, controlled outdoor testing and clear pilot override behavior.

Examples could eventually include:

- landing alignment;
- obstacle-avoidance intervention;
- adaptive mission modification.

Factory RC override and Pause/RTH/failsafe paths must remain understood and preserved.

---

# 25. Strategic Conclusion

The X-Star should not be treated as an obsolete drone with a 4K camera and GPS. It is better understood as an **existing mobile robotics platform with multiple sensing domains that were never fully exploited by its original software**.

The most promising combination is:

```text
main RGB camera
+ downward optical-flow camera
+ ultrasonic ground ranging
+ IMU
+ GNSS
+ compass
+ barometer
+ gimbal pose
+ battery telemetry
+ RC/RF telemetry
+ historical flight logs
```

That sensor stack is sufficient to pursue meaningful modern capabilities including:

- intelligent landing and tip-over mitigation;
- visual obstacle warnings;
- Visual RTH/navigation assistance;
- Vision Copilot and Subject Lock;
- photogrammetry and property intelligence;
- Ghost Flight and temporal change detection;
- advanced battery and aircraft health;
- RF/environment mapping;
- realistic simulation;
- AR flight tools;
- multi-aircraft perception;
- post-flight AI engineering analysis.

The immediate engineering priority remains **access, characterization and evidence**: establish exactly which values are exposed through the official SDK and open protocol, characterize timing/quality, and avoid assuming access to raw Starpoint imagery or other internal sensor channels until demonstrated.
