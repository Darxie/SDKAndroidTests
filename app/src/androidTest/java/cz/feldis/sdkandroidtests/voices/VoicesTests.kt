package cz.feldis.sdkandroidtests.voices

import org.mockito.kotlin.*
import com.sygic.sdk.OperationStatus
import com.sygic.sdk.voice.VoiceEntry
import com.sygic.sdk.voice.VoiceManager
import com.sygic.sdk.voice.VoiceManager.InstalledVoicesCallback
import com.sygic.sdk.voice.VoiceManager.OnSetVoiceCallback
import com.sygic.sdk.voice.VoiceManagerProvider
import cz.feldis.sdkandroidtests.BaseTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VoicesTests : BaseTest() {

    private lateinit var voicesManager: VoiceManager

    override fun setUp() {
        super.setUp()
        voicesManager = VoiceManagerProvider.getInstance().get()
    }

    @Test
    fun testGetInstalledVoices() {
        val listener: InstalledVoicesCallback = mock(verboseLogging = true)
        voicesManager.getInstalledVoices(listener)

        val captor = argumentCaptor<List<VoiceEntry>>()

        verify(listener, timeout(10_000L)).onInstalledVoiceList(
            captor.capture(),
            eq(OperationStatus(OperationStatus.Result.Success, ""))
        )

        val list = captor.lastValue
        assertFalse(list.isEmpty())
    }

    /**
     * This may not work on Samsung devices as the requested voice is probably missing.
     * You may use en-GB-default for this purpose.
     */
    @Test
    fun onSetVoiceCallbackTest() {
        val onSetVoiceCallback: OnSetVoiceCallback = mock(verboseLogging = true)
        val installedVoicesCallback: InstalledVoicesCallback = mock(verboseLogging = true)
        val voiceListCaptor = argumentCaptor<List<VoiceEntry>>()
        voicesManager.addOnSetVoiceCallback(onSetVoiceCallback)
        voicesManager.getInstalledVoices(installedVoicesCallback)
        verify(installedVoicesCallback, timeout(10_000L))
            .onInstalledVoiceList(
                voiceListCaptor.capture(),
                eq(OperationStatus(OperationStatus.Result.Success, ""))
            )
        assertEquals(1, voiceListCaptor.allValues.size)
        voiceListCaptor.lastValue.find { it.id == "en-au-x-aub-local" }
            ?.let { voice ->
                voicesManager.setVoice(voice)
            }
        verify(onSetVoiceCallback, timeout(10_000L)).onSetVoice("en-au-x-aub-local", true)
    }
}