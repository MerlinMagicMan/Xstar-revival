package io.xstarrevival.core.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimulatorControllerInputTest {
    @Test
    fun `mode 2 maps left vertical to throttle and right vertical to pitch`() {
        val mapped = SimulatorControllerInputMapper.map(
            SimulatorPhysicalControllerInput(leftX = .4, leftY = -.8, rightX = -.3, rightY = .6),
            SimulatorControllerResponseProfile(sensitivity = .55, deadZone = 0.0, expo = 0.0)
        )

        assertEquals(.8, mapped.throttle, .0001)
        assertEquals(.4, mapped.yaw, .0001)
        assertEquals(-.6, mapped.pitch, .0001)
        assertEquals(-.3, mapped.roll, .0001)
    }

    @Test
    fun `mode 1 swaps vertical pitch and throttle channels`() {
        val mapped = SimulatorControllerInputMapper.map(
            SimulatorPhysicalControllerInput(leftY = -.7, rightY = .25),
            SimulatorControllerResponseProfile(stickMode = 1, sensitivity = .55, deadZone = 0.0, expo = 0.0)
        )

        assertEquals(-.25, mapped.throttle, .0001)
        assertEquals(.7, mapped.pitch, .0001)
    }

    @Test
    fun `dead zone expo sensitivity and gimbal reversal are deterministic`() {
        val quiet = SimulatorControllerInputMapper.map(
            SimulatorPhysicalControllerInput(leftX = .04),
            SimulatorControllerResponseProfile(deadZone = .05)
        )
        val shaped = SimulatorControllerInputMapper.map(
            SimulatorPhysicalControllerInput(leftX = .6, gimbalWheel = .5),
            SimulatorControllerResponseProfile(sensitivity = .3, deadZone = .05, expo = .8, gimbalWheelReversed = true)
        )

        assertEquals(0.0, quiet.yaw)
        assertTrue(shaped.yaw in .05..0.6)
        assertTrue(shaped.gimbal < 0.0)
    }

    @Test
    fun `invalid input and profile values normalize safely`() {
        val mapped = SimulatorControllerInputMapper.map(
            SimulatorPhysicalControllerInput(leftX = Double.NaN, rightX = Double.POSITIVE_INFINITY, rightY = -9.0),
            SimulatorControllerResponseProfile(stickMode = 99, sensitivity = Double.NaN, deadZone = 4.0, expo = -2.0)
        )

        assertEquals(0.0, mapped.yaw)
        assertEquals(0.0, mapped.roll)
        assertTrue(mapped.pitch > 0.0)
    }
}
