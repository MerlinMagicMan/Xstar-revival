package io.xstarrevival.core.sim

import kotlin.math.abs
import kotlin.math.sign

/** Raw, device-neutral stick channels. Android HID and future accessory decoders feed this type. */
data class SimulatorPhysicalControllerInput(
    val leftX: Double = 0.0,
    val leftY: Double = 0.0,
    val rightX: Double = 0.0,
    val rightY: Double = 0.0,
    val gimbalWheel: Double = 0.0
) {
    fun bounded() = copy(
        leftX = leftX.bounded(),
        leftY = leftY.bounded(),
        rightX = rightX.bounded(),
        rightY = rightY.bounded(),
        gimbalWheel = gimbalWheel.bounded()
    )
}

data class SimulatorControllerResponseProfile(
    val stickMode: Int = 2,
    val sensitivity: Double = 0.55,
    val deadZone: Double = 0.05,
    val expo: Double = 0.35,
    val gimbalWheelReversed: Boolean = false
) {
    fun normalized() = copy(
        stickMode = stickMode.coerceIn(1, 2),
        sensitivity = sensitivity.finiteOr(0.55).coerceIn(0.1, 1.0),
        deadZone = deadZone.finiteOr(0.05).coerceIn(0.0, 0.2),
        expo = expo.finiteOr(0.35).coerceIn(0.0, 1.0)
    )
}

enum class SimulatorControllerAction {
    TOGGLE_ARM,
    TAKE_OFF,
    LAND,
    RETURN_TO_HOME,
    CANCEL_RETURN_TO_HOME,
    TAKE_PHOTO,
    TOGGLE_RECORDING,
    RECENTER_GIMBAL
}

/**
 * Converts physical controller channels to the semantic controls consumed by the isolated
 * simulator. This code has no Android, USB, SDK, radio, or aircraft transport dependency.
 */
object SimulatorControllerInputMapper {
    fun map(
        raw: SimulatorPhysicalControllerInput,
        profile: SimulatorControllerResponseProfile
    ): SimulatorControlInput {
        val bounded = raw.bounded()
        val normalizedProfile = profile.normalized()
        val leftX = shape(bounded.leftX, normalizedProfile)
        val leftY = shape(-bounded.leftY, normalizedProfile)
        val rightX = shape(bounded.rightX, normalizedProfile)
        val rightY = shape(-bounded.rightY, normalizedProfile)
        val gimbal = shape(bounded.gimbalWheel, normalizedProfile) *
            if (normalizedProfile.gimbalWheelReversed) -1.0 else 1.0

        return if (normalizedProfile.stickMode == 2) {
            SimulatorControlInput(
                throttle = leftY,
                yaw = leftX,
                pitch = rightY,
                roll = rightX,
                gimbal = gimbal
            )
        } else {
            SimulatorControlInput(
                throttle = rightY,
                yaw = leftX,
                pitch = leftY,
                roll = rightX,
                gimbal = gimbal
            )
        }.bounded()
    }

    internal fun shape(value: Double, profile: SimulatorControllerResponseProfile): Double {
        val bounded = value.bounded()
        val magnitude = abs(bounded)
        if (magnitude <= profile.deadZone) return 0.0
        val normalized = ((magnitude - profile.deadZone) / (1.0 - profile.deadZone)).coerceIn(0.0, 1.0)
        val curved = normalized * (1.0 - profile.expo) + normalized * normalized * normalized * profile.expo
        val gain = (profile.sensitivity / 0.55).coerceIn(0.2, 1.8)
        return (curved * gain).coerceAtMost(1.0) * sign(bounded)
    }
}

private fun Double.bounded(): Double = finiteOr(0.0).coerceIn(-1.0, 1.0)

private fun Double.finiteOr(default: Double): Double = if (isFinite()) this else default
