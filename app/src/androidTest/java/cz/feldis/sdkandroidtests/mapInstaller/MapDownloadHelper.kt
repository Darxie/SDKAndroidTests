package cz.feldis.sdkandroidtests.mapInstaller

import com.nhaarman.mockitokotlin2.*
import com.sygic.sdk.map.MapInstaller
import com.sygic.sdk.map.MapInstallerProvider
import com.sygic.sdk.map.listeners.MapInstallProgressListener
import com.sygic.sdk.map.listeners.MapResultListener
import com.sygic.sdk.map.listeners.MapStatusListener
import com.sygic.sdk.map.listeners.ResultListener
import cz.feldis.sdkandroidtests.BaseTest

class MapDownloadHelper : BaseTest() {

    private val installer = MapInstallerProvider.getInstance().get()

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
        val lis: MapInstallProgressListener
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
        verify(listener, timeout(5_000L)).onResult(eq(MapInstaller.LoadResult.Success))
    }

}