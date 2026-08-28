package io.xstarrevival.sdkprobe

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import android.support.v7.app.AppCompatActivity
import io.xstarrevival.autelsdk.OfficialAutelSdkBridge
import io.xstarrevival.core.adapter.AutelSdkPlatformAdapter
import io.xstarrevival.core.model.ConnectionState
import io.xstarrevival.core.model.XStarState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Props-off engineering UI backed only by the receive-only official SDK bridge. */
class MainActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var output: TextView
    private var platform: AutelSdkPlatformAdapter? = null
    private var usbInventory: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        output = TextView(this).apply {
            textSize = 16f
            setPadding(32, 32, 32, 32)
        }
        setContentView(ScrollView(this).apply { addView(output) })
        usbInventory = UsbInventory.describe(this)

        val appKey = BuildConfig.AUTEL_APP_KEY
        if (appKey.isBlank()) {
            renderNoKey()
            return
        }

        val bridge = OfficialAutelSdkBridge(applicationContext, appKey)
        val sdkPlatform = AutelSdkPlatformAdapter(scope, bridge)
        platform = sdkPlatform

        scope.launch {
            sdkPlatform.state.collectLatest(::renderState)
        }
        scope.launch {
            runCatching { sdkPlatform.connect() }
        }
    }

    override fun onDestroy() {
        platform?.let { sdkPlatform -> runBlocking { sdkPlatform.disconnect() } }
        scope.cancel()
        super.onDestroy()
    }

    private fun renderNoKey() {
        output.text = buildString {
            appendLine("X-STAR OFFICIAL SDK PROBE")
            appendLine("READ-ONLY / PROPS-OFF BENCH MODE")
            appendLine()
            appendLine("SDK auth: NO APP KEY")
            appendLine("Set AUTEL_APP_KEY locally; it is never committed or logged.")
            appendUsbInventory()
        }
    }

    private fun renderState(state: XStarState) {
        output.text = buildString {
            appendLine("X-STAR OFFICIAL SDK PROBE")
            appendLine("READ-ONLY / PROPS-OFF BENCH MODE")
            appendLine()
            appendLine("CONNECTION")
            appendLine(connectionText(state.connection))
            appendLine("Product: ${state.aircraft.productName ?: "—"}")
            appendLine()
            appendLine("BATTERY")
            appendLine("Remaining: ${state.battery.percent?.let { "$it%" } ?: "—"}")
            appendLine("Pack: ${state.battery.packVoltageV?.let { "%.3f V".format(it) } ?: "—"}")
            appendLine("Current: ${state.battery.currentA?.let { "%.3f A".format(it) } ?: "—"}")
            appendLine("Temperature: ${state.battery.temperatureC?.let { "%.1f °C".format(it) } ?: "—"}")
            state.battery.cells.forEach { cell ->
                appendLine("Cell ${cell.index}: ${cell.voltageV?.let { "%.3f V".format(it) } ?: "—"}")
            }
            appendLine()
            appendLine("FLIGHT / SENSORS")
            appendLine("GPS: ${state.navigation.gpsFix ?: "—"} · sats ${state.navigation.satellites ?: "—"}")
            appendLine("Altitude: ${state.navigation.altitudeM?.let { "%.2f m".format(it) } ?: "—"}")
            appendLine("Ultrasonic: ${state.navigation.ultrasonicHeightM?.let { "%.3f m".format(it) } ?: state.navigation.ultrasonicHeightRaw?.let { "%.3f raw SDK units".format(it) } ?: "—"}")
            appendLine("Attitude: roll ${formatAngle(state.attitude.rollDeg)}, pitch ${formatAngle(state.attitude.pitchDeg)}, yaw ${formatAngle(state.attitude.yawDeg)}")
            appendLine("Mode: ${state.aircraft.flightMode ?: "—"}")
            appendLine()
            appendLine("REMOTE / IMAGE LINK")
            appendLine("Remote: ${state.remote.connected ?: "—"}")
            appendLine("RC signal: ${state.remote.signalPercent?.let { "$it%" } ?: "—"}")
            appendLine("RC battery: ${state.remote.batteryPercent?.let { "$it%" } ?: "—"}")
            appendLine("Image signal: ${state.remote.imageSignalPercent?.let { "$it%" } ?: "—"}")
            appendLine("USB enabled: ${state.imageLink.usbEnabled ?: "—"}")
            appendLine("RF: ${state.imageLink.rfFrequencyHz?.let { "%.3f MHz".format(it / 1_000_000.0) } ?: "—"} · raw ${state.imageLink.rfSignalValue ?: "—"}")
            appendLine()
            appendLine("GIMBAL / CAMERA / VIDEO")
            appendLine("Gimbal: ${state.gimbal.status ?: "—"} · ${formatAngle(state.gimbal.pitchDeg)}")
            appendLine("Camera: ${state.camera.connected ?: "—"} · ${state.camera.mode ?: "—"}")
            appendLine("Exposure: ${state.camera.exposureMode ?: "—"} · ISO ${state.camera.iso ?: "—"} · ${state.camera.shutter ?: "—"}")
            appendLine("H.264: ${if (state.camera.video.receiving) "RECEIVING" else "NO STREAM"} · ${state.camera.video.framesReceived} frames")
            appendLine()
            appendLine("COMPONENT VERSIONS")
            state.aircraft.componentVersions.toSortedMap().forEach { (name, value) -> appendLine("$name: $value") }
            appendLine()
            appendLine("DIAGNOSTICS")
            state.diagnostics.counters.toSortedMap().forEach { (name, value) -> appendLine("$name: $value") }
            state.diagnostics.notes.takeLast(6).forEach(::appendLine)
            appendUsbInventory()
        }
    }

    private fun StringBuilder.appendUsbInventory() {
        appendLine()
        appendLine("PASSIVE USB INVENTORY")
        usbInventory.forEach(::appendLine)
    }

    private fun connectionText(connection: ConnectionState): String = when (connection) {
        ConnectionState.Disconnected -> "Disconnected"
        ConnectionState.Discovering -> "Discovering"
        is ConnectionState.Connecting -> "Connecting: ${connection.stage}"
        is ConnectionState.Connected -> "Connected: ${connection.transport}"
        is ConnectionState.Failed -> "Failed: ${connection.stage} · ${connection.reason}"
    }

    private fun formatAngle(value: Double?): String = value?.let { "%.1f°".format(it) } ?: "—"
}
