package cz.feldis.sdkandroidtests.position

import org.mockito.kotlin.*
import com.sygic.sdk.position.CustomPositionUpdater
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.position.GeoCourse
import com.sygic.sdk.position.GeoPosition
import com.sygic.sdk.position.PositionManager
import com.sygic.sdk.position.PositionManagerProvider
import cz.feldis.sdkandroidtests.BaseTest
import org.junit.Test

class PositionManagerTests : BaseTest() {

    private lateinit var positionManager: PositionManager

    override fun setUp() {
        super.setUp()
        positionManager = PositionManagerProvider.getInstance().get()
    }

    @Test
    fun getLastValidLocationTest() {
        startPositionUpdating()
        val positionListener: PositionManager.OnLastKnownPositionListener =
            mock(verboseLogging = true)

        PositionManagerProvider.getInstance().get().getLastKnownPosition(positionListener)
        verify(positionListener, timeout(5_000L)).onLastKnownPosition(argThat { isValid })
    }

    @Test
    fun customPositionUpdaterTest() {
        val mPositionManager = PositionManagerProvider.getInstance().get()

        val positionChangeListener: PositionManager.PositionChangeListener =
            mock(verboseLogging = true)
        val operationListener: PositionManager.OnOperationComplete = mock(verboseLogging = true)
        val updatePositionListener: CustomPositionUpdater.OnOperationComplete =
            mock(verboseLogging = true)

        val customPositionUpdater = CustomPositionUpdater()
        mPositionManager.setCustomPositionUpdater(customPositionUpdater, operationListener)
        mPositionManager.addPositionChangeListener(positionChangeListener)
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
    fun doubleStartPositionUpdatingTest() {
        val firstStartListener: PositionManager.OnOperationComplete = mock(verboseLogging = true)
        val secondStartListener: PositionManager.OnOperationComplete = mock(verboseLogging = true)
        val stopListener: PositionManager.OnOperationComplete = mock(verboseLogging = true)

        positionManager.startPositionUpdating(firstStartListener)
        verify(firstStartListener, timeout(5_000L)).onComplete()

        positionManager.startPositionUpdating(secondStartListener)
        verify(secondStartListener, timeout(5_000L)).onComplete()

        positionManager.stopPositionUpdating(stopListener)
        verify(stopListener, timeout(5_000L)).onComplete()
    }

    @Test
    fun customPositionUpdaterMultipleUpdatesTest() {
        val positionChangeListener: PositionManager.PositionChangeListener =
            mock(verboseLogging = true)
        val operationListener: PositionManager.OnOperationComplete = mock(verboseLogging = true)

        val customPositionUpdater = CustomPositionUpdater()
        positionManager.setCustomPositionUpdater(customPositionUpdater, operationListener)
        positionManager.addPositionChangeListener(positionChangeListener)
        verify(operationListener, timeout(5_000L)).onComplete()

        val coords0 = GeoCoordinates(48.1000, 17.1000)
        val coords1 = GeoCoordinates(48.1001, 17.1001)
        val coords2 = GeoCoordinates(48.1002, 17.1002)

        val positions = listOf(
            GeoPosition(coords0, 50.0, 90.0F, 1000L),
            GeoPosition(coords1, 55.0, 91.0F, 2000L),
            GeoPosition(coords2, 60.0, 92.0F, 3000L)
        )

        positions.forEach { position ->
            val updateListener: CustomPositionUpdater.OnOperationComplete =
                mock(verboseLogging = true)
            customPositionUpdater.updatePosition(position, updateListener)
            verify(updateListener, timeout(5_000L)).onComplete()
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
    fun zeroSpeedPositionUpdateTest() {
        val positionChangeListener: PositionManager.PositionChangeListener =
            mock(verboseLogging = true)
        val operationListener: PositionManager.OnOperationComplete = mock(verboseLogging = true)
        val updateListener: CustomPositionUpdater.OnOperationComplete =
            mock(verboseLogging = true)

        val customPositionUpdater = CustomPositionUpdater()
        positionManager.setCustomPositionUpdater(customPositionUpdater, operationListener)
        positionManager.addPositionChangeListener(positionChangeListener)
        verify(operationListener, timeout(5_000L)).onComplete()

        val geoPosition = GeoPosition(GeoCoordinates(48.4100, 17.4100), 0.0, 0.0F, 17000L)
        customPositionUpdater.updatePosition(geoPosition, updateListener)

        verify(updateListener, timeout(5_000L)).onComplete()
        verify(positionChangeListener, timeout(10_000L)).onPositionChanged(eq(geoPosition))
    }

    @Test
    fun multiplePositionChangeListenersTest() {
        val listener1: PositionManager.PositionChangeListener = mock(verboseLogging = true)
        val listener2: PositionManager.PositionChangeListener = mock(verboseLogging = true)
        val operationListener: PositionManager.OnOperationComplete = mock(verboseLogging = true)
        val updateListener: CustomPositionUpdater.OnOperationComplete =
            mock(verboseLogging = true)

        val customPositionUpdater = CustomPositionUpdater()
        positionManager.setCustomPositionUpdater(customPositionUpdater, operationListener)
        positionManager.addPositionChangeListener(listener1)
        positionManager.addPositionChangeListener(listener2)
        verify(operationListener, timeout(5_000L)).onComplete()

        val geoPosition = GeoPosition(GeoCoordinates(48.1500, 17.1500), 60.0, 100.0F, 13000L)
        customPositionUpdater.updatePosition(geoPosition, updateListener)

        verify(updateListener, timeout(5_000L)).onComplete()
        verify(listener1, timeout(10_000L)).onPositionChanged(eq(geoPosition))
        verify(listener2, timeout(10_000L)).onPositionChanged(eq(geoPosition))
    }

    @Test
    fun removePositionChangeListenerTest() {
        val positionChangeListener: PositionManager.PositionChangeListener =
            mock(verboseLogging = true)
        val operationListener: PositionManager.OnOperationComplete = mock(verboseLogging = true)

        val customPositionUpdater = CustomPositionUpdater()
        positionManager.setCustomPositionUpdater(customPositionUpdater, operationListener)
        positionManager.addPositionChangeListener(positionChangeListener)
        verify(operationListener, timeout(5_000L)).onComplete()

        val firstPosition = GeoPosition(GeoCoordinates(48.3000, 17.4000), 70.0, 30.0F, 9000L)
        val firstUpdateListener: CustomPositionUpdater.OnOperationComplete =
            mock(verboseLogging = true)
        customPositionUpdater.updatePosition(firstPosition, firstUpdateListener)
        verify(firstUpdateListener, timeout(5_000L)).onComplete()
        verify(positionChangeListener, timeout(10_000L)).onPositionChanged(eq(firstPosition))

        positionManager.removePositionChangeListener(positionChangeListener)

        val secondPosition = GeoPosition(GeoCoordinates(48.4000, 17.5000), 75.0, 35.0F, 10000L)
        val secondUpdateListener: CustomPositionUpdater.OnOperationComplete =
            mock(verboseLogging = true)
        customPositionUpdater.updatePosition(secondPosition, secondUpdateListener)
        verify(secondUpdateListener, timeout(5_000L)).onComplete()

        Thread.sleep(2_000L)
        verify(positionChangeListener, never()).onPositionChanged(eq(secondPosition))
    }

    @Test
    fun customPositionUpdaterCourseUpdateTest() {
        val positionChangeListener: PositionManager.PositionChangeListener =
            mock(verboseLogging = true)
        val operationListener: PositionManager.OnOperationComplete = mock(verboseLogging = true)
        val courseUpdateListener: CustomPositionUpdater.OnOperationComplete =
            mock(verboseLogging = true)

        val customPositionUpdater = CustomPositionUpdater()
        positionManager.setCustomPositionUpdater(customPositionUpdater, operationListener)
        positionManager.addPositionChangeListener(positionChangeListener)
        verify(operationListener, timeout(5_000L)).onComplete()

        val geoCourse = GeoCourse(180.0F, 5.0F, 4000L)
        customPositionUpdater.updateCourse(geoCourse, courseUpdateListener)

        verify(courseUpdateListener, timeout(5_000L)).onComplete()
        verify(positionChangeListener, timeout(10_000L)).onCourseChanged(eq(geoCourse))
    }

    @Test
    fun lastKnownPositionWithCustomUpdaterTest() {
        val customPositionUpdater = CustomPositionUpdater()
        val operationListener: PositionManager.OnOperationComplete = mock(verboseLogging = true)
        val updateListener: CustomPositionUpdater.OnOperationComplete =
            mock(verboseLogging = true)

        positionManager.setCustomPositionUpdater(customPositionUpdater, operationListener)
        verify(operationListener, timeout(5_000L)).onComplete()

        val expectedCoordinates = GeoCoordinates(48.5000, 17.8000)
        val expectedPosition = GeoPosition(expectedCoordinates, 100.0, 45.0F, 8000L)
        customPositionUpdater.updatePosition(expectedPosition, updateListener)
        verify(updateListener, timeout(5_000L)).onComplete()

        val positionListener: PositionManager.OnLastKnownPositionListener =
            mock(verboseLogging = true)
        positionManager.getLastKnownPosition(positionListener)
        verify(positionListener, timeout(5_000L)).onLastKnownPosition(argThat {
            isValid && coordinates == expectedCoordinates
        })
    }

    @Test
    fun customPositionUpdaterWithAccuracyTest() {
        val positionChangeListener: PositionManager.PositionChangeListener =
            mock(verboseLogging = true)
        val operationListener: PositionManager.OnOperationComplete = mock(verboseLogging = true)
        val updateListener: CustomPositionUpdater.OnOperationComplete =
            mock(verboseLogging = true)

        val customPositionUpdater = CustomPositionUpdater()
        positionManager.setCustomPositionUpdater(customPositionUpdater, operationListener)
        positionManager.addPositionChangeListener(positionChangeListener)
        verify(operationListener, timeout(5_000L)).onComplete()

        val geoCoordinates = GeoCoordinates(48.6000, 17.9000, 250.0)
        val geoPosition = GeoPosition(geoCoordinates, 90.0, 180.0F, 11000L)
        customPositionUpdater.updatePosition(geoPosition, updateListener)

        verify(updateListener, timeout(5_000L)).onComplete()
        verify(positionChangeListener, timeout(10_000L)).onPositionChanged(
            argThat { coordinates.altitude == 250.0 }
        )
    }

    @Test
    fun setCustomPositionUpdaterTest() {
        val customPositionUpdater = CustomPositionUpdater()
        val operationListener: PositionManager.OnOperationComplete = mock(verboseLogging = true)
        positionManager.setCustomPositionUpdater(customPositionUpdater, operationListener)
        verify(operationListener, timeout(5_000L)).onComplete()

        val positionChangeListener: PositionManager.PositionChangeListener =
            mock(verboseLogging = true)
        val updateListener: CustomPositionUpdater.OnOperationComplete =
            mock(verboseLogging = true)
        positionManager.addPositionChangeListener(positionChangeListener)

        val geoPosition = GeoPosition(GeoCoordinates(48.7000, 17.2000), 40.0, 200.0F, 12000L)
        customPositionUpdater.updatePosition(geoPosition, updateListener)

        verify(updateListener, timeout(5_000L)).onComplete()
        verify(positionChangeListener, timeout(10_000L)).onPositionChanged(eq(geoPosition))
    }

    @Test
    fun customUpdaterPositionWithSpeedAndCourseVerificationTest() {
        val positionChangeListener: PositionManager.PositionChangeListener =
            mock(verboseLogging = true)
        val operationListener: PositionManager.OnOperationComplete = mock(verboseLogging = true)
        val updateListener: CustomPositionUpdater.OnOperationComplete =
            mock(verboseLogging = true)

        val customPositionUpdater = CustomPositionUpdater()
        positionManager.setCustomPositionUpdater(customPositionUpdater, operationListener)
        positionManager.addPositionChangeListener(positionChangeListener)
        verify(operationListener, timeout(5_000L)).onComplete()

        val geoPosition = GeoPosition(GeoCoordinates(48.8500, 17.6500), 120.5, 359.9F, 18000L)
        customPositionUpdater.updatePosition(geoPosition, updateListener)

        verify(updateListener, timeout(5_000L)).onComplete()
        verify(positionChangeListener, timeout(10_000L)).onPositionChanged(
            argThat {
                speed == 120.5 && course == 359.9F && coordinates.latitude == 48.8500
            }
        )
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