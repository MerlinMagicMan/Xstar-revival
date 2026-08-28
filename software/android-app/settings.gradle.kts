pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "xstar-revival-android"
include(":app")
include(":appCore")
project(":appCore").projectDir = file("../app-core")

val defaultAutelAar = file("../android-sdk-probe/app/libs/autel-sdk-release.aar")
val configuredAutelAar = providers.gradleProperty("AUTEL_SDK_AAR")
    .orElse(providers.environmentVariable("AUTEL_SDK_AAR"))
    .orNull
    ?.let(::file)
val autelSdkAar = configuredAutelAar ?: defaultAutelAar

// The public build stays independent of Autel's proprietary binary. Supplying
// the official AAR opts a local build into the receive-only hardware binding.
if (autelSdkAar.isFile) {
    include(":autelBridge")
    project(":autelBridge").projectDir = file("../android-autel-bridge")
}
