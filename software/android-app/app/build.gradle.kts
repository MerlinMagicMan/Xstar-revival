plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val defaultAutelAar = rootProject.file("../android-sdk-probe/app/libs/autel-sdk-release.aar")
val autelSdkAar = providers.gradleProperty("AUTEL_SDK_AAR")
    .orElse(providers.environmentVariable("AUTEL_SDK_AAR"))
    .map(::file)
    .getOrElse(defaultAutelAar)
val autelSdkEnabled = autelSdkAar.isFile && project.findProject(":autelBridge") != null
val autelAppKey = providers.gradleProperty("AUTEL_APP_KEY")
    .orElse(providers.environmentVariable("AUTEL_APP_KEY"))
    .getOrElse("")
val escapedAutelAppKey = autelAppKey.replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "io.xstarrevival.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.xstarrevival.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("boolean", "AUTEL_SDK_AVAILABLE", autelSdkEnabled.toString())
        buildConfigField("String", "AUTEL_APP_KEY", "\"$escapedAutelAppKey\"")
    }

    buildFeatures {
        buildConfig = true
        compose = true
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

    if (autelSdkEnabled) {
        implementation(project(":autelBridge"))
        implementation(files(autelSdkAar))
    }

    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation(kotlin("test"))

    debugImplementation("androidx.compose.ui:ui-tooling")
}

tasks.withType<Test>().configureEach {
    systemProperty(
        "xstar.videoFixture",
        layout.projectDirectory.file("src/main/res/raw/xstar_synthetic_fpv.h264").asFile.absolutePath
    )
}
