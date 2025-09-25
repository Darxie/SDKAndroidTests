package cz.feldis.sdkandroidtests.mapInstaller

import android.util.Log
import com.sygic.sdk.map.MapInstaller
import com.sygic.sdk.map.MapInstallerProvider
import com.sygic.sdk.map.listeners.MapListResult
import com.sygic.sdk.map.listeners.MapListResultListener
import com.sygic.sdk.map.listeners.MapResultListener
import com.sygic.sdk.map.listeners.MapStatusListener
import com.sygic.sdk.map.listeners.MapsResultListener
import com.sygic.sdk.map.listeners.ResultListener
import cz.feldis.sdkandroidtests.BaseTest
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.mockito.ArgumentMatchers.anyList
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import timber.log.Timber

class MapDownloadHelper : BaseTest() {

    private val installer = runBlocking { MapInstallerProvider.getInstance() }

    fun ensureMapNotInstalled(iso: String) {
        val uninstallListener: MapResultListener = mock(verboseLogging = true)
        installer.uninstallMap(iso, uninstallListener)
        verify(uninstallListener, timeout(5_000L)).onMapResult(eq(iso), any())

        val statusListener: MapStatusListener = mock(verboseLogging = true)
        installer.getMapStatus(iso, statusListener)
        verify(statusListener, timeout(3_000L)).onStatusFetched(
            eq(MapInstaller.LoadResult.Success),
            eq(MapInstaller.MapStatus.NotInstalled)
        )
    }

    fun installAndLoadMap(iso: String) {
        val listener: MapResultListener = mock(verboseLogging = true)
        installer.installMap(iso, listener)
        verify(listener, timeout(300_000L)).onMapResult(
            eq(iso),
            eq(MapInstaller.LoadResult.Success)
        )

        val loadListener: MapResultListener = mock(verboseLogging = true)
        installer.loadMap(iso, loadListener)
        verify(loadListener, timeout(5_000L)).onMapResult(
            eq(iso),
            eq(MapInstaller.LoadResult.Success)
        )
    }

    fun uninstallMap(iso: String) {
        val uninstallListener: MapResultListener = mock(verboseLogging = true)
        installer.uninstallMap(iso, uninstallListener)
        verify(uninstallListener, timeout(15000)).onMapResult(eq(iso), any())
    }

    fun resetMapLocale() {
        val listener: ResultListener = mock(verboseLogging = true)
        installer.setLocale("en-en", listener)
        verify(listener, timeout(15_000L)).onResult(eq(MapInstaller.LoadResult.Success))
    }

    fun clearCache() {
        val listener: ResultListener = mock(verboseLogging = true)
        installer.clearCache(listener)
        verify(listener, timeout(20_000L)).onResult(eq(MapInstaller.LoadResult.Success))
    }

    fun unloadMap(iso: String) = runBlocking {
        val result = installer.unloadMap(iso)
        assertTrue(result is MapInstaller.LoadResult.Success)
    }

    fun unloadAllMaps() = runBlocking {
        val result = installer.getAvailableCountries(installed = true)
        check(result.result is MapInstaller.LoadResult.Success) {
            "Failed to get installed maps: ${result.result}"
        }
        Log.d("SYGIC", "Going to unload maps")
        val unloadResult = installer.unloadMaps(result.mapIsos)
        assertTrue(
            "Failed to unload maps: ${result.mapIsos}",
            unloadResult is MapInstaller.LoadResult.Success
        )
        Log.d("SYGIC","Maps unloaded successfully: ${result.mapIsos}")
    }
}
