package cz.feldis.sdkandroidtests.search

import com.sygic.sdk.places.Place
import com.sygic.sdk.places.PlaceCategories
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.search.CreateSearchCallback
import com.sygic.sdk.search.OnlineMapSearch
import com.sygic.sdk.search.PlaceRequest
import com.sygic.sdk.search.PlacesListener
import com.sygic.sdk.search.ReverseGeocoder
import com.sygic.sdk.search.ReverseGeocoder.ErrorCode
import com.sygic.sdk.search.ReverseGeocoderProvider
import com.sygic.sdk.search.ResultStatus
import com.sygic.sdk.search.SearchManager
import com.sygic.sdk.search.SearchManagerProvider
import com.sygic.sdk.search.SearchRequest
import com.sygic.sdk.search.results.LocalTimeAtLocationResult
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.mapInstaller.MapDownloadHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.ranges.contains

class OnlineSearchTests: BaseTest() {

    private lateinit var searchHelper: SearchHelper
    private lateinit var reverseGeocoder: ReverseGeocoder
    private lateinit var searchManager: SearchManager

    override fun setUp() {
        super.setUp()
        searchHelper = SearchHelper()
        MapDownloadHelper().unloadAllMaps()
        reverseGeocoder = runBlocking { ReverseGeocoderProvider.getInstance() }
        searchManager = runBlocking { SearchManagerProvider.getInstance() }
    }

    @Test
    fun searchPetrolStationInAreaOnlineTest() {
        val position = GeoCoordinates(48.100806, 17.234972)
        val categories = listOf(PlaceCategories.PetrolStation)
        val placeRequest = PlaceRequest(position, categories, 4000)

        val results = searchHelper.onlineSearchPlaces(placeRequest)
        results.forEach {
            assert(it.category == PlaceCategories.PetrolStation)
        }
    }

    @Test
    fun onlineAutocompleteBratislava() {
        val request = SearchRequest(
            "bratislava",
            GeoCoordinates(48.145718, 17.118669),
            6
        )
        val results = runBlocking { searchHelper.onlineAutocomplete(request) }
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
        val searchCallback: CreateSearchCallback<OnlineMapSearch> = mock(verboseLogging = true)

        val categories = listOf(PlaceCategories.Bank)
        val request = PlaceRequest(GeoCoordinates(48.145718, 17.118669), categories, 1000)
        searchManager.createOnlineMapSearch(searchCallback)

        val onlineMapSearchCaptor = argumentCaptor<OnlineMapSearch>()

        verify(searchCallback, timeout(10_000L)).onSuccess(
            onlineMapSearchCaptor.capture()
        )

        val session = onlineMapSearchCaptor.firstValue.createSession()
        val maxRetries = 3
        val retryDelay = 5_000L

        repeat(maxRetries) { attempt ->
            println("🔍 Search places bank test attempt ${attempt + 1}")

            val latch = CountDownLatch(1)
            var resultList: List<Place>? = null
            var errorStatus: ResultStatus? = null
            val listener: PlacesListener = mock(verboseLogging = true) {
                on { onPlacesLoaded(org.mockito.kotlin.any(), org.mockito.kotlin.anyOrNull()) } doAnswer {
                    resultList = it.getArgument(0)
                    latch.countDown()
                    null
                }
                on { onPlacesError(org.mockito.kotlin.any()) } doAnswer {
                    errorStatus = it.getArgument(0)
                    latch.countDown()
                    null
                }
            }

            session.searchPlaces(request, listener)

            if (!latch.await(10_000L, TimeUnit.MILLISECONDS)) {
                fail("searchPlacesValidCategoryBankOnline timed out")
            }

            resultList?.let { places ->
                assertNotNull(places)
                for (bank in places) {
                    assertFalse(bank.name.isEmpty())
                    assertFalse(bank.details.isEmpty())
                    assertTrue(bank.category == PlaceCategories.Bank)
                }
                return
            }

            if (errorStatus == ResultStatus.UNSPECIFIED_ERROR && attempt < maxRetries - 1) {
                runBlocking { delay(retryDelay) }
            } else {
                fail("searchPlacesValidCategoryBankOnline failed: $errorStatus")
            }
        }

        fail("searchPlacesValidCategoryBankOnline failed after $maxRetries attempts")
    }

    @Test
    fun getTimeZoneOnlineMap() {
        val utcUnixTimestamp = System.currentTimeMillis() / 1000L
        val location = GeoCoordinates(-37.82626706998113, 140.77709279621698) // South Australia

        val result =
            runBlocking { reverseGeocoder.getLocalTimeAtLocation(location, utcUnixTimestamp) }
        when (result) {
            is LocalTimeAtLocationResult.Success -> {
                assertTrue(result.unixTimestamp - utcUnixTimestamp in 34200..37800)
            }

            is LocalTimeAtLocationResult.Error -> {
                fail("getLocalTimeAtLocation error: ${result.errorCode.name}")
            }
        }
    }

    @Test
    fun reverseGeoExpectNoSelectionOnlineMaps() {
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
    fun testDecimalRoundingOfCoordinates() {
        val request = SearchRequest(
            "N45 59 21.0 E9 17 31.8",
            GeoCoordinates(48.145718, 17.118669),
            1
        )
        val results = runBlocking { searchHelper.onlineAutocomplete(request) }
        assert(results[0].title == "N 45°59'21.0\" E 9°17'31.8\"")
    }

    @Test
    fun testDecimalRoundingOfCoordinates2() {
        val request = SearchRequest(
            "N 48.14212° E 17.13729",
            GeoCoordinates(48.145718, 17.118669),
            1
        )
        val results = runBlocking { searchHelper.onlineAutocomplete(request) }
        assert(results[0].title == "N 48°08'31.6\" E 17°08'14.2\"")
    }
}