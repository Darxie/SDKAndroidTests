package cz.feldis.sdkandroidtests.customPlaces

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.timeout
import com.nhaarman.mockitokotlin2.verify
import com.sygic.sdk.map.Camera
import com.sygic.sdk.map.CameraState
import com.sygic.sdk.map.MapAnimation
import com.sygic.sdk.map.MapCenter
import com.sygic.sdk.map.MapCenterSettings
import com.sygic.sdk.map.MapView
import com.sygic.sdk.map.MapView.InjectSkinResultListener
import com.sygic.sdk.map.listeners.OnMapInitListener
import com.sygic.sdk.places.CustomPlacesManager
import com.sygic.sdk.places.CustomPlacesManagerProvider
import com.sygic.sdk.places.listeners.CustomPlacesSearchIndexingListener
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.search.AutocompleteResult
import com.sygic.sdk.search.AutocompleteResultListener
import com.sygic.sdk.search.CreateSearchCallback
import com.sygic.sdk.search.CustomPlacesSearch
import com.sygic.sdk.search.PlaceRequest
import com.sygic.sdk.search.ResultType
import com.sygic.sdk.search.SearchManagerProvider
import com.sygic.sdk.search.SearchRequest
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.SygicActivity
import cz.feldis.sdkandroidtests.TestMapFragment
import cz.feldis.sdkandroidtests.search.SearchHelper
import cz.feldis.sdkandroidtests.utils.AdvancedRunner
import cz.feldis.sdkandroidtests.utils.Repeat
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AdvancedRunner::class)
class CustomPlacesTests : BaseTest() {
    private lateinit var cpManager: CustomPlacesManager
    private lateinit var searchHelper: SearchHelper

    override fun setUp() {
        super.setUp()
        searchHelper = SearchHelper()
        cpManager = CustomPlacesManagerProvider.getInstance().get()
        cpManager.setMode(CustomPlacesManager.Mode.OFFLINE)
        uninstallOfflinePlaces("sk")
    }

    private fun installOfflinePlaces(iso: String) {
        val customPlacesResultListener: CustomPlacesManager.InstallResultListener = mock(verboseLogging = true)
        cpManager.installOfflinePlaces(iso, customPlacesResultListener)
        verify(customPlacesResultListener, timeout(20_000L)).onResult(eq(CustomPlacesManager.InstallResult.SUCCESS), anyOrNull())
    }

    private fun uninstallOfflinePlaces(iso: String) {
        val resultListener: CustomPlacesManager.InstallResultListener = mock(verboseLogging = true)
        cpManager.uninstallOfflinePlaces(iso, resultListener)
        verify(resultListener, timeout(20_000L)).onResult(eq(CustomPlacesManager.InstallResult.SUCCESS), anyOrNull())
    }

    private fun getInitialCameraState(): CameraState {
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

    private fun getMapView(mapFragment: TestMapFragment): MapView {
        val mapInitListener: OnMapInitListener = mock(verboseLogging = true)
        val mapViewCaptor = argumentCaptor<MapView>()

        mapFragment.getMapAsync(mapInitListener)
        verify(mapInitListener, timeout(5_000L)).onMapReady(
            mapViewCaptor.capture()
        )
        return mapViewCaptor.firstValue
    }

    @Test
    fun testInstallPlaces() {
        val customPlacesResultListener: CustomPlacesManager.InstallResultListener = mock(verboseLogging = true)
        cpManager.installOfflinePlaces("sk", customPlacesResultListener)
        verify(customPlacesResultListener, timeout(20_000L)).onResult(eq(CustomPlacesManager.InstallResult.SUCCESS), anyOrNull())
    }

    @Test
    fun setModeGetModeTest() {
        cpManager.setMode(CustomPlacesManager.Mode.ONLINE)
        assertEquals(cpManager.getMode(), CustomPlacesManager.Mode.ONLINE)
        cpManager.setMode(CustomPlacesManager.Mode.OFFLINE)
        assertEquals(cpManager.getMode(), CustomPlacesManager.Mode.OFFLINE)
    }

    @Test
    fun testInstallCustomPlacesAndVerifyIndexing() {
        val customPlacesSearchIndexingListener: CustomPlacesSearchIndexingListener = mock(verboseLogging = true)
        cpManager.addSearchIndexingListener(customPlacesSearchIndexingListener)
        installOfflinePlaces("sk")
        verify(customPlacesSearchIndexingListener, atLeastOnce()).onStarted()
        verify(customPlacesSearchIndexingListener, atLeastOnce()).onSuccess()
        verify(customPlacesSearchIndexingListener, never()).onError(any(), any())
    }

    @Test
    fun testInstallPlacesOfCountryWithNoPlaces() {
        val customPlacesResultListener: CustomPlacesManager.InstallResultListener = mock(verboseLogging = true)
        val installedCountriesListener: CustomPlacesManager.InstalledCountriesListener = mock(verboseLogging = true)
        cpManager.installOfflinePlaces("es", customPlacesResultListener)
        verify(customPlacesResultListener, timeout(20_000L)).onResult(eq(CustomPlacesManager.InstallResult.SUCCESS), anyOrNull())
        cpManager.getInstalledCountries(installedCountriesListener)
        verify(installedCountriesListener, timeout(5_000L)).onInstalledCountries(argThat {
            this.find { it != "es" } == null
        })
    }

    @Test
    fun testInstallUninstallAndCheck() {
        val installedCountriesListener: CustomPlacesManager.InstalledCountriesListener = mock(verboseLogging = true)
        installOfflinePlaces("sk")
        uninstallOfflinePlaces("sk")
        cpManager.getInstalledCountries(installedCountriesListener)
        verify(installedCountriesListener, timeout(5_000L)).onInstalledCountries(argThat {
            this.find { it != "sk" } == null
        })
    }

    @Test
    fun testInstallAndSearchAndVerifyPlaceNameEnglishLanguageTag() {
        installOfflinePlaces("sk")
        // Create a mock InjectSkinResultListener for skin injection logging.
        val injectSkinResultListener: InjectSkinResultListener = mock(verboseLogging = true)

        // Create a TestMapFragment and launch the SygicActivity for testing.
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager
                .beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }

        // Get the MapView instance from the TestMapFragment.
        val mapView = getMapView(mapFragment)

        // Inject a skin definition using a JSON file and the injectSkinResultListener.
        mapView.injectSkinDefinition(
            readJson("skin_poi_custom.json"),
            injectSkinResultListener
        )

        // Verify that the skin injection result is successful.
        verify(injectSkinResultListener, timeout(5_000L)).onResult(eq(MapView.InjectSkinResult.Success))

        // Create a place request with location, category tags, radius, and language tag.
        val placeRequest = PlaceRequest(
            location = GeoCoordinates(48.2718, 17.7697),
            categoryTags = listOf("mojaSuperKategoria"),
            radius = 50,
            languageTag = "fr"
        )

        // Perform a search for custom places using the place request.
        val searchResult = searchHelper.searchCustomPlaces(placeRequest)[0]

        // Verify that the searched place's name is "ja som POI".
        assertEquals("ja som POI", searchResult.link.name)

        // Close the scenario and destroy the activity.
        scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    @Test
    @Repeat(5)
    fun testInstallAndSearchAndVerifyPlaceNameFrenchLanguageTag() {
        installOfflinePlaces("sk")
        val injectSkinResultListener: InjectSkinResultListener = mock(verboseLogging = true)

        // create mapView
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager
                .beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }
        val mapView = getMapView(mapFragment)

        // inject skin
        mapView.injectSkinDefinition(
            readJson("skin_poi_custom.json"),
            injectSkinResultListener
        )
        verify(injectSkinResultListener, timeout(5_000L)).onResult(eq(MapView.InjectSkinResult.Success))

        // search for places
        val placeRequest = PlaceRequest(
            location = GeoCoordinates(48.2718, 17.7697),
            categoryTags = listOf("mojaSuperKategoria"),
            radius = 50,
            languageTag = "fr"
        )
        val searchResult = searchHelper.searchCustomPlaces(placeRequest)[0]

        // verify
        assertEquals("ja som POI", searchResult.link.name)

        //close scenario & activity
        scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    @Test
    fun testInstallPlacesAndAutocompleteOffline() {
        installOfflinePlaces("sk")
        val searchRequest = SearchRequest(
            searchInput = "vyzlec sa",
            location = GeoCoordinates(48.2718, 17.7697),
        )

        val autocompleteResult = searchHelper.offlineAutocompleteCustomPlaces(searchRequest)[0]
        assertEquals("ibi maiga", autocompleteResult.subtitle)
        assertEquals("mojaSuperKategoria", autocompleteResult.categoryTags[0])
        assertEquals("vyzlec sa", autocompleteResult.title)
    }

    @Test
    fun testInstallPlacesAndAutocompleteOfflineLangFr() {
        installOfflinePlaces("sk")
        val searchRequest = SearchRequest(
            searchInput = "ibi maiga",
            location = GeoCoordinates(48.2718, 17.7697),
            languageTag = "fr"
        )

        val autocompleteResult = searchHelper.offlineAutocompleteCustomPlaces(searchRequest)[0]
        assertEquals("ibi maiga", autocompleteResult.subtitle)
        assertEquals("mojaSuperKategoria", autocompleteResult.categoryTags[0])
        assertEquals("ja som POI", autocompleteResult.title)
    }

    // we search for ibi maiga in english, but ibi maiga is only in french
    @Test
    fun testInstallPlacesAndAutocompleteOfflineLangEnExpectFoundByFr() {
        installOfflinePlaces("sk")
        val searchRequest = SearchRequest(
            searchInput = "ibi maiga",
            location = GeoCoordinates(48.2718, 17.7697),
            languageTag = "en"
        )

        val autocompleteResultListener: AutocompleteResultListener = mock(verboseLogging = true)
        val createSearchListener: CreateSearchCallback<CustomPlacesSearch> =
            mock(verboseLogging = true)
        val searchCaptor = argumentCaptor<CustomPlacesSearch>()
        val resultCaptor = argumentCaptor<List<AutocompleteResult>>()
        SearchManagerProvider.getInstance().get().createCustomPlacesSearch(createSearchListener)
        verify(createSearchListener, timeout(3_000L)).onSuccess(searchCaptor.capture())
        val search = searchCaptor.lastValue

        val session = search.createSession()

        session.autocomplete(searchRequest, autocompleteResultListener)

        verify(autocompleteResultListener, timeout(10_000L)).onAutocomplete(
            resultCaptor.capture()
        )
        assertEquals("ja som POI", resultCaptor.firstValue[0].title)
    }

    @Test
    fun onlineAutocompletePlace() {
        val searchRequest = SearchRequest(
            searchInput = "ibi maiga",
            location = GeoCoordinates(48.2718, 17.7697),
        )
        val searchResult = searchHelper.onlineAutocomplete(searchRequest)
        // assert if there is no custom place in the results
        assert(searchResult.find { it.type == ResultType.CUSTOM_PLACE } != null)
    }
}