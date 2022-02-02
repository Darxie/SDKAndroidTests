package cz.feldis.sdkandroidtests

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.annotation.CallSuper
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.rule.GrantPermissionRule
import com.sygic.sdk.SygicEngine
import com.sygic.sdk.SygicEngine.initialize
import com.sygic.sdk.context.CoreInitException
import com.sygic.sdk.context.SygicContext
import com.sygic.sdk.context.SygicContextInitRequest
import com.sygic.sdk.diagnostics.LogConnector
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import androidx.test.core.app.ActivityScenario
import com.sygic.sdk.navigation.RouteEventNotificationsSettings
import com.sygic.sdk.navigation.traffic.TrafficManagerProvider
import com.sygic.sdk.position.PositionManagerProvider


abstract class BaseTest {
    private val mDefaultConfig = SygicEngine.JsonConfigBuilder()
    var isEngineInitialized = false
    lateinit var appContext: Context
    lateinit var sygicContext: SygicContext

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
        val jsonConfig = buildDefaultConfig()
        val logConnector = object: LogConnector() {
            override fun onLogReceived(message: String, logLevel: LogLevel) {
                super.onLogReceived(message, logLevel)
                println(message)
            }
        }

        val contextInitRequest = SygicContextInitRequest(
            jsonConfig,
            this@BaseTest.appContext,
            tracking = null,
            logConnector = logConnector,
            loadMaps = false,
            clearOnlineCache = true
        )
        initialize(contextInitRequest, object : SygicEngine.OnInitCallback {
            override fun onError(error: CoreInitException) {
                latch.countDown()
                Assert.fail("sdk init FAILED with error $error")
            }

            override fun onInstance(instance: SygicContext) {
                this@BaseTest.isEngineInitialized = true
                this@BaseTest.sygicContext = instance
                latch.countDown()
            }
        })
        latch.await(30, TimeUnit.SECONDS)
    }

    @After
    open fun tearDown() {
        if (sygicContext.isInitialized) {
            val settings = RouteEventNotificationsSettings()
            settings.resetToDefault()
        }
        this@BaseTest.sygicContext.destroy()
    }

    private fun buildDefaultConfig(): String {
        this@BaseTest.mDefaultConfig.authentication(BuildConfig.SYGIC_SDK_CLIENT_ID)
        val path = appContext.getExternalFilesDir(null).toString()
        this@BaseTest.mDefaultConfig.storageFolders().rootPath(path)
        this@BaseTest.mDefaultConfig.online().routingUrl("https://directions-testing.api.sygic.com")
        this@BaseTest.mDefaultConfig.online().sSOServerUrl("https://auth-testing.api.sygic.com")
        this@BaseTest.mDefaultConfig.online().productServer()
            .licenceUrl("https://licensing-testing.api.sygic.com")
        this@BaseTest.mDefaultConfig.online().productServer()
            .connectUrl("https://productserver-testing.api.sygic.com")
        this@BaseTest.mDefaultConfig.online().productServer()
            .onlineMapsLinkUrl("https://licensing-testing.api.sygic.com")
        this@BaseTest.mDefaultConfig.online().searchUrl("https://search-testing.api.sygic.com")
        this@BaseTest.mDefaultConfig.online().incidents()
            .url("https://incidents-testing.api.sygic.com")
        this@BaseTest.mDefaultConfig.online().trafficUrl("https://traffic-testing.sygic.com")
        this@BaseTest.mDefaultConfig.online()
            .offlineMapsApiUrl("https://licensing-testing.api.sygic.com")
        this@BaseTest.mDefaultConfig.mapReaderSettings().startupOnlineMapsEnabled(true)
        this@BaseTest.mDefaultConfig.online().networkAccessEnabled(true)
        return this@BaseTest.mDefaultConfig.build()
    }
}