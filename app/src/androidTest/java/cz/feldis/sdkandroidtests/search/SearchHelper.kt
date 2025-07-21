package cz.feldis.sdkandroidtests.search

import com.sygic.sdk.places.Place
import com.sygic.sdk.search.AutocompleteResult
import com.sygic.sdk.search.AutocompleteResultListener
import com.sygic.sdk.search.CreateSearchCallback
import com.sygic.sdk.search.CustomPlacesSearch
import com.sygic.sdk.search.GeocodeLocationRequest
import com.sygic.sdk.search.GeocodingResult
import com.sygic.sdk.search.GeocodingResultListener
import com.sygic.sdk.search.GeocodingResultsListener
import com.sygic.sdk.search.OfflineMapSearch
import com.sygic.sdk.search.OnlineMapSearch
import com.sygic.sdk.search.PlaceRequest
import com.sygic.sdk.search.PlacesListener
import com.sygic.sdk.search.ResultStatus
import com.sygic.sdk.search.ResultType
import com.sygic.sdk.search.SearchManagerProvider
import com.sygic.sdk.search.SearchRequest
import kotlinx.coroutines.runBlocking
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import java.nio.ByteBuffer
import java.util.UUID

class SearchHelper {

    private val searchManager = runBlocking { SearchManagerProvider.getInstance() }


    class NoResultsException : RuntimeException("Autocomplete returned no results")

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
        val errorCaptor = argumentCaptor<ResultStatus>()
        searchManager.createOnlineMapSearch(createSearchListener)
        verify(createSearchListener, timeout(3_000L)).onSuccess(searchCaptor.capture())
        val search = searchCaptor.lastValue

        val session = search.createSession()

        var attempts = 0
        val maxRetries = 3
        val retryDelay = 5000L // 2 seconds delay between retries

        while (attempts < maxRetries) {
            session.autocomplete(autocompleteRequest, autocompleteResultListener)

            try {
                verify(autocompleteResultListener, timeout(10_000L)).onAutocomplete(
                    resultCaptor.capture()
                )
                verify(autocompleteResultListener, never()).onAutocompleteError(any())
                return resultCaptor.firstValue
            } catch (e: Exception) {
                verify(autocompleteResultListener).onAutocompleteError(errorCaptor.capture())
                val capturedError = errorCaptor.lastValue

                if (capturedError == ResultStatus.UNSPECIFIED_ERROR && attempts < maxRetries - 1) {
                    attempts++
                    Thread.sleep(retryDelay) // Wait before retrying
                } else {
                    throw e // Rethrow the exception
                }
            }
        }

        throw RuntimeException("Failed to retrieve autocomplete results after $maxRetries attempts")
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
        verify(createSearchListener, timeout(5_000L)).onSuccess(searchCaptor.capture())
        val search = searchCaptor.lastValue

        val session = search.createSession()

        session.autocomplete(searchRequest, autocompleteResultListener)

        verify(autocompleteResultListener, timeout(10_000L)).onAutocomplete(
            resultCaptor.capture()
        )
        verify(autocompleteResultListener, never()).onAutocompleteError(any())
        if (resultCaptor.firstValue.isEmpty()) {
            session.close()
            throw NoResultsException()
        }
        assert(resultCaptor.firstValue[0].type == ResultType.CUSTOM_PLACE) // fail here already
        session.close()
        return resultCaptor.firstValue
    }

    fun offlineAutocompleteCustomPlacesWithDataset(
        searchRequest: SearchRequest,
        dataset: String
    ): List<AutocompleteResult> {
        val autocompleteResultListener: AutocompleteResultListener = mock(verboseLogging = true)
        val createSearchListener: CreateSearchCallback<CustomPlacesSearch> =
            mock(verboseLogging = true)
        val searchCaptor = argumentCaptor<CustomPlacesSearch>()
        val resultCaptor = argumentCaptor<List<AutocompleteResult>>()
        searchManager.createCustomPlacesSearchForDataset(dataset, createSearchListener)
        verify(createSearchListener, timeout(5_000L)).onSuccess(searchCaptor.capture())
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

    fun onlineGeocode(searchRequest: SearchRequest): List<GeocodingResult> {
        val geocodeResultListener: GeocodingResultsListener = mock(verboseLogging = true)
        val createSearchListener: CreateSearchCallback<OnlineMapSearch> =
            mock(verboseLogging = true)
        val searchCaptor = argumentCaptor<OnlineMapSearch>()
        val resultCaptor = argumentCaptor<List<GeocodingResult>>()
        searchManager.createOnlineMapSearch(createSearchListener)
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
            anyOrNull()
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
            anyOrNull()
        )

        return argumentCaptor.firstValue
    }

    fun searchCustomPlaces(placeRequest: PlaceRequest): List<Place> {
        val searchCallback: CreateSearchCallback<CustomPlacesSearch> = mock(verboseLogging = true)
        val listener: PlacesListener = mock(verboseLogging = true)

        val customPlacesSearchCaptor = argumentCaptor<CustomPlacesSearch>()
        val argumentCaptor = argumentCaptor<List<Place>>()

        searchManager.createCustomPlacesSearch(searchCallback)

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