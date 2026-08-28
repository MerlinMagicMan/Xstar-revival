plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val defaultAutelAar = layout.projectDirectory.file("../android-sdk-probe/app/libs/autel-sdk-release.aar").asFile
val autelSdkAar = providers.gradleProperty("AUTEL_SDK_AAR")
    .orElse(providers.environmentVariable("AUTEL_SDK_AAR"))
    .map(::file)
    .getOrElse(defaultAutelAar)

android {
    namespace = "io.xstarrevival.autelsdk"
    compileSdk = 35

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":appCore"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    compileOnly(files(autelSdkAar))
}

tasks.register("verifyAutelSdkAar") {
    doLast {
        check(autelSdkAar.isFile) {
            "Official Autel AAR not found. Set AUTEL_SDK_AAR or place autel-sdk-release.aar in android-sdk-probe/app/libs."
        }
    }
}

tasks.register("verifyReadOnlyBinding") {
    val bindingSource = layout.projectDirectory.file(
        "src/main/kotlin/io/xstarrevival/autelsdk/OfficialAutelSdkBridge.kt"
    )
    inputs.file(bindingSource)
    doLast {
        val source = bindingSource.asFile.readText()
        val forbiddenCalls = listOf(
            "cancelLand", "cancelMission", "cancelReturn", "deleteFMCMedia", "deleteMedia",
            "enterPairing",
            "exitPairing", "formatSDCard", "goHome", "land", "lockGimbalWhenTakePhoto",
            "pauseMission", "prepareMission", "resetDefaults", "resetWifi", "resumeMission",
            "set3DNoiseReductionEnable", "setAircraftLocationAsHomePoint", "setAntiFlicker",
            "setAspectRatio", "setAttitudeModeEnable", "setAutoExposureLockState",
            "setBeginnerModeEnable", "setCameraPattern", "setColorStyle", "setCommandStickMode",
            "setCriticalBatteryNotifyThreshold", "setCurrentRFData", "setDigitalZoomScale",
            "setDischargeDay", "setExposure", "setExposureMode", "setGimbalAngle",
            "setGimbalAngleWithSpeed", "setGimbalDialAdjustSpeed", "setGimbalLimitUpward",
            "setGimbalWorkMode", "setGpsCoordinateType", "setISO", "setLanguage",
            "setLedPilotLamp", "setLocationAsHomePoint", "setLowBatteryNotifyThreshold",
            "setMaxHeight", "setMaxHorizontalSpeed", "setMaxRange", "setMediaMode",
            "setParameterUnit", "setPhotoAEBCount", "setPhotoBurstCount", "setPhotoFormat",
            "setPhotoStyle", "setPhotoTimelapseInterval", "setRFPower", "setRemoteControlStick",
            "setReturnHeight", "setRollAdjustData", "setShutter", "setSpotMeteringArea",
            "setStickCalibration", "setTeachingMode", "setVideoFormat",
            "setVideoResolutionAndFrameRate", "setVideoStandard", "setVideoSubtitleEnable",
            "setWhiteBalance", "setYawCoefficient", "startCalibrateCompass", "startMission",
            "startRecordVideo", "startTakePhoto", "stopRecordVideo", "stopTakePhoto", "takeOff",
            "updateNewSSIDInfo", "yawRestore"
        )
        val referenced = forbiddenCalls.filter { "$it(" in source }
        check(referenced.isEmpty()) {
            "Read-only binding references forbidden SDK calls: ${referenced.joinToString()}"
        }
    }
}

tasks.matching { it.name.startsWith("compile") }.configureEach {
    dependsOn("verifyAutelSdkAar")
    dependsOn("verifyReadOnlyBinding")
}
