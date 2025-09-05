package cz.feldis.sdkandroidtests.customPlaces

import android.R
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.sygic.sdk.map.Camera
import com.sygic.sdk.map.CameraState
import com.sygic.sdk.map.GetMapResult
import com.sygic.sdk.map.MapAnimation
import com.sygic.sdk.map.MapCenter
import com.sygic.sdk.map.MapCenterSettings
import com.sygic.sdk.map.MapView
import com.sygic.sdk.map.MapView.InjectSkinResultListener
import com.sygic.sdk.map.listeners.RequestObjectCallback
import com.sygic.sdk.map.`object`.Appearance
import com.sygic.sdk.map.`object`.ProxyPlace
import com.sygic.sdk.map.`object`.ViewObject
import com.sygic.sdk.map.`object`.data.ViewObjectData
import com.sygic.sdk.places.CustomPlacesManager
import com.sygic.sdk.places.CustomPlacesManagerProvider
import com.sygic.sdk.places.listeners.CustomPlacesSearchIndexingListener
import com.sygic.sdk.places.results.InstallDatasetsData
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.search.AutocompleteResult
import com.sygic.sdk.search.AutocompleteResultListener
import com.sygic.sdk.search.CreateSearchCallback
import com.sygic.sdk.search.CustomPlacesSearch
import com.sygic.sdk.search.PlaceRequest
import com.sygic.sdk.search.ResultType
import com.sygic.sdk.search.SearchManager
import com.sygic.sdk.search.SearchManagerProvider
import com.sygic.sdk.search.SearchRequest
import com.sygic.sdk.vehicletraits.listeners.SetVehicleProfileListener
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.SygicActivity
import cz.feldis.sdkandroidtests.TestMapFragment
import cz.feldis.sdkandroidtests.routing.RouteComputeHelper
import cz.feldis.sdkandroidtests.search.SearchHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import java.util.Locale

class CustomPlacesTests : BaseTest() {
    private lateinit var cpManager: CustomPlacesManager
    private lateinit var searchHelper: SearchHelper
    private lateinit var searchManager: SearchManager
    private val defaultDataset = "bf19e514-487b-43c4-b0df-9073b2397dd1"

    override fun setUp() {
        super.setUp()
        searchHelper = SearchHelper()
        cpManager = runBlocking { CustomPlacesManagerProvider.getInstance() }
        searchManager = runBlocking { SearchManagerProvider.getInstance() }
        cpManager.setMode(CustomPlacesManager.Mode.OFFLINE)
        uninstallOfflinePlaces("sk")
    }

    private fun installOfflinePlaces(iso: String) = runBlocking {
        cpManager.installOfflineDatasets(listOf(defaultDataset), iso)
            .filterIsInstance<InstallDatasetsData.Result>()
            .first { it.result.result == CustomPlacesManager.InstallResult.SUCCESS }
    }

    private fun uninstallOfflinePlaces(iso: String) = runBlocking {
        val result = cpManager.uninstallOfflineDatasetsFromCountry(iso)
        assertTrue(result.result == CustomPlacesManager.InstallResult.SUCCESS)
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

    private suspend fun getMapView(mapFragment: TestMapFragment): MapView =
        withTimeout(5_000L) {
            when (val res = mapFragment.getMapAsync()) {
                is GetMapResult.Success -> res.mapView
                is GetMapResult.Error -> fail("getMapAsync returned error")
            } as MapView
        }

    @Test
    fun testInstallPlaces() = runBlocking {
        val result = cpManager.installOfflineDatasets(listOf(defaultDataset), "sk")
            .filterIsInstance<InstallDatasetsData.Result>()
            .map { it.result }
            .first { it.result == CustomPlacesManager.InstallResult.SUCCESS }

        assertEquals(
            CustomPlacesManager.InstallResult.SUCCESS,
            result.result,
        )
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
        val searchCallback: CreateSearchCallback<CustomPlacesSearch> = mock(verboseLogging = true)
        searchManager.createCustomPlacesSearch(searchCallback)

        verify(searchCallback, timeout(10_000L)).onSuccess(
            any()
        )
        val customPlacesSearchIndexingListener: CustomPlacesSearchIndexingListener =
            mock(verboseLogging = true)
        cpManager.addSearchIndexingListener(customPlacesSearchIndexingListener)
        installOfflinePlaces("sk")
        verify(
            customPlacesSearchIndexingListener,
            timeout(10_000L).atLeastOnce()
        ).onStarted(eq(defaultDataset))
        verify(
            customPlacesSearchIndexingListener,
            timeout(10_000L).atLeastOnce()
        ).onSuccess(eq(defaultDataset))
        verify(
            customPlacesSearchIndexingListener,
            never()
        ).onError(eq(defaultDataset), any(), any())
    }

    @Test
    fun testCustomPlacesFromJsonWithUpperCaseIsoCode(): Unit = runBlocking {
        val customPlacesResultListener: CustomPlacesManager.InstallResultListener =
            mock(verboseLogging = true)
        val injectSkinResultListener: InjectSkinResultListener = mock(verboseLogging = true)

        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        // create test scenario with activity & map fragment
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager
                .beginTransaction()
                .add(R.id.content, mapFragment)
                .commitNow()
        }

        val mapView = getMapView(mapFragment)

        mapView.injectSkinDefinition(
            readJson("skin_poi_truck.json"),
            injectSkinResultListener
        )

        verify(
            injectSkinResultListener,
            timeout(5_000L)
        ).onResult(eq(MapView.InjectSkinResult.Success))

        cpManager.installOfflinePlacesFromJson(
            readJson("svk_custom_places.json"), customPlacesResultListener
        )

        verify(
            customPlacesResultListener,
            timeout(5_000L)
        ).onResult(eq(CustomPlacesManager.InstallResult.SUCCESS), any())

        mapView.cameraModel.position = GeoCoordinates(48.2587, 17.75712)
        mapView.cameraModel.zoomLevel = 22F
        mapView.cameraModel.tilt = 0F
        delay(3000) // it takes around 1,5s until the custom place is shown on map
        val callback: RequestObjectCallback = mock(verboseLogging = true)
        val captor = argumentCaptor<List<ViewObject<ViewObjectData>>>()

        val view = requireNotNull(mapView.view)

        val x: Float = view.width / 2F
        val y: Float = view.height / 2F
        val id = mapView.requestObjectsAtPoint(x, y, callback) // click in the middle of the screen

        verify(callback, timeout(5_000L)).onRequestResult(captor.capture(), eq(x), eq(y), eq(id))

        val capturedObjects = captor.firstValue

        if (capturedObjects.isNotEmpty() && capturedObjects[0] is ProxyPlace) {
            val proxyPlace = capturedObjects[0] as ProxyPlace

            assertEquals(proxyPlace.data.place.name, "Odpočívadlo Horná Dolná")
            assertEquals(proxyPlace.data.place.category, "SYTruckRestArea")
            scenario.moveToState(Lifecycle.State.DESTROYED)
        } else {
            scenario.moveToState(Lifecycle.State.DESTROYED)
            fail("Expected object of type ProxyPlace, but found: ${capturedObjects[0]::class.java}")
        }
    }

    @Test
    fun testInstallPlacesOfCountryWithNoPlaces() {
        val customPlacesResultListener: CustomPlacesManager.InstallResultListener =
            mock(verboseLogging = true)
        val installedDatasetListener: CustomPlacesManager.InstalledDatasetListener =
            mock(verboseLogging = true)
        cpManager.installOfflineDatasets(
            listOf(defaultDataset),
            "es",
            customPlacesResultListener
        )
        verify(
            customPlacesResultListener,
            timeout(10_000L)
        ).onResult(eq(CustomPlacesManager.InstallResult.SUCCESS), anyOrNull())
        cpManager.getInstalledDatasets(
            CustomPlacesManager.InstalledDatasetSourceFilter.Any,
            installedDatasetListener
        )
        verify(installedDatasetListener, timeout(5_000L)).onInstalledDatasets(emptyList())
    }

    @Test
    fun testInstallUninstallAndCheck() {
        val installedDatasetListener: CustomPlacesManager.InstalledDatasetListener =
            mock(verboseLogging = true)
        installOfflinePlaces("sk")
        uninstallOfflinePlaces("sk")
        cpManager.getInstalledDatasets(
            CustomPlacesManager.InstalledDatasetSourceFilter.Any,
            installedDatasetListener
        )
        verify(installedDatasetListener, timeout(5_000L)).onInstalledDatasets(emptyList())
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
                .add(R.id.content, mapFragment)
                .commitNow()
        }

        // Get the MapView instance from the TestMapFragment.
        val mapView = runBlocking { getMapView(mapFragment) }
        mapView.setMapLanguage(Locale.ENGLISH)

        // Inject a skin definition using a JSON file and the injectSkinResultListener.
        mapView.injectSkinDefinition(
            readJson("skin_poi_custom.json"),
            injectSkinResultListener
        )

        // Verify that the skin injection result is successful.
        verify(
            injectSkinResultListener,
            timeout(5_000L)
        ).onResult(eq(MapView.InjectSkinResult.Success))

        // Create a place request with location, category tags, radius, and language tag.
        val placeRequest = PlaceRequest(
            location = GeoCoordinates(48.2718, 17.7697),
            categoryTags = listOf("mojaSuperKategoria"),
            radius = 50,
            languageTag = "en"
        )

        // Perform a search for custom places using the place request.
        val searchResult = searchHelper.searchCustomPlaces(placeRequest)[0]

        // Close the scenario and destroy the activity.
        scenario.moveToState(Lifecycle.State.DESTROYED)

        // Verify that the searched place's name is "vyzlec sa".
        assertEquals("vyzlec sa", searchResult.name)
    }

    @Test
    fun testInstallAndSearchAndVerifyPlaceNameFrenchLanguageTag() {
        installOfflinePlaces("sk")
        val injectSkinResultListener: InjectSkinResultListener = mock(verboseLogging = true)

        // create mapView
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager
                .beginTransaction()
                .add(R.id.content, mapFragment)
                .commitNow()
        }
        val mapView = runBlocking { getMapView(mapFragment) }
        mapView.setMapLanguage(Locale.FRENCH)

        // inject skin
        mapView.injectSkinDefinition(
            readJson("skin_poi_custom.json"),
            injectSkinResultListener
        )
        verify(
            injectSkinResultListener,
            timeout(5_000L)
        ).onResult(eq(MapView.InjectSkinResult.Success))

        // search for places
        val placeRequest = PlaceRequest(
            location = GeoCoordinates(48.2718, 17.7697),
            categoryTags = listOf("mojaSuperKategoria"),
            radius = 50,
            languageTag = "fr"
        )
        val searchResult = searchHelper.searchCustomPlaces(placeRequest)[0]

        //close scenario & activity
        scenario.moveToState(Lifecycle.State.DESTROYED)

        // verify
        assertEquals("ja som POI", searchResult.name)
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
    fun testInstallPlacesAndAutocompleteWithCountryIsoFilterOffline2() {
        installOfflinePlaces("sk")
        val searchRequest = SearchRequest(
            searchInput = "vyzlec sa",
            location = GeoCoordinates(48.2718, 17.7697),
            countryIsoFilter = listOf("sk")
        )

        val autocompleteResult = searchHelper.offlineAutocompleteCustomPlaces(searchRequest)[0]
        assertEquals("ibi maiga", autocompleteResult.subtitle)
        assertEquals("mojaSuperKategoria", autocompleteResult.categoryTags[0])
        assertEquals("vyzlec sa", autocompleteResult.title)
    }

    @Test
    fun testInstallPlacesAndAutocompleteWithCountryIsoFilterOffline3() {
        installOfflinePlaces("sk")
        val searchRequest = SearchRequest(
            searchInput = "vyzlec sa",
            location = GeoCoordinates(48.2718, 17.7697),
            countryIsoFilter = listOf("svk")
        )

        assertThrows(SearchHelper.NoResultsException::class.java) {
            searchHelper.offlineAutocompleteCustomPlaces(searchRequest)
        }
    }

    @Test
    fun testAutocompleteWithMultipleCountryIsoFilters() {
        installOfflinePlaces("sk")
        val searchRequest = SearchRequest(
            searchInput = "vyzlec sa",
            location = GeoCoordinates(48.2718, 17.7697),
            countryIsoFilter = listOf("cz", "sk")
        )

        val result = searchHelper.offlineAutocompleteCustomPlaces(searchRequest)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun testAutocompleteWithEmptySearchInput() {
        installOfflinePlaces("sk")
        val searchRequest = SearchRequest(
            searchInput = "",
            location = GeoCoordinates(48.2718, 17.7697),
            countryIsoFilter = listOf("sk")
        )

        assertThrows(SearchHelper.NoResultsException::class.java) {
            searchHelper.offlineAutocompleteCustomPlaces(searchRequest)
        }
    }

    @Test
    fun testAutocompleteWithEmptyCountryIsoFilterList() {
        installOfflinePlaces("sk")
        val searchRequest = SearchRequest(
            searchInput = "vyzlec sa",
            location = GeoCoordinates(48.2718, 17.7697),
            countryIsoFilter = emptyList()
        )

        val result = searchHelper.offlineAutocompleteCustomPlaces(searchRequest)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun testInstallPlacesAndAutocompleteWithCountryIsoFilterOfflineNegative() {
        installOfflinePlaces("sk")
        val searchRequest = SearchRequest(
            searchInput = "vyzlec sa",
            location = GeoCoordinates(48.2718, 17.7697),
            countryIsoFilter = listOf("cz")
        )

        assertThrows(SearchHelper.NoResultsException::class.java) {
            searchHelper.offlineAutocompleteCustomPlaces(searchRequest)
        }
    }

    @Test
    fun testAutocompleteWithFarAwayLocation() {
        installOfflinePlaces("sk")
        val searchRequest = SearchRequest(
            searchInput = "vyzlec sa",
            location = GeoCoordinates(52.52, 13.405), // Berlín
            countryIsoFilter = listOf("sk")
        )

        val result = searchHelper.offlineAutocompleteCustomPlaces(searchRequest)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun testInstallPlacesAndAutocompleteInDatasetOffline() {
        installOfflinePlaces("sk")
        val searchRequest = SearchRequest(
            searchInput = "vyzlec sa",
            location = GeoCoordinates(48.2718, 17.7697),
        )

        val autocompleteResult = searchHelper.offlineAutocompleteCustomPlacesWithDataset(
            searchRequest,
            defaultDataset
        )[0]
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
        searchManager.createCustomPlacesSearch(createSearchListener)
        verify(createSearchListener, timeout(3_000L)).onSuccess(searchCaptor.capture())
        val search = searchCaptor.lastValue

        val session = search.createSession()

        session.autocomplete(searchRequest, autocompleteResultListener)

        verify(autocompleteResultListener, timeout(10_000L)).onAutocomplete(
            resultCaptor.capture()
        )
        assertEquals("ja som POI", resultCaptor.firstValue[0].title)
        session.close()
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

    @Test
    fun testCustomPoiTopCategory(): Unit = runBlocking {
        val customPlacesResultListener: CustomPlacesManager.InstallResultListener =
            mock(verboseLogging = true)
        // Create a map fragment with the initial camera state.
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        // Launch the activity and add the map fragment.
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity { activity ->
            activity.supportFragmentManager.beginTransaction()
                .add(R.id.content, mapFragment)
                .commitNow()
        }

        cpManager.installOfflinePlacesFromJson(
            readJson("svk_custom_places.json"), customPlacesResultListener
        )

        verify(
            customPlacesResultListener,
            timeout(5_000L)
        ).onResult(eq(CustomPlacesManager.InstallResult.SUCCESS), any())

        val mapView = getMapView(mapFragment)

        val injectSkinResultListener: InjectSkinResultListener = mock(verboseLogging = true)
        mapView.injectSkinDefinition(
            readJson("skin_custompoi_top.json"),
            injectSkinResultListener
        )

        verify(
            injectSkinResultListener,
            timeout(5_000L)
        ).onResult(eq(MapView.InjectSkinResult.Success))

        mapView.cameraModel.position = GeoCoordinates(48.2587, 17.75712)
        mapView.cameraModel.zoomLevel = 20F
        mapView.cameraModel.tilt = 0F

        delay(3000)

        val callback: RequestObjectCallback = mock(verboseLogging = true)
        val captor = argumentCaptor<List<ViewObject<ViewObjectData>>>()

        val view = requireNotNull(mapView.view)

        val x: Float = view.width / 2F
        val y: Float = view.height / 2F
        val id = mapView.requestObjectsAtPoint(x, y, callback) // click in the middle of the screen

        verify(callback, timeout(5_000L)).onRequestResult(captor.capture(), eq(x), eq(y), eq(id))

        val capturedObjects = captor.firstValue

        if (capturedObjects.isNotEmpty() && capturedObjects[0] is ProxyPlace) {
            val proxyPlace = capturedObjects[0] as ProxyPlace

            assertEquals(proxyPlace.data.appearance.importance, Appearance.Importance.Top)
            assertEquals(proxyPlace.data.place.category, "SYTruckRestArea")
            scenario.moveToState(Lifecycle.State.DESTROYED)
        } else {
            scenario.moveToState(Lifecycle.State.DESTROYED)
            fail("Expected object of type ProxyPlace, but found: ${capturedObjects[0]::class.java}")
        }
    }

    @Test
    fun testEVPlaceCompatible(): Unit = runBlocking {
        val customPlacesResultListener: CustomPlacesManager.InstallResultListener =
            mock(verboseLogging = true)
        // Create a map fragment with the initial camera state.
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        // Launch the activity and add the map fragment.
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity { activity ->
            activity.supportFragmentManager.beginTransaction()
                .add(R.id.content, mapFragment)
                .commitNow()
        }

        cpManager.installOfflinePlacesFromJson(
            readJson("CP_compatibleEVcharger.json"), customPlacesResultListener
        )

        verify(
            customPlacesResultListener,
            timeout(5_000L)
        ).onResult(eq(CustomPlacesManager.InstallResult.SUCCESS), any())

        val mapView = getMapView(mapFragment)

        val vehicleProfile = RouteComputeHelper().createElectricVehicleProfileTruck()
        val vehicleProfileListener: SetVehicleProfileListener = mock(verboseLogging = true)
        mapView.setVehicleProfile(vehicleProfile, vehicleProfileListener)
        verify(vehicleProfileListener, timeout(5_000L)).onSuccess()
        verify(vehicleProfileListener, never()).onError()

        val injectSkinResultListener: InjectSkinResultListener = mock(verboseLogging = true)
        mapView.injectSkinDefinition(
            readJson("skin_EV.json"),
            injectSkinResultListener
        )

        verify(
            injectSkinResultListener,
            timeout(5_000L)
        ).onResult(eq(MapView.InjectSkinResult.Success))

        mapView.cameraModel.position = GeoCoordinates(48.10201396997687, 17.24821095500223)
        mapView.cameraModel.zoomLevel = 20F
        mapView.cameraModel.tilt = 0F

        delay(3000)

        val callback: RequestObjectCallback = mock(verboseLogging = true)
        val captor = argumentCaptor<List<ViewObject<ViewObjectData>>>()

        val view = requireNotNull(mapView.view)

        val x: Float = view.width / 2F
        val y: Float = view.height / 2F
        val id = mapView.requestObjectsAtPoint(x, y, callback) // click in the middle of the screen

        verify(callback, timeout(5_000L)).onRequestResult(captor.capture(), eq(x), eq(y), eq(id))

        val capturedObjects = captor.firstValue

        if (capturedObjects.isNotEmpty() && capturedObjects[0] is ProxyPlace) {
            val proxyPlace = capturedObjects[0] as ProxyPlace

            assertEquals(proxyPlace.data.appearance.color, -16777216)

            val conditionValue = proxyPlace.data.conditionResult["ev_connector_match"]
            assertEquals("compatible", conditionValue)

            scenario.moveToState(Lifecycle.State.DESTROYED)
        } else {
            scenario.moveToState(Lifecycle.State.DESTROYED)
            fail("Expected object of type ProxyPlace, but found: ${capturedObjects[0]::class.java}")
        }
    }

    @Test
    fun testEVPlaceIncompatible(): Unit = runBlocking {
        val customPlacesResultListener: CustomPlacesManager.InstallResultListener =
            mock(verboseLogging = true)
        // Create a map fragment with the initial camera state.
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        // Launch the activity and add the map fragment.
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity { activity ->
            activity.supportFragmentManager.beginTransaction()
                .add(R.id.content, mapFragment)
                .commitNow()
        }

        cpManager.installOfflinePlacesFromJson(
            readJson("CP_incompatibleEVcharger.json"), customPlacesResultListener
        )

        verify(
            customPlacesResultListener,
            timeout(5_000L)
        ).onResult(eq(CustomPlacesManager.InstallResult.SUCCESS), any())

        val mapView = getMapView(mapFragment)

        val vehicleProfile = RouteComputeHelper().createElectricVehicleProfileTruck()
        val vehicleProfileListener: SetVehicleProfileListener = mock(verboseLogging = true)
        mapView.setVehicleProfile(vehicleProfile, vehicleProfileListener)
        verify(vehicleProfileListener, timeout(5_000L)).onSuccess()
        verify(vehicleProfileListener, never()).onError()

        val injectSkinResultListener: InjectSkinResultListener = mock(verboseLogging = true)
        mapView.injectSkinDefinition(
            readJson("skin_EV.json"),
            injectSkinResultListener
        )

        verify(
            injectSkinResultListener,
            timeout(5_000L)
        ).onResult(eq(MapView.InjectSkinResult.Success))

        mapView.cameraModel.position = GeoCoordinates(48.10201396997687, 17.24821095500223)
        mapView.cameraModel.zoomLevel = 20F
        mapView.cameraModel.tilt = 0F

        delay(3000)

        val callback: RequestObjectCallback = mock(verboseLogging = true)
        val captor = argumentCaptor<List<ViewObject<ViewObjectData>>>()

        val view = requireNotNull(mapView.view)

        val x: Float = view.width / 2F
        val y: Float = view.height / 2F
        val id = mapView.requestObjectsAtPoint(x, y, callback) // click in the middle of the screen

        verify(callback, timeout(5_000L)).onRequestResult(captor.capture(), eq(x), eq(y), eq(id))

        val capturedObjects = captor.firstValue

        if (capturedObjects.isNotEmpty() && capturedObjects[0] is ProxyPlace) {
            val proxyPlace = capturedObjects[0] as ProxyPlace

            assertEquals(proxyPlace.data.appearance.color, -8355712)

            val conditionValue = proxyPlace.data.conditionResult["ev_connector_match"]
            assertEquals("other", conditionValue)

            scenario.moveToState(Lifecycle.State.DESTROYED)
        } else {
            scenario.moveToState(Lifecycle.State.DESTROYED)
            fail("Expected object of type ProxyPlace, but found: ${capturedObjects[0]::class.java}")
        }
    }

    @Test
    fun testEVPlacePreferred(): Unit = runBlocking {
        val customPlacesResultListener: CustomPlacesManager.InstallResultListener =
            mock(verboseLogging = true)
        // Create a map fragment with the initial camera state.
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        // Launch the activity and add the map fragment.
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity { activity ->
            activity.supportFragmentManager.beginTransaction()
                .add(R.id.content, mapFragment)
                .commitNow()
        }

        cpManager.installOfflinePlacesFromJson(
            readJson("CP_preferredEVcharger.json"), customPlacesResultListener
        )

        verify(
            customPlacesResultListener,
            timeout(5_000L)
        ).onResult(eq(CustomPlacesManager.InstallResult.SUCCESS), any())

        val mapView = getMapView(mapFragment)

        val vehicleProfile = RouteComputeHelper().createElectricVehicleProfileTruck()
        val vehicleProfileListener: SetVehicleProfileListener = mock(verboseLogging = true)
        mapView.setVehicleProfile(vehicleProfile, vehicleProfileListener)
        verify(vehicleProfileListener, timeout(5_000L)).onSuccess()
        verify(vehicleProfileListener, never()).onError()

        val injectSkinResultListener: InjectSkinResultListener = mock(verboseLogging = true)
        mapView.injectSkinDefinition(
            readJson("skin_EV.json"),
            injectSkinResultListener
        )

        verify(
            injectSkinResultListener,
            timeout(5_000L)
        ).onResult(eq(MapView.InjectSkinResult.Success))

        mapView.cameraModel.position = GeoCoordinates(48.10201396997687, 17.24821095500223)
        mapView.cameraModel.zoomLevel = 20F
        mapView.cameraModel.tilt = 0F

        delay(3000)

        val callback: RequestObjectCallback = mock(verboseLogging = true)
        val captor = argumentCaptor<List<ViewObject<ViewObjectData>>>()

        val view = requireNotNull(mapView.view)

        val x: Float = view.width / 2F
        val y: Float = view.height / 2F
        val id = mapView.requestObjectsAtPoint(x, y, callback) // click in the middle of the screen

        verify(callback, timeout(5_000L)).onRequestResult(captor.capture(), eq(x), eq(y), eq(id))

        val capturedObjects = captor.firstValue

        if (capturedObjects.isNotEmpty() && capturedObjects[0] is ProxyPlace) {
            val proxyPlace = capturedObjects[0] as ProxyPlace

            assertEquals(proxyPlace.data.appearance.color, -16730804)

            val conditionValue = proxyPlace.data.conditionResult["ev_connector_match"]
            assertEquals("preferred", conditionValue)

            scenario.moveToState(Lifecycle.State.DESTROYED)
        } else {
            scenario.moveToState(Lifecycle.State.DESTROYED)
            fail("Expected object of type ProxyPlace, but found: ${capturedObjects[0]::class.java}")
        }
    }
}