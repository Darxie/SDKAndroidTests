package cz.feldis.sdkandroidtests.search

import android.util.Log
import com.sygic.sdk.places.Place
import com.sygic.sdk.places.PlaceCategories
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.search.AutocompleteResult
import com.sygic.sdk.search.CreateSearchCallback
import com.sygic.sdk.search.HouseNumberResult
import com.sygic.sdk.search.OfflineMapSearch
import com.sygic.sdk.search.OnlineMapSearch
import com.sygic.sdk.search.PlaceRequest
import com.sygic.sdk.search.PlacesListener
import com.sygic.sdk.search.ResultType
import com.sygic.sdk.search.ReverseGeocoder
import com.sygic.sdk.search.ReverseGeocoder.ErrorCode
import com.sygic.sdk.search.ReverseGeocoderProvider
import com.sygic.sdk.search.SearchManager
import com.sygic.sdk.search.SearchManagerProvider
import com.sygic.sdk.search.SearchRequest
import com.sygic.sdk.search.results.LocalTimeAtLocationResult
import com.sygic.sdk.search.results.ReverseGeocodeResult
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.mapInstaller.MapDownloadHelper
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNotNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import timber.log.Timber
import java.util.concurrent.TimeUnit

class SearchTests : BaseTest() {

    private lateinit var searchHelper: SearchHelper
    private lateinit var mapDownloadHelper: MapDownloadHelper
    private lateinit var reverseGeocoder: ReverseGeocoder
    private lateinit var searchManager: SearchManager

    override fun setUp() {
        super.setUp()
        searchHelper = SearchHelper()
        mapDownloadHelper = MapDownloadHelper()
        reverseGeocoder = runBlocking { ReverseGeocoderProvider.getInstance() }
        searchManager = runBlocking { SearchManagerProvider.getInstance() }
    }

    private fun offlineAutocompleteForMap(
        mapCode: String,
        searchInput: String,
        location: GeoCoordinates,
        maxResults: Int? = null
    ): List<AutocompleteResult> {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap(mapCode)
        val request = if (maxResults == null) {
            SearchRequest(searchInput, location)
        } else {
            SearchRequest(searchInput, location, maxResults)
        }
        return searchHelper.offlineAutocomplete(request)
    }

    @Test
    fun searchEVStationInAreaOfflineTest() {
        mapDownloadHelper.installAndLoadMap("nl")
        val position = GeoCoordinates(51.6188, 4.72933)
        val categories = listOf(PlaceCategories.EVStation)
        val placeRequest = PlaceRequest(position, categories, 4000)

        val results = searchHelper.offlineSearchPlaces(placeRequest)
        results.forEach {
            assert(it.category == PlaceCategories.EVStation)
        }
    }

    @Test
    fun searchEVStationInAreaAndCheckSYMaxPowerOfflineTest() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("sk")
        val position = GeoCoordinates(48.11708633532345, 17.216519354470783)
        val categories = listOf(PlaceCategories.EVStation)
        val placeRequest = PlaceRequest(position, categories, 50)

        val results = searchHelper.offlineSearchPlaces(placeRequest)
        results.forEach { place ->
            assert(place.category == PlaceCategories.EVStation)

            val chademoConnector = place.evCharger?.evses
                ?.flatMap { it.chargingConnectors.toList() }
                ?.firstOrNull { it.type.name.equals("chademo", ignoreCase = true) }

            assertNotNull("CHAdeMO connector not found", chademoConnector)
            assertEquals(50000.0F, chademoConnector!!.maximalPower)

            val brandDetail = place.details.firstOrNull { it.key == "SYBrand" }
            assertNotNull("SYBrand detail not found", brandDetail)
            assertEquals("Lidl", brandDetail!!.value)
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
    fun reverseGeocodingCheckIfBratislava(): Unit = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")

        withTimeout(5_000L) {
            when (val result = reverseGeocoder.reverseGeocode(
                GeoCoordinates(48.145387813685645, 17.126208780846095),
                setOf()
            )) {
                is ReverseGeocodeResult.Error -> {
                    Log.w("SYGIC", "Reverse geocoding error: ${result.errorCode.name}")
                }

                is ReverseGeocodeResult.Success -> {
                    for (reverseGeocodingResult in result.results) {
                        assertTrue(reverseGeocodingResult.names.city == "Bratislava")
                    }
                }
            }
        }
    }

    @Test
    fun searchPlacesValidCategoryEVStationOffline() {
        mapDownloadHelper.installAndLoadMap("nl")
        val searchCallback: CreateSearchCallback<OfflineMapSearch> = mock(verboseLogging = true)
        val listener: PlacesListener = mock(verboseLogging = true)

        val categories = listOf(PlaceCategories.EVStation)
        val request = PlaceRequest(GeoCoordinates(51.6188, 4.72933), categories, 1000)

        val offlineMapSearchCaptor = argumentCaptor<OfflineMapSearch>()
        searchManager.createOfflineMapSearch(searchCallback)
        verify(searchCallback, timeout(10_000L)).onSuccess(
            offlineMapSearchCaptor.capture()
        )
        offlineMapSearchCaptor.firstValue.createSession().searchPlaces(request, listener)


        verify(listener, timeout(10_000L))
            .onPlacesLoaded(argThat {
                for (place in this) {
                    if (place.name.isEmpty() || place.details.isEmpty()) {
                        Timber.e("Place link name or place details are empty")
                        return@argThat false
                    }
                    if (place.category != PlaceCategories.EVStation) {
                        Timber.e(
                            "Category of the place: " + place.name + "at" +
                                    place.position + "is not equal to the requested one"
                        )
                        return@argThat false
                    }
                }
                true
            }, any())
    }

//    @Test
//    fun loadExternalPlaceId() {
//        mapDownloadHelper.installAndLoadMap("eg")
//        val placesManager = PlacesManagerProvider.getInstance().get()
//
//        val category = listOf(PlaceCategories.ImportantTouristAttraction)
//        val request = PlaceRequest(GeoCoordinates(29.978296, 31.132839), category, 500)
//
//        val expectedKhufuExternalId = "30303266-3831-6238-2d30-3032642d6265"
//        val expectedGizaExternalId = "30303266-3830-6630-2d30-3032642d6266"
//
//        val placesList = searchHelper.offlineSearchPlaces(request)
//        for (place in placesList) {
//            if (place.name == "Pyramid of Khufu") {
//                print("fksdjf")
//            }
//        }
//    }

    @Test
    fun searchPlacesDetails() {
        mapDownloadHelper.installAndLoadMap("eg")
        val searchCallback: CreateSearchCallback<OfflineMapSearch> = mock(verboseLogging = true)
        val listener: PlacesListener = mock(verboseLogging = true)

        val categories = listOf(PlaceCategories.ImportantTouristAttraction)
        val request = PlaceRequest(GeoCoordinates(29.9774, 31.1323), categories, 1000)
        val offlineMapSearchCaptor = argumentCaptor<OfflineMapSearch>()
        searchManager.createOfflineMapSearch(searchCallback)
        verify(searchCallback, timeout(10_000L)).onSuccess(
            offlineMapSearchCaptor.capture()
        )
        offlineMapSearchCaptor.firstValue.createSession().searchPlaces(request, listener)

        val captor = argumentCaptor<List<Place>>()

        verify(listener, Mockito.timeout(10_000L))
            .onPlacesLoaded(captor.capture(), anyOrNull())

        val placesList = captor.allValues.flatten()
        assert(placesList.isNotEmpty())
        assert(placesList.size > 2)
        for (place in placesList) {
            assert(place.details.isNotEmpty())

        }
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

        reverseGeocoder
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
    fun reverseGeoBerlin() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("de-04")
        val reverseGeoListener: ReverseGeocoder.ReverseGeocodingResultListener =
            mock(verboseLogging = true)

        reverseGeocoder
            .reverseGeocode(GeoCoordinates(52.5129, 13.4076), emptySet(), reverseGeoListener)
        verify(reverseGeoListener, timeout(10_000L)).onReverseGeocodingResult(argThat {
            this.forEach {
                if ((it.names.houseNumber == "4") && (it.names.street == "Fischerinsel"))
                    return@argThat true
            }
            return@argThat false
        })
    }

    @Test
    fun reverseGeoCanada() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("ca-08")
        val reverseGeoListener: ReverseGeocoder.ReverseGeocodingResultListener =
            mock(verboseLogging = true)

        reverseGeocoder
            .reverseGeocode(GeoCoordinates(49.8987, -97.1627), emptySet(), reverseGeoListener)
        verify(reverseGeoListener, timeout(10_000L)).onReverseGeocodingResult(argThat {
            this.forEach {
                if ((it.names.houseNumber == "684") && (it.names.street == "Victor St"))
                    return@argThat true
            }
            return@argThat false
        })
    }

    @Test
    fun reverseGeoSlovakia() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("sk")
        val reverseGeoListener: ReverseGeocoder.ReverseGeocodingResultListener =
            mock(verboseLogging = true)

        reverseGeocoder
            .reverseGeocode(GeoCoordinates(48.1476, 17.1046), emptySet(), reverseGeoListener)
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
        val reverseGeoListener: ReverseGeocoder.ReverseGeocodingResultListener =
            mock(verboseLogging = true)

        reverseGeocoder
            .reverseGeocode(GeoCoordinates(51.8889, 5.66974), emptySet(), reverseGeoListener)
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
        assertTrue(
            "The result should contain an item with the title 'MK2 2RU'",
            result.any { it.title == "MK2 2RU" })
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

    @Test
    fun searchPostalSKWithSpace() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("sk")
        val searchRequest = SearchRequest(
            searchInput = "915 01",
            location = GeoCoordinates(48.74409946027763, 17.887561142146495)
        )
        val result = searchHelper.offlineAutocomplete(searchRequest)
        assertTrue("Search found no results, empty list", result.isNotEmpty())
        assertTrue(
            "The result should contain an item with the title '915 01'",
            result.any { it.title == "915 01" })
        assertTrue(
            "The type of the result is not 'POSTAL_CODE'",
            result.any { it.type == ResultType.POSTAL_CODE })
    }

    @Test
    fun searchPostalUKWithLowercaseAndSpace() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("gb")
        val searchRequest = SearchRequest(
            searchInput = "mk2 2ru",
            location = GeoCoordinates(51.141742277855585, -1.012316722312827)
        )
        val result = searchHelper.offlineAutocomplete(searchRequest)
        assertTrue("Search found no results, empty list", result.isNotEmpty())
        assertTrue(
            "The result should contain an item with the title 'MK2 2RU'",
            result.any { it.title == "MK2 2RU" })
        assertTrue(
            "The type of the result is not 'POSTAL_CODE'",
            result.any { it.type == ResultType.POSTAL_CODE })
    }

    @Test
    fun offlineAutocompleteBratislava() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("sk")
        val searchRequest = SearchRequest(
            searchInput = "bratislava",
            location = GeoCoordinates(48.145718, 17.118669),
            8
        )
        val result = searchHelper.offlineAutocomplete(searchRequest)
        assertTrue("Search found no results, empty list", result.isNotEmpty())
        assertTrue(
            "Expected at least one Bratislava entry in title",
            result.any { it.title.contains("Bratislava", ignoreCase = true) })
    }

    @Test
    fun searchPostalSKMaxResultsOne() {
        val result = offlineAutocompleteForMap(
            mapCode = "sk",
            searchInput = "91501",
            location = GeoCoordinates(48.74409946027763, 17.887561142146495),
            maxResults = 1
        )
        assertTrue("Search found no results, empty list", result.isNotEmpty())
        assertTrue("Expected at most 1 result, got ${result.size}", result.size <= 1)
        assertTrue("Expected postal code 91501 in results", result.any { it.title == "91501" })
    }

    @Test
    fun searchPostalUKMaxResultsOne() {
        val result = offlineAutocompleteForMap(
            mapCode = "gb",
            searchInput = "MK22RU",
            location = GeoCoordinates(51.141742277855585, -1.012316722312827),
            maxResults = 1
        )
        assertTrue("Search found no results, empty list", result.isNotEmpty())
        assertTrue("Expected at most 1 result, got ${result.size}", result.size <= 1)
        assertTrue("Expected postal code MK2 2RU in results", result.any { it.title == "MK2 2RU" })
    }

    @Test
    fun searchPostalUKMixedCase() {
        val result = offlineAutocompleteForMap(
            mapCode = "gb",
            searchInput = "Mk2 2rU",
            location = GeoCoordinates(51.141742277855585, -1.012316722312827)
        )
        assertTrue("Search found no results, empty list", result.isNotEmpty())
        assertTrue(
            "Expected normalized UK postal code MK2 2RU in results",
            result.any { it.title == "MK2 2RU" && it.type == ResultType.POSTAL_CODE })
    }

    @Test
    fun searchSoutocicoUppercase() {
        val result = offlineAutocompleteForMap(
            mapCode = "pt",
            searchInput = "SOUTOCICO",
            location = GeoCoordinates(48.144334505339934, 17.136729455651594)
        )
        assertTrue("Search found no results, empty list", result.isNotEmpty())
        assertTrue(
            "The result should contain an item with the title 'Soutocico'",
            result.any { it.title == "Soutocico" })
    }

    @Test
    fun autocompleteEyckeveldWithoutHouseNumber() {
        val result = offlineAutocompleteForMap(
            mapCode = "be",
            searchInput = "Eyckeveld",
            location = GeoCoordinates(50.84367811558576, 4.667406856390823)
        )
        assertTrue("Search found no results, empty list", result.isNotEmpty())
        assertTrue(
            "Expected at least one Eyckeveld suggestion",
            result.any { it.title.contains("Eyckeveld", ignoreCase = true) })
    }

    @Test
    fun offlineAutocompleteBratislavaMaxResultsTwo() {
        val result = offlineAutocompleteForMap(
            mapCode = "sk",
            searchInput = "bratislava",
            location = GeoCoordinates(48.145718, 17.118669),
            maxResults = 2
        )
        assertTrue("Search found no results, empty list", result.isNotEmpty())
        assertTrue("Expected at most 2 results, got ${result.size}", result.size <= 2)
        assertTrue(
            "Expected at least one Bratislava entry in title",
            result.any { it.title.contains("Bratislava", ignoreCase = true) })
    }

    @Test
    fun offlineAutocompleteBratislavaTitlesNotBlank() {
        val result = offlineAutocompleteForMap(
            mapCode = "sk",
            searchInput = "bratislava",
            location = GeoCoordinates(48.145718, 17.118669),
            maxResults = 8
        )
        assertTrue("Search found no results, empty list", result.isNotEmpty())
        assertTrue(
            "Expected all Bratislava autocomplete titles to be non-blank",
            result.all { it.title.isNotBlank() })
    }

    @Test
    fun searchPostalSKExpectedTitleHasPostalCodeType() {
        val result = offlineAutocompleteForMap(
            mapCode = "sk",
            searchInput = "91501",
            location = GeoCoordinates(48.74409946027763, 17.887561142146495)
        )
        val expectedResult = result.firstOrNull { it.title == "91501" }
        assertNotNull("Expected result with title 91501 was not found", expectedResult)
        assertTrue(
            "Expected result 91501 to have type POSTAL_CODE",
            expectedResult!!.type == ResultType.POSTAL_CODE
        )
    }

    @Test
    fun reverseGeoExpectNoSelection() {
        disableOnlineMaps()
        val reverseGeoListener: ReverseGeocoder.ReverseGeocodingResultListener =
            mock(verboseLogging = true)

        reverseGeocoder
            .reverseGeocode(
                GeoCoordinates(37.288480477393286, -41.35639017659597),
                emptySet(),
                reverseGeoListener
            )

        verify(reverseGeoListener, timeout(10_000L)).onReverseGeocodingResultError(
            eq(ErrorCode.NO_SELECTION)
        )
    }

    @Test
    fun reverseGeoCanadaManitoba() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("ca-08")
        val reverseGeoListener: ReverseGeocoder.ReverseGeocodingResultListener =
            mock(verboseLogging = true)

        reverseGeocoder
            .reverseGeocode(GeoCoordinates(49.8987, -97.1627), emptySet(), reverseGeoListener)
        verify(reverseGeoListener, timeout(10_000L)).onReverseGeocodingResult(argThat {
            this.forEach {
                if ((it.names.houseNumber == "684") && (it.names.street == "Victor St"))
                    return@argThat true
            }
            return@argThat false
        })
    }
}
