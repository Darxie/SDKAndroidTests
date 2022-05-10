package cz.feldis.sdkandroidtests.places

import com.nhaarman.mockitokotlin2.*
import com.sygic.sdk.places.CustomPlacesManager
import com.sygic.sdk.places.CustomPlacesManagerProvider
import cz.feldis.sdkandroidtests.BaseTest
import org.junit.Assert
import org.junit.Test

class CustomPlacesTests : BaseTest() {

    lateinit var customPlacesManager: CustomPlacesManager

    override fun setUp() {
        super.setUp()
        customPlacesManager = CustomPlacesManagerProvider.getInstance().get()
    }

    @Test
    fun downloadCustomPlacesSlovakia() {
        val uninstallResultListener: CustomPlacesManager.InstallResultListener = mock(verboseLogging = true)
        customPlacesManager.uninstallOfflinePlaces("sk", uninstallResultListener)
        verify(uninstallResultListener, timeout(2_000L)).onResult(any())
        val resultListener : CustomPlacesManager.InstallResultListener = mock(verboseLogging = true)
        val progressListener : CustomPlacesManager.InstallProgressListener = mock(verboseLogging = true)
        customPlacesManager.installOfflinePlaces("sk", resultListener, progressListener)
        verify(progressListener, timeout(5_000L)).onProgress(any())
        verify(resultListener, timeout(5_000L)).onResult(eq(CustomPlacesManager.InstallResult.SUCCESS))
        verify(resultListener, never()).onResult(eq(CustomPlacesManager.InstallResult.FAIL))
    }

    @Test
    fun setModeGetModeTest() {
        customPlacesManager.setMode(CustomPlacesManager.Mode.ONLINE)
        Assert.assertEquals(customPlacesManager.getMode(), CustomPlacesManager.Mode.ONLINE)
        customPlacesManager.setMode(CustomPlacesManager.Mode.OFFLINE)
        Assert.assertEquals(customPlacesManager.getMode(), CustomPlacesManager.Mode.OFFLINE)
    }
}