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
        val session = searchManager.newOnlineSession()
        searchManager.closeSession(session)
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
        val session = searchManager.newOnlineSession()
        searchManager.closeSession(session)
    }

    fun onlineGeocode() {
        val session = searchManager.newOnlineSession()
        searchManager.closeSession(session)
    }

    fun offlineReverseGeocode() {
    }

    fun onlineReverseGeocode() {
    }

    fun offlineSearchPlaces(placeRequest: PlaceRequest): List<Place> {
        val session = searchManager.newOfflineSession()
        val listener: PlacesListener = mock(verboseLogging = true)
        val argumentCaptor = argumentCaptor<List<Place>>()
        session.searchPlaces(placeRequest, listener)

        verify(listener, timeout(10_000L)).onPlacesLoaded(
            argumentCaptor.capture(),
            isNull()
        )
        searchManager.closeSession(session)
        return argumentCaptor.firstValue
    }


    fun onlineSearchPlaces(placeRequest: PlaceRequest): List<Place> {
        val session = searchManager.newOnlineSession()
        val listener: PlacesListener = mock(verboseLogging = true)
        session.searchPlaces(placeRequest, listener)
        val argumentCaptor = argumentCaptor<List<Place>>()

        verify(listener, timeout(10_000L)).onPlacesLoaded(
            argumentCaptor.capture(),
            isNull()
        )
        searchManager.closeSession(session)
        return argumentCaptor.firstValue
    }

    fun byteArrayToUUID(byteArray: ByteArray): String {
        val buffer = ByteBuffer.wrap(byteArray)
        val uuid = UUID(buffer.long, buffer.long).toString()
        return uuid
    }
}