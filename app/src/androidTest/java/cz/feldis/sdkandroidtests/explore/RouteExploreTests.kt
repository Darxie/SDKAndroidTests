package cz.feldis.sdkandroidtests.explore

import com.nhaarman.mockitokotlin2.*
import com.sygic.sdk.navigation.explorer.RouteExplorer
import com.sygic.sdk.navigation.traffic.TrafficManagerProvider
import com.sygic.sdk.position.GeoCoordinates
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.routing.RouteComputeHelper
import org.junit.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito

class RouteExploreTests : BaseTest() {

    @Test
    fun exploreTrafficOnRoute() {
        TrafficManagerProvider.getInstance().get().enableTrafficService()
        val routeCompute = RouteComputeHelper()

        val listener: RouteExplorer.OnExploreTrafficOnRouteListener = mock(verboseLogging = true)

        val route = routeCompute.onlineComputeRoute(
            GeoCoordinates(48.155195, 17.136827),
            GeoCoordinates(48.289024, 17.264717)
        )

        RouteExplorer.exploreTrafficOnRoute(route, listener)

        verify(listener, Mockito.timeout(10_000L))
            .onExploreTrafficLoaded(any())

        verify(listener, never())
            .onExploreTrafficError(any())

        TrafficManagerProvider.getInstance().get().disableTrafficService()
    }

    /**
     * Explore test on places on route
     *
     * In this test we compute route and via route explorer explore places
     * type of SYRestArea and SYPetrolStation on this route.
     * We verify that onExplorePlacesLoaded was invoked with progress equals 100.
     */
    @Test
    fun onExplorePlacesOnRoute() {
        val routeCompute = RouteComputeHelper()
        val listener: RouteExplorer.OnExplorePlacesOnRouteListener = mock(verboseLogging = true)
        val route =
            routeCompute.onlineComputeRoute(
                GeoCoordinates(48.167749, 17.184778),
                GeoCoordinates(48.586029, 17.824360)
            )
        val list = listOf("SYRestArea", "SYPetrolStation")

        RouteExplorer.explorePlacesOnRoute(route, list, listener)

        verify(
            listener,
            Mockito.timeout(15_000L)
        )
            .onExplorePlacesLoaded(anyList(), eq(100))

        verify(listener, never())
            .onExplorePlacesError(any())
    }

    @Test
    fun exploreIncidentsOnRoute() {
        val routeCompute = RouteComputeHelper()
        val listener: RouteExplorer.OnExploreIncidentsOnRouteListener = mock(verboseLogging = true)

        val route = routeCompute.onlineComputeRoute(
            GeoCoordinates(48.167749, 17.184778),
            GeoCoordinates(48.586029, 17.824360)
        )
        RouteExplorer.exploreIncidentsOnRoute(route, emptyList(), listener)

        verify(
            listener,
            Mockito.timeout(30_000L)
        )
            .onExploreIncidentsLoaded(anyList(), eq(100))

        verify(listener, never())
            .onExploreIncidentsError(any())
    }

}