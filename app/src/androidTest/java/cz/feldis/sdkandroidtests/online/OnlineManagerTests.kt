package cz.feldis.sdkandroidtests.online

import com.sygic.sdk.online.OnlineManager
import com.sygic.sdk.online.OnlineManagerProvider
import cz.feldis.sdkandroidtests.BaseTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify

class OnlineManagerTests : BaseTest() {
    private lateinit var onlineManager: OnlineManager

    override fun setUp() {
        super.setUp()
        onlineManager = runBlocking { OnlineManagerProvider.getInstance() }
    }

    override fun tearDown() {
    }

    private fun ensureMapStreamingEnabled() {
        if (!onlineManager.isOnlineMapStreamingEnabled()) {
            val listener: OnlineManager.MapStreamingListener = mock(verboseLogging = true)
            onlineManager.enableOnlineMapStreaming(listener)
            verify(listener, timeout(STATUS_TIMEOUT)).onSuccess()
        }
    }

    private fun ensureMapStreamingDisabled() {
        if (onlineManager.isOnlineMapStreamingEnabled()) {
            val listener: OnlineManager.MapStreamingListener = mock(verboseLogging = true)
            onlineManager.disableOnlineMapStreaming(listener)
            verify(listener, timeout(STATUS_TIMEOUT)).onSuccess()
        }
    }

    /**
     * Enable map streaming onError
     *
     * In this test we enable online map streaming if it is disabled. Then
     * we enable online map streaming and verify that MapStreamingListener
     * onError was invoked with error code MapStreamingError.ModeAlreadyInUse.
     */
    @Test
    fun mapStreamingTestEnableError() {

        if (!onlineManager.isOnlineMapStreamingEnabled()) {
            val listener: OnlineManager.MapStreamingListener = mock(verboseLogging = true)
            onlineManager.enableOnlineMapStreaming(listener)
            Mockito.verify(listener, Mockito.timeout(STATUS_TIMEOUT))
                .onSuccess()
        }

        val listener2: OnlineManager.MapStreamingListener = mock(verboseLogging = true)

        onlineManager.enableOnlineMapStreaming(listener2)

        Mockito.verify(listener2, Mockito.timeout(STATUS_TIMEOUT))
            .onError(eq(OnlineManager.MapStreamingError.ModeAlreadyInUse))

    }

    /**
     * Enable map streaming onSuccess
     *
     * In this test we disable online map streaming if it is enabled. Then
     * we enable online map streaming and verify that on MapStreamingListener was invoked onSuccess.
     */
    @Test
    fun mapStreamingTestEnableSuccess() {

        if (onlineManager.isOnlineMapStreamingEnabled()) {
            val listener: OnlineManager.MapStreamingListener = mock(verboseLogging = true)
            onlineManager.disableOnlineMapStreaming(listener)
            Mockito.verify(listener, timeout(STATUS_TIMEOUT))
                .onSuccess()
        }

        val listener2: OnlineManager.MapStreamingListener = mock(verboseLogging = true)

        onlineManager.enableOnlineMapStreaming(listener2)

        verify(listener2, timeout(STATUS_TIMEOUT))
            .onSuccess()
    }

    /**
     * Disable map streaming onSuccess
     *
     * In this test we enable online map streaming if it is disabled. Then
     * we disable online map streaming and verify that on MapStreamingListener was invoked onSuccess.
     */
    @Test
    fun mapStreamingTestDisableSuccess() {

        if (!onlineManager.isOnlineMapStreamingEnabled()) {
            val listener: OnlineManager.MapStreamingListener = mock(verboseLogging = true)
            onlineManager.enableOnlineMapStreaming(listener)
            verify(listener, timeout(STATUS_TIMEOUT))
                .onSuccess()
        }

        val listener2: OnlineManager.MapStreamingListener = mock(verboseLogging = true)

        onlineManager.disableOnlineMapStreaming(listener2)

        verify(listener2, timeout(STATUS_TIMEOUT))
            .onSuccess()
    }

    /**
     * Disable map streaming onError
     *
     * In this test we disable online map streaming if it is enabled. Then
     * we disable online map streaming and verify that MapStreamingListener
     * onError was invoked with error code MapStreamingError.ModeAlreadyInUse.
     */
    @Test
    fun mapStreamingTestDisableError() {

        if (onlineManager.isOnlineMapStreamingEnabled()) {
            val listener: OnlineManager.MapStreamingListener = mock(verboseLogging = true)
            onlineManager.disableOnlineMapStreaming(listener)
            verify(listener, timeout(STATUS_TIMEOUT))
                .onSuccess()
        }

        val listener2: OnlineManager.MapStreamingListener = mock(verboseLogging = true)

        onlineManager.disableOnlineMapStreaming(listener2)

        verify(listener2, timeout(STATUS_TIMEOUT))
            .onError(eq(OnlineManager.MapStreamingError.ModeAlreadyInUse))
    }

    @Test
    fun mapStreamingEnabledStateAfterEnable() {
        ensureMapStreamingDisabled()
        val listener: OnlineManager.MapStreamingListener = mock(verboseLogging = true)

        onlineManager.enableOnlineMapStreaming(listener)

        verify(listener, timeout(STATUS_TIMEOUT)).onSuccess()
        assertTrue("Online map streaming should be enabled", onlineManager.isOnlineMapStreamingEnabled())
    }

    @Test
    fun mapStreamingDisabledStateAfterDisable() {
        ensureMapStreamingEnabled()
        val listener: OnlineManager.MapStreamingListener = mock(verboseLogging = true)

        onlineManager.disableOnlineMapStreaming(listener)

        verify(listener, timeout(STATUS_TIMEOUT)).onSuccess()
        assertFalse("Online map streaming should be disabled", onlineManager.isOnlineMapStreamingEnabled())
    }

    @Test
    fun mapStreamingEnableDisableRoundTripState() {
        ensureMapStreamingDisabled()
        val enableListener: OnlineManager.MapStreamingListener = mock(verboseLogging = true)
        val disableListener: OnlineManager.MapStreamingListener = mock(verboseLogging = true)

        onlineManager.enableOnlineMapStreaming(enableListener)
        verify(enableListener, timeout(STATUS_TIMEOUT)).onSuccess()
        onlineManager.disableOnlineMapStreaming(disableListener)
        verify(disableListener, timeout(STATUS_TIMEOUT)).onSuccess()

        assertFalse(
            "Online map streaming should be disabled after enable/disable round trip",
            onlineManager.isOnlineMapStreamingEnabled()
        )
    }

    @Test
    fun mapStreamingDisableEnableRoundTripState() {
        ensureMapStreamingEnabled()
        val disableListener: OnlineManager.MapStreamingListener = mock(verboseLogging = true)
        val enableListener: OnlineManager.MapStreamingListener = mock(verboseLogging = true)

        onlineManager.disableOnlineMapStreaming(disableListener)
        verify(disableListener, timeout(STATUS_TIMEOUT)).onSuccess()
        onlineManager.enableOnlineMapStreaming(enableListener)
        verify(enableListener, timeout(STATUS_TIMEOUT)).onSuccess()

        assertTrue(
            "Online map streaming should be enabled after disable/enable round trip",
            onlineManager.isOnlineMapStreamingEnabled()
        )
    }

    @Test
    fun mapStreamingEnableSuccessCallbackCalledOnce() {
        ensureMapStreamingDisabled()
        val listener: OnlineManager.MapStreamingListener = mock(verboseLogging = true)

        onlineManager.enableOnlineMapStreaming(listener)

        Mockito.verify(listener, Mockito.timeout(STATUS_TIMEOUT).times(1)).onSuccess()
    }

    @Test
    fun mapStreamingDisableSuccessCallbackCalledOnce() {
        ensureMapStreamingEnabled()
        val listener: OnlineManager.MapStreamingListener = mock(verboseLogging = true)

        onlineManager.disableOnlineMapStreaming(listener)

        Mockito.verify(listener, Mockito.timeout(STATUS_TIMEOUT).times(1)).onSuccess()
    }

    companion object {
        const val STATUS_TIMEOUT = 4000L
    }
}
