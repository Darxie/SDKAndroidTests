package cz.feldis.sdkandroidtests.explore

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.timeout
import com.nhaarman.mockitokotlin2.verify
import com.sygic.sdk.navigation.NavigationManager
import com.sygic.sdk.navigation.NavigationManagerProvider
import com.sygic.sdk.navigation.explorer.RouteExplorer
import com.sygic.sdk.navigation.traffic.TrafficManager
import com.sygic.sdk.navigation.traffic.TrafficManagerProvider
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.route.simulator.PositionSimulator
import com.sygic.sdk.route.simulator.RouteDemonstrateSimulatorProvider
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.ktx.NavigationManagerKtx
import cz.feldis.sdkandroidtests.ktx.TrafficManagerKtx
import cz.feldis.sdkandroidtests.mapInstaller.MapDownloadHelper
import cz.feldis.sdkandroidtests.routing.RouteComputeHelper
import cz.feldis.sdkandroidtests.utils.RouteDemonstrateSimulatorAdapter
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import org.junit.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito
import timber.log.Timber

class RouteExploreTests : BaseTest() {

    private lateinit var routeCompute: RouteComputeHelper
    private val navigationManagerKtx = NavigationManagerKtx()
    private val trafficManagerKtx = TrafficManagerKtx()
    private lateinit var trafficManager: TrafficManager

    override fun setUp() {
        super.setUp()
        routeCompute = RouteComputeHelper()
        trafficManager = TrafficManagerProvider.getInstance().get()
    }

    @Test
    fun exploreTrafficOnRoute() = runBlocking {

        trafficManagerKtx.enableTrafficService(trafficManager)

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

        trafficManagerKtx.disableTrafficService(trafficManager)
    }

    @Test
    fun exploreTrafficOnRouteWithDisabledTraffic() = runBlocking {
        trafficManagerKtx.disableTrafficService(trafficManager)

        val listener: RouteExplorer.OnExploreTrafficOnRouteListener = mock(verboseLogging = true)

        val route = routeCompute.onlineComputeRoute(
            GeoCoordinates(48.155195, 17.136827),
            GeoCoordinates(48.289024, 17.264717)
        )

        RouteExplorer.exploreTrafficOnRoute(route, listener)

        verify(listener, never())
            .onExploreTrafficLoaded(any())

        verify(listener, Mockito.timeout(5_000L))
            .onExploreTrafficError(TrafficManager.ErrorCode.SERVICE_DISABLED)

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
            .onExplorePlacesLoaded(argThat { this.isNotEmpty() }, eq(100))

        verify(listener, never())
            .onExplorePlacesError(any())
    }

    @Test
    fun exploreIncidentsOnRoute() {
        val listener: RouteExplorer.OnExploreIncidentsOnRouteListener = mock(verboseLogging = true)

        val route = routeCompute.onlineComputeRoute(
            GeoCoordinates(48.7429, 17.8603),
            GeoCoordinates(48.7457, 17.86)
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

    @Test
    @Ignore("takes too long on simulator in debug")
    fun explorePlacesOnRouteLongRoutePerformance() = runBlocking {
        disableOnlineMaps()
        val listener: NavigationManager.OnPlaceListener = mock(verboseLogging = true)
        val completeListener: PositionSimulator.OnOperationComplete = mock(verboseLogging = true)
        val navigation = NavigationManagerProvider.getInstance().get()

        val mapDownloadHelper = MapDownloadHelper()
        mapDownloadHelper.installAndLoadMap("sk")
        mapDownloadHelper.installAndLoadMap("at")
        mapDownloadHelper.installAndLoadMap("it")

        val route =
            routeCompute.offlineRouteCompute(
                GeoCoordinates(48.15343979881289, 17.13600525926161),
                GeoCoordinates(40.74965825876095, 14.504129256547257) // pompeje
            )

        navigation.addOnPlaceListener(listener)
        navigationManagerKtx.setRouteForNavigation(route, navigation)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        val simulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.startSimulator(simulatorAdapter)

        val startTime = System.currentTimeMillis()

        verify(
            listener,
            Mockito.timeout(50_000L)
        )
            .onPlaceInfoChanged(argThat {
                val isNonEmpty = this.isNotEmpty()
                if (isNonEmpty) {
                    // Calculate elapsed time
                    val elapsedTime = System.currentTimeMillis() - startTime
                    Timber.i("Time elapsed waiting for PoR: $elapsedTime ms")
                    simulator.stop(completeListener)
                    verify(completeListener, timeout(5_000L)).onComplete()
                }
                return@argThat isNonEmpty
            })
    }

}