package io.xstarrevival.app.gs

import io.xstarrevival.core.groundstation.FlightSessionRecorder
import io.xstarrevival.core.model.XStarState

/**
 * Converts the normalized telemetry stream into local flight summaries.
 * It never sends aircraft commands and is intentionally independent of transport type.
 */
class GsSessionTracker(private val persistence: GsPersistence) {
    private val recorder = FlightSessionRecorder()
    private var active = false

    fun observe(state: XStarState, timestampEpochMs: Long = System.currentTimeMillis()): Boolean {
        val flyingNow = state.aircraft.armed == true || (state.navigation.altitudeM ?: 0.0) > 1.5
        var changed = false
        if (flyingNow && !active) {
            recorder.start(timestampEpochMs)
            active = true
            changed = true
        }
        if (active) recorder.observe(state)
        if (!flyingNow && active) {
            recorder.finish(timestampEpochMs)?.let { summary ->
                persistence.saveFlightSummary(
                    PersistedFlightSummary(
                        startedAtEpochMs = summary.startedAtEpochMs,
                        endedAtEpochMs = summary.endedAtEpochMs,
                        maximumAltitudeM = summary.maximumAltitudeM,
                        maximumSpeedMps = summary.maximumGroundSpeedMps,
                        batteryStartPercent = summary.batteryStartPercent,
                        batteryEndPercent = summary.batteryEndPercent
                    )
                )
            }
            active = false
            changed = true
        }
        return changed
    }

    fun isRecording(): Boolean = active
}
