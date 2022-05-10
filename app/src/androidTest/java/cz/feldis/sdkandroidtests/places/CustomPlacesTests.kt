package cz.feldis.sdkandroidtests.places

import com.nhaarman.mockitokotlin2.*
import com.sygic.sdk.places.CustomPlacesManager
import com.sygic.sdk.places.CustomPlacesManagerProvider
import cz.feldis.sdkandroidtests.BaseTest
import org.junit.Assert
import org.junit.Test

class CustomPlacesTests : BaseTest() {

    lateinit var customPlacesmanager: CustomPlacesManager

    override fun setUp() {
        super.setUp()
        customPlacesmanager = CustomPlacesManagerProvider.getInstance().get()
    }

    @Test
    fun downloadCustomPlacesSlovakia() {
        val uninstallResultListener: CustomPlacesManager.InstallResultListener = mock(verboseLogging = true)
        customPlacesmanager.uninstallOfflinePlaces("sk", uninstallResultListener)
        verify(uninstallResultListener, timeout(2_000L)).onResult(any())
        val resultListener : CustomPlacesManager.InstallResultListener = mock(verboseLogging = true)
        val progressListener : CustomPlacesManager.InstallProgressListener = mock(verboseLogging = true)
        customPlacesmanager.installOfflinePlaces("sk", resultListener, progressListener)
        verify(progressListener, timeout(5_000L)).onProgress(any())
        verify(resultListener, timeout(5_000L)).onResult(eq(CustomPlacesManager.InstallResult.SUCCESS))
        verify(resultListener, never()).onResult(eq(CustomPlacesManager.InstallResult.FAIL))
    }

    @Test
    fun setModeGetModeTest() {
        customPlacesmanager.setMode(CustomPlacesManager.Mode.ONLINE)
        Assert.assertEquals(customPlacesmanager.getMode(), CustomPlacesManager.Mode.ONLINE)
        customPlacesmanager.setMode(CustomPlacesManager.Mode.OFFLINE)
        Assert.assertEquals(customPlacesmanager.getMode(), CustomPlacesManager.Mode.OFFLINE)
    }
}