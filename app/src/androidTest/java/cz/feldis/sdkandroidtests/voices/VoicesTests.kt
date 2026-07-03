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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.*

class VoicesTests : BaseTest() {

    private lateinit var voicesManager: VoiceManager

    override fun setUp() {
        super.setUp()
        voicesManager = runBlocking { VoiceManagerProvider.getInstance() }
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
        runBlocking { VoiceDownloadProvider.getInstance() }
            .getAvailableVoiceList(listener)

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
        runBlocking { VoiceDownloadProvider.getInstance() }.getAvailableVoiceList(listener)

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

    @Test
    fun getVoiceReturnsCurrentlySetVoiceTest() {
        val helper = VoiceTestHelper()
        val installedVoices = helper.getInstalledVoices()
        val voiceToSet = installedVoices.first()

        val setResult = helper.setVoiceAndAwait(voiceToSet)
        assertEquals(VoiceManager.SetVoiceResult(voiceToSet.id, true), setResult)

        val currentVoice = runBlocking { voicesManager.getVoice() }
        assertEquals(voiceToSet.id, currentVoice.id)
    }

    /**
     * The native SDK only rejects setVoice() for ids unknown to its voice catalog. A
     * known catalog voice that is merely not installed yet is still accepted
     * (onSetVoice reports success=true), even though getVoice() may not reflect it
     * as the active voice since the voice data isn't actually present on disk.
     */
    @Test
    fun setNotInstalledVoiceStillSucceedsTest() {
        val helper = VoiceTestHelper()
        val notInstalledVoice = helper.findDownloadableVoice()
        helper.ensureVoiceNotInstalled(notInstalledVoice)

        val setResult = helper.setVoiceAndAwait(notInstalledVoice)
        assertEquals(VoiceManager.SetVoiceResult(notInstalledVoice.id, true), setResult)
    }

    @Test
    fun setTtsVoiceTest() {
        val helper = VoiceTestHelper()
        val ttsVoice = helper.findTtsVoice(helper.getInstalledVoices())

        val setResult = helper.setVoiceAndAwait(ttsVoice)
        assertEquals(VoiceManager.SetVoiceResult(ttsVoice.id, true), setResult)

        val currentVoice = runBlocking { voicesManager.getVoice() }
        assertEquals(ttsVoice.id, currentVoice.id)
        assertTrue(currentVoice.isTts)
    }

    @Test
    fun playSampleTest() {
        val helper = VoiceTestHelper()
        val installedVoice = helper.getInstalledVoices().first()

        val result = helper.playSampleAndAwait(installedVoice)
        assertEquals(VoiceManager.PlaySampleResult(installedVoice.id, true), result)
    }

    /**
     * Similar to setVoice(), the native SDK only rejects playSample() for ids unknown
     * to its voice catalog. A known catalog voice that isn't installed yet is still
     * accepted and played.
     */
    @Test
    fun playSampleOfNotInstalledVoiceStillSucceedsTest() {
        val helper = VoiceTestHelper()
        val notInstalledVoice = helper.findDownloadableVoice()
        helper.ensureVoiceNotInstalled(notInstalledVoice)

        val result = helper.playSampleAndAwait(notInstalledVoice)
        assertEquals(VoiceManager.PlaySampleResult(notInstalledVoice.id, true), result)
    }

    @Test
    fun getDefaultTtsLocaleTest() {
        val locale = runBlocking { voicesManager.getDefaultTtsLocale() }
        // Per docs, an empty string is returned only on API < 21 or when there is no
        // default locale for the used TTS Engine. On modern devices (minSdk 29) we
        // expect a locale-shaped, non-empty string (e.g. "en_US").
        assertTrue(
            "Unexpected default TTS locale format: '$locale'",
            locale.isEmpty() || locale.matches(Regex("^[a-zA-Z]{2,3}([_-][a-zA-Z0-9]+)*$"))
        )
    }

    @Test
    fun installedVoicesConsistentWithAvailableVoicesTest() {
        val helper = VoiceTestHelper()
        val installedVoices = helper.getInstalledVoices()
        val availableVoices = helper.getAvailableVoices()

        assertFalse(installedVoices.isEmpty())
        installedVoices.forEach { installedVoice ->
            val matching = availableVoices.find { it.id == installedVoice.id }
            assertNotNull(
                "Installed voice ${installedVoice.id} was not found in the available voices catalog",
                matching
            )
            assertEquals(installedVoice.isTts, matching!!.isTts)
            assertEquals(installedVoice.language, matching.language)

            val permanentId = runBlocking { installedVoice.getPermanentId() }
            assertFalse(
                "Installed voice ${installedVoice.id} should have a non-empty permanent id",
                permanentId.isNullOrEmpty()
            )
        }
    }

    @Test
    fun voiceEntryPropertiesValidTest() {
        val helper = VoiceTestHelper()
        val installedVoices = helper.getInstalledVoices()
        assertFalse(installedVoices.isEmpty())

        installedVoices.forEach { voiceEntry ->
            assertFalse("Voice id should not be blank", voiceEntry.id.isBlank())
            assertFalse("Voice name should not be blank", voiceEntry.name.isBlank())
            assertFalse("Voice language should not be blank", voiceEntry.language.isBlank())
            assertTrue(
                "Voice sizeOnDisk should not be negative for ${voiceEntry.id}",
                voiceEntry.sizeOnDisk >= 0
            )
            assertTrue(
                "Voice gender should be a known enum value for ${voiceEntry.id}",
                voiceEntry.gender in VoiceEntry.VoiceGender.entries
            )
        }
    }
}