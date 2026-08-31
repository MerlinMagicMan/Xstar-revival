package io.xstarrevival.app.gs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BatteryProfileTest {
    @Test
    fun profileNormalizationBoundsUserSuppliedValues() {
        val normalized = PersistedBatteryProfile(
            id = "manual-1",
            name = "   ",
            kind = "UNKNOWN",
            ratedCapacityMah = 99_999,
            createdAtEpochMs = 1L
        ).normalized()

        assertEquals("Battery Pack", normalized.name)
        assertEquals("CUSTOM", normalized.kind)
        assertEquals(20_000, normalized.ratedCapacityMah)
    }

    @Test
    fun historySamplesClassifyBatteryEvents() {
        val stressed = PersistedBatterySample(
            timestampEpochMs = 1L,
            percent = 20,
            packVoltageV = 13.3,
            currentA = 8.0,
            temperatureC = 52.0,
            fullCapacityMah = 3_500,
            cycleCount = 220,
            healthPercent = 71,
            cellVoltagesV = listOf(3.39, 3.48, 3.50, 3.49),
            cellDeltaV = .11
        )
        val healthy = stressed.copy(temperatureC = 31.0, cellVoltagesV = listOf(4.0, 4.01), cellDeltaV = .01)

        assertTrue(stressed.highTemperatureEvent)
        assertTrue(stressed.lowVoltageEvent)
        assertTrue(stressed.imbalanceEvent)
        assertFalse(healthy.highTemperatureEvent)
        assertFalse(healthy.lowVoltageEvent)
        assertFalse(healthy.imbalanceEvent)

        assertEquals(
            2,
            countBatteryEvents(listOf(stressed.copy(timestampEpochMs = 4L), healthy.copy(timestampEpochMs = 3L), stressed.copy(timestampEpochMs = 2L), stressed)) {
                it.highTemperatureEvent
            }
        )
    }
}
