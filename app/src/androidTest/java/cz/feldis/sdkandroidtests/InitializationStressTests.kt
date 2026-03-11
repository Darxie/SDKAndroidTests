package cz.feldis.sdkandroidtests

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.sygic.sdk.MapReaderSettings
import com.sygic.sdk.SygicEngine
import com.sygic.sdk.context.SygicContext
import com.sygic.sdk.context.SygicContextInitRequest
import com.sygic.sdk.context.SygicContextInitResult
import com.sygic.sdk.diagnostics.LogConnector
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test

class InitializationStressTests {

    @get:Rule
    var activityRule: ActivityScenarioRule<SygicActivity> =
        ActivityScenarioRule(SygicActivity::class.java)

    private val iterations = 1000
    private val initTimeoutMs = 60_000L

    /**
     * Manual stress test for sporadic native crashes during SDK init (custom places path).
     */
    @Test
    fun initializeDestroyStressCustomPlaces() = runBlocking {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val rootPath = appContext.getExternalFilesDir(null)?.absolutePath
            ?: throw AssertionError("External files directory is not available")

        repeat(iterations) { index ->
            val iteration = index + 1
            val jsonConfig = SygicEngine.JsonConfigBuilder().apply {
                license(BuildConfig.LICENSE_KEY)
                authentication(BuildConfig.SYGIC_SDK_CLIENT_ID)
                storageFolders().rootPath(rootPath)
                mapReaderSettings().startupPoiProvider(MapReaderSettings.StartupPoiProvider.CUSTOM_PLACES)
            }.build()

            val request = SygicContextInitRequest(
                jsonConfiguration = jsonConfig,
                context = appContext,
                logConnector = object : LogConnector() {},
                loadMaps = true,
                clearOnlineCache = true
            )

            try {
                val initResult = withTimeout(initTimeoutMs) { SygicEngine.initialize(request) }
                val context = (initResult as? SygicContextInitResult.Success)?.instance
                    ?: throw AssertionError("SDK init failed in iteration $iteration: $initResult")

                println("[stress] iteration=$iteration init=OK")
                context.destroy()
            } finally {
                // Defensive cleanup for partial init states.
                runCatching { SygicContext.getInstance().destroy() }
            }
        }
    }
}
