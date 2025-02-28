package cz.feldis.sdkandroidtests

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.annotation.CallSuper
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.rule.GrantPermissionRule
import com.sygic.sdk.LoggingSettings
import com.sygic.sdk.MapReaderSettings
import com.sygic.sdk.SygicEngine
import com.sygic.sdk.context.CoreInitException
import com.sygic.sdk.context.SygicContext
import com.sygic.sdk.context.SygicContextInitRequest
import com.sygic.sdk.diagnostics.LogConnector
import com.sygic.sdk.map.data.MapProvider
import com.sygic.sdk.online.OnlineManager
import com.sygic.sdk.online.OnlineManagerProvider
import com.sygic.sdk.online.listeners.SetActiveMapProviderListener
import com.sygic.sdk.position.PositionManager
import com.sygic.sdk.position.PositionManagerProvider
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.rules.TestWatcher
import org.junit.rules.Timeout
import org.junit.runner.Description
import org.mockito.Mockito.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


abstract class BaseTest {
    private val defaultConfig = SygicEngine.JsonConfigBuilder()
    var isEngineInitialized = false
    open lateinit var appContext: Context
    lateinit var sygicContext: SygicContext
    open lateinit var appDataPath: String

    @get:Rule
    var activityRule: ActivityScenarioRule<SygicActivity> =
        ActivityScenarioRule(SygicActivity::class.java)

    @get:Rule
    var permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    @get:Rule
    var watcher: TestRule = object : TestWatcher() {
        override fun starting(description: Description) {
            Log.i("SYGIC_TEST", "Starting test " + description.methodName)
        }

        override fun failed(e: Throwable?, description: Description) {
            Log.e("SYGIC_TEST", "Test " + description.methodName + "finished - FAIL")
        }

        override fun succeeded(description: Description) {
            Log.i("SYGIC_TEST", "Test " + description.methodName + "finished - SUCCESS")
        }
    }

    @get:Rule
    var globalTimeout: Timeout = Timeout.seconds(1200)


    /**
     * Initialization of SDK Sygic Engine
     */
    @Before
    @CallSuper
    open fun setUp() {
        appContext =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        appDataPath = appContext.getExternalFilesDir(null).toString()

        initializeSdk(loadMaps = true)
    }

    @After
    open fun tearDown() {
        sygicContext.destroy()
    }

    private fun initializeSdk(loadMaps: Boolean) {
        val latch = CountDownLatch(1)

        val contextInitRequest = SygicContextInitRequest(
            jsonConfiguration = buildConfig(isUAT = true),
            context = appContext,
            logConnector = object : LogConnector() {},
            loadMaps = loadMaps,
            clearOnlineCache = false
        )

        SygicEngine.initialize(contextInitRequest, object : SygicEngine.OnInitCallback {
            override fun onError(error: CoreInitException) {
                Assert.fail("SDK initialization failed: $error")
                latch.countDown()
            }

            override fun onInstance(instance: SygicContext) {
                sygicContext = instance
                isEngineInitialized = true
                PositionManagerProvider.getInstance().get().openGpsConnection()
                latch.countDown()
            }
        })

        latch.await(30, TimeUnit.SECONDS)
    }

    private fun buildConfig(isUAT: Boolean = true): String {
        val consoleAppenderBuilder =
            LoggingSettings.LoggingItem.AppenderItem.ConsoleAppender.Builder()
                .format("%levshort %datetime %msg\n")
                .level(LoggingSettings.LoggingItem.AppenderItem.LogLevel.TRACE)
                .time("%y/%m/%d %H:%M:%S")
        val diagnosticsAppender =
            LoggingSettings.LoggingItem.AppenderItem.DiagnosticsAppender.Builder()
                .format("%levshort %datetime %msg\n")
                .level(LoggingSettings.LoggingItem.AppenderItem.LogLevel.DEBUG)
                .time("%y/%m/%d %H:%M:%S")
        val loggingItemBuilder = LoggingSettings.LoggingItem.Builder()
            .name("logger")
            .classpath("")
            .addAppender(consoleAppenderBuilder)
            .addAppender(diagnosticsAppender)

        return defaultConfig.apply {
            license(BuildConfig.LICENSE_KEY)
            authentication(BuildConfig.SYGIC_SDK_CLIENT_ID)
            mapReaderSettings().startupOnlineMapsEnabled(true)
            storageFolders().rootPath(appDataPath)
            mapReaderSettings()
                .startupPoiProvider(MapReaderSettings.StartupPoiProvider.CUSTOM_PLACES)
            if (isUAT) {
                online().apply {
                    routingUrl("https://routing-uat.api.sygic.com")
                    sSOServerUrl("https://auth-uat.api.sygic.com")
                    productServer().onlineMapsLinkUrl("https://licensing-uat.api.sygic.com")
                    searchUrl("https://search-uat.api.sygic.com")
                    trafficUrl("https://traffic-uat.api.sygic.com")
                    offlineMapsApiUrl("https://licensing-uat.api.sygic.com")
                    voicesUrl("https://nonttsvoices-testing.api.sygic.com")
                    placesUrl("https://places-uat.api.sygic.com")
                }
            }
            logging {
                addLoggingItem(loggingItemBuilder)
            }
        }.build()
    }

    open fun readJson(filename: String): String {
        return try {
            appContext.assets.open(filename).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            Log.e("SYGIC_TEST", "Error reading JSON file: $filename", e)
            ""
        }
    }

    open fun disableOnlineMaps() {
        val onlineManager = OnlineManagerProvider.getInstance().get()
        if (!onlineManager.isOnlineMapStreamingEnabled()) return

        val listener = mock<OnlineManager.MapStreamingListener>()
        whenever(listener.onSuccess()).then {}

        onlineManager.disableOnlineMapStreaming(listener)

        verify(listener, timeout(5000L)).onSuccess()
    }

    open fun enableOnlineMaps() {
        val onlineManager = OnlineManagerProvider.getInstance().get()
        if (onlineManager.isOnlineMapStreamingEnabled()) return

        val listener = mock<OnlineManager.MapStreamingListener>()
        whenever(listener.onSuccess()).then {}

        onlineManager.enableOnlineMapStreaming(listener)

        verify(listener, timeout(5000L)).onSuccess()
    }

    fun setActiveMapProvider(providerName: String) {
        val listener = mock<SetActiveMapProviderListener>()
        whenever(listener.onActiveProviderSet())

        OnlineManagerProvider.getInstance().get()
            .setActiveMapProvider(MapProvider(providerName), listener)

        verify(listener, timeout(5000L)).onActiveProviderSet()
    }

    open fun startPositionUpdating() {
        val listener = mock<PositionManager.OnOperationComplete>()
        whenever(listener.onComplete())

        PositionManagerProvider.getInstance().get().startPositionUpdating(listener)

        verify(listener, timeout(5000L)).onComplete()
    }

    open fun stopPositionUpdating() {
        val listener = mock<PositionManager.OnOperationComplete>()
        whenever(listener.onComplete())

        PositionManagerProvider.getInstance().get().stopPositionUpdating(listener)

        verify(listener, timeout(5000L)).onComplete()
    }


}