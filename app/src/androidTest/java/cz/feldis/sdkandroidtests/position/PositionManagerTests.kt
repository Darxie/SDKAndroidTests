package cz.feldis.sdkandroidtests.position

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.timeout
import com.nhaarman.mockitokotlin2.verify
import com.sygic.sdk.position.PositionManager
import com.sygic.sdk.position.PositionManagerProvider
import cz.feldis.sdkandroidtests.BaseTest
import junit.framework.TestCase.assertNotNull
import org.junit.Test

class PositionManagerTests: BaseTest() {

    @Test
    fun getLastValidLocationTest() {
        val positionListener : PositionManager.OnLastKnownPositionListener = mock(verboseLogging = true)

        assertNotNull(PositionManagerProvider.getInstance().get().lastKnownPosition)
        PositionManagerProvider.getInstance().get().getLastKnownPosition(positionListener)
        verify(positionListener, timeout(5_000L)).onLastKnownPosition(any())
    }
}