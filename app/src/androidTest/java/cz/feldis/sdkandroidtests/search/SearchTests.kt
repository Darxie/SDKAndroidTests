package cz.feldis.sdkandroidtests.search

import com.nhaarman.mockitokotlin2.*
import com.sygic.sdk.places.PlaceCategories
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.search.*
import cz.feldis.sdkandroidtests.BaseTest
import org.junit.Ignore
import org.junit.Test
import org.mockito.Mockito

class SearchTests : BaseTest() {

    private lateinit var searchHelper: SearchHelper

    override fun setUp() {
        super.setUp()
        searchHelper = SearchHelper()
    }

    @Ignore("wtf toto nefunguje")
    @Test
    fun searchPlacesInvalidCategoryOnline() {
        val listener: PlacesListener = mock(verboseLogging = true)
        val searchManager = SearchManagerProvider.getInstance().get()
        val categories = listOf("tu_nemame_kategoriu")
        val request = PlaceRequest(GeoCoordinates(48.145718, 17.118669), categories, 100)
        val session = SearchManagerProvider.getInstance().get().newOnlineSession()

        session.searchPlaces(request, listener)
        searchManager.closeSession(session)

        verify(listener, Mockito.timeout(10_000L))
            .onPlacesError(eq(ResultStatus.INVALID_CATEGORY_TAG))
        verify(listener, Mockito.never()).onPlacesLoaded(any(), any())
    }

    @Test
    fun searchPetrolStationInArea() {
        val position = GeoCoordinates(48.100806, 17.234972)
        val categories = listOf(PlaceCategories.PetrolStation)
        val placeRequest = PlaceRequest(position, categories, 4000)

        val results = searchHelper.onlineSearchPlaces(placeRequest)
        results.forEach {
            assert(it.link.category == PlaceCategories.PetrolStation)
        }
    }

//    @Test
//    fun onlineAutocompleteBratislava() {
//        val request = SearchRequest(
//            "bratislava",
//            GeoCoordinates(48.145718, 17.118669),
//            6
//        )
//        val results = searchHelper.onlineAutocomplete(request)
//        results.forEach {
//            if (it.subtitle == "Bratislava") {
//                pass
//            }
//        }
//    }

//    @Test
//    fun geocode() {
//        val position = GeoCoordinates(48.100806, 17.234972)
//        val listener: GeocodingResultsListener = mock(verboseLogging = true)
//        val searchManager = SearchManagerProvider.getInstance().get()
//
//        val request = SearchRequest("Lukoil pálenisko", position)
//        val session = SearchManagerProvider.getInstance().get().newOnlineSession()
//
//        session.geocode(request, listener)
//
//        verify(listener, timeout(10_000L)).onGeocodingResults(
//            argThat {
//                print(this[0].)
//            }
//        )
//        searchManager.closeSession(session)
//    }


}