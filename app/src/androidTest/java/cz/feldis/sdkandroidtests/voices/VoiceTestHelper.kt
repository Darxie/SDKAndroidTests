package cz.feldis.sdkandroidtests.voices

import com.sygic.sdk.OperationStatus
import com.sygic.sdk.voice.VoiceDownload
import com.sygic.sdk.voice.VoiceDownloadProvider
import com.sygic.sdk.voice.VoiceEntry
import com.sygic.sdk.voice.VoiceInstallData
import com.sygic.sdk.voice.VoiceManager
import com.sygic.sdk.voice.VoiceManagerProvider
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Helper for voice acceptance tests. Provides discovery of downloadable voices and
 * synchronous wrappers around the install/uninstall flow so tests don't have to
 * deal with the underlying coroutine Flow machinery directly.
 */
class VoiceTestHelper {

    val voiceManager: VoiceManager = runBlocking { VoiceManagerProvider.getInstance() }
    val voiceDownload: VoiceDownload = runBlocking { VoiceDownloadProvider.getInstance() }

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    fun getAvailableVoices(): List<VoiceEntry> = runBlocking {
        val result = voiceDownload.getAvailableVoices()
        assertEquals(
            "Failed to get available voices: ${result.result}",
            OperationStatus.Result.Success,
            result.result.result
        )
        assertTrue("Available voices list is empty", result.voiceList.isNotEmpty())
        result.voiceList
    }

    fun getInstalledVoices(): List<VoiceEntry> = runBlocking {
        val result = voiceManager.getInstalledVoices()
        assertEquals(
            "Failed to get installed voices: ${result.result}",
            OperationStatus.Result.Success,
            result.result.result
        )
        result.voiceList
    }

    /**
     * Finds a non-tts (downloadable) voice which is not currently installed, so it is
     * safe to use in install/uninstall tests. [excludeIds] can be used to avoid picking
     * the same voice as another concurrently running test.
     */
    fun findDownloadableVoice(
        voices: List<VoiceEntry> = getAvailableVoices(),
        excludeIds: Set<String> = emptySet()
    ): VoiceEntry {
        val candidate = voices.firstOrNull {
            !it.isTts && it.id !in excludeIds &&
                runBlocking { it.getStatus() } == VoiceEntry.VoicePackageStatus.NotInstalled
        }
        return requireNotNull(candidate) {
            "No downloadable (non-installed, non-tts) voice found for testing among ${voices.size} voices"
        }
    }

    fun findTtsVoice(voices: List<VoiceEntry> = getAvailableVoices()): VoiceEntry {
        val candidate = voices.firstOrNull { it.isTts }
        return requireNotNull(candidate) { "No tts voice found among ${voices.size} voices" }
    }

    fun ensureVoiceNotInstalled(voiceEntry: VoiceEntry) = runBlocking {
        if (voiceEntry.getStatus() == VoiceEntry.VoicePackageStatus.NotInstalled) {
            return@runBlocking
        }
        uninstallVoice(voiceEntry)
    }

    /**
     * Installs [voiceEntry] and suspends until the install operation finishes
     * (or times out), returning the resulting [OperationStatus].
     */
    fun installVoice(voiceEntry: VoiceEntry, timeoutMs: Long = 60_000L): OperationStatus = runBlocking {
        val installFinished = CompletableDeferred<VoiceInstallData.InstallFinished>()
        val job = scope.launch {
            voiceDownload.voiceInstall().collect {
                if (it is VoiceInstallData.InstallFinished && it.voiceEntry.id == voiceEntry.id) {
                    installFinished.complete(it)
                }
            }
        }
        assertTrue(
            "installVoice request was rejected for ${voiceEntry.id}",
            voiceDownload.installVoice(voiceEntry)
        )
        val result = withTimeout(timeoutMs) { installFinished.await() }.result
        job.cancel()
        result
    }

    /**
     * Uninstalls [voiceEntry] and suspends until the uninstall operation finishes
     * (or times out), returning the resulting [OperationStatus].
     */
    fun uninstallVoice(voiceEntry: VoiceEntry, timeoutMs: Long = 30_000L): OperationStatus = runBlocking {
        val uninstallFinished = CompletableDeferred<VoiceInstallData.UninstallFinished>()
        val job = scope.launch {
            voiceDownload.voiceInstall().collect {
                if (it is VoiceInstallData.UninstallFinished && it.voiceEntry.id == voiceEntry.id) {
                    uninstallFinished.complete(it)
                }
            }
        }
        assertTrue(
            "uninstallVoice request was rejected for ${voiceEntry.id}",
            voiceDownload.uninstallVoice(voiceEntry)
        )
        val result = withTimeout(timeoutMs) { uninstallFinished.await() }.result
        job.cancel()
        result
    }

    fun waitForStatus(
        voiceEntry: VoiceEntry,
        expected: VoiceEntry.VoicePackageStatus,
        timeoutMs: Long = 30_000L
    ) = runBlocking {
        withTimeout(timeoutMs) {
            while (voiceEntry.getStatus() != expected) {
                delay(300)
            }
        }
    }

    /**
     * Sets [voiceEntry] as the active voice and suspends until [VoiceManager.onSetVoice]
     * reports a result for it.
     */
    fun setVoiceAndAwait(voiceEntry: VoiceEntry, timeoutMs: Long = 15_000L): VoiceManager.SetVoiceResult =
        runBlocking {
            val setVoiceResult = CompletableDeferred<VoiceManager.SetVoiceResult>()
            val job = scope.launch {
                voiceManager.onSetVoice().collect {
                    if (it.id == voiceEntry.id) {
                        setVoiceResult.complete(it)
                    }
                }
            }
            voiceManager.setVoice(voiceEntry)
            val result = withTimeout(timeoutMs) { setVoiceResult.await() }
            job.cancel()
            result
        }

    /**
     * Plays a sample of [voiceEntry] and suspends until [VoiceManager.onPlaySample]
     * reports a result for it.
     */
    fun playSampleAndAwait(voiceEntry: VoiceEntry, timeoutMs: Long = 15_000L): VoiceManager.PlaySampleResult =
        runBlocking {
            val playSampleResult = CompletableDeferred<VoiceManager.PlaySampleResult>()
            val job = scope.launch {
                voiceManager.onPlaySample().collect {
                    if (it.id == voiceEntry.id) {
                        playSampleResult.complete(it)
                    }
                }
            }
            voiceEntry.playSample()
            val result = withTimeout(timeoutMs) { playSampleResult.await() }
            job.cancel()
            result
        }
}
