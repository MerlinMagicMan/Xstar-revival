package io.xstarrevival.core.adapter

import io.xstarrevival.core.event.XStarEvent
import io.xstarrevival.core.model.ConnectionState
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutelSdkObservationMapperTest {
    @Test
    fun `battery callback units become normalized app units`() {
        val event = AutelSdkObservationMapper.map(
            AutelSdkObservation.Battery(
                percent = 73,
                packVoltageMv = 15_840.0,
                currentMa = -2_350.0,
                temperatureC = 31.5,
                designCapacityMah = 4_900,
                fullCapacityMah = 4_620,
                remainingCapacityMah = 3_370,
                cellVoltagesMv = listOf(3_962, 3_958, 3_964, 3_956)
            ),
            "test-sdk"
        ).single()

        val battery = assertIs<XStarEvent.BatterySnapshot>(event)
        assertEquals(15.84, battery.packVoltageV)
        assertEquals(-2.35, battery.currentA)
        assertEquals(listOf(3.962, 3.958, 3.964, 3.956), battery.cellVoltagesV)
    }

    @Test
    fun `documented radians are converted while unknown units stay unknown`() {
        val events = AutelSdkObservationMapper.map(
            AutelSdkObservation.Flight(
                ultrasonicHeight = AutelDistance(135.0, AutelDistanceUnit.UNKNOWN),
                attitude = AutelAttitude(PI / 2, -PI / 4, PI, AutelAngleUnit.RADIANS)
            ),
            "test-sdk"
        )

        val navigation = events.filterIsInstance<XStarEvent.NavigationSnapshot>().single()
        val attitude = events.filterIsInstance<XStarEvent.AttitudeSnapshot>().single()
        assertNull(navigation.ultrasonicHeightM)
        assertEquals(90.0, attitude.rollDeg)
        assertEquals(-45.0, attitude.pitchDeg)
        assertEquals(180.0, attitude.yawDeg)
        assertTrue(events.filterIsInstance<XStarEvent.DiagnosticNote>().single().value.contains("Ultrasonic"))
    }

    @Test
    fun `product callback establishes official transport connection`() {
        val events = AutelSdkObservationMapper.map(
            AutelSdkObservation.ProductConnected(
                productName = "X-Star Premium",
                firmwareVersion = "2.0.12",
                availableComponents = setOf(AutelSdkComponent.BATTERY, AutelSdkComponent.CODEC)
            ),
            "Autel SDK 2.0"
        )

        assertEquals(
            ConnectionState.Connected("Autel SDK 2.0", "X-Star Premium"),
            events.filterIsInstance<XStarEvent.ConnectionChanged>().single().value
        )
        assertEquals("2.0.12", events.filterIsInstance<XStarEvent.ProductIdentified>().single().firmwareVersion)
    }
}
