package io.xstarrevival.core.groundstation

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OperationalMapGeometryTest {
    private val home = GeoPoint(41.8781, -87.6298)

    @Test
    fun `camera center projects to viewport center`() {
        val camera = OperationalMapCamera(home, metersPerPixel = 2.0)

        val pixel = OperationalMapGeometry.project(home, camera, 800.0, 600.0)

        assertEquals(400.0, pixel.x, 1e-6)
        assertEquals(300.0, pixel.y, 1e-6)
    }

    @Test
    fun `heading up places eastward target above aircraft at east heading`() {
        val camera = OperationalMapCamera(home, metersPerPixel = 1.0, rotationDeg = 90.0)
        val east = home.copy(longitudeDeg = home.longitudeDeg + 0.001)

        val pixel = OperationalMapGeometry.project(east, camera, 800.0, 600.0)

        assertEquals(400.0, pixel.x, 0.2)
        assertTrue(pixel.y < 300.0)
    }

    @Test
    fun `projection round trips across a rotated viewport`() {
        val camera = OperationalMapCamera(home, metersPerPixel = 1.7, rotationDeg = 237.0)
        val point = GeoPoint(41.8797, -87.6262)

        val pixel = OperationalMapGeometry.project(point, camera, 1024.0, 768.0)
        val restored = OperationalMapGeometry.unproject(pixel, camera, 1024.0, 768.0)

        assertEquals(point.latitudeDeg, restored.latitudeDeg, 1e-8)
        assertEquals(point.longitudeDeg, restored.longitudeDeg, 1e-8)
    }

    @Test
    fun `fit keeps route inside padded viewport`() {
        val route = listOf(
            GeoPoint(41.875, -87.635),
            GeoPoint(41.881, -87.624),
            GeoPoint(41.884, -87.632)
        )

        val camera = OperationalMapGeometry.fit(route, 900.0, 600.0, paddingPx = 70.0)
        val pixels = route.map { OperationalMapGeometry.project(it, camera, 900.0, 600.0) }

        assertTrue(pixels.all { it.x in 70.0..830.0 && it.y in 70.0..530.0 })
    }

    @Test
    fun `fit follows shortest span at antimeridian`() {
        val points = listOf(GeoPoint(10.0, 179.9), GeoPoint(10.0, -179.9))

        val camera = OperationalMapGeometry.fit(points, 800.0, 500.0)
        val pixels = points.map { OperationalMapGeometry.project(it, camera, 800.0, 500.0) }

        assertTrue(abs(camera.center.longitudeDeg) > 179.0)
        assertTrue(abs(pixels[1].x - pixels[0].x) < 700.0)
    }
}
