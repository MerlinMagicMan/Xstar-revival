#!/usr/bin/env python3
"""Fail if the official SDK binding references any control/write operation."""

from pathlib import Path
import re
import sys


BINDING = Path(__file__).parents[1] / "src/main/kotlin/io/xstarrevival/autelsdk/OfficialAutelSdkBridge.kt"
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

print(f"Read-only Autel binding audit passed ({len(FORBIDDEN_CALLS)} control/write calls excluded).")
