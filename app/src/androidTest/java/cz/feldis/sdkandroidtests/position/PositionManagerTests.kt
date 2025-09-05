package cz.feldis.sdkandroidtests.position

import com.sygic.sdk.position.CustomPositionUpdater
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.position.GeoPosition
import com.sygic.sdk.position.PositionManager
import com.sygic.sdk.position.PositionManagerProvider
import cz.feldis.sdkandroidtests.BaseTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
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
}