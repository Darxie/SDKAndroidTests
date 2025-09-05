package cz.feldis.sdkandroidtests.online

import com.sygic.sdk.online.OnlineManager
import com.sygic.sdk.online.OnlineManagerProvider
import cz.feldis.sdkandroidtests.BaseTest
import kotlinx.coroutines.runBlocking
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

    companion object {
        const val STATUS_TIMEOUT = 4000L
    }
}