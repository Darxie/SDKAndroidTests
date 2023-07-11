package cz.feldis.sdkandroidtests.voices

import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.timeout
import com.nhaarman.mockitokotlin2.verify
import com.sygic.sdk.OperationStatus
import com.sygic.sdk.voice.VoiceEntry
import com.sygic.sdk.voice.VoiceManager
import com.sygic.sdk.voice.VoiceManager.InstalledVoicesCallback
import com.sygic.sdk.voice.VoiceManagerProvider
import cz.feldis.sdkandroidtests.BaseTest
import org.junit.Assert.assertFalse
import org.junit.Test

class VoicesTests: BaseTest() {

    private lateinit var voicesManager : VoiceManager

    override fun setUp() {
        super.setUp()
        voicesManager = VoiceManagerProvider.getInstance().get()
    }

    @Test
    fun testGetInstalledVoices() {
        val listener : InstalledVoicesCallback = mock(verboseLogging = true)
        voicesManager.getInstalledVoices(listener)

        val captor = argumentCaptor<List<VoiceEntry>>()

        verify(listener, timeout(10_000L)).onInstalledVoiceList(captor.capture(), eq(OperationStatus(OperationStatus.Result.Success,"")))

        val list = captor.lastValue
        assertFalse(list.isEmpty())
    }

}