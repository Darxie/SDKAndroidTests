package cz.feldis.sdkandroidtests.mapInstaller

import androidx.test.filters.RequiresDevice
import com.nhaarman.mockitokotlin2.*
import com.sygic.sdk.map.CountryDetails
import com.sygic.sdk.map.MapInstaller
import com.sygic.sdk.map.MapInstallerProvider
import com.sygic.sdk.map.listeners.MapCountryDetailsListener
import com.sygic.sdk.map.listeners.MapListResultListener
import com.sygic.sdk.map.listeners.MapResultListener
import com.sygic.sdk.map.listeners.ResultListener
import cz.feldis.sdkandroidtests.BaseTest
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertFalse
import org.junit.Assert
import org.junit.Test
import java.util.*
import kotlin.concurrent.schedule

class MapDownloadTests : BaseTest() {

    private lateinit var mapDownloadHelper: MapDownloadHelper

    override fun setUp() {
        super.setUp()
        mapDownloadHelper = MapDownloadHelper()
        mapDownloadHelper.resetMapLocale()
        mapDownloadHelper.clearCache()
    }

    @Test
    @RequiresDevice
    fun installUninstallAndVerifyMapTest() {
        //vatican
        mapDownloadHelper.ensureMapNotInstalled("va")
        mapDownloadHelper.installAndLoadMap("va")
        mapDownloadHelper.uninstallMap("va")
        val installer = MapInstallerProvider.getInstance().get()
        val listener: MapListResultListener = mock(verboseLogging = true)
        val captor = argumentCaptor<List<String>>()
        installer.getAvailableCountries(true, listener)
        verify(listener, timeout(10_000L)).onMapListResult(
            captor.capture(),
            eq(MapInstaller.LoadResult.Success)
        )
        assertFalse(captor.firstValue.contains("va"))
    }

    @Test
    fun getFranceCountryDetailsTest() {
        val installer = MapInstallerProvider.getInstance().get()
        val listener: MapCountryDetailsListener = mock(verboseLogging = true)
        val countryCaptor = argumentCaptor<CountryDetails>()
        installer.getCountryDetails("fr", false, listener)
        verify(listener, timeout(30_000L)).onCountryDetails(countryCaptor.capture())

        verify(
            listener,
            never()
        ).onCountryDetailsError(argThat { this != MapInstaller.LoadResult.Success })
        Assert.assertEquals("France", countryCaptor.firstValue.name)
        Assert.assertEquals("Europe", countryCaptor.firstValue.continentName)
        Assert.assertEquals("fr", countryCaptor.firstValue.iso)
        Assert.assertEquals(13, countryCaptor.firstValue.regions.size)
        Assert.assertTrue(countryCaptor.firstValue.totalSize > 0)
        Assert.assertNotNull(countryCaptor.firstValue.version.month)
        Assert.assertNotNull(countryCaptor.firstValue.version.year)
        countryCaptor.firstValue.regions.forEach { Assert.assertTrue(it.startsWith("fr")) }
    }

    @Test
    fun getCountryDetailsFail() {
        val installer = MapInstallerProvider.getInstance().get()
        val listener: MapCountryDetailsListener = mock(verboseLogging = true)
        installer.getCountryDetails("xr", true, listener)
        verify(
            listener,
            timeout(30_000L)
        ).onCountryDetailsError(eq(MapInstaller.LoadResult.DetailsNotAvailable))
        verify(listener, never()).onCountryDetails(any())
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
        verify(listener, timeout(15000).only()).onMapListResult(
            argThat { isNotEmpty() },
            argThat { this == MapInstaller.LoadResult.Success })
    }

    @Test
    fun detectCurrentCountryTest() {
        val installer = MapInstallerProvider.getInstance().get()
        val listener: MapResultListener = mock(verboseLogging = true)
        installer.detectCurrentCountry("sk", listener)
        verify(listener, timeout(15_000L).only()).onMapResult(
            eq("sk"),
            eq(MapInstaller.LoadResult.Success)
        )

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

    @Test
    @RequiresDevice
    fun installSplitMapTest() {
        mapDownloadHelper.ensureMapNotInstalled("gb-05")
        mapDownloadHelper.installAndLoadMap("gb-05")
    }

    @Test
    @RequiresDevice
    fun installAndDeleteSplitMapTest() {
        mapDownloadHelper.ensureMapNotInstalled("gb-05")
        mapDownloadHelper.installAndLoadMap("gb-05")
        mapDownloadHelper.uninstallMap("gb-05")
    }

    @Test
    fun setUnsupportedLocaleExpectErrorTest() {
        val installer = MapInstallerProvider.getInstance().get()
        val listener: ResultListener = mock(verboseLogging = true)
        installer.setLocale("invalid", listener)
        verify(listener, timeout(20_000L).times(1))
            .onResult(eq(MapInstaller.LoadResult.UnsupportedLocale))
    }

    @Test
    fun setLocaleTest() {
        val installer = MapInstallerProvider.getInstance().get()
        val listener: ResultListener = mock(verboseLogging = true)
        mapDownloadHelper.installAndLoadMap("sk")
        installer.setLocale("sk-def", listener)
        verify(listener, timeout(20_000L).times(1))
            .onResult(eq(MapInstaller.LoadResult.Success))
    }

    @Test
    fun verifyProviderOfInstalledMapTest() {
        val cdListener : MapCountryDetailsListener = mock(verboseLogging = true)
        mapDownloadHelper.installAndLoadMap("is")
        MapInstallerProvider.getInstance().get().getCountryDetails("is", true, cdListener)
        val captor = argumentCaptor<CountryDetails>()
        verify(cdListener, timeout(10_000L)).onCountryDetails(captor.capture())
        verify(cdListener, never()).onCountryDetailsError(any())
        val details = captor.firstValue
        assertEquals("ta", details.version.provider.toString())
    }

    @Test
    fun verifyProviderOfNotInstalledMapCountrySplit() {
        val cdListener : MapCountryDetailsListener = mock(verboseLogging = true)
        mapDownloadHelper.ensureMapNotInstalled("de-02")
        MapInstallerProvider.getInstance().get().getCountryDetails("de-02", false, cdListener)
        val captor = argumentCaptor<CountryDetails>()
        verify(cdListener, timeout(10_000L)).onCountryDetails(captor.capture())
        verify(cdListener, never()).onCountryDetailsError(any())
        val details = captor.firstValue
        assertEquals("ta", details.version.provider.toString())
    }

    @Test
    fun verifyNonExistentCountryDetailsOfResourceMap() {
        val cdListener : MapCountryDetailsListener = mock(verboseLogging = true)
        MapInstallerProvider.getInstance().get().getCountryDetails("de-01", false, cdListener)
        verify(cdListener, never()).onCountryDetails(any())
        verify(cdListener, timeout(10_000L)).onCountryDetailsError(eq(MapInstaller.LoadResult.DetailsNotAvailable))
    }
}