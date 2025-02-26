package cz.feldis.sdkandroidtests.voices

import com.sygic.sdk.OperationStatus
import com.sygic.sdk.voice.VoiceDownload
import com.sygic.sdk.voice.VoiceDownloadProvider
import com.sygic.sdk.voice.VoiceEntry
import com.sygic.sdk.voice.VoiceManager
import com.sygic.sdk.voice.VoiceManager.InstalledVoicesCallback
import com.sygic.sdk.voice.VoiceManager.OnSetVoiceCallback
import com.sygic.sdk.voice.VoiceManagerProvider
import cz.feldis.sdkandroidtests.BaseTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.mockito.kotlin.*

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

    @Test
    fun getVoiceStatusTest() {
        val listener: VoiceDownload.AvailableVoicesCallback = mock(verboseLogging = true)
        val statusCallback: VoiceEntry.OnGetStatusCallback = mock(verboseLogging = true)
        VoiceDownloadProvider.getInstance().get().getAvailableVoiceList(listener)

        val captor = argumentCaptor<List<VoiceEntry>>()
        verify(listener, timeout(10_000L)).onAvailableVoiceList(
            captor.capture(),
            eq(OperationStatus(OperationStatus.Result.Success, ""))
        )

        val voices = captor.firstValue
        assertFalse(voices.isEmpty())

        voices.forEach { voiceEntry ->
            voiceEntry.getStatus(statusCallback)
        }

        // Verify the exact number of invocations
        verify(statusCallback, timeout(10_000L).times(voices.size)).onStatus(any())
    }

    @Test
    fun getPermanentIdTest() {
        val listener: VoiceDownload.AvailableVoicesCallback = mock(verboseLogging = true)
        val statusCallback: VoiceEntry.OnGetPermanentIdCallback = mock(verboseLogging = true)
        VoiceDownloadProvider.getInstance().get().getAvailableVoiceList(listener)

        val captor = argumentCaptor<List<VoiceEntry>>()
        verify(listener, timeout(10_000L)).onAvailableVoiceList(
            captor.capture(),
            eq(OperationStatus(OperationStatus.Result.Success, ""))
        )

        val voices = captor.firstValue
        assertFalse(voices.isEmpty())

        voices.forEach { voiceEntry ->
            voiceEntry.getPermanentId(statusCallback)
        }

        // Verify the exact number of invocations
        verify(statusCallback, timeout(10_000L).times(voices.size)).onPermanentId(anyOrNull())
    }
}