package io.github.hatake716.animalplanet

import io.github.hatake716.animalplanet.globe.Camera
import io.github.hatake716.animalplanet.globe.Rotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CameraTest {
    private fun cam(lat: Double, lon: Double, alt: Double, w: Int = 1080, h: Int = 2400): Camera {
        val c = Camera()
        c.setViewport(w, h)
        c.centerLat = Math.toRadians(lat)
        c.centerLon = Math.toRadians(lon)
        c.altitude = alt
        c.update()
        return c
    }

    @Test
    fun centerProjectsToScreenCenter() {
        for ((lat, lon, alt) in listOf(Triple(35.0, 135.0, 2.0), Triple(-33.9, 18.4, 0.05), Triple(60.0, -150.0, 0.005), Triple(0.0, 0.0, 5.0))) {
            val c = cam(lat, lon, alt)
            val xyz = DoubleArray(3)
            Camera.toXyz(Math.toRadians(lat), Math.toRadians(lon), xyz)
            val out = FloatArray(3)
            assertTrue(c.project(xyz[0], xyz[1], xyz[2], out))
            assertEquals(540f, out[0], 0.5f)
            assertEquals(1200f, out[1], 0.5f)
            assertTrue(c.isFrontFacing(xyz[0], xyz[1], xyz[2]))
        }
    }

    @Test
    fun screenToLatLonRoundTrip() {
        val c = cam(48.86, 2.35, 0.3)
        for (sx in listOf(100f, 540f, 900f)) for (sy in listOf(400f, 1200f, 2000f)) {
            val ll = c.screenToLatLon(sx, sy) ?: continue
            val xyz = DoubleArray(3)
            Camera.toXyz(ll[0], ll[1], xyz)
            val out = FloatArray(3)
            assertTrue(c.project(xyz[0], xyz[1], xyz[2], out))
            assertEquals("sx", sx, out[0], 0.6f)
            assertEquals("sy", sy, out[1], 0.6f)
        }
    }

    @Test
    fun rayMissesGlobeOutsideSilhouette() {
        val c = cam(0.0, 0.0, 5.0)
        assertNull(c.screenToLatLon(0f, 0f))
        assertNotNull(c.screenToLatLon(540f, 1200f))
    }

    @Test
    fun fitAltitudeKeepsGlobeInsideShortSide() {
        val c = cam(0.0, 0.0, 1.0)
        c.altitude = c.fitAltitude()
        c.update()
        val r = c.globeRadiusPx()
        assertTrue("radius $r", r < 540 && r > 540 * 0.8)
        val l = cam(0.0, 0.0, 1.0, 2400, 1080)
        l.altitude = l.fitAltitude()
        l.update()
        val r2 = l.globeRadiusPx()
        assertTrue("radius $r2", r2 < 540 && r2 > 540 * 0.8)
    }

    @Test
    fun pinchKeepsFocalPointFixed() {
        val c = cam(35.0, 135.0, 0.8)
        val fx = 300f
        val fy = 1500f
        val point = c.surfacePoint(fx, fy)!!
        val before = c.screenToLatLon(fx, fy)!!
        c.altitude /= 1.7
        c.update()
        c.moveSurfacePoint(point, fx, fy)
        c.update()
        val check = c.screenToLatLon(fx, fy)!!
        val err = Camera.angularDistance(before[0], before[1], check[0], check[1])
        assertTrue("err=$err rpp=${c.radiansPerPixel()}", err < c.radiansPerPixel() * 0.5)
    }

    @Test
    fun wrapLon() {
        assertEquals(0.0, Camera.wrapLon(2 * Math.PI), 1e-12)
        assertTrue(abs(Camera.wrapLon(Math.toRadians(190.0)) - Math.toRadians(-170.0)) < 1e-12)
    }

    @Test fun exactPolesCanBeCentered() {
        for (lat in listOf(-90.0, 90.0)) {
            val c = cam(lat, 35.0, 0.01)
            val center = c.screenToLatLon(540f, 1200f)!!
            assertEquals(lat, Math.toDegrees(center[0]), 0.00001)
        }
    }

    @Test fun draggingAcrossBothPolesKeepsTheGrabbedPointUnderTheFinger() {
        for (sign in listOf(-1, 1)) {
            val c = cam(sign * 85.0, 40.0, 2.0)
            val point = c.surfacePoint(540f, 1200f)!!
            for (step in 1..12) {
                val sy = 1200f + sign * step * 50f
                val change = c.moveSurfacePoint(point, 540f, sy)!!
                c.update()
                val out = FloatArray(3)
                assertTrue(c.project(point[0], point[1], point[2], out))
                assertEquals(540f, out[0], 0.05f)
                assertEquals(sy, out[1], 0.05f)
                assertTrue("no polar jump", change.vector().sumOf { it * it } < 0.02)
            }
            assertTrue("crossed the pole", abs(Math.toDegrees(Camera.wrapLon(c.centerLon - Math.toRadians(40.0)))) > 170)
            assertTrue("continued on the far side", abs(Math.toDegrees(c.centerLat)) < 80)
        }
    }

    @Test fun horizontalAndDiagonalDragsWorkAtPolesAndDateLineAtMaximumZoom() {
        for (lat in listOf(-90.0, -89.99, 0.0, 89.99, 90.0)) for (lon in listOf(-179.99, 0.0, 179.99)) {
            val c = cam(lat, lon, Camera.MIN_ALT)
            val point = c.surfacePoint(480f, 1150f)!!
            c.moveSurfacePoint(point, 700f, 1400f)
            c.update()
            val out = FloatArray(3)
            assertTrue(c.project(point[0], point[1], point[2], out))
            assertEquals(700f, out[0], 0.5f); assertEquals(1400f, out[1], 0.5f)
            c.altitude *= 2
            c.moveSurfacePoint(point, 700f, 1400f)
            c.update()
            assertTrue(c.project(point[0], point[1], point[2], out))
            assertEquals(700f, out[0], 0.5f); assertEquals(1400f, out[1], 0.5f)
        }
    }

    @Test fun fullRotationsStayFiniteAndProjectionRayRoundTripRetainsRoll() {
        val c = cam(80.0, 170.0, 0.01)
        repeat(10000) { c.rotation = Rotation.vector(0.002, 0.003, -0.001) * c.rotation }
        val restored = Camera().apply { setViewport(1080, 2400); copyFrom(c); update() }
        for (sx in listOf(150f, 540f, 900f)) for (sy in listOf(400f, 1200f, 2000f)) {
            val p = restored.surfacePoint(sx, sy)!!
            val out = FloatArray(3)
            assertTrue(restored.project(p[0], p[1], p[2], out))
            assertEquals(sx, out[0], 0.5f); assertEquals(sy, out[1], 0.5f)
        }
        val q = c.rotation
        assertEquals(1.0, q.w*q.w+q.x*q.x+q.y*q.y+q.z*q.z, 1e-12)
    }

    @Test fun rotationInterpolationHandlesOppositeDirectionsAndQuaternionSigns() {
        val a = doubleArrayOf(0.0, 0.0, 1.0)
        val b = doubleArrayOf(0.0, 0.0, -1.0)
        val turn = Rotation.between(a, b)
        val end = turn.apply(a)
        for (i in a.indices) assertEquals(b[i], end[i], 1e-12)
        val mid = Rotation.IDENTITY.interpolate(turn, 0.5).apply(a)
        assertEquals(0.0, mid[2], 1e-12)
        val q = Rotation.northUp(1.5, 2.0)
        val same = Rotation.of(-q.w, -q.x, -q.y, -q.z)
        assertEquals(0.0, (q.interpolate(same, 0.5) * q.inverse()).vector().sumOf { it * it }, 1e-12)
    }
}
