package cz.feldis.sdkandroidtests

import androidx.test.filters.RequiresDevice
import com.nhaarman.mockitokotlin2.*
import com.sygic.sdk.map.MapInstaller
import com.sygic.sdk.map.MapInstallerProvider
import com.sygic.sdk.map.listeners.MapListResultListener
import com.sygic.sdk.map.listeners.MapResultListener
import org.junit.Test
import java.util.*
import kotlin.concurrent.schedule

class MapDownloadTests : BaseTest() {

    private lateinit var mapDownloadHelper: MapDownloadHelper

    override fun setUp() {
        super.setUp()
        mapDownloadHelper = MapDownloadHelper()
    }

    @Test
    @RequiresDevice
    fun installCancelTest() {
        mapDownloadHelper.ensureMapNotInstalled("sk")
        mapDownloadHelper.ensureMapNotInstalled("us-dc")
        mapDownloadHelper.ensureMapNotInstalled("de-02")

        val installer = MapInstallerProvider.getInstance().get()
        val listenerSK: MapResultListener = mock(verboseLogging = true)
        val listenerUSDC: MapResultListener = mock(verboseLogging = true)
        val listenerDE02: MapResultListener = mock(verboseLogging = true)

        installer.installMap("sk", listenerSK)
        installer.installMap("de-02", listenerDE02)
        val task = installer.installMap("us-dc", listenerUSDC)
        Timer().schedule(2000) {
            task.cancel()
        }

        verify(listenerSK, timeout(180_000L)).onMapResult(
            eq("sk"),
            eq(MapInstaller.LoadResult.Success)
        )
        verify(listenerUSDC, timeout(180_000L)).onMapResult(
            eq("us-dc"),
            eq(MapInstaller.LoadResult.Cancelled)
        )
        verify(listenerDE02, timeout(180_000L)).onMapResult(
            eq("de-02"),
            eq(MapInstaller.LoadResult.Success)
        )
    }

    @Test
    fun getAvailableCountriesTest() {
        val installer = MapInstallerProvider.getInstance().get()
        val listener: MapListResultListener = mock(verboseLogging = true)
        installer.getAvailableCountries(false, listener)
        verify(listener, timeout(15000)).onMapListResult(
            argThat { isNotEmpty() },
            argThat { this == MapInstaller.LoadResult.Success })
        verify(listener, never()).onMapListResult(
            argThat { isEmpty() },
            argThat { this != MapInstaller.LoadResult.Success })
    }

    @Test
    fun detectCurrentCountryTest() {
        val installer = MapInstallerProvider.getInstance().get()
        val listener: MapResultListener = mock(verboseLogging = true)
        installer.detectCurrentCountry("sk", listener)
        verify(listener, timeout(15000)).onMapResult(eq("sk"), eq(MapInstaller.LoadResult.Success))
        verify(listener, never()).onMapResult(
            any(),
            argThat { this != MapInstaller.LoadResult.Success })
    }

    @Test
    @RequiresDevice
    fun installIcelandTest() {
        mapDownloadHelper.ensureMapNotInstalled("is")
        val installer = MapInstallerProvider.getInstance().get()

        val listener: MapResultListener = mock(verboseLogging = true)
        installer.installMap("is", listener)
        verify(listener, timeout(60_000L)).onMapResult(
            eq("is"),
            eq(MapInstaller.LoadResult.Success)
        )

        val installedMapsListener: MapListResultListener = mock(verboseLogging = true)
        installer.getAvailableCountries(true, installedMapsListener)
        var installedMaps = emptyList<String>()

        verify(installedMapsListener, timeout(15000)).onMapListResult(argThat {
            installedMaps = this.filter { it == "is" }
            true
        }, argThat { this == MapInstaller.LoadResult.Success })
        verify(installedMapsListener, never()).onMapListResult(
            any(),
            argThat { this != MapInstaller.LoadResult.Success })

        val loadListener: MapResultListener = mock(verboseLogging = true)
        val installedMap = installedMaps.first()
        installer.loadMap(installedMap, loadListener)
        verify(loadListener, timeout(15000)).onMapResult(any(), eq(MapInstaller.LoadResult.Success))
    }
}