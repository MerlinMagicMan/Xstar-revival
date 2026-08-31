package io.xstarrevival.core.groundstation

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Camera for the offline operational map. Rotation is the aircraft bearing shown at screen-up. */
data class OperationalMapCamera(
    val center: GeoPoint,
    val metersPerPixel: Double,
    val rotationDeg: Double = 0.0
)

data class OperationalMapPixel(val x: Double, val y: Double)

object OperationalMapGeometry {
    private const val EARTH_RADIUS_M = 6_371_000.0
    private const val MIN_METERS_PER_PIXEL = 0.05

    fun project(
        point: GeoPoint,
        camera: OperationalMapCamera,
        widthPx: Double,
        heightPx: Double
    ): OperationalMapPixel {
        val (eastM, northM) = localMeters(camera.center, point)
        val bearing = Math.toRadians(camera.rotationDeg)
        val screenRightM = eastM * cos(bearing) - northM * sin(bearing)
        val screenUpM = eastM * sin(bearing) + northM * cos(bearing)
        val scale = camera.metersPerPixel.coerceAtLeast(MIN_METERS_PER_PIXEL)
        return OperationalMapPixel(
            x = widthPx / 2.0 + screenRightM / scale,
            y = heightPx / 2.0 - screenUpM / scale
        )
    }

    fun unproject(
        pixel: OperationalMapPixel,
        camera: OperationalMapCamera,
        widthPx: Double,
        heightPx: Double
    ): GeoPoint {
        val scale = camera.metersPerPixel.coerceAtLeast(MIN_METERS_PER_PIXEL)
        val screenRightM = (pixel.x - widthPx / 2.0) * scale
        val screenUpM = (heightPx / 2.0 - pixel.y) * scale
        val bearing = Math.toRadians(camera.rotationDeg)
        val eastM = screenRightM * cos(bearing) + screenUpM * sin(bearing)
        val northM = -screenRightM * sin(bearing) + screenUpM * cos(bearing)
        val latitude = camera.center.latitudeDeg + Math.toDegrees(northM / EARTH_RADIUS_M)
        val longitudeScale = cos(Math.toRadians(camera.center.latitudeDeg)).coerceAtLeast(1e-6)
        val longitude = camera.center.longitudeDeg + Math.toDegrees(eastM / (EARTH_RADIUS_M * longitudeScale))
        return GeoPoint(latitude.coerceIn(-85.0, 85.0), normalizeLongitude(longitude))
    }

    fun fit(
        points: List<GeoPoint>,
        widthPx: Double,
        heightPx: Double,
        paddingPx: Double = 56.0,
        rotationDeg: Double = 0.0,
        fallback: GeoPoint = GeoPoint(0.0, 0.0)
    ): OperationalMapCamera {
        val valid = points.filter { it.latitudeDeg in -85.0..85.0 && it.longitudeDeg in -180.0..180.0 }
        if (valid.isEmpty()) return OperationalMapCamera(fallback, 10.0, rotationDeg)

        val referenceLongitude = valid.first().longitudeDeg
        val center = GeoPoint(
            latitudeDeg = valid.map { it.latitudeDeg }.average(),
            longitudeDeg = normalizeLongitude(
                referenceLongitude + valid.map { shortestLongitudeDelta(referenceLongitude, it.longitudeDeg) }.average()
            )
        )
        if (valid.size == 1) return OperationalMapCamera(center, 2.0, rotationDeg)

        val bearing = Math.toRadians(rotationDeg)
        val rotated = valid.map { point ->
            val (eastM, northM) = localMeters(center, point)
            OperationalMapPixel(
                x = eastM * cos(bearing) - northM * sin(bearing),
                y = eastM * sin(bearing) + northM * cos(bearing)
            )
        }
        val horizontalM = rotated.maxOf { kotlin.math.abs(it.x) } * 2.0
        val verticalM = rotated.maxOf { kotlin.math.abs(it.y) } * 2.0
        val availableWidth = max(1.0, widthPx - paddingPx * 2.0)
        val availableHeight = max(1.0, heightPx - paddingPx * 2.0)
        val scale = max(horizontalM / availableWidth, verticalM / availableHeight)
            .coerceAtLeast(MIN_METERS_PER_PIXEL)
        return OperationalMapCamera(center, scale * 1.08, rotationDeg)
    }

    private fun localMeters(origin: GeoPoint, point: GeoPoint): Pair<Double, Double> {
        val northM = Math.toRadians(point.latitudeDeg - origin.latitudeDeg) * EARTH_RADIUS_M
        val eastM = Math.toRadians(shortestLongitudeDelta(origin.longitudeDeg, point.longitudeDeg)) *
            EARTH_RADIUS_M * cos(Math.toRadians(origin.latitudeDeg)).coerceAtLeast(1e-6)
        return eastM to northM
    }

    private fun shortestLongitudeDelta(fromDeg: Double, toDeg: Double): Double {
        var delta = (toDeg - fromDeg) % 360.0
        if (delta > 180.0) delta -= 360.0
        if (delta < -180.0) delta += 360.0
        return delta
    }

    private fun normalizeLongitude(longitudeDeg: Double): Double {
        var normalized = (longitudeDeg + 180.0) % 360.0
        if (normalized < 0.0) normalized += 360.0
        return normalized - 180.0
    }
}
