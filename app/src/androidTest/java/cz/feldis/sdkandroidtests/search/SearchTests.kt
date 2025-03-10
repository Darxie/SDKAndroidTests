package cz.feldis.sdkandroidtests.search

import org.mockito.kotlin.*
import com.sygic.sdk.places.Place
import com.sygic.sdk.places.PlaceCategories
import com.sygic.sdk.places.PlacesManager
import com.sygic.sdk.places.PlacesManagerProvider
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.search.CreateSearchCallback
import com.sygic.sdk.search.HouseNumberResult
import com.sygic.sdk.search.OnlineMapSearch
import com.sygic.sdk.search.PlaceRequest
import com.sygic.sdk.search.PlacesListener
import com.sygic.sdk.search.ResultType
import com.sygic.sdk.search.ReverseGeocoder
import com.sygic.sdk.search.ReverseGeocoderProvider
import com.sygic.sdk.search.SearchManagerProvider
import com.sygic.sdk.search.SearchRequest
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.mapInstaller.MapDownloadHelper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.mockito.Mockito
import timber.log.Timber
import java.util.concurrent.TimeUnit

class SearchTests : BaseTest() {

    private lateinit var searchHelper: SearchHelper
    private lateinit var mapDownloadHelper: MapDownloadHelper
    private lateinit var reverseGeocoder: ReverseGeocoder

    override fun setUp() {
        super.setUp()
        searchHelper = SearchHelper()
        mapDownloadHelper = MapDownloadHelper()
        reverseGeocoder = ReverseGeocoderProvider.getInstance().get()
    }

    @Test
    fun searchPetrolStationInAreaOnlineTest() {
        val position = GeoCoordinates(48.100806, 17.234972)
        val categories = listOf(PlaceCategories.PetrolStation)
        val placeRequest = PlaceRequest(position, categories, 4000)

        val results = searchHelper.onlineSearchPlaces(placeRequest)
        results.forEach {
            assert(it.link.category == PlaceCategories.PetrolStation)
        }
    }

    @Test
    fun searchEVStationInAreaOfflineTest() {
        mapDownloadHelper.installAndLoadMap("nl")
        val position = GeoCoordinates(51.6188, 4.72933)
        val categories = listOf(PlaceCategories.EVStation)
        val placeRequest = PlaceRequest(position, categories, 4000)

        val results = searchHelper.offlineSearchPlaces(placeRequest)
        results.forEach {
            assert(it.link.category == PlaceCategories.EVStation)
        }
    }

    @Test
    fun autocompleteEyckeveldCheckResult() {
        mapDownloadHelper.installAndLoadMap("be")
        val position = GeoCoordinates(50.84367811558576, 4.667406856390823)
        val searchRequest = SearchRequest("Eyckeveld 7", position)

        val results = searchHelper.offlineAutocomplete(searchRequest)

        assertTrue(results.isNotEmpty())
        assertTrue(results.first().titleHighlights.isNotEmpty())
        assertTrue(results.first().title == "Eyckeveld 7")

    }

    @Test
    fun searchAddressCheckLocationGeocoding() {
        mapDownloadHelper.installAndLoadMap("be")
        val position = GeoCoordinates(50.84367811558576, 4.667406856390823)
        val searchRequest = SearchRequest("Eyckeveld 7", position)

        val results = searchHelper.offlineGeocode(searchRequest)

        val firstResult = results.first() as HouseNumberResult
        assertTrue(firstResult.entry != firstResult.location)
        assertTrue(firstResult.entry == GeoCoordinates(50.84349060058594, 4.667229652404785))
        assertTrue(firstResult.location == GeoCoordinates(50.843379974365234, 4.6673197746276855))
    }

    @Test
    fun onlineAutocompleteBratislava() {
        val request = SearchRequest(
            "bratislava",
            GeoCoordinates(48.145718, 17.118669),
            6
        )
        val results = searchHelper.onlineAutocomplete(request)
        results.forEach {
            assert("Bratislava" in it.title)
        }
    }

    @Test
    fun onlineGeocodeTestLukoil() {
        val position = GeoCoordinates(48.100806, 17.234972)

        val request = SearchRequest("Lukoil pálenisko", position)
        val results = searchHelper.onlineGeocode(request)
        assertTrue(results.find { it.title == "LUKOIL Pálenisko" } != null)
    }

    /**
     * Search places test with valid string category and load place with link from search
     *
     * In this test we create place request with Radius 1000, GeoCoordinates (Bratislava 48.145718, 17.118669)
     * and category Bank. Verify that the list from onPlaceLoaded is not empty and place link name and details are not empty.
     * We then verify that all of the found Places are of the category Bank.
     */
    @Test
    fun searchPlacesValidCategoryBankOnline() {
        val listener: PlacesListener = mock(verboseLogging = true)
        val searchManager = SearchManagerProvider.getInstance().get()
        val searchCallback: CreateSearchCallback<OnlineMapSearch> = mock(verboseLogging = true)

        val categories = listOf(PlaceCategories.Bank)
        val request = PlaceRequest(GeoCoordinates(48.145718, 17.118669), categories, 1000)
        searchManager.createOnlineMapSearch(searchCallback)

        val onlineMapSearchCaptor = argumentCaptor<OnlineMapSearch>()
        val argumentCaptor = argumentCaptor<List<Place>>()

        verify(searchCallback, timeout(10_000L)).onSuccess(
            onlineMapSearchCaptor.capture()
        )

        // actual search
        onlineMapSearchCaptor.firstValue.createSession().searchPlaces(request, listener)

        verify(listener, timeout(10_000L)).onPlacesLoaded(
            argumentCaptor.capture(),
            isNotNull()
        )
        verify(listener, never()).onPlacesError(any())

        val resultList = argumentCaptor.firstValue
        for (bank in resultList) {
            assertNotNull(resultList)
            assertFalse(bank.link.name.isEmpty())
            assertFalse(bank.details.isEmpty())
            assertTrue(bank.link.category == PlaceCategories.Bank)
        }
    }

    @Test
    fun reverseGeocodingCheckIfBratislava() {
        mapDownloadHelper.installAndLoadMap("sk")
        val listener: ReverseGeocoder.ReverseGeocodingResultListener = mock(verboseLogging = true)

        ReverseGeocoderProvider.getInstance().get()
            .reverseGeocode(
                GeoCoordinates(48.145387813685645, 17.126208780846095),
                setOf(),
                listener
            )
        verify(listener, timeout(5_000L)).onReverseGeocodingResult(
            argThat {
                for (reverseGeocodingResult in this) {
                    if (reverseGeocodingResult.names.city == "Bratislava")
                        return@argThat true
                }
                false
            }
        )
    }

    @Test
    fun searchPlacesValidCategoryEVStationOffline() {
        mapDownloadHelper.installAndLoadMap("nl")
        val listener: PlacesListener = mock(verboseLogging = true)
        val searchManager = SearchManagerProvider.getInstance().get()

        val categories = listOf(PlaceCategories.EVStation)
        val request = PlaceRequest(GeoCoordinates(51.6188, 4.72933), categories, 1000)
        val session = searchManager.newOfflineSession()

        session.searchPlaces(request, listener)

        verify(listener, timeout(10_000L))
            .onPlacesLoaded(argThat {
                for (place in this) {
                    if (place.link.name.isEmpty() || place.details.isEmpty()) {
                        Timber.e("Place link name or place details are empty")
                        return@argThat false
                    }
                    if (place.link.category != PlaceCategories.EVStation) {
                        Timber.e(
                            "Category of the place: " + place.link.name + "at" +
                                    place.link.location + "is not equal to the requested one"
                        )
                        return@argThat false
                    }
                }
                true
            }, any())
    }

    @Test
    fun loadExternalPlaceId() {
        mapDownloadHelper.installAndLoadMap("eg")
        val placesManager = PlacesManagerProvider.getInstance().get()

        val listenerExternalIdKhufu: PlacesManager.PlaceExternalIdListener =
            mock(verboseLogging = true)
        val listenerExternalIdGiza: PlacesManager.PlaceExternalIdListener =
            mock(verboseLogging = true)

        val category = listOf(PlaceCategories.ImportantTouristAttraction)
        val request = PlaceRequest(GeoCoordinates(29.978296, 31.132839), category, 500)

        val expectedKhufuExternalId = "30303266-3831-6238-2d30-3032642d6265"
        val expectedGizaExternalId = "30303266-3830-6630-2d30-3032642d6266"

        val placesList = searchHelper.offlineSearchPlaces(request)
        for (place in placesList) {
            if (place.link.name == "Pyramid of Khufu") {
                placesManager.loadExternalPlaceId(place.link, listenerExternalIdKhufu)
                verify(
                    listenerExternalIdKhufu,
                    Mockito.timeout(5000)
                )
                    .onExternalPlaceIdLoaded(argThat {
                        if (searchHelper.byteArrayToUUID(this) != expectedKhufuExternalId) {
                            return@argThat false
                        }
                        true
                    })
            }
            if (place.link.name == "El Giza Monuments Area") {
                placesManager.loadExternalPlaceId(place.link, listenerExternalIdGiza)
                verify(
                    listenerExternalIdGiza,
                    Mockito.timeout(5000)
                )
                    .onExternalPlaceIdLoaded(argThat {
                        if (searchHelper.byteArrayToUUID(this) != expectedGizaExternalId) {
                            return@argThat false
                        }
                        true
                    })
            }
        }
    }

    @Test
    fun searchPlacesDetails() {
        mapDownloadHelper.installAndLoadMap("eg")
        val searchManager = SearchManagerProvider.getInstance().get()
        val listener: PlacesListener = mock(verboseLogging = true)

        val categories = listOf(PlaceCategories.ImportantTouristAttraction)
        val request = PlaceRequest(GeoCoordinates(29.9774, 31.1323), categories, 1000)
        val session = searchManager.newOfflineSession()

        session.searchPlaces(request, listener)

        val captor = argumentCaptor<List<Place>>()

        verify(listener, Mockito.timeout(10_000L))
            .onPlacesLoaded(captor.capture(), anyOrNull())

        val placesList = captor.allValues.flatten()
        assert(placesList.isNotEmpty())
        assert(placesList.size > 2)
        for (place in placesList) {
            if (place.link.name == "Pyramid of Khufu")
                assert(place.details[0].value == "Sahra' Muhafadhat al-Jeezah")
            assert(place.details.isNotEmpty())
        }
    }

    @Test
    fun getTimeZoneOnlineMap() {
        val listener: ReverseGeocoder.TimeAtLocationResultListener = mock()
        val utcUnixTimestamp = System.currentTimeMillis() / 1000L
        val location = GeoCoordinates(-37.82626706998113, 140.77709279621698) // South Australia

        reverseGeocoder.getLocalTimeAtLocation(location, utcUnixTimestamp, listener)

        val timestampCaptor = argumentCaptor<Long>()

        verify(listener, timeout(5_000L)).onSuccess(timestampCaptor.capture())
        verify(listener, never()).onError(any())

        assertTrue(timestampCaptor.lastValue - utcUnixTimestamp in 34200..37800) // in seconds
    }

    @Test
    fun getTimeZoneOfflineMap() {
        mapDownloadHelper.installAndLoadMap("sk")
        val listener: ReverseGeocoder.TimeAtLocationResultListener = mock()
        val utcUnixTimestamp = System.currentTimeMillis() / 1000L
        val location = GeoCoordinates(48.12361, 17.11153) // Bratiska

        reverseGeocoder.getLocalTimeAtLocation(location, utcUnixTimestamp, listener)

        val timestampCaptor = argumentCaptor<Long>()

        verify(listener, timeout(5_000L)).onSuccess(timestampCaptor.capture())
        verify(listener, never()).onError(any())

        // local time in Bratiska is always later, but no more than 2 hours (summer time)
        assertTrue(timestampCaptor.lastValue > utcUnixTimestamp)
        assertTrue(timestampCaptor.lastValue - utcUnixTimestamp <= TimeUnit.HOURS.toMillis(2))
    }

    @Test
    fun testSearchSoutocico() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("pt")
        val searchHelper = SearchHelper()
        val searchRequest = SearchRequest(
            searchInput = "soutocico",
            location = GeoCoordinates(48.144334505339934, 17.136729455651594)
        )
        val result = searchHelper.offlineAutocomplete(searchRequest)
        assertTrue("Search found no results, empty list", result.isNotEmpty())
        assertTrue(
            "The result should contain an item with the title 'Soutocico'",
            result.any { it.title == "Soutocico" })
    }

    @Test
    fun reverseGeoNewYork() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("us-ny")
        val reverseGeoListener: ReverseGeocoder.ReverseGeocodingResultListener =
            mock(verboseLogging = true)

        ReverseGeocoderProvider.getInstance().get()
            .reverseGeocode(GeoCoordinates(40.7456, -73.9888), emptySet(), reverseGeoListener)
        verify(reverseGeoListener, timeout(10_000L)).onReverseGeocodingResult(argThat {
            this.forEach {
                if ((it.names.houseNumber == "1187") && (it.names.street == "Broadway"))
                    return@argThat true
            }
            return@argThat false
        })
    }

    @Test
    fun reverseGeoSlovakia() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("sk")
        val reverseGeoListener: ReverseGeocoder.ReverseGeocodingResultListener = mock(verboseLogging = true)

        ReverseGeocoderProvider.getInstance().get().reverseGeocode(GeoCoordinates(48.1476, 17.1046), emptySet(), reverseGeoListener)
        verify(reverseGeoListener, timeout(10_000L)).onReverseGeocodingResult(argThat {
            this.forEach {
                if ((it.names.houseNumber == "6504/4") && (it.names.street == "Lýcejná"))
                    return@argThat true
            }
            return@argThat false
        })
    }

    @Test
    fun reverseGeoVriezewegNetherlands() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("nl")
        val reverseGeoListener: ReverseGeocoder.ReverseGeocodingResultListener = mock(verboseLogging = true)

        ReverseGeocoderProvider.getInstance().get().reverseGeocode(GeoCoordinates(51.8889, 5.66974), emptySet(), reverseGeoListener)
        verify(reverseGeoListener, timeout(10_000L)).onReverseGeocodingResult(argThat {
            this.forEach {
                if ((it.names.houseNumber == "63") && (it.names.street == "Vriezeweg"))
                    return@argThat true
            }
            return@argThat false
        })
    }

    @Test
    fun searchPostalUK() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("gb")
        val searchHelper = SearchHelper()
        val searchRequest = SearchRequest(
            searchInput = "MK22RU",
            location = GeoCoordinates(51.141742277855585, -1.012316722312827)
        )
        val result = searchHelper.offlineAutocomplete(searchRequest)
        assertTrue("Search found no results, empty list", result.isNotEmpty())
        assertTrue("The result should contain an item with the title 'MK2 2RU'", result.any { it.title == "MK2 2RU" })
    }

    @Test
    fun searchPostalUK2() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("gb")
        val searchHelper = SearchHelper()
        val searchRequest = SearchRequest(
            searchInput = "rg213hz",
            location = GeoCoordinates(51.141742277855585, -1.012316722312827)
        )
        val result = searchHelper.offlineAutocomplete(searchRequest)
        assertTrue("Search found no results, empty list", result.isNotEmpty())
        assertTrue(
            "The result should contain an item with the title 'RG21 3HZ'",
            result.any { it.title == "RG21 3HZ" })
        assertTrue(
            "The type of the result is not 'POSTAL_CODE'",
            result.any { it.type == ResultType.POSTAL_CODE })
    }

    @Test
    fun searchPostalSK() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("sk")
        val searchHelper = SearchHelper()
        val searchRequest = SearchRequest(
            searchInput = "91501",
            location = GeoCoordinates(48.74409946027763, 17.887561142146495)
        )
        val result = searchHelper.offlineAutocomplete(searchRequest)
        assertTrue("Search found no results, empty list", result.isNotEmpty())
        assertTrue(
            "The result should contain an item with the title '91501'",
            result.any { it.title == "91501" })
        assertTrue(
            "The type of the result is not 'POSTAL_CODE'",
            result.any { it.type == ResultType.POSTAL_CODE })
        assertTrue(
            "The subtitle is not 'Nové Mesto nad Váhom, Slovensko', but is '${result.first().subtitle}'",
            result.any { it.subtitle == "Nové Mesto nad Váhom, Slovensko" })
    }
}