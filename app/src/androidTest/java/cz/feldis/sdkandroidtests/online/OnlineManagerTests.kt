package cz.feldis.sdkandroidtests.online

import com.nhaarman.mockitokotlin2.*
import com.sygic.sdk.online.OnlineManager
import com.sygic.sdk.online.OnlineManagerProvider
import cz.feldis.sdkandroidtests.BaseTest
import org.junit.Test
import org.mockito.Mockito

class OnlineManagerTests : BaseTest() {
    private lateinit var mOnlineManager: OnlineManager

    override fun setUp() {
        super.setUp()
        mOnlineManager = OnlineManagerProvider.getInstance().get()
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

        if (!mOnlineManager.isOnlineMapStreamingEnabled()) {
            val listener: OnlineManager.MapStreamingListener = mock(verboseLogging = true)
            mOnlineManager.enableOnlineMapStreaming(listener)
            Mockito.verify(listener, Mockito.timeout(STATUS_TIMEOUT))
                .onSuccess()
            mOnlineManager.removeMapFlagSettingErrorListener(listener)
        }

        val listener2: OnlineManager.MapStreamingListener = mock(verboseLogging = true)

        mOnlineManager.enableOnlineMapStreaming(listener2)

        Mockito.verify(listener2, Mockito.timeout(STATUS_TIMEOUT))
            .onError(eq(OnlineManager.MapStreamingError.ModeAlreadyInUse))

        mOnlineManager.removeMapFlagSettingErrorListener(listener2)
    }

    /**
     * Enable map streaming onSuccess
     *
     * In this test we disable online map streaming if it is enabled. Then
     * we enable online map streaming and verify that on MapStreamingListener was invoked onSuccess.
     */
    @Test
    fun mapStreamingTestEnableSuccess() {

        if (mOnlineManager.isOnlineMapStreamingEnabled()) {
            val listener: OnlineManager.MapStreamingListener = mock(verboseLogging = true)
            mOnlineManager.disableOnlineMapStreaming(listener)
            Mockito.verify(listener, timeout(STATUS_TIMEOUT))
                .onSuccess()
        }

        val listener2: OnlineManager.MapStreamingListener = mock(verboseLogging = true)

        mOnlineManager.enableOnlineMapStreaming(listener2)

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

        if (!mOnlineManager.isOnlineMapStreamingEnabled()) {
            val listener: OnlineManager.MapStreamingListener = mock(verboseLogging = true)
            mOnlineManager.enableOnlineMapStreaming(listener)
            verify(listener, timeout(STATUS_TIMEOUT))
                .onSuccess()
        }

        val listener2: OnlineManager.MapStreamingListener = mock(verboseLogging = true)

        mOnlineManager.disableOnlineMapStreaming(listener2)

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

        if (mOnlineManager.isOnlineMapStreamingEnabled()) {
            val listener: OnlineManager.MapStreamingListener = mock(verboseLogging = true)
            mOnlineManager.disableOnlineMapStreaming(listener)
            verify(listener, timeout(STATUS_TIMEOUT))
                .onSuccess()
        }

        val listener2: OnlineManager.MapStreamingListener = mock(verboseLogging = true)

        mOnlineManager.disableOnlineMapStreaming(listener2)

        verify(listener2, timeout(STATUS_TIMEOUT))
            .onError(eq(OnlineManager.MapStreamingError.ModeAlreadyInUse))
    }

    companion object {
        const val STATUS_TIMEOUT = 4000L
    }
}