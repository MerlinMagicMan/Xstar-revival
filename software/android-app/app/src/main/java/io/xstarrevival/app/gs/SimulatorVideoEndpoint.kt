package io.xstarrevival.app.gs

import java.net.URI

const val DEFAULT_SIMULATOR_VIDEO_URL =
    "http://josephs-macbook-pro.local:8080/player.html"

internal fun normalizeSimulatorVideoUrl(rawValue: String): String {
    val candidate = runCatching { URI(rawValue.trim()) }.getOrNull() ?: return DEFAULT_SIMULATOR_VIDEO_URL
    val host = candidate.host?.lowercase() ?: return DEFAULT_SIMULATOR_VIDEO_URL
    val port = candidate.port
    if (candidate.scheme?.lowercase() != "http" || candidate.userInfo != null || candidate.fragment != null) {
        return DEFAULT_SIMULATOR_VIDEO_URL
    }
    if (port !in -1..65535 || host != "localhost" && !host.endsWith(".local")) {
        return DEFAULT_SIMULATOR_VIDEO_URL
    }
    val path = candidate.rawPath?.takeIf { it.isNotBlank() && it != "/" } ?: "/player.html"
    return URI("http", null, host, port, path, candidate.rawQuery, null).toASCIIString()
}

internal fun simulatorPlayerUrl(baseUrl: String): String {
    val normalized = normalizeSimulatorVideoUrl(baseUrl)
    val separator = if ('?' in normalized) '&' else '?'
    return normalized + separator + listOf(
        "AutoConnect=true",
        "AutoPlayVideo=true",
        "StartVideoMuted=true",
        "WaitForStreamer=true",
        "HideUI=true",
        "TouchInput=false",
        "GamepadInput=false",
        "KeyboardInput=false",
        "MouseInput=false"
    ).joinToString("&")
}
