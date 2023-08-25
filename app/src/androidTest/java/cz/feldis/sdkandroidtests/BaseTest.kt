package cz.feldis.sdkandroidtests

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.annotation.CallSuper
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.rule.GrantPermissionRule
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.timeout
import com.nhaarman.mockitokotlin2.verify
import com.sygic.sdk.LoggingSettings
import com.sygic.sdk.MapReaderSettings
import com.sygic.sdk.SygicEngine
import com.sygic.sdk.SygicEngine.initialize
import com.sygic.sdk.context.CoreInitException
import com.sygic.sdk.context.SygicContext
import com.sygic.sdk.context.SygicContextInitRequest
import com.sygic.sdk.diagnostics.LogConnector
import com.sygic.sdk.map.data.MapProvider
import com.sygic.sdk.online.OnlineManager
import com.sygic.sdk.online.OnlineManagerProvider
import com.sygic.sdk.online.data.MapProviderError
import com.sygic.sdk.online.listeners.SetActiveMapProviderListener
import com.sygic.sdk.position.PositionManagerProvider
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


abstract class BaseTest {
    private val defaultConfig = SygicEngine.JsonConfigBuilder()
    var isEngineInitialized = false
    private lateinit var appContext: Context
    lateinit var sygicContext: SygicContext
    lateinit var appDataPath: String
    private lateinit var logConnector: LogConnector

    @get:Rule
    var mActivityRule: ActivityScenarioRule<SygicActivity> =
        ActivityScenarioRule(SygicActivity::class.java)

    @get:Rule
    var mGrantPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    @get:Rule
    var mWatcher: TestRule = object : TestWatcher() {
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

    /**
     * Initialization of SDK Sygic Engine
     */
    @Before
    @CallSuper
    open fun setUp() {
        appContext =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        appDataPath = appContext.getExternalFilesDir(null).toString()
        val latch = CountDownLatch(1)
        this.isEngineInitialized = false
        val jsonConfig = buildUATConfig(true)

        logConnector = object : LogConnector() {
            override fun onLogReceived(message: String, logLevel: LogLevel) {
            }
        }

        val contextInitRequest = SygicContextInitRequest(
            jsonConfig,
            appContext,
            tracking = null,
            logConnector = logConnector,
            loadMaps = true,
            clearOnlineCache = false
        )
        initialize(contextInitRequest, object : SygicEngine.OnInitCallback {
            override fun onError(error: CoreInitException) {
                latch.countDown()
                Assert.fail("sdk init FAILED with error $error")
            }

            override fun onInstance(instance: SygicContext) {
                isEngineInitialized = true
                sygicContext = instance
                PositionManagerProvider.getInstance().get().startPositionUpdating()
                OnlineManagerProvider.getInstance().get().setActiveMapProvider(
                    MapProvider("ta"), object : SetActiveMapProviderListener {
                        override fun onActiveProviderSet() {
                        }

                        override fun onError(error: MapProviderError) {
                            assert(true)
                        }
                    }
                )
                latch.countDown()
            }
        })
        latch.await(30, TimeUnit.SECONDS)
    }

    @After
    open fun tearDown() {
        //sygicContext.destroy()
    }

    private fun buildUATConfig(onlineMaps: Boolean): String {
        defaultConfig.license(BuildConfig.LICENSE_KEY)
        defaultConfig.mapReaderSettings().startupOnlineMapsEnabled(onlineMaps)
        defaultConfig.storageFolders().rootPath(appDataPath)

        defaultConfig.mapReaderSettings()
            .startupPoiProvider(MapReaderSettings.StartupPoiProvider.CUSTOM_PLACES)

        defaultConfig.authentication(BuildConfig.SYGIC_SDK_CLIENT_ID)
        defaultConfig.online().routingUrl("https://routing-uat.api.sygic.com")
        defaultConfig.online().sSOServerUrl("https://auth-uat.api.sygic.com")
        defaultConfig.online().productServer()
            .onlineMapsLinkUrl("https://licensing-uat.api.sygic.com")
        defaultConfig.online().searchUrl("https://search-uat.api.sygic.com")
        defaultConfig.online().speedCameras().url("https://incidents-testing.api.sygic.com")
        defaultConfig.online().incidents()
            .url("https://incidents-testing.api.sygic.com")
        defaultConfig.online().trafficUrl("https://traffic-uat.api.sygic.com")
        defaultConfig.online()
            .offlineMapsApiUrl("https://licensing-uat.api.sygic.com")
        defaultConfig.online().voicesUrl("https://nonttsvoices-testing.api.sygic.com")
        defaultConfig.online().placesUrl("https://places-uat.api.sygic.com")

        val consoleAppenderBuilder =
            LoggingSettings.LoggingItem.AppenderItem.ConsoleAppender.Builder()
                .format("%levshort %datetime %msg\n")
                .level(LoggingSettings.LoggingItem.AppenderItem.LogLevel.TRACE)
                .time("%y/%m/%d %H:%M:%S")
        val loggingItemBuilder = LoggingSettings.LoggingItem.Builder()
            .name("logger")
            .addAppender(consoleAppenderBuilder)
        defaultConfig.logging {
            addLoggingItem(loggingItemBuilder)
        }
        return defaultConfig.build()
    }

    private fun buildProductionConfig(): String {
        defaultConfig.mapReaderSettings().startupOnlineMapsEnabled(true)
        val path = appContext.getExternalFilesDir(null).toString()
        defaultConfig.storageFolders().rootPath(path)
        defaultConfig.authentication(BuildConfig.SYGIC_SDK_CLIENT_ID)
        defaultConfig.license(BuildConfig.LICENSE_KEY)
        return defaultConfig.build()
    }

    open fun readJson(filename: String): String {
        lateinit var jsonString: String
        try {
            jsonString = appContext.assets.open(filename)
                .bufferedReader()
                .use { it.readText() }
        } catch (_: IOException) {
            assert(true)
        }
        return jsonString
    }

    open fun disableOnlineMaps() {
        if (!OnlineManagerProvider.getInstance().get().isOnlineMapStreamingEnabled()) {
            val listener: OnlineManager.MapStreamingListener = mock(verboseLogging = true)
            OnlineManagerProvider.getInstance().get().enableOnlineMapStreaming(listener)
            verify(listener, timeout(5_000L))
                .onSuccess()
        }

        val listener2: OnlineManager.MapStreamingListener = mock(verboseLogging = true)

        OnlineManagerProvider.getInstance().get().disableOnlineMapStreaming(listener2)

        verify(listener2, timeout(5_000L))
            .onSuccess()
    }
}