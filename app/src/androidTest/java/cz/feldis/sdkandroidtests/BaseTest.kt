package cz.feldis.sdkandroidtests

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.annotation.CallSuper
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.rule.GrantPermissionRule
import com.sygic.sdk.LoggingSettings

import com.sygic.sdk.SygicEngine
import com.sygic.sdk.SygicEngine.initialize
import com.sygic.sdk.context.CoreInitException
import com.sygic.sdk.context.SygicContext
import com.sygic.sdk.context.SygicContextInitRequest
import com.sygic.sdk.diagnostics.LogConnector
import com.sygic.sdk.position.PositionManagerProvider

import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


abstract class BaseTest {
    private val defaultConfig = SygicEngine.JsonConfigBuilder()
    var isEngineInitialized = false
    lateinit var appContext: Context
    lateinit var sygicContext: SygicContext
    lateinit var logConnector: LogConnector

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
        this@BaseTest.appContext =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val latch = CountDownLatch(1)
        this.isEngineInitialized = false
        val jsonConfig = buildUATConfig(true)

        logConnector = object : LogConnector() {
            override fun onLogReceived(message: String, logLevel: LogLevel) {
            }
        }

        val contextInitRequest = SygicContextInitRequest(
            jsonConfig,
            this@BaseTest.appContext,
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
                this@BaseTest.isEngineInitialized = true
                this@BaseTest.sygicContext = instance
                PositionManagerProvider.getInstance().get().startPositionUpdating()
                latch.countDown()
            }
        })
        latch.await(30, TimeUnit.SECONDS)
    }

    @After
    open fun tearDown() {
        this@BaseTest.sygicContext.destroy()
    }

    private fun buildUATConfig(onlineMaps: Boolean): String {
        this@BaseTest.defaultConfig.license(BuildConfig.LICENSE_KEY)
        this@BaseTest.defaultConfig.mapReaderSettings().startupOnlineMapsEnabled(onlineMaps)
        val path = appContext.getExternalFilesDir(null).toString()
        this@BaseTest.defaultConfig.storageFolders().rootPath(path)
        this@BaseTest.defaultConfig.authentication(BuildConfig.SYGIC_SDK_CLIENT_ID)
        this@BaseTest.defaultConfig.online().routingUrl("https://routing-uat.api.sygic.com")
        this@BaseTest.defaultConfig.online().sSOServerUrl("https://auth-uat.api.sygic.com")
        this@BaseTest.defaultConfig.online().productServer()
            .onlineMapsLinkUrl("https://licensing-uat.api.sygic.com")
        this@BaseTest.defaultConfig.online().searchUrl("https://search-uat.api.sygic.com")
        this@BaseTest.defaultConfig.online().incidents()
            .url("https://incidents-testing.api.sygic.com")
        this@BaseTest.defaultConfig.online().trafficUrl("https://traffic-uat.api.sygic.com")
        this@BaseTest.defaultConfig.online()
            .offlineMapsApiUrl("https://licensing-uat.api.sygic.com")
        this@BaseTest.defaultConfig.online().voicesUrl("https://nonttsvoices-testing.api.sygic.com")

        val consoleAppenderBuilder = LoggingSettings.LoggingItem.AppenderItem.ConsoleAppender.Builder()
            .format("%levshort %datetime %msg\n")
            .level(LoggingSettings.LoggingItem.AppenderItem.LogLevel.INFO)
            .time("%y/%m/%d %H:%M:%S")
        val loggingItemBuilder = LoggingSettings.LoggingItem.Builder()
            .name("logger")
            .addAppender(consoleAppenderBuilder)
        this@BaseTest.defaultConfig.logging {
            addLoggingItem(loggingItemBuilder)
        }
        return this@BaseTest.defaultConfig.build()
    }

    private fun buildProductionConfig(): String {
        this@BaseTest.defaultConfig.mapReaderSettings().startupOnlineMapsEnabled(true)
        val path = appContext.getExternalFilesDir(null).toString()
        this@BaseTest.defaultConfig.storageFolders().rootPath(path)
        this@BaseTest.defaultConfig.authentication(BuildConfig.SYGIC_SDK_CLIENT_ID)
        return this@BaseTest.defaultConfig.build()
    }
}