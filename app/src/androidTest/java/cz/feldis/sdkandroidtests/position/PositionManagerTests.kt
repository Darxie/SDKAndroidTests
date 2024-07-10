package cz.feldis.sdkandroidtests.position

import androidx.test.filters.RequiresDevice
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.timeout
import com.nhaarman.mockitokotlin2.verify
import com.sygic.sdk.position.CustomPositionUpdater
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.position.GeoPosition
import com.sygic.sdk.position.PositionManager
import com.sygic.sdk.position.PositionManagerProvider
import cz.feldis.sdkandroidtests.BaseTest
import org.junit.Test

class PositionManagerTests: BaseTest() {

    @Test
    @RequiresDevice
    fun getLastValidLocationTest() {
        val positionListener : PositionManager.OnLastKnownPositionListener = mock(verboseLogging = true)

        PositionManagerProvider.getInstance().get().getLastKnownPosition(positionListener)
        verify(positionListener, timeout(5_000L)).onLastKnownPosition(argThat {
            if (this.isValid) {
                return@argThat true
            }
            false
        })
    }

    @Test
    fun customPositionUpdaterTest() {
        val mPositionManager = PositionManagerProvider.getInstance().get()

        val positionChangeListener : PositionManager.PositionChangeListener = mock(verboseLogging = true)
        val operationListener : PositionManager.OnOperationComplete = mock(verboseLogging = true)
        val updatePositionListener : CustomPositionUpdater.OnOperationComplete = mock(verboseLogging = true)

        val customPositionUpdater = CustomPositionUpdater()
        mPositionManager.setCustomPositionUpdater(customPositionUpdater, operationListener)
        mPositionManager.addPositionChangeListener(positionChangeListener)
        verify(operationListener, timeout(5_000L)).onComplete()

        val geoCoordinates = GeoCoordinates(48.11111,17.55555)
        val geoPosition = GeoPosition(geoCoordinates, 69.0, 42.0F, 5000L)
        customPositionUpdater.updatePosition(geoPosition, updatePositionListener)
        verify(updatePositionListener, timeout(5_000L)).onComplete()

        verify(positionChangeListener, timeout(10_000L)).onPositionChanged(eq(geoPosition))
    }
}