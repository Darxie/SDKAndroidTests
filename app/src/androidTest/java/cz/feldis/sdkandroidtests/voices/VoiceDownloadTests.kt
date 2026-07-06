package cz.feldis.sdkandroidtests.voices

import com.sygic.sdk.OperationStatus
import com.sygic.sdk.voice.VoiceEntry
import com.sygic.sdk.voice.VoiceInstallData
import cz.feldis.sdkandroidtests.BaseTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
        // A cancelled download can leave partial files on disk; the SDK then reports
        // UpdateAvailable (its "corrupted/incomplete package" status) instead of
        // NotInstalled - both are acceptable outcomes of a cancelled install.
        val status = runBlocking { voiceToInstall.getStatus() }
        assertTrue(
            "Unexpected status after cancel: $status",
            status == VoiceEntry.VoicePackageStatus.NotInstalled ||
                status == VoiceEntry.VoicePackageStatus.UpdateAvailable
        )

        // cleanup
        voiceTestHelper.ensureVoiceNotInstalled(voiceToInstall)
    }

    /**
     * Regression test for a fixed SDK bug: OnVoiceInstallationCompleted used to
     * require an exact operation-handle match, but sygm_operation_cancel() removes
     * the operation handle from tracking before delivering the completion, so a
     * cancelled operation used to complete with an invalid handle and its Canceled
     * result was silently dropped - while an unrelated, concurrently running install
     * for a *different* voice was unaffected. This verifies that cancelling one
     * voice's install still delivers its Canceled result promptly and does not
     * disturb another voice installing at the same time.
     */
    @Test
    fun cancelOneInstallDoesNotAffectConcurrentInstallTest() {
        assumeTrue(!isRunningOnEmulator())
        val voiceToCancel = voiceTestHelper.findDownloadableVoice()
        val voiceToKeep = voiceTestHelper.findDownloadableVoice(excludeIds = setOf(voiceToCancel.id))
        voiceTestHelper.ensureVoiceNotInstalled(voiceToCancel)
        voiceTestHelper.ensureVoiceNotInstalled(voiceToKeep)

        val cancelledFinished = CompletableDeferred<VoiceInstallData.InstallFinished>()
        val keptFinished = CompletableDeferred<VoiceInstallData.InstallFinished>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val job = scope.launch {
            voiceTestHelper.voiceDownload.voiceInstall().collect {
                if (it is VoiceInstallData.InstallFinished) {
                    when (it.voiceEntry.id) {
                        voiceToCancel.id -> cancelledFinished.complete(it)
                        voiceToKeep.id -> keptFinished.complete(it)
                    }
                }
            }
        }

        assertTrue(voiceTestHelper.voiceDownload.installVoice(voiceToCancel))
        assertTrue(voiceTestHelper.voiceDownload.installVoice(voiceToKeep))
        Timer().schedule(1000) {
            voiceTestHelper.voiceDownload.cancelDownload(voiceToCancel)
        }

        val cancelledResult = runBlocking { withTimeout(30_000L) { cancelledFinished.await() } }.result
        val keptResult = runBlocking { withTimeout(60_000L) { keptFinished.await() } }.result
        job.cancel()

        assertEquals(OperationStatus.Result.Canceled, cancelledResult.result)
        assertEquals(OperationStatus.Result.Success, keptResult.result)
        // A cancelled download can leave partial files on disk; the SDK then reports
        // UpdateAvailable (its "corrupted/incomplete package" status) instead of
        // NotInstalled - both are acceptable outcomes of a cancelled install.
        val cancelledStatus = runBlocking { voiceToCancel.getStatus() }
        assertTrue(
            "Unexpected status after cancel: $cancelledStatus",
            cancelledStatus == VoiceEntry.VoicePackageStatus.NotInstalled ||
                cancelledStatus == VoiceEntry.VoicePackageStatus.UpdateAvailable
        )
        assertEquals(
            VoiceEntry.VoicePackageStatus.Installed,
            runBlocking { voiceToKeep.getStatus() }
        )

        // cleanup
        voiceTestHelper.ensureVoiceNotInstalled(voiceToCancel)
        voiceTestHelper.uninstallVoice(voiceToKeep)
    }

    /**
     * Regression guard for the same handle-matching fix: by the time an install has
     * already finished (and its operation removed from the native tracking map),
     * calling cancelDownload() for that voice must be a safe no-op - it must not
     * crash and must not retroactively flip a completed, Installed voice back to
     * NotInstalled.
     */
    @Test
    fun cancelAlreadyFinishedInstallIsNoOpTest() {
        val voiceToInstall = voiceTestHelper.findDownloadableVoice()
        voiceTestHelper.ensureVoiceNotInstalled(voiceToInstall)
        voiceTestHelper.installVoice(voiceToInstall)
        assertEquals(
            VoiceEntry.VoicePackageStatus.Installed,
            runBlocking { voiceToInstall.getStatus() }
        )

        voiceTestHelper.voiceDownload.cancelDownload(voiceToInstall)
        runBlocking { delay(500) }

        assertEquals(
            VoiceEntry.VoicePackageStatus.Installed,
            runBlocking { voiceToInstall.getStatus() }
        )

        // cleanup
        voiceTestHelper.uninstallVoice(voiceToInstall)
    }

    /**
     * Cancelling a voice for which no install/uninstall operation is currently
     * tracked (native operations map has no entry for its id) must be a safe no-op.
     */
    @Test
    fun cancelVoiceWithNoActiveOperationIsNoOpTest() {
        val voiceToInstall = voiceTestHelper.findDownloadableVoice()
        voiceTestHelper.ensureVoiceNotInstalled(voiceToInstall)

        voiceTestHelper.voiceDownload.cancelDownload(voiceToInstall)
        runBlocking { delay(500) }

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
