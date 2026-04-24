package cz.feldis.sdkandroidtests.mapInstaller

import android.util.Log
import com.sygic.sdk.map.MapInstaller
import com.sygic.sdk.map.MapInstallerProvider
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class MapDownloadHelper {

    private val installer = runBlocking { MapInstallerProvider.getInstance() }

    fun ensureMapNotInstalled(iso: String) = runBlocking {
        uninstallMap(iso)
        delay(500)
        val getMapStatusResult = runBlocking {
            installer.getMapStatus(iso)
        }
        assertTrue(getMapStatusResult.result is MapInstaller.LoadResult.Success)
        assertTrue(getMapStatusResult.status == MapInstaller.MapStatus.NotInstalled)
    }

    fun installAndLoadMap(iso: String)  = runBlocking {
        val installMapResult = runBlocking {
            installer.installMap(iso)
        }
        assertTrue("Failed to install map: $installMapResult",
            installMapResult is MapInstaller.LoadResult.Success)

        val loadMapResult = runBlocking {
            installer.loadMap(iso)
        }
        assertTrue(loadMapResult is MapInstaller.LoadResult.Success)
        delay(500)
    }

    fun uninstallMap(iso: String) {
        val loadResult = runBlocking {
            installer.uninstallMap(iso)
        }
        assertTrue(
            "Unexpected loadResult: $loadResult",
            loadResult is MapInstaller.LoadResult.Success || loadResult is MapInstaller.LoadResult.MapNotInstalled
        )
    }

    fun resetMapLocale() {
        val loadResult = runBlocking {
            installer.setLocale("en-en")
        }
        assertTrue(loadResult is MapInstaller.LoadResult.Success)
    }

    fun setMapLocale(locale: String) {
        val loadResult = runBlocking {
            installer.setLocale(locale)
        }
        assertTrue(loadResult is MapInstaller.LoadResult.Success)
    }

    fun clearCache() {
        val loadResult = runBlocking {
            installer.clearCache()
        }
        assertTrue(loadResult is MapInstaller.LoadResult.Success)
    }

    fun unloadMap(iso: String) {
        val loadResult = runBlocking {
            installer.unloadMap(iso)
        }
        assertTrue(loadResult is MapInstaller.LoadResult.Success)
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
        Log.d("SYGIC", "Maps unloaded successfully: ${result.mapIsos}")
        delay(1000)
    }
}
