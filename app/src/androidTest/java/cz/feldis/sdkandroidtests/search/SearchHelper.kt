package cz.feldis.sdkandroidtests.search

import com.nhaarman.mockitokotlin2.*
import com.sygic.sdk.places.Place
import com.sygic.sdk.search.*
import cz.feldis.sdkandroidtests.BaseTest
import java.nio.ByteBuffer
import java.util.*

class SearchHelper : BaseTest() {

    private val searchManager = SearchManagerProvider.getInstance().get()

    fun offlineAutocomplete() {
    }

    fun onlineAutocomplete(autocompleteRequest: SearchRequest): List<AutocompleteResult> {
        val session = searchManager.newOnlineSession()
        val listener: AutocompleteResultListener = mock(verboseLogging = true)
        val argumentCaptor = argumentCaptor<List<AutocompleteResult>>()
        session.autocomplete(autocompleteRequest, listener)
        verify(listener, timeout(10_000L)).onAutocomplete(
            argumentCaptor.capture()
        )
        verify(listener, never()).onAutocompleteError(
            any()
        )
        searchManager.closeSession(session)
        return argumentCaptor.firstValue
    }

    fun offlineGeocode() {

    }

    fun onlineGeocode() {

    }

    fun offlineReverseGeocode() {
    }

    fun onlineReverseGeocode() {
    }

    fun offlineSearchPlaces(placeRequest: PlaceRequest): List<Place> {
        val searchCallback : CreateSearchCallback<OfflineMapSearch> = mock(verboseLogging = true)
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
        val searchCallback : CreateSearchCallback<OnlineMapSearch> = mock(verboseLogging = true)
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

    fun byteArrayToUUID(byteArray: ByteArray): String {
        val buffer = ByteBuffer.wrap(byteArray)
        val uuid = UUID(buffer.long, buffer.long).toString()
        return uuid
    }
}