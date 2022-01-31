package cz.feldis.sdkandroidtests

import com.nhaarman.mockitokotlin2.*
import com.sygic.sdk.map.MapInstaller
import com.sygic.sdk.map.MapInstallerProvider
import com.sygic.sdk.map.listeners.MapResultListener
import com.sygic.sdk.map.listeners.MapStatusListener

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
        installer.installMap(iso, listener)
        verify(listener, timeout(60_000L)).onMapResult(eq(iso), eq(MapInstaller.LoadResult.Success))

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

}