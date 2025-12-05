package cz.feldis.sdkandroidtests

import android.Manifest
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.CallSuper
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.rule.GrantPermissionRule
import com.sygic.sdk.LoggingSettings
import com.sygic.sdk.MapReaderSettings
import com.sygic.sdk.SygicEngine
import com.sygic.sdk.buildJsonConfig
import com.sygic.sdk.context.SygicContext
import com.sygic.sdk.context.SygicContextInitRequest
import com.sygic.sdk.context.SygicContextInitResult
import com.sygic.sdk.diagnostics.LogConnector
import com.sygic.sdk.map.Camera
import com.sygic.sdk.map.CameraState
import com.sygic.sdk.map.GetMapResult
import com.sygic.sdk.map.MapAnimation
import com.sygic.sdk.map.MapCenter
import com.sygic.sdk.map.MapCenterSettings
import com.sygic.sdk.map.MapView
import com.sygic.sdk.map.data.MapProvider
import com.sygic.sdk.online.OnlineManagerProvider
import com.sygic.sdk.online.listeners.SetActiveMapProviderListener
import com.sygic.sdk.online.results.OperationResult
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.position.PositionManager
import com.sygic.sdk.position.PositionManagerProvider
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.fail
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
import timber.log.Timber
import java.io.IOException


abstract class BaseTest {
    private val defaultConfig = SygicEngine.JsonConfigBuilder()
    open lateinit var appContext: Context
    lateinit var sygicContext: SygicContext
    open lateinit var appDataPath: String
    protected open val betaRouting: Boolean = true
    protected open val loadMaps: Boolean = true

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

        runBlocking { initializeSdk(loadMaps, betaRouting) }
    }

    @After
    open fun tearDown() {
        sygicContext.destroy()
    }

    private suspend fun initializeSdk(loadMaps: Boolean, betaRouting: Boolean) {
        val contextInitRequest = SygicContextInitRequest(
            jsonConfiguration = buildJsonConfig(buildConfig(isUAT = true)) {}.betaRouting(
                betaRouting
            ),
            context = appContext,
            logConnector = object : LogConnector() {},
            loadMaps = loadMaps,
            clearOnlineCache = false
        )

        val initializeResult = SygicEngine.initialize(contextInitRequest)
        assertTrue(initializeResult is SygicContextInitResult.Success)
        sygicContext = (initializeResult as SygicContextInitResult.Success).instance
        enableOnlineMaps()
        PositionManagerProvider.getInstance().openGpsConnection()
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
                    productServer().onlineMapsLinkUrl("https://onlinemaps-uat.api.sygic.com")
                    searchUrl("https://search-uat.api.sygic.com")
                    trafficUrl("https://traffic-uat.api.sygic.com")
                    offlineMapsApiUrl("https://offlinemaps-uat.api.sygic.com")
                    voicesUrl("https://nonttsvoices-testing.api.sygic.com")
                    placesUrl("https://places-uat.api.sygic.com")
                    incidents().url("https://incidents-uat.api.sygic.com")
                    speedCameras().url("https://incidents-uat.api.sygic.com")
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
        val onlineManager = runBlocking { OnlineManagerProvider.getInstance() }
        if (!onlineManager.isOnlineMapStreamingEnabled()) {
            Log.d("SYGIC","Disabling online map streaming which is already disabled, skipping")
            return
        }

        val state = runBlocking { onlineManager.disableOnlineMapStreaming() }
        assertTrue(state is OperationResult.Success)
    }

    open fun enableOnlineMaps() {
        val onlineManager = runBlocking { OnlineManagerProvider.getInstance() }

        if (onlineManager.isOnlineMapStreamingEnabled()) {
            Log.d("SYGIC","Enabling online map streaming which is already enabled, skipping")
            return
        }

        val state = runBlocking { onlineManager.enableOnlineMapStreaming() }
        assertTrue(state is OperationResult.Success)
    }

    fun setActiveMapProvider(providerName: String) {
        val listener = mock<SetActiveMapProviderListener>()
        whenever(listener.onActiveProviderSet())

        runBlocking { OnlineManagerProvider.getInstance() }
            .setActiveMapProvider(MapProvider(providerName), listener)

        verify(listener, timeout(5000L)).onActiveProviderSet()
    }

    open fun startPositionUpdating() {
        val listener = mock<PositionManager.OnOperationComplete>()

        runBlocking { PositionManagerProvider.getInstance() }.startPositionUpdating(listener)

        verify(listener, timeout(5000L)).onComplete()
    }

    open fun stopPositionUpdating() {
        val listener = mock<PositionManager.OnOperationComplete>()
        whenever(listener.onComplete())

        runBlocking { PositionManagerProvider.getInstance() }.stopPositionUpdating(listener)

        verify(listener, timeout(5000L)).onComplete()
    }

    open fun isRunningOnEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val brand = Build.BRAND.lowercase()
        val device = Build.DEVICE.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val product = Build.PRODUCT.lowercase()

        return fingerprint.contains("generic")
                || fingerprint.contains("test-keys")
                || model.contains("google_sdk")
                || model.contains("droid4x")
                || model.contains("emulator")
                || model.contains("android sdk built for")
                || brand.contains("generic")
                || device.contains("generic")
                || product.contains("sdk_gphone")
                || manufacturer.contains("genymotion")
                || product.contains("vbox")
                || product.contains("emulator")
    }

    private fun String.betaRouting(useBetaRouting: Boolean): String {
        val sdkConfig = JSONObject(this)
        val online = runCatching { sdkConfig.getJSONObject("Online") }
            .getOrElse {
                val newOnline = JSONObject()
                sdkConfig.put("Online", newOnline)
                newOnline
            }
        val routing = runCatching { online.getJSONObject("Routing") }
            .getOrElse {
                val newRouting = JSONObject()
                online.put("Routing", newRouting)
                newRouting
            }
        routing.put("use_beta_online_routing", useBetaRouting)
        return sdkConfig.toString()
    }

    protected suspend fun getMapView(mapFragment: TestMapFragment): MapView =
        withTimeout(5_000L) {
            when (val res = mapFragment.getMapAsync()) {
                is GetMapResult.Success -> res.mapView
                is GetMapResult.Error -> fail("getMapAsync returned error")
            } as MapView
        }

    protected fun getInitialCameraState(): CameraState {
        return CameraState.Builder().apply {
            setPosition(GeoCoordinates(48.15132, 17.07665))
            setMapCenterSettings(
                MapCenterSettings(
                    MapCenter(0.5f, 0.5f),
                    MapCenter(0.5f, 0.5f),
                    MapAnimation.NONE, MapAnimation.NONE
                )
            )
            setMapPadding(0.0f, 0.0f, 0.0f, 0.0f)
            setRotation(0f)
            setZoomLevel(14F)
            setMovementMode(Camera.MovementMode.Free)
            setRotationMode(Camera.RotationMode.Free)
            setTilt(0f)
        }.build()
    }
}