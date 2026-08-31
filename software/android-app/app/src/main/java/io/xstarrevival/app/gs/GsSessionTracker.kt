package io.xstarrevival.app.gs

import io.xstarrevival.core.groundstation.FlightSessionRecorder
import io.xstarrevival.core.model.XStarState

/**
 * Converts the normalized telemetry stream into local flight summaries.
 * It never sends aircraft commands and is intentionally independent of transport type.
 */
class GsSessionTracker(private val persistence: GsPersistence) {
    private val recorder = FlightSessionRecorder()
    private val samples = ArrayDeque<PersistedFlightSample>()
    private var active = false
    private var lastSampleAtEpochMs: Long? = null

    fun observe(state: XStarState, timestampEpochMs: Long = System.currentTimeMillis()): Boolean {
        val flyingNow = state.aircraft.armed == true || (state.navigation.altitudeM ?: 0.0) > 1.5
        var changed = false
        if (flyingNow && !active) {
            recorder.start(timestampEpochMs)
            samples.clear()
            lastSampleAtEpochMs = null
            active = true
            changed = true
        }
        if (active) {
            recorder.observe(state)
            captureSample(state, timestampEpochMs)
        }
        if (!flyingNow && active) {
            recorder.finish(timestampEpochMs)?.let { summary ->
                persistence.saveFlightSummary(
                    PersistedFlightSummary(
                        startedAtEpochMs = summary.startedAtEpochMs,
                        endedAtEpochMs = summary.endedAtEpochMs,
                        maximumAltitudeM = summary.maximumAltitudeM,
                        maximumSpeedMps = summary.maximumGroundSpeedMps,
                        batteryStartPercent = summary.batteryStartPercent,
                        batteryEndPercent = summary.batteryEndPercent,
                        samples = samples.toList()
                    )
                )
            }
            active = false
            samples.clear()
            lastSampleAtEpochMs = null
            changed = true
        }
        return changed
    }

    fun isRecording(): Boolean = active

    private fun captureSample(state: XStarState, timestampEpochMs: Long) {
        val previous = lastSampleAtEpochMs
        if (previous != null && timestampEpochMs - previous < SAMPLE_INTERVAL_MS) return
        samples.addLast(
            PersistedFlightSample(
                timestampEpochMs = timestampEpochMs,
                latitudeDeg = state.navigation.latitudeDeg,
                longitudeDeg = state.navigation.longitudeDeg,
                altitudeM = state.navigation.altitudeM,
                groundSpeedMps = state.navigation.groundSpeedMps,
                verticalSpeedMps = state.navigation.verticalSpeedMps,
                headingDeg = state.attitude.yawDeg,
                batteryPercent = state.battery.percent
            )
        )
        lastSampleAtEpochMs = timestampEpochMs
        while (samples.size > MAX_SAMPLES_PER_FLIGHT) samples.removeFirst()
    }

    private companion object {
        const val SAMPLE_INTERVAL_MS = 2_000L
        const val MAX_SAMPLES_PER_FLIGHT = 1_800
    }
}
