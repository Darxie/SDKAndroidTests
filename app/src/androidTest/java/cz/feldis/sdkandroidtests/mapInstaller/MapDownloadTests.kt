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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import timber.log.Timber
import java.util.Timer
import kotlin.concurrent.schedule

class MapDownloadTests : BaseTest() {

    private lateinit var mapDownloadHelper: MapDownloadHelper
    private lateinit var mapInstaller: MapInstaller

    override fun setUp() {
        super.setUp()
        mapDownloadHelper = MapDownloadHelper()
        mapDownloadHelper.resetMapLocale()
        mapDownloadHelper.clearCache()
        mapInstaller = runBlocking { MapInstallerProvider.getInstance() }
    }

    @Test
    fun installUninstallAndVerifyMapTest() {
        assumeTrue(!isRunningOnEmulator())
        //vatican
        mapDownloadHelper.ensureMapNotInstalled("va")
        mapDownloadHelper.installAndLoadMap("va")
        mapDownloadHelper.uninstallMap("va")
        val listener: MapListResultListener = mock(verboseLogging = true)
        val captor = argumentCaptor<List<String>>()
        mapInstaller.getAvailableCountries(true, listener)
        verify(listener, timeout(10_000L)).onMapListResult(
            captor.capture(),
            eq(MapInstaller.LoadResult.Success)
        )
        assertFalse(captor.firstValue.contains("va"))
    }

    @Test
    fun getFranceCountryDetailsTest() {
        val listener: MapCountryDetailsListener = mock(verboseLogging = true)
        val countryCaptor = argumentCaptor<CountryDetails>()
        mapInstaller.getCountryDetails("fr", false, listener)
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
        val listener: MapCountryDetailsListener = mock(verboseLogging = true)
        mapInstaller.getCountryDetails("xr", true, listener)
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

        val spyListenerGI = createSpyListener()
        val spyListenerUSDC = createSpyListener()
        val spyListenerAD = createSpyListener()

        mapInstaller.installMap("gi", spyListenerGI)
        mapInstaller.installMap("ad", spyListenerAD)
        val task = mapInstaller.installMap("us-dc", spyListenerUSDC)
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
        val listener: MapListResultListener = mock(verboseLogging = true)
        mapInstaller.getAvailableCountries(false, listener)
        verify(listener, timeout(15000).only()).onMapListResult(
            argThat { isNotEmpty() },
            argThat { this == MapInstaller.LoadResult.Success })
    }

    @Test
    fun detectCurrentCountryTest() {
        val listener: MapResultListener = mock(verboseLogging = true)
        mapInstaller.detectCurrentCountry("sk", listener)
        verify(listener, timeout(15_000L).only()).onMapResult(
            eq("sk"),
            eq(MapInstaller.LoadResult.Success)
        )

    }

    @Test
    fun installIcelandTest() {
        assumeTrue(!isRunningOnEmulator())
        mapDownloadHelper.ensureMapNotInstalled("is")

        val listener: MapResultListener = mock(verboseLogging = true)
        mapInstaller.installMap("is", listener)
        verify(listener, timeout(60_000L)).onMapResult(
            eq("is"),
            eq(MapInstaller.LoadResult.Success)
        )

        val installedMapsListener: MapListResultListener = mock(verboseLogging = true)
        mapInstaller.getAvailableCountries(true, installedMapsListener)
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
        mapInstaller.loadMap(installedMap, loadListener)
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
    fun setUnsupportedLocaleExpectErrorTest() {
        val listener: ResultListener = mock(verboseLogging = true)
        mapInstaller.setLocale("invalid", listener)
        verify(listener, timeout(20_000L).times(1))
            .onResult(eq(MapInstaller.LoadResult.UnsupportedLocale))
    }

    @Test
    fun setLocaleTest() {
        // cache is cleared during setUp() and locale set to en-en
        val listener: ResultListener = mock(verboseLogging = true)

        mapInstaller.setLocale("sk-sk", listener)
        verify(listener, timeout(20_000L).times(1))
            .onResult(eq(MapInstaller.LoadResult.Success))

        val cdListener: MapCountryDetailsListener = mock(verboseLogging = true)
        val detailsCaptor = argumentCaptor<CountryDetails>()

        mapInstaller.getCountryDetails("sk", true, cdListener)
        verify(cdListener, timeout(20_000L)).onCountryDetails(detailsCaptor.capture())
        assertTrue(detailsCaptor.firstValue.name == "Slovensko")
        assertTrue(detailsCaptor.firstValue.continentName == "Európa")
    }

    @Test
    fun verifyProviderOfInstalledMapTest() {
        val cdListener: MapCountryDetailsListener = mock(verboseLogging = true)
        mapDownloadHelper.installAndLoadMap("is")
        mapInstaller.getCountryDetails("is", true, cdListener)
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
        mapInstaller.getCountryDetails("de-02", false, cdListener)
        val captor = argumentCaptor<CountryDetails>()
        verify(cdListener, timeout(10_000L)).onCountryDetails(captor.capture())
        verify(cdListener, never()).onCountryDetailsError(any())
        val details = captor.firstValue
        assertEquals(MapProvider("ta"), details.version.provider)
    }

    @Test
    fun verifyNonExistentCountryDetailsOfResourceMap() {
        val cdListener: MapCountryDetailsListener = mock(verboseLogging = true)
        mapInstaller.getCountryDetails("de-01", false, cdListener)
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

    private fun createSpyListener(): MapResultListener {
        return spy(TestMapResultListener())
    }
}