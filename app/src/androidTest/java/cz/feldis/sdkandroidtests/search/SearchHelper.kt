package cz.feldis.sdkandroidtests.search

import com.nhaarman.mockitokotlin2.*
import com.sygic.sdk.places.Place
import com.sygic.sdk.search.*
import cz.feldis.sdkandroidtests.BaseTest
import java.nio.ByteBuffer
import java.util.*

class SearchHelper {

    private val searchManager = SearchManagerProvider.getInstance().get()

    fun offlineAutocomplete(searchRequest: SearchRequest): List<AutocompleteResult> {
        val autocompleteResultListener: AutocompleteResultListener = mock(verboseLogging = true)
        val createSearchListener: CreateSearchCallback<OfflineMapSearch> =
            mock(verboseLogging = true)
        val searchCaptor = argumentCaptor<OfflineMapSearch>()
        val resultCaptor = argumentCaptor<List<AutocompleteResult>>()
        searchManager.createOfflineMapSearch(createSearchListener)
        verify(createSearchListener, timeout(3_000L)).onSuccess(searchCaptor.capture())
        val search = searchCaptor.lastValue

        val session = search.createSession()

        session.autocomplete(searchRequest, autocompleteResultListener)

        verify(autocompleteResultListener, timeout(10_000L)).onAutocomplete(
            resultCaptor.capture()
        )
        verify(autocompleteResultListener, never()).onAutocompleteError(any())
        return resultCaptor.firstValue
    }

    fun onlineAutocomplete(autocompleteRequest: SearchRequest): List<AutocompleteResult> {
        val autocompleteResultListener: AutocompleteResultListener = mock(verboseLogging = true)
        val createSearchListener: CreateSearchCallback<OnlineMapSearch> = mock()
        val searchCaptor = argumentCaptor<OnlineMapSearch>()
        val resultCaptor = argumentCaptor<List<AutocompleteResult>>()
        searchManager.createOnlineMapSearch(createSearchListener)
        verify(createSearchListener, timeout(3_000L)).onSuccess(searchCaptor.capture())
        val search = searchCaptor.lastValue

        val session = search.createSession()

        session.autocomplete(autocompleteRequest, autocompleteResultListener)

        verify(autocompleteResultListener, timeout(10_000L)).onAutocomplete(
            resultCaptor.capture()
        )
        verify(autocompleteResultListener, never()).onAutocompleteError(any())
        return resultCaptor.firstValue
    }

    //ToDo doesnt work
    fun offlineGeocodeLocation(geocodeRequest: GeocodeLocationRequest): GeocodingResult {
        val geocodingResultListener: GeocodingResultListener = mock(verboseLogging = true)
        val createSearchListener: CreateSearchCallback<OfflineMapSearch> = mock()
        val searchCaptor = argumentCaptor<OfflineMapSearch>()
        val resultCaptor = argumentCaptor<GeocodingResult>()
        searchManager.createOfflineMapSearch(createSearchListener)
        verify(createSearchListener, timeout(3_000L)).onSuccess(searchCaptor.capture())
        val search = searchCaptor.lastValue
        val session = search.createSession()
        session.geocode(geocodeRequest, geocodingResultListener)
        verify(geocodingResultListener, timeout(10_000L)).onGeocodingResult(resultCaptor.capture())
        verify(geocodingResultListener, never()).onGeocodingResultError(any())

        return resultCaptor.lastValue
    }

    fun offlineGeocode(searchRequest: SearchRequest): List<GeocodingResult> {
        val geocodeResultListener: GeocodingResultsListener = mock(verboseLogging = true)
        val createSearchListener: CreateSearchCallback<OfflineMapSearch> =
            mock(verboseLogging = true)
        val searchCaptor = argumentCaptor<OfflineMapSearch>()
        val resultCaptor = argumentCaptor<List<GeocodingResult>>()
        searchManager.createOfflineMapSearch(createSearchListener)
        verify(createSearchListener, timeout(3_000L)).onSuccess(searchCaptor.capture())
        val search = searchCaptor.lastValue

        val session = search.createSession()

        session.geocode(searchRequest, geocodeResultListener)

        verify(geocodeResultListener, timeout(10_000L)).onGeocodingResults(
            resultCaptor.capture()
        )
        verify(geocodeResultListener, never()).onGeocodingResultsError(any())
        return resultCaptor.firstValue
    }

    fun offlineAutocompleteCustomPlaces(searchRequest: SearchRequest): List<AutocompleteResult> {
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
        verify(autocompleteResultListener, never()).onAutocompleteError(any())
        assert(resultCaptor.firstValue[0].type == ResultType.CUSTOM_PLACE) // fail here already
        return resultCaptor.firstValue
    }

    fun onlineGeocode() {

    }

    fun offlineReverseGeocode() {
    }

    fun onlineReverseGeocode() {
    }

    fun offlineSearchPlaces(placeRequest: PlaceRequest): List<Place> {
        val searchCallback: CreateSearchCallback<OfflineMapSearch> = mock(verboseLogging = true)
        val listener: PlacesListener = mock(verboseLogging = true)

        val onlineMapSearchCaptor = argumentCaptor<OfflineMapSearch>()
        val argumentCaptor = argumentCaptor<List<Place>>()

        searchManager.createOfflineMapSearch(searchCallback)

        verify(searchCallback, timeout(10_000L)).onSuccess(
            onlineMapSearchCaptor.capture()
        )
        onlineMapSearchCaptor.firstValue.createSession().searchPlaces(placeRequest, listener)

        verify(listener, timeout(10_000L)).onPlacesLoaded(
            argumentCaptor.capture(),
            isNotNull()
        )

        return argumentCaptor.firstValue
    }


    fun onlineSearchPlaces(placeRequest: PlaceRequest): List<Place> {
        val searchCallback: CreateSearchCallback<OnlineMapSearch> = mock(verboseLogging = true)
        val listener: PlacesListener = mock(verboseLogging = true)

        val onlineMapSearchCaptor = argumentCaptor<OnlineMapSearch>()
        val argumentCaptor = argumentCaptor<List<Place>>()

        searchManager.createOnlineMapSearch(searchCallback)

        verify(searchCallback, timeout(10_000L)).onSuccess(
            onlineMapSearchCaptor.capture()
        )
        onlineMapSearchCaptor.firstValue.createSession().searchPlaces(placeRequest, listener)

        verify(listener, timeout(10_000L)).onPlacesLoaded(
            argumentCaptor.capture(),
            isNotNull()
        )

        return argumentCaptor.firstValue
    }

    fun searchCustomPlaces(placeRequest: PlaceRequest): List<Place> {
        val searchCallback: CreateSearchCallback<CustomPlacesSearch> = mock(verboseLogging = true)
        val listener: PlacesListener = mock(verboseLogging = true)

        val customPlacesSearchCaptor = argumentCaptor<CustomPlacesSearch>()
        val argumentCaptor = argumentCaptor<List<Place>>()

        SearchManagerProvider.getInstance().get().createCustomPlacesSearch(searchCallback)

        verify(searchCallback, timeout(10_000L)).onSuccess(
            customPlacesSearchCaptor.capture()
        )
        customPlacesSearchCaptor.firstValue.createSession().searchPlaces(placeRequest, listener)

        verify(listener, timeout(10_000L)).onPlacesLoaded(
            argumentCaptor.capture(),
            anyOrNull()
        )

        return argumentCaptor.firstValue
    }

    fun byteArrayToUUID(byteArray: ByteArray): String {
        val buffer = ByteBuffer.wrap(byteArray)
        val uuid = UUID(buffer.long, buffer.long).toString()
        return uuid
    }
}