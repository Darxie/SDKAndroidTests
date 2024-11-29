package cz.feldis.sdkandroidtests.voices

import org.mockito.kotlin.*
import com.sygic.sdk.OperationStatus
import com.sygic.sdk.voice.VoiceDownload
import com.sygic.sdk.voice.VoiceDownloadProvider
import com.sygic.sdk.voice.VoiceEntry
import com.sygic.sdk.voice.VoiceManager
import com.sygic.sdk.voice.VoiceManager.InstalledVoicesCallback
import com.sygic.sdk.voice.VoiceManager.OnSetVoiceCallback
import com.sygic.sdk.voice.VoiceManagerProvider
import cz.feldis.sdkandroidtests.BaseTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Ignore
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import timber.log.Timber
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
    @Ignore("Won't retrieve all permanentIds until SDK28")
    fun getPermanentIdOnlineVoice() {
        val listener : VoiceDownload.AvailableVoicesCallback = mock()
        val permanentIdCallback: VoiceEntry.OnGetPermanentIdCallback = mock()
        val voiceListCaptor = argumentCaptor<List<VoiceEntry>>()
        VoiceDownloadProvider.getInstance().get().getAvailableVoiceList(listener)
        verify(listener, timeout(10_000L)).onAvailableVoiceList(voiceListCaptor.capture(), eq(OperationStatus(OperationStatus.Result.Success, "")))
        val voices = voiceListCaptor.allValues.flatten()
        Timber.i("Number of voices: ${voices.size}")

        val latch = CountDownLatch(voices.size)
        val permanentIds = mutableListOf<String>()

        // Mock the callback to collect permanent IDs and log progress
        whenever(permanentIdCallback.onPermanentId(anyString())).thenAnswer { invocation ->
            val permanentId = invocation.getArgument<String>(0)
            synchronized(permanentIds) {
                permanentIds.add(permanentId)
                Timber.i("Progress: ${permanentIds.size}/${voices.size} permanent IDs collected")
            }
            latch.countDown() // Decrement the latch count
        }

        // Call getPermanentId for each voice
        voices.forEach { it.getPermanentId(permanentIdCallback) }

        // Wait for all callbacks to complete
        if (!latch.await(30, TimeUnit.SECONDS)) { // Adjust the timeout as needed
            fail("Timeout waiting for all permanent IDs to be retrieved")
        }

        Timber.i("All Permanent IDs: $permanentIds")
        assertEquals(voices.size, permanentIds.size)
    }
}