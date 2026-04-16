package cz.feldis.sdkandroidtests.position

import com.sygic.sdk.position.CustomPositionUpdater
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.position.GeoCourse
import com.sygic.sdk.position.GeoPosition
import com.sygic.sdk.position.PositionManager
import com.sygic.sdk.position.PositionManagerProvider
import com.sygic.sdk.position.TunnelPositionMode
import com.sygic.sdk.position.results.PositionChangedData
import cz.feldis.sdkandroidtests.BaseTest
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify

class PositionManagerTests : BaseTest() {

    private lateinit var positionManager: PositionManager

    @Before
    override fun setUp() {
        super.setUp()
        positionManager = runBlocking { PositionManagerProvider.getInstance() }
    }

    @Test
    fun getLastValidLocationTest() {
        startPositionUpdating()
        val positionListener: PositionManager.OnLastKnownPositionListener =
            mock(verboseLogging = true)

        positionManager.getLastKnownPosition(positionListener)
        verify(positionListener, timeout(5_000L)).onLastKnownPosition(argThat { isValid })
    }

    @Test
    fun customPositionUpdaterTest() {

        val positionChangeListener: PositionManager.PositionChangeListener =
            mock(verboseLogging = true)
        val operationListener: PositionManager.OnOperationComplete = mock(verboseLogging = true)
        val updatePositionListener: CustomPositionUpdater.OnOperationComplete =
            mock(verboseLogging = true)

        val customPositionUpdater = CustomPositionUpdater()
        positionManager.setCustomPositionUpdater(customPositionUpdater, operationListener)
        positionManager.addPositionChangeListener(positionChangeListener)
        verify(operationListener, timeout(5_000L)).onComplete()

        val geoCoordinates = GeoCoordinates(48.11111, 17.55555)
        val geoPosition = GeoPosition(geoCoordinates, 69.0, 42.0F, 5000L)
        customPositionUpdater.updatePosition(geoPosition, updatePositionListener)
        verify(updatePositionListener, timeout(5_000L)).onComplete()

        verify(positionChangeListener, timeout(10_000L)).onPositionChanged(eq(geoPosition))
    }

    @Test
    fun startAndStopPositionUpdatingTest() {
        val startListener: PositionManager.OnOperationComplete = mock(verboseLogging = true)
        val stopListener: PositionManager.OnOperationComplete = mock(verboseLogging = true)

        positionManager.startPositionUpdating(startListener)
        verify(startListener, timeout(5_000L)).onComplete()

        positionManager.stopPositionUpdating(stopListener)
        verify(stopListener, timeout(5_000L)).onComplete()
    }

    @Test
    fun startPositionUpdatingSuspendTest() {
        runBlocking {
            withTimeout(5_000L) {
                positionManager.startPositionUpdating()
            }
        }
        runBlocking {
            withTimeout(5_000L) {
                positionManager.stopPositionUpdating()
            }
        }
    }

    @Test
    fun getLastKnownPositionSuspendTest() {
        startPositionUpdating()
        val position = runBlocking {
            withTimeout(5_000L) {
                positionManager.getLastKnownPosition()
            }
        }
        assertTrue(position.isValid)
    }

    @Test
    fun customPositionUpdaterMultipleUpdatesTest() {
        val positionChangeListener: PositionManager.PositionChangeListener =
            mock(verboseLogging = true)

        val customPositionUpdater = CustomPositionUpdater()
        runBlocking { positionManager.setCustomPositionUpdater(customPositionUpdater) }
        positionManager.addPositionChangeListener(positionChangeListener)

        val coords0 = GeoCoordinates(48.1000, 17.1000)
        val coords1 = GeoCoordinates(48.1001, 17.1001)
        val coords2 = GeoCoordinates(48.1002, 17.1002)

        val positions = listOf(
            GeoPosition(coords0, 50.0, 90.0F, 1000L),
            GeoPosition(coords1, 55.0, 91.0F, 2000L),
            GeoPosition(coords2, 60.0, 92.0F, 3000L)
        )

        for (position in positions) {
            runBlocking { customPositionUpdater.updatePosition(position) }
        }

        verify(positionChangeListener, timeout(10_000L)).onPositionChanged(
            argThat { coordinates == coords0 && course == 90.0F }
        )
        verify(positionChangeListener, timeout(10_000L)).onPositionChanged(
            argThat { coordinates == coords1 && course == 91.0F }
        )
        verify(positionChangeListener, timeout(10_000L)).onPositionChanged(
            argThat { coordinates == coords2 && course == 92.0F }
        )
    }

    @Test
    fun customPositionUpdaterCourseUpdateTest() {
        val positionChangeListener: PositionManager.PositionChangeListener =
            mock(verboseLogging = true)

        val customPositionUpdater = CustomPositionUpdater()
        runBlocking { positionManager.setCustomPositionUpdater(customPositionUpdater) }
        positionManager.addPositionChangeListener(positionChangeListener)

        val geoCourse = GeoCourse(180.0F, 5.0F, 4000L)
        runBlocking { customPositionUpdater.updateCourse(geoCourse) }

        verify(positionChangeListener, timeout(10_000L)).onCourseChanged(eq(geoCourse))
    }

    @Test
    fun removeCustomPositionUpdaterTest() {
        val customPositionUpdater = CustomPositionUpdater()
        runBlocking { positionManager.setCustomPositionUpdater(customPositionUpdater) }

        // Remove the custom position updater by setting null
        runBlocking { positionManager.setCustomPositionUpdater(null) }

        // After removing, start regular GPS position updating should work
        val startListener: PositionManager.OnOperationComplete = mock(verboseLogging = true)
        positionManager.startPositionUpdating(startListener)
        verify(startListener, timeout(5_000L)).onComplete()
    }

    @Test
    fun positionChangesFlowTest() {
        val customPositionUpdater = CustomPositionUpdater()
        runBlocking { positionManager.setCustomPositionUpdater(customPositionUpdater) }

        val geoCoordinates = GeoCoordinates(48.2000, 17.3000)
        val geoPosition = GeoPosition(geoCoordinates, 80.0, 120.0F, 6000L)

        val receivedPosition = runBlocking {
            withTimeout(10_000L) {
                val deferred = async {
                    positionManager.positionChanges()
                        .first { it is PositionChangedData.PositionChanged }
                        .let { (it as PositionChangedData.PositionChanged).position }
                }
                delay(500L)
                customPositionUpdater.updatePosition(geoPosition)
                deferred.await()
            }
        }
        assertEquals(geoCoordinates, receivedPosition.coordinates)
        assertEquals(120.0F, receivedPosition.course)
    }

    @Test
    fun courseChangesFlowTest() {
        val customPositionUpdater = CustomPositionUpdater()
        runBlocking { positionManager.setCustomPositionUpdater(customPositionUpdater) }

        val geoCourse = GeoCourse(270.0F, 3.0F, 7000L)

        val receivedCourse = runBlocking {
            withTimeout(10_000L) {
                val deferred = async {
                    positionManager.positionChanges()
                        .first { it is PositionChangedData.CourseChanged }
                        .let { (it as PositionChangedData.CourseChanged).course }
                }
                delay(500L)
                customPositionUpdater.updateCourse(geoCourse)
                deferred.await()
            }
        }
        assertEquals(geoCourse, receivedCourse)
    }

    @Test
    fun getTunnelPositionModeTest() {
        val mode = runBlocking {
            withTimeout(5_000L) {
                positionManager.getTunnelPositionMode()
            }
        }
        assertTrue(
            mode == TunnelPositionMode.Interpolation || mode == TunnelPositionMode.RawSignal
        )
    }

    @Test
    fun setTunnelPositionModeInterpolationTest() {
        runBlocking {
            withTimeout(5_000L) {
                positionManager.setTunnelPositionMode(TunnelPositionMode.Interpolation)
            }
        }
        val mode = runBlocking {
            withTimeout(5_000L) {
                positionManager.getTunnelPositionMode()
            }
        }
        assertEquals(TunnelPositionMode.Interpolation, mode)
    }

    @Test
    fun setTunnelPositionModeRawSignalTest() {
        runBlocking {
            withTimeout(5_000L) {
                positionManager.setTunnelPositionMode(TunnelPositionMode.RawSignal)
            }
        }
        val mode = runBlocking {
            withTimeout(5_000L) {
                positionManager.getTunnelPositionMode()
            }
        }
        assertEquals(TunnelPositionMode.RawSignal, mode)
    }

    @Test
    fun lastKnownPositionWithCustomUpdaterTest() {
        val customPositionUpdater = CustomPositionUpdater()
        runBlocking { positionManager.setCustomPositionUpdater(customPositionUpdater) }

        val expectedCoordinates = GeoCoordinates(48.5000, 17.8000)
        val expectedPosition = GeoPosition(expectedCoordinates, 100.0, 45.0F, 8000L)
        runBlocking { customPositionUpdater.updatePosition(expectedPosition) }

        // Give SDK time to process the position update
        Thread.sleep(1_000L)

        val lastKnown = runBlocking {
            withTimeout(5_000L) {
                positionManager.getLastKnownPosition()
            }
        }
        assertTrue(lastKnown.isValid)
        assertEquals(expectedCoordinates, lastKnown.coordinates)
    }

    @Test
    fun removePositionChangeListenerTest() {
        val positionChangeListener: PositionManager.PositionChangeListener =
            mock(verboseLogging = true)

        val customPositionUpdater = CustomPositionUpdater()
        runBlocking { positionManager.setCustomPositionUpdater(customPositionUpdater) }
        positionManager.addPositionChangeListener(positionChangeListener)

        val firstPosition = GeoPosition(GeoCoordinates(48.3000, 17.4000), 70.0, 30.0F, 9000L)
        runBlocking { customPositionUpdater.updatePosition(firstPosition) }
        verify(positionChangeListener, timeout(10_000L)).onPositionChanged(eq(firstPosition))

        // Remove the listener
        positionManager.removePositionChangeListener(positionChangeListener)

        // Send another position - should NOT be received
        val secondPosition = GeoPosition(GeoCoordinates(48.4000, 17.5000), 75.0, 35.0F, 10000L)
        runBlocking { customPositionUpdater.updatePosition(secondPosition) }

        // Wait a bit and verify the second position was never received
        Thread.sleep(2_000L)
        verify(positionChangeListener, never()).onPositionChanged(eq(secondPosition))
    }

    @Test
    fun customPositionUpdaterWithAccuracyTest() {
        val positionChangeListener: PositionManager.PositionChangeListener =
            mock(verboseLogging = true)

        val customPositionUpdater = CustomPositionUpdater()
        runBlocking { positionManager.setCustomPositionUpdater(customPositionUpdater) }
        positionManager.addPositionChangeListener(positionChangeListener)

        val geoCoordinates = GeoCoordinates(48.6000, 17.9000, 250.0)
        val geoPosition = GeoPosition(geoCoordinates, 90.0, 180.0F, 11000L)
        runBlocking { customPositionUpdater.updatePosition(geoPosition) }

        verify(positionChangeListener, timeout(10_000L)).onPositionChanged(
            argThat { coordinates.altitude == 250.0 }
        )
    }

    @Test
    fun setCustomPositionUpdaterSuspendTest() {
        val customPositionUpdater = CustomPositionUpdater()
        runBlocking {
            withTimeout(5_000L) {
                positionManager.setCustomPositionUpdater(customPositionUpdater)
            }
        }
        // Verify it works by sending a position
        val positionChangeListener: PositionManager.PositionChangeListener =
            mock(verboseLogging = true)
        positionManager.addPositionChangeListener(positionChangeListener)

        val geoPosition = GeoPosition(GeoCoordinates(48.7000, 17.2000), 40.0, 200.0F, 12000L)
        runBlocking { customPositionUpdater.updatePosition(geoPosition) }

        verify(positionChangeListener, timeout(10_000L)).onPositionChanged(eq(geoPosition))
    }

    @Test
    fun multiplePositionChangeListenersTest() {
        val listener1: PositionManager.PositionChangeListener = mock(verboseLogging = true)
        val listener2: PositionManager.PositionChangeListener = mock(verboseLogging = true)

        val customPositionUpdater = CustomPositionUpdater()
        runBlocking { positionManager.setCustomPositionUpdater(customPositionUpdater) }
        positionManager.addPositionChangeListener(listener1)
        positionManager.addPositionChangeListener(listener2)

        val geoPosition = GeoPosition(GeoCoordinates(48.1500, 17.1500), 60.0, 100.0F, 13000L)
        runBlocking { customPositionUpdater.updatePosition(geoPosition) }

        // Both listeners should receive the position update
        verify(listener1, timeout(10_000L)).onPositionChanged(eq(geoPosition))
        verify(listener2, timeout(10_000L)).onPositionChanged(eq(geoPosition))
    }

    @Test
    fun replaceCustomPositionUpdaterTest() {
        val positionChangeListener: PositionManager.PositionChangeListener =
            mock(verboseLogging = true)

        val firstUpdater = CustomPositionUpdater()
        runBlocking { positionManager.setCustomPositionUpdater(firstUpdater) }
        positionManager.addPositionChangeListener(positionChangeListener)

        val firstPosition = GeoPosition(GeoCoordinates(48.2100, 17.2100), 50.0, 45.0F, 14000L)
        runBlocking { firstUpdater.updatePosition(firstPosition) }
        verify(positionChangeListener, timeout(10_000L)).onPositionChanged(eq(firstPosition))

        // Replace with a second custom updater
        val secondUpdater = CustomPositionUpdater()
        runBlocking { positionManager.setCustomPositionUpdater(secondUpdater) }

        val secondCoordinates = GeoCoordinates(48.2200, 17.2200)
        val secondPosition = GeoPosition(secondCoordinates, 55.0, 50.0F, 15000L)
        runBlocking { secondUpdater.updatePosition(secondPosition) }
        verify(positionChangeListener, timeout(10_000L)).onPositionChanged(
            argThat { coordinates == secondCoordinates && course == 50.0F }
        )
    }

    @Test
    fun positionAndCourseInterleavedFlowTest() {
        val customPositionUpdater = CustomPositionUpdater()
        runBlocking { positionManager.setCustomPositionUpdater(customPositionUpdater) }

        val geoPosition = GeoPosition(GeoCoordinates(48.3100, 17.3100), 70.0, 90.0F, 16000L)
        val geoCourse = GeoCourse(45.0F, 2.0F, 16500L)

        val results = runBlocking {
            withTimeout(10_000L) {
                val flow = positionManager.positionChanges()

                launch {
                    delay(500L)
                    customPositionUpdater.updatePosition(geoPosition)
                    customPositionUpdater.updateCourse(geoCourse)
                }

                flow.take(2).toList()
            }
        }
        assertTrue(results.any { it is PositionChangedData.PositionChanged })
        assertTrue(results.any { it is PositionChangedData.CourseChanged })
    }

    @Test
    fun zeroSpeedPositionUpdateTest() {
        val positionChangeListener: PositionManager.PositionChangeListener =
            mock(verboseLogging = true)

        val customPositionUpdater = CustomPositionUpdater()
        runBlocking { positionManager.setCustomPositionUpdater(customPositionUpdater) }
        positionManager.addPositionChangeListener(positionChangeListener)

        val geoPosition = GeoPosition(GeoCoordinates(48.4100, 17.4100), 0.0, 0.0F, 17000L)
        runBlocking { customPositionUpdater.updatePosition(geoPosition) }

        verify(positionChangeListener, timeout(10_000L)).onPositionChanged(eq(geoPosition))
    }

    @Test
    fun toggleTunnelPositionModeTest() {
        // Set to Interpolation
        runBlocking {
            withTimeout(5_000L) {
                positionManager.setTunnelPositionMode(TunnelPositionMode.Interpolation)
            }
        }
        var mode = runBlocking {
            withTimeout(5_000L) { positionManager.getTunnelPositionMode() }
        }
        assertEquals(TunnelPositionMode.Interpolation, mode)

        // Toggle to RawSignal
        runBlocking {
            withTimeout(5_000L) {
                positionManager.setTunnelPositionMode(TunnelPositionMode.RawSignal)
            }
        }
        mode = runBlocking {
            withTimeout(5_000L) { positionManager.getTunnelPositionMode() }
        }
        assertEquals(TunnelPositionMode.RawSignal, mode)

        // Toggle back to Interpolation
        runBlocking {
            withTimeout(5_000L) {
                positionManager.setTunnelPositionMode(TunnelPositionMode.Interpolation)
            }
        }
        mode = runBlocking {
            withTimeout(5_000L) { positionManager.getTunnelPositionMode() }
        }
        assertEquals(TunnelPositionMode.Interpolation, mode)
    }

    @Test
    fun doubleStartPositionUpdatingTest() {
        runBlocking {
            withTimeout(5_000L) { positionManager.startPositionUpdating() }
        }
        // Starting again should complete without error
        runBlocking {
            withTimeout(5_000L) { positionManager.startPositionUpdating() }
        }
        runBlocking {
            withTimeout(5_000L) { positionManager.stopPositionUpdating() }
        }
    }

    @Test
    fun customUpdaterPositionWithSpeedAndCourseVerificationTest() {
        val positionChangeListener: PositionManager.PositionChangeListener =
            mock(verboseLogging = true)

        val customPositionUpdater = CustomPositionUpdater()
        runBlocking { positionManager.setCustomPositionUpdater(customPositionUpdater) }
        positionManager.addPositionChangeListener(positionChangeListener)

        val geoPosition = GeoPosition(GeoCoordinates(48.8500, 17.6500), 120.5, 359.9F, 18000L)
        runBlocking { customPositionUpdater.updatePosition(geoPosition) }

        verify(positionChangeListener, timeout(10_000L)).onPositionChanged(
            argThat {
                speed == 120.5 && course == 359.9F && coordinates.latitude == 48.8500
            }
        )
    }

    @Test
    fun removeOneOfMultipleListenersTest() {
        val listener1: PositionManager.PositionChangeListener = mock(verboseLogging = true)
        val listener2: PositionManager.PositionChangeListener = mock(verboseLogging = true)

        val customPositionUpdater = CustomPositionUpdater()
        runBlocking { positionManager.setCustomPositionUpdater(customPositionUpdater) }
        positionManager.addPositionChangeListener(listener1)
        positionManager.addPositionChangeListener(listener2)

        val firstCoords = GeoCoordinates(48.9100, 17.7100)
        val firstPosition = GeoPosition(firstCoords, 30.0, 10.0F, 19000L)
        runBlocking { customPositionUpdater.updatePosition(firstPosition) }
        verify(listener1, timeout(10_000L)).onPositionChanged(
            argThat { coordinates == firstCoords }
        )
        verify(listener2, timeout(10_000L)).onPositionChanged(
            argThat { coordinates == firstCoords }
        )

        // Remove only listener1
        positionManager.removePositionChangeListener(listener1)

        val secondCoords = GeoCoordinates(48.9200, 17.7200)
        val secondPosition = GeoPosition(secondCoords, 35.0, 15.0F, 20000L)
        runBlocking { customPositionUpdater.updatePosition(secondPosition) }

        // listener2 should still receive updates
        verify(listener2, timeout(10_000L)).onPositionChanged(
            argThat { coordinates == secondCoords }
        )

        // listener1 should NOT receive the second update
        Thread.sleep(2_000L)
        verify(listener1, never()).onPositionChanged(
            argThat { coordinates == secondCoords }
        )
    }

    @Test
    fun lastKnownPositionBeforeAnyUpdateTest() {
        val position = runBlocking {
            withTimeout(5_000L) {
                positionManager.getLastKnownPosition()
            }
        }
        // Position should be returned (may or may not be valid depending on GPS state)
        // The call itself should not throw
        assertTrue(position != null)
    }

    @Test
    fun customUpdaterCourseWithCallbackTest() {
        val positionChangeListener: PositionManager.PositionChangeListener =
            mock(verboseLogging = true)
        val courseUpdateListener: CustomPositionUpdater.OnOperationComplete =
            mock(verboseLogging = true)

        val customPositionUpdater = CustomPositionUpdater()
        val operationListener: PositionManager.OnOperationComplete = mock(verboseLogging = true)
        positionManager.setCustomPositionUpdater(customPositionUpdater, operationListener)
        verify(operationListener, timeout(5_000L)).onComplete()

        positionManager.addPositionChangeListener(positionChangeListener)

        val geoCourse = GeoCourse(90.0F, 1.5F, 21000L)
        customPositionUpdater.updateCourse(geoCourse, courseUpdateListener)
        verify(courseUpdateListener, timeout(5_000L)).onComplete()

        verify(positionChangeListener, timeout(10_000L)).onCourseChanged(eq(geoCourse))
    }
}