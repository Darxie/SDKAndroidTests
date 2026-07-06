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
     * Regression test for a fixed SDK bug: setVoice() used to only check that the id
     * exists in the in-memory voice catalog (VoiceManager::FindVoicePackage) and then
     * unconditionally record the audio system's voice preference (SetVoicePackage ->
     * AudioServiceLocator::SetVoice), without checking whether the package's files
     * were present on disk. For a catalog-only (not-yet-installed) entry, the
     * underlying native AudioVoice data is empty/default, so onSetVoice used to report
     * success=true even though no real, playable voice was actually applied - callers
     * relying on success=true as proof of a usable voice could be misled (e.g.
     * navigation guidance could silently end up with no/broken audio).
     *
     * The SDK now checks the package's actual installation status before applying it,
     * so setVoice() on a not-installed voice correctly reports success=false.
     */
    @Test
    fun setVoiceOnNotInstalledVoiceFailsTest() {
        val helper = VoiceTestHelper()
        val notInstalledVoice = helper.findDownloadableVoice()
        helper.ensureVoiceNotInstalled(notInstalledVoice)

        val setResult = helper.setVoiceAndAwait(notInstalledVoice)
        assertEquals(VoiceManager.SetVoiceResult(notInstalledVoice.id, false), setResult)
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
     * Regression test for a fixed SDK bug: same underlying cause as
     * setVoiceOnNotInstalledVoiceFailsTest. playSample() used to only check that the
     * id exists in the in-memory voice catalog (VoiceManager::FindVoicePackage). For a
     * catalog-only (not-yet-installed) entry the native AudioVoice data is
     * empty/default, so sygm_voice_play_sample() used to call audio.PlaySample() with
     * empty/invalid sample data yet unconditionally reported success (onPlaySample
     * success=true) as long as the id was found - regardless of whether any audio was
     * actually produced. This could mislead a "preview a voice before downloading it"
     * UX into believing playback succeeded.
     *
     * The SDK now checks the package's actual installation status before playing it,
     * so playSample() on a not-installed voice correctly reports success=false.
     */
    @Test
    fun playSampleOfNotInstalledVoiceFailsTest() {
        val helper = VoiceTestHelper()
        val notInstalledVoice = helper.findDownloadableVoice()
        helper.ensureVoiceNotInstalled(notInstalledVoice)

        val result = helper.playSampleAndAwait(notInstalledVoice)
        assertEquals(VoiceManager.PlaySampleResult(notInstalledVoice.id, false), result)
    }

    /**
     * Confirms the installation-status check the SDK fix added is a live check
     * (re-evaluated on every call), not a one-time/cached verdict: setVoice() must
     * keep failing for a not-installed voice, but start succeeding for that very
     * same voice as soon as it becomes installed.
     */
    @Test
    fun setVoiceSucceedsOnceVoiceBecomesInstalledTest() {
        val helper = VoiceTestHelper()
        val voice = helper.findDownloadableVoice()
        helper.ensureVoiceNotInstalled(voice)

        val failedResult = helper.setVoiceAndAwait(voice)
        assertEquals(VoiceManager.SetVoiceResult(voice.id, false), failedResult)

        helper.installVoice(voice)

        val succeededResult = helper.setVoiceAndAwait(voice)
        assertEquals(VoiceManager.SetVoiceResult(voice.id, true), succeededResult)

        val currentVoice = runBlocking { voicesManager.getVoice() }
        assertEquals(voice.id, currentVoice.id)

        // cleanup
        helper.uninstallVoice(voice)
    }

    /**
     * The whole point of the SDK fix is to protect the currently active voice from
     * being silently replaced by a non-functional one: a failed setVoice() attempt
     * (e.g. against a not-installed voice) must leave the previously active,
     * genuinely playable voice untouched - navigation guidance must not end up with
     * no/broken audio just because some other code path attempted to switch to an
     * unavailable voice.
     */
    @Test
    fun currentVoiceUnchangedAfterFailedSetVoiceTest() {
        val helper = VoiceTestHelper()
        val goodVoice = helper.getInstalledVoices().first()
        val notInstalledVoice = helper.findDownloadableVoice(excludeIds = setOf(goodVoice.id))
        helper.ensureVoiceNotInstalled(notInstalledVoice)

        val setGoodResult = helper.setVoiceAndAwait(goodVoice)
        assertEquals(VoiceManager.SetVoiceResult(goodVoice.id, true), setGoodResult)

        val failedResult = helper.setVoiceAndAwait(notInstalledVoice)
        assertEquals(VoiceManager.SetVoiceResult(notInstalledVoice.id, false), failedResult)

        val currentVoice = runBlocking { voicesManager.getVoice() }
        assertEquals(goodVoice.id, currentVoice.id)
    }

    /**
     * Confirms the fix applies dynamically, not just to voices that were never
     * installed: setVoice()/playSample() must succeed while a voice is installed,
     * and correctly start failing again for that same voice once it is uninstalled.
     */
    @Test
    fun setVoiceAndPlaySampleFailAfterUninstallTest() {
        val helper = VoiceTestHelper()
        val voice = helper.findDownloadableVoice()
        helper.ensureVoiceNotInstalled(voice)
        helper.installVoice(voice)

        assertEquals(VoiceManager.SetVoiceResult(voice.id, true), helper.setVoiceAndAwait(voice))
        assertEquals(VoiceManager.PlaySampleResult(voice.id, true), helper.playSampleAndAwait(voice))

        helper.uninstallVoice(voice)

        assertEquals(VoiceManager.SetVoiceResult(voice.id, false), helper.setVoiceAndAwait(voice))
        assertEquals(VoiceManager.PlaySampleResult(voice.id, false), helper.playSampleAndAwait(voice))
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
            // TTS/system voices come from the device's TTS engine, not from the online
            // downloadable-voices catalog, so only non-tts (downloaded) voices are
            // expected to appear in getAvailableVoices().
            if (!installedVoice.isTts) {
                val matching = availableVoices.find { it.id == installedVoice.id }
                assertNotNull(
                    "Installed voice ${installedVoice.id} was not found in the available voices catalog",
                    matching
                )
                assertEquals(installedVoice.isTts, matching!!.isTts)
                assertEquals(installedVoice.language, matching.language)
            }

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