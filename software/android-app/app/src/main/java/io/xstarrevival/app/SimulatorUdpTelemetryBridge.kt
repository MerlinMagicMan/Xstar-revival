package io.xstarrevival.app

import io.xstarrevival.core.model.XStarState
import io.xstarrevival.core.sim.SIMULATOR_BRIDGE_UDP_PORT
import io.xstarrevival.core.sim.SimulatorBridgeProtocol
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SimulatorBridgeUiState(
    val enabled: Boolean = true,
    val destination: String = "LAN broadcast + Android emulator host:$SIMULATOR_BRIDGE_UDP_PORT",
    val framesSent: Long = 0,
    val lastSequence: Long? = null,
    val lastSentAtEpochMs: Long? = null,
    val lastError: String? = null
)

/**
 * Sends simulator telemetry only. It has no receive socket and no path into an aircraft transport.
 * The LAN broadcast serves physical Android devices; 10.0.2.2 serves the Android emulator host.
 */
internal class SimulatorUdpTelemetryBridge(
    scope: CoroutineScope,
    private val clockMs: () -> Long = System::currentTimeMillis
) : AutoCloseable {
    private data class PendingFrame(val sequence: Long, val emittedAtEpochMs: Long, val state: XStarState)

    private val sequence = AtomicLong()
    private val pending = Channel<PendingFrame>(Channel.CONFLATED)
    private val mutableState = MutableStateFlow(SimulatorBridgeUiState())
    val state: StateFlow<SimulatorBridgeUiState> = mutableState.asStateFlow()

    private val worker = scope.launch(Dispatchers.IO) {
        DatagramSocket().use { socket ->
            socket.broadcast = true
            val destinations = listOf(
                InetAddress.getByName("255.255.255.255"),
                InetAddress.getByName("10.0.2.2")
            )
            for (frame in pending) {
                val bytes = SimulatorBridgeProtocol.telemetryJson(
                    frame.state,
                    frame.sequence,
                    frame.emittedAtEpochMs
                ).encodeToByteArray()
                var sent = false
                var lastFailure: String? = null
                destinations.forEach { destination ->
                    runCatching {
                        socket.send(DatagramPacket(bytes, bytes.size, destination, SIMULATOR_BRIDGE_UDP_PORT))
                    }.onSuccess {
                        sent = true
                    }.onFailure {
                        lastFailure = it.message ?: it.javaClass.simpleName
                    }
                }
                val current = mutableState.value
                mutableState.value = current.copy(
                    framesSent = current.framesSent + if (sent) 1 else 0,
                    lastSequence = frame.sequence,
                    lastSentAtEpochMs = frame.emittedAtEpochMs,
                    lastError = if (sent) null else lastFailure ?: "No simulator telemetry destination accepted the frame"
                )
            }
        }
    }

    fun publish(state: XStarState) {
        val emittedAt = clockMs()
        pending.trySend(PendingFrame(sequence.incrementAndGet(), emittedAt, state))
    }

    override fun close() {
        pending.close()
        worker.cancel()
    }
}
