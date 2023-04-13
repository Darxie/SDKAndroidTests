package cz.feldis.sdkandroidtests.search

import com.nhaarman.mockitokotlin2.*
import com.sygic.sdk.places.*
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.search.*
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.mapInstaller.MapDownloadHelper
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.mockito.Mockito
import timber.log.Timber
import java.util.*
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
    fun autocompleteEyckveldCheckResult() {
        mapDownloadHelper.installAndLoadMap("be")
        val position = GeoCoordinates(50.84367811558576, 4.667406856390823)
        val searchRequest = SearchRequest("Eyckveld 7", position)

        val results = searchHelper.offlineAutocomplete(searchRequest)

        assertTrue(results.isNotEmpty())
        assertTrue(results.first().titleHighlights.isNotEmpty())
        assertTrue(results.first().title == "Eyckeveld 7")

    }

    @Test
    @Ignore("SDC-8569")
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
        val listener: GeocodingResultsListener = mock(verboseLogging = true)
        val searchManager = SearchManagerProvider.getInstance().get()

        val request = SearchRequest("Lukoil pálenisko", position)
        val session = SearchManagerProvider.getInstance().get().newOnlineSession()

        session.geocode(request, listener)

        verify(listener, timeout(10_000L)).onGeocodingResults(
            argThat {
                this.forEach {
                    if (it.title == "LUKOIL Pálenisko")
                        return@argThat true
                }
                false
            }

        )
        searchManager.closeSession(session)
    }

    /**
     * Search places test with valid string category and load place with link from search
     *
     * In this test we create place request with Radius 10000, GeoCoordinates (Bratislava 48.145718, 17.118669)
     * and category Bank. Verify search places onPlaceLoaded is not empty and place link name and details is not empty.
     * We get last place link from places and with loadPlaces verify that onPlaceLoaded is Bank place category.
     */
    @Test
    fun searchPlacesValidCategoryBankOnline() {
        val listener: PlacesListener = mock(verboseLogging = true)
        val searchManager = SearchManagerProvider.getInstance().get()

        val categories = listOf(PlaceCategories.Bank)
        val request = PlaceRequest(GeoCoordinates(48.145718, 17.118669), categories, 10000)
        val session = searchManager.newOnlineSession()

        session.searchPlaces(request, listener)

        verify(listener, Mockito.timeout(10_000L))
            .onPlacesLoaded(argThat {
                if (this.isEmpty()) {
                    Timber.e("List of loaded places is empty")
                    return@argThat false
                }
                for (place in this) {
                    if (place.link.name.isEmpty() || place.details.isEmpty()) {
                        Timber.e("Place link name or place details are empty")
                        return@argThat false
                    }
                    if (place.link.category != PlaceCategories.Bank) {
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
                if (this.isEmpty()) {
                    Timber.e("List of loaded places is empty")
                    return@argThat false
                }
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

    /**
     * Search places and loadExternalPlaceId test
     *
     * In this test we create place request with Radius 1000, GeoCoordinates (Egypt 29.978296, 31.132839)
     * and with category ImportantTouristAttraction. We verify that search places with this request return onPlaceLoaded
     * and list of objects  is not empty, also whether places link name in list is equals "Pyramid of Khufu"
     * and "El Giza Monuments Area". According to this links we can  verify whether loadExternalPlaceId with place link from previous search places
     * equals expected Khufu External Id and expected Giza External Id
     */

    @Test
    fun loadExternalPlaceId() {
        mapDownloadHelper.installAndLoadMap("eg")
        val searchManager = SearchManagerProvider.getInstance().get()
        val placesManager = PlacesManagerProvider.getInstance().get()
        val listener = Mockito.mock(
            PlacesListener::class.java,
            Mockito.withSettings().verboseLogging()
        )

        val listenerExternalIdKhufu = Mockito.mock(
            PlacesManager.PlaceExternalIdListener::class.java,
            Mockito.withSettings().verboseLogging()
        )

        val listenerExternalIdGiza = Mockito.mock(
            PlacesManager.PlaceExternalIdListener::class.java,
            Mockito.withSettings().verboseLogging()
        )

        val category = listOf(PlaceCategories.ImportantTouristAttraction)
        val request = PlaceRequest(GeoCoordinates(29.978296, 31.132839), category, 1000)
        val session = searchManager.newOfflineSession()

        session.searchPlaces(request, listener)

        val expectedKhufuExternalId = "002f80f0-002d-bef0-6632-356165633562"
        val expectedGizaExternalId = "002f80f0-002d-bfb8-3665-383732623732"

        verify(listener, timeout(10000))
            .onPlacesLoaded(argThat {
                if (this.isEmpty()) {
                    Timber.e("The list returned by searchPlaces is empty")
                    return@argThat false
                }

                for (place in this) {
                    if (place.link.name == "Pyramid of Khufu") {
                        placesManager.loadExternalPlaceId(place.link, listenerExternalIdKhufu)
                        verify<PlacesManager.PlaceExternalIdListener>(
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
                        verify<PlacesManager.PlaceExternalIdListener>(
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
                true
            }, any())

    }

    /**
     * Search places test with valid string category
     *
     *
     * In this test we create place request with Radius 100, GeoCoordinates (Egypt 29.978296, 31.132839)
     * and with category ImportantTouristAttraction. We verify that search places with this request return onPlaceLoaded
     * and list of objects  is not empty, also whether places link name and details is not empty. We also expected that
     * details of place with link name Pyramid of Khufu will be same as expectedDetail.
     */
    @Test
    fun searchPlacesDetails() {
        mapDownloadHelper.installAndLoadMap("eg")
        val searchManager = SearchManagerProvider.getInstance().get()
        val listener: PlacesListener = mock(verboseLogging = true)

        val categories = listOf(PlaceCategories.ImportantTouristAttraction)
        val request = PlaceRequest(GeoCoordinates(29.9774, 31.1323), categories, 1000)
        val session = searchManager.newOfflineSession()

        val expectedDetail = mutableListOf<PlaceDetail>()
        expectedDetail.add(PlaceDetail(PlaceDetailAttributes.City, "Desert of Giza Governorate"))
//        expectedDetail.add(PlaceDetail(PlaceDetailAttributes.Street, "al-Qahirah - al-Fayyoum"))
        expectedDetail.add(PlaceDetail(PlaceDetailAttributes.Iso, "eg"))
        expectedDetail.add(PlaceDetail(PlaceDetailAttributes.Entry, "29.97429;31.12732"))

        session.searchPlaces(request, listener)

        verify(listener, Mockito.timeout(10_000L))
            .onPlacesLoaded(argThat {
                Timber.d("Size of searchPlaces response: ${this.size}")

                if (this.isEmpty()) {
                    Timber.e("The list returned by searchPlaces is empty")
                    return@argThat false
                }
                if (this.size < 2) {
                    Timber.e("The list returned by searchPlaces has less than 2 objects")
                    return@argThat false
                }
                for (place in this) {
                    if (place.link.name.isEmpty() || place.details.isEmpty()) {
                        Timber.e("Place link name is empty or place details are empty ")
                        return@argThat false
                    }
                    if (place.link.name == "Pyramid of Khufu")
                        if (place.details != expectedDetail) {
                            Timber.e("Place details not equal to expected details ")
                            return@argThat false
                        }
                }
                true
            }, any())
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

        assertTrue(timestampCaptor.lastValue - utcUnixTimestamp in 36000..37800) // in seconds
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

}