package cz.feldis.sdkandroidtests.voices

import com.sygic.sdk.OperationStatus
import com.sygic.sdk.voice.VoiceEntry
import com.sygic.sdk.voice.VoiceInstallData
import cz.feldis.sdkandroidtests.BaseTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.Timer
import kotlin.concurrent.schedule

class VoiceDownloadTests : BaseTest() {

    private lateinit var voiceTestHelper: VoiceTestHelper

    override fun setUp() {
        super.setUp()
        voiceTestHelper = VoiceTestHelper()
    }

    @Test
    fun installVoiceAndVerifyInstalledTest() {
        val voiceToInstall = voiceTestHelper.findDownloadableVoice()
        voiceTestHelper.ensureVoiceNotInstalled(voiceToInstall)

        val result = voiceTestHelper.installVoice(voiceToInstall)
        assertEquals(OperationStatus.Result.Success, result.result)

        val status = runBlocking { voiceToInstall.getStatus() }
        assertEquals(VoiceEntry.VoicePackageStatus.Installed, status)

        val installedVoices = voiceTestHelper.getInstalledVoices()
        assertTrue(installedVoices.any { it.id == voiceToInstall.id })

        // cleanup
        voiceTestHelper.uninstallVoice(voiceToInstall)
    }

    @Test
    fun uninstallVoiceTest() {
        val voiceToInstall = voiceTestHelper.findDownloadableVoice()
        voiceTestHelper.ensureVoiceNotInstalled(voiceToInstall)
        voiceTestHelper.installVoice(voiceToInstall)
        assertEquals(
            VoiceEntry.VoicePackageStatus.Installed,
            runBlocking { voiceToInstall.getStatus() }
        )

        val result = voiceTestHelper.uninstallVoice(voiceToInstall)
        assertEquals(OperationStatus.Result.Success, result.result)

        val status = runBlocking { voiceToInstall.getStatus() }
        assertEquals(VoiceEntry.VoicePackageStatus.NotInstalled, status)

        val installedVoices = voiceTestHelper.getInstalledVoices()
        assertFalse(installedVoices.any { it.id == voiceToInstall.id })
    }

    /**
     * Cancelling immediately after issuing the install request is flaky: on fast
     * connections/emulators the download can finish before the cancel request is
     * processed. We therefore delay the cancel slightly (mirroring
     * MapDownloadTests.installCancelTest) and skip the test on emulators, whose
     * networking/timing characteristics make it unreliable.
     */
    @Test
    fun cancelVoiceDownloadTest() {
        assumeTrue(!isRunningOnEmulator())
        val voiceToInstall = voiceTestHelper.findDownloadableVoice()
        voiceTestHelper.ensureVoiceNotInstalled(voiceToInstall)

        val installFinished = CompletableDeferred<VoiceInstallData.InstallFinished>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val job = scope.launch {
            voiceTestHelper.voiceDownload.voiceInstall().collect {
                if (it is VoiceInstallData.InstallFinished && it.voiceEntry.id == voiceToInstall.id) {
                    installFinished.complete(it)
                }
            }
        }

        assertTrue(voiceTestHelper.voiceDownload.installVoice(voiceToInstall))
        Timer().schedule(1000) {
            voiceTestHelper.voiceDownload.cancelDownload(voiceToInstall)
        }

        val result = runBlocking { withTimeout(30_000L) { installFinished.await() } }.result
        job.cancel()

        assertEquals(OperationStatus.Result.Canceled, result.result)
        assertEquals(
            VoiceEntry.VoicePackageStatus.NotInstalled,
            runBlocking { voiceToInstall.getStatus() }
        )
    }

    @Test
    fun installAlreadyInstalledVoiceTest() {
        val voiceToInstall = voiceTestHelper.findDownloadableVoice()
        voiceTestHelper.ensureVoiceNotInstalled(voiceToInstall)
        voiceTestHelper.installVoice(voiceToInstall)

        // installing an already installed voice should not fail/crash and should
        // still report a finished operation
        val result = voiceTestHelper.installVoice(voiceToInstall)
        assertEquals(OperationStatus.Result.Success, result.result)
        assertEquals(
            VoiceEntry.VoicePackageStatus.Installed,
            runBlocking { voiceToInstall.getStatus() }
        )

        // cleanup
        voiceTestHelper.uninstallVoice(voiceToInstall)
    }

    @Test
    fun uninstallNotInstalledVoiceTest() {
        val voiceToInstall = voiceTestHelper.findDownloadableVoice()
        voiceTestHelper.ensureVoiceNotInstalled(voiceToInstall)

        // Uninstalling a voice that is not installed is treated as a no-op by the
        // native SDK: the request is accepted and reported as a successful finish,
        // it must simply not change/break the (already NotInstalled) status.
        val result = voiceTestHelper.uninstallVoice(voiceToInstall)
        assertEquals(OperationStatus.Result.Success, result.result)
        assertEquals(
            VoiceEntry.VoicePackageStatus.NotInstalled,
            runBlocking { voiceToInstall.getStatus() }
        )
    }

    @Test
    fun voiceStatusTransitionTest() {
        val voiceToInstall = voiceTestHelper.findDownloadableVoice()
        voiceTestHelper.ensureVoiceNotInstalled(voiceToInstall)
        assertEquals(
            VoiceEntry.VoicePackageStatus.NotInstalled,
            runBlocking { voiceToInstall.getStatus() }
        )

        val installResult = voiceTestHelper.installVoice(voiceToInstall)
        assertEquals(OperationStatus.Result.Success, installResult.result)
        assertEquals(
            VoiceEntry.VoicePackageStatus.Installed,
            runBlocking { voiceToInstall.getStatus() }
        )

        val uninstallResult = voiceTestHelper.uninstallVoice(voiceToInstall)
        assertEquals(OperationStatus.Result.Success, uninstallResult.result)
        assertEquals(
            VoiceEntry.VoicePackageStatus.NotInstalled,
            runBlocking { voiceToInstall.getStatus() }
        )
    }
}
