package cz.feldis.sdkandroidtests.utils

import com.sygic.sdk.position.GeoBoundingBox
import com.sygic.sdk.position.GeoCoordinates
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

@Ignore("Only run manually")
class GeoUtilsTest {

    private val boundingBox = GeoBoundingBox(
        topLeft = GeoCoordinates(56.0, 12.0),
        bottomRight = GeoCoordinates(55.0, 13.0)
    )

    @Test
    fun pointWellInsideBoundingBox_returnsTrue() {
        val point = GeoCoordinates(55.5, 12.5)
        assertTrue(GeoUtils.isPointInBoundingBox(point, boundingBox))
    }

    @Test
    fun pointJustOutsideNorthernEdge_returnsFalse() {
        val point = GeoCoordinates(56.0001, 12.5)
        assertFalse(GeoUtils.isPointInBoundingBox(point, boundingBox))
    }

    @Test
    fun pointJustOutsideSouthernEdge_returnsFalse() {
        val point = GeoCoordinates(54.9999, 12.5)
        assertFalse(GeoUtils.isPointInBoundingBox(point, boundingBox))
    }

    @Test
    fun pointJustOutsideWesternEdge_returnsFalse() {
        val point = GeoCoordinates(55.5, 11.9999)
        assertFalse(GeoUtils.isPointInBoundingBox(point, boundingBox))
    }

    @Test
    fun pointJustOutsideEasternEdge_returnsFalse() {
        val point = GeoCoordinates(55.5, 13.0001)
        assertFalse(GeoUtils.isPointInBoundingBox(point, boundingBox))
    }

    @Test
    fun pointExactlyOnTopLeftCorner_returnsTrue() {
        val point = GeoCoordinates(56.0, 12.0)
        assertTrue(GeoUtils.isPointInBoundingBox(point, boundingBox))
    }

    @Test
    fun pointExactlyOnBottomRightCorner_returnsTrue() {
        val point = GeoCoordinates(55.0, 13.0)
        assertTrue(GeoUtils.isPointInBoundingBox(point, boundingBox))
    }

    @Test
    fun pointExactlyOnTopEdge_returnsTrue() {
        val point = GeoCoordinates(56.0, 12.5)
        assertTrue(GeoUtils.isPointInBoundingBox(point, boundingBox))
    }

    @Test
    fun pointExactlyOnBottomEdge_returnsTrue() {
        val point = GeoCoordinates(55.0, 12.5)
        assertTrue(GeoUtils.isPointInBoundingBox(point, boundingBox))
    }

    @Test
    fun pointExactlyOnLeftEdge_returnsTrue() {
        val point = GeoCoordinates(55.5, 12.0)
        assertTrue(GeoUtils.isPointInBoundingBox(point, boundingBox))
    }

    @Test
    fun pointExactlyOnRightEdge_returnsTrue() {
        val point = GeoCoordinates(55.5, 13.0)
        assertTrue(GeoUtils.isPointInBoundingBox(point, boundingBox))
    }

    @Test
    fun pointDiagonallyOutsideTopLeft_returnsFalse() {
        val point = GeoCoordinates(56.0001, 11.9999)
        assertFalse(GeoUtils.isPointInBoundingBox(point, boundingBox))
    }

    @Test
    fun pointDiagonallyOutsideBottomRight_returnsFalse() {
        val point = GeoCoordinates(54.9999, 13.0001)
        assertFalse(GeoUtils.isPointInBoundingBox(point, boundingBox))
    }

    @Test
    fun pointOnSameLatitudeAsTopButOutsideLongitude_returnsFalse() {
        val point = GeoCoordinates(56.0, 13.1)
        assertFalse(GeoUtils.isPointInBoundingBox(point, boundingBox))
    }

    @Test
    fun pointOnSameLongitudeAsLeftButOutsideLatitude_returnsFalse() {
        val point = GeoCoordinates(56.1, 12.0)
        assertFalse(GeoUtils.isPointInBoundingBox(point, boundingBox))
    }

    @Test
    fun pointOnSameLatitudeAsBottomButOutsideLongitude_returnsFalse() {
        val point = GeoCoordinates(55.0, 13.1)
        assertFalse(GeoUtils.isPointInBoundingBox(point, boundingBox))
    }

    @Test
    fun pointOnSameLongitudeAsRightButOutsideLatitude_returnsFalse() {
        val point = GeoCoordinates(54.9, 13.0)
        assertFalse(GeoUtils.isPointInBoundingBox(point, boundingBox))
    }
}
