package cz.feldis.sdkandroidtests.mapInstaller

import com.sygic.sdk.map.CountryDetails
import com.sygic.sdk.map.MapInstaller
import com.sygic.sdk.map.MapInstallerProvider
import com.sygic.sdk.map.data.MapProvider
import com.sygic.sdk.map.listeners.MapCountryDetailsListener
import com.sygic.sdk.map.listeners.MapListResultListener
import com.sygic.sdk.map.listeners.MapResultListener
import com.sygic.sdk.map.listeners.ResultListener
import cz.feldis.sdkandroidtests.BaseTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Ignore
import org.junit.Test
import org.mockito.kotlin.*
import timber.log.Timber
import java.util.Timer
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
    fun installUninstallAndVerifyMapTest() {
        assumeTrue(!isRunningOnEmulator())
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
        assertEquals("France", countryCaptor.firstValue.name)
        assertEquals("Europe", countryCaptor.firstValue.continentName)
        assertEquals("fr", countryCaptor.firstValue.iso)
        assertEquals(13, countryCaptor.firstValue.regions.size)
        assertTrue(countryCaptor.firstValue.totalSize > 0)
        assertNotNull(countryCaptor.firstValue.version.month)
        assertNotNull(countryCaptor.firstValue.version.year)
        countryCaptor.firstValue.regions.forEach { assertTrue(it.startsWith("fr")) }
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
    fun installCancelTest() {
        assumeTrue(!isRunningOnEmulator())
        mapDownloadHelper.ensureMapNotInstalled("gi")
        mapDownloadHelper.ensureMapNotInstalled("ad")
        mapDownloadHelper.ensureMapNotInstalled("us-dc")

        val installer = MapInstallerProvider.getInstance().get()

        val spyListenerGI = createSpyListener()
        val spyListenerUSDC = createSpyListener()
        val spyListenerAD = createSpyListener()

        installer.installMap("gi", spyListenerGI)
        installer.installMap("ad", spyListenerAD)
        val task = installer.installMap("us-dc", spyListenerUSDC)
        Timer().schedule(2000) {
            task.cancel()
        }

        verify(spyListenerGI, timeout(30_000L)).onMapResult(
            eq("gi"),
            eq(MapInstaller.LoadResult.Success)
        )
        verify(spyListenerAD, timeout(30_000L)).onMapResult(
            eq("ad"),
            eq(MapInstaller.LoadResult.Success)
        )
        verify(spyListenerUSDC, timeout(30_000L)).onMapResult(
            eq("us-dc"),
            eq(MapInstaller.LoadResult.Cancelled)
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
    fun installIcelandTest() {
        assumeTrue(!isRunningOnEmulator())
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
    fun installSplitMapTest() {
        assumeTrue(!isRunningOnEmulator())
        mapDownloadHelper.ensureMapNotInstalled("gb-05")
        mapDownloadHelper.installAndLoadMap("gb-05")
    }

    @Test
    fun installAndDeleteSplitMapTest() {
        assumeTrue(!isRunningOnEmulator())
        mapDownloadHelper.ensureMapNotInstalled("gb-05")
        mapDownloadHelper.installAndLoadMap("gb-05")
        mapDownloadHelper.uninstallMap("gb-05")
    }

    @Test
    @Ignore("fails on sdk28")
    fun setUnsupportedLocaleExpectErrorTest() {
        val installer = MapInstallerProvider.getInstance().get()
        val listener: ResultListener = mock(verboseLogging = true)
        installer.setLocale("invalid", listener)
        verify(listener, timeout(20_000L).times(1))
            .onResult(eq(MapInstaller.LoadResult.UnsupportedLocale))
    }

    @Test
    fun setLocaleTest() {
        // cache is cleared during setUp() and locale set to en-en
        val installer = MapInstallerProvider.getInstance().get()
        val listener: ResultListener = mock(verboseLogging = true)

        installer.setLocale("sk-sk", listener)
        verify(listener, timeout(20_000L).times(1))
            .onResult(eq(MapInstaller.LoadResult.Success))

        val cdListener: MapCountryDetailsListener = mock(verboseLogging = true)
        val detailsCaptor = argumentCaptor<CountryDetails>()

        installer.getCountryDetails("sk", true, cdListener)
        verify(cdListener, timeout(20_000L)).onCountryDetails(detailsCaptor.capture())
        assertTrue(detailsCaptor.firstValue.name == "Slovensko")
        assertTrue(detailsCaptor.firstValue.continentName == "Európa")
    }

    @Test
    fun verifyProviderOfInstalledMapTest() {
        val cdListener: MapCountryDetailsListener = mock(verboseLogging = true)
        mapDownloadHelper.installAndLoadMap("is")
        MapInstallerProvider.getInstance().get().getCountryDetails("is", true, cdListener)
        val captor = argumentCaptor<CountryDetails>()
        verify(cdListener, timeout(10_000L)).onCountryDetails(captor.capture())
        verify(cdListener, never()).onCountryDetailsError(any())
        val details = captor.firstValue
        assertEquals(MapProvider("ta"), details.version.provider)
    }

    @Test
    fun verifyProviderOfNotInstalledMapCountrySplit() {
        val cdListener: MapCountryDetailsListener = mock(verboseLogging = true)
        mapDownloadHelper.ensureMapNotInstalled("de-02")
        MapInstallerProvider.getInstance().get().getCountryDetails("de-02", false, cdListener)
        val captor = argumentCaptor<CountryDetails>()
        verify(cdListener, timeout(10_000L)).onCountryDetails(captor.capture())
        verify(cdListener, never()).onCountryDetailsError(any())
        val details = captor.firstValue
        assertEquals(MapProvider("ta"), details.version.provider)
    }

    @Test
    fun verifyNonExistentCountryDetailsOfResourceMap() {
        val cdListener: MapCountryDetailsListener = mock(verboseLogging = true)
        MapInstallerProvider.getInstance().get().getCountryDetails("de-01", false, cdListener)
        verify(cdListener, never()).onCountryDetails(any())
        verify(
            cdListener,
            timeout(10_000L)
        ).onCountryDetailsError(eq(MapInstaller.LoadResult.DetailsNotAvailable))
    }

    open class TestMapResultListener : MapResultListener {
        override fun onMapResult(mapIso: String, result: MapInstaller.LoadResult) {
            Timber.d("MapResultListener called with iso $mapIso and result: $result")
        }
    }

    fun createSpyListener(): MapResultListener {
        return spy(TestMapResultListener())
    }
}