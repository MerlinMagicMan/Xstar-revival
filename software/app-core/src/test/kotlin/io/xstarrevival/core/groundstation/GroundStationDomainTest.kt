package io.xstarrevival.core.groundstation

import io.xstarrevival.core.model.BatteryState
import io.xstarrevival.core.model.CellState
import io.xstarrevival.core.model.NavigationState
import io.xstarrevival.core.model.XStarState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GroundStationDomainTest {
    @Test
    fun validMissionPassesBasicValidation() {
        val plan = MissionPlan(
            id = "m1",
            name = "Survey",
            waypoints = listOf(
                MissionWaypoint("w1", GeoPoint(35.5, -97.6), 40.0, 5.0),
                MissionWaypoint("w2", GeoPoint(35.51, -97.59), 45.0, 6.0)
            )
        )
        val validation = MissionValidator.validate(plan)
        assertTrue(validation.canExecute)
        assertTrue(validation.issues.isEmpty())
    }

    @Test
    fun lowGpsBlocksLiveMission() {
        val plan = MissionPlan(
            id = "m1",
            name = "Survey",
            waypoints = listOf(MissionWaypoint("w1", GeoPoint(35.5, -97.6), 40.0, 5.0))
        )
        val aircraft = XStarState(navigation = NavigationState(satellites = 4))
        val validation = MissionValidator.validate(plan, aircraft)
        assertFalse(validation.canExecute)
        assertTrue(validation.issues.any { it.message.contains("GPS") })
    }

    @Test
    fun batteryHealthUsesCapacityAndCellDelta() {
        val battery = BatteryState(
            designCapacityMah = 4900,
            fullCapacityMah = 4410,
            cells = listOf(
                CellState(1, 4.10),
                CellState(2, 4.01),
                CellState(3, 4.08),
                CellState(4, 4.07)
            )
        )
        val assessment = BatteryHealthAnalyzer.assess(battery)
        assertEquals(90, assessment.healthPercent)
        assertEquals(BatteryHealthBand.GOOD, assessment.band)
        assertTrue(assessment.advisories.any { it.contains("imbalance", ignoreCase = true) })
    }

    @Test
    fun trackerKeepsMostRecentPoints() {
        val tracker = LastKnownAircraftTracker(capacity = 3)
        repeat(5) { i ->
            tracker.observe(
                XStarState(navigation = NavigationState(latitudeDeg = 35.0 + i, longitudeDeg = -97.0)),
                i.toLong()
            )
        }
        assertEquals(3, tracker.path().size)
        assertEquals(39.0, tracker.last()?.position?.latitudeDeg)
        assertEquals(3, tracker.recent(10).size)
    }

    @Test
    fun flightRecorderProducesSummary() {
        val recorder = FlightSessionRecorder()
        recorder.start(1000)
        recorder.observe(XStarState(navigation = NavigationState(altitudeM = 10.0, groundSpeedMps = 3.0), battery = BatteryState(percent = 90)))
        recorder.observe(XStarState(navigation = NavigationState(altitudeM = 42.0, groundSpeedMps = 8.0), battery = BatteryState(percent = 80)))
        val summary = recorder.finish(5000)
        assertNotNull(summary)
        assertEquals(42.0, summary.maximumAltitudeM)
        assertEquals(8.0, summary.maximumGroundSpeedMps)
        assertEquals(90, summary.batteryStartPercent)
        assertEquals(80, summary.batteryEndPercent)
    }
}
