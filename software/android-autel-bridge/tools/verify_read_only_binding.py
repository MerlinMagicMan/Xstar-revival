#!/usr/bin/env python3
"""Fail if the official SDK binding references any control/write operation."""

from pathlib import Path
import re
import sys


BINDING = Path(__file__).parents[1] / "src/main/kotlin/io/xstarrevival/autelsdk/OfficialAutelSdkBridge.kt"
USB_PROBE = (
    Path(__file__).parents[2]
    / "android-app/app/src/main/java/io/xstarrevival/app/ControllerUsbInputProbe.kt"
)
SIMULATOR_PLATFORM = (
    Path(__file__).parents[2]
    / "app-core/src/main/kotlin/io/xstarrevival/core/sim/SimulatorXStarPlatform.kt"
)
FORBIDDEN_CALLS = (
    "cancelLand",
    "cancelMission",
    "cancelReturn",
    "deleteFMCMedia",
    "deleteMedia",
    "enterPairing",
    "exitPairing",
    "formatSDCard",
    "goHome",
    "land",
    "lockGimbalWhenTakePhoto",
    "pauseMission",
    "prepareMission",
    "resetWifi",
    "resetDefaults",
    "resumeMission",
    "set3DNoiseReductionEnable",
    "setAircraftLocationAsHomePoint",
    "setAntiFlicker",
    "setAspectRatio",
    "setAttitudeModeEnable",
    "setAutoExposureLockState",
    "setBeginnerModeEnable",
    "setCameraPattern",
    "setColorStyle",
    "setCommandStickMode",
    "setCriticalBatteryNotifyThreshold",
    "setCurrentRFData",
    "setDigitalZoomScale",
    "setDischargeDay",
    "setExposure",
    "setExposureMode",
    "setGimbalAngle",
    "setGimbalAngleWithSpeed",
    "setGimbalDialAdjustSpeed",
    "setGimbalLimitUpward",
    "setGimbalWorkMode",
    "setGpsCoordinateType",
    "setISO",
    "setLanguage",
    "setLedPilotLamp",
    "setLocationAsHomePoint",
    "setLowBatteryNotifyThreshold",
    "setMaxHeight",
    "setMaxHorizontalSpeed",
    "setMaxRange",
    "setMediaMode",
    "setParameterUnit",
    "setPhotoAEBCount",
    "setPhotoBurstCount",
    "setPhotoFormat",
    "setPhotoStyle",
    "setPhotoTimelapseInterval",
    "setRFPower",
    "setRemoteControlStick",
    "setReturnHeight",
    "setRollAdjustData",
    "setShutter",
    "setSpotMeteringArea",
    "setStickCalibration",
    "setTeachingMode",
    "setVideoFormat",
    "setVideoResolutionAndFrameRate",
    "setVideoStandard",
    "setVideoSubtitleEnable",
    "setWhiteBalance",
    "setYawCoefficient",
    "startCalibrateCompass",
    "startMission",
    "startRecordVideo",
    "startTakePhoto",
    "stopRecordVideo",
    "stopTakePhoto",
    "takeOff",
    "updateNewSSIDInfo",
    "yawRestore",
)


source = BINDING.read_text(encoding="utf-8")
referenced = [
    name for name in FORBIDDEN_CALLS if re.search(rf"\b{re.escape(name)}\s*\(", source)
]
if referenced:
    print("Read-only binding references forbidden SDK calls: " + ", ".join(referenced), file=sys.stderr)
    raise SystemExit(1)

if USB_PROBE.is_file():
    usb_source = USB_PROBE.read_text(encoding="utf-8")
    forbidden_usb_output = (
        "FileOutputStream",
        "OutputStream",
        "USB_DIR_OUT",
        "controlTransfer",
        "sendCommand",
        "writeUsbData",
    )
    output_references = [name for name in forbidden_usb_output if name in usb_source]
    if re.search(r"\.write\s*\(", usb_source):
        output_references.append(".write(")
    if output_references:
        print(
            "Receive-only USB probe references output operations: "
            + ", ".join(output_references),
            file=sys.stderr,
        )
        raise SystemExit(1)
    if "bulkTransfer" in usb_source and "UsbConstants.USB_DIR_IN" not in usb_source:
        print(
            "Receive-only USB bulk transfer is not guarded by an IN endpoint check",
            file=sys.stderr,
        )
        raise SystemExit(1)

if SIMULATOR_PLATFORM.is_file():
    simulator_source = SIMULATOR_PLATFORM.read_text(encoding="utf-8")
    forbidden_simulator_bridges = (
        "AutelSdkBridge",
        "OpenXStarTransport",
        "UsbManager",
        "UsbAccessory",
        "FileOutputStream",
        "OutputStream",
    )
    bridge_references = [
        name for name in forbidden_simulator_bridges if name in simulator_source
    ]
    if bridge_references:
        print(
            "Local simulator references a hardware/output bridge: "
            + ", ".join(bridge_references),
            file=sys.stderr,
        )
        raise SystemExit(1)

print(
    f"Read-only Autel audit passed ({len(FORBIDDEN_CALLS)} control/write calls excluded; "
    "USB probe has no output path; simulator has no hardware bridge)."
)
