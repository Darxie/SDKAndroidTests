package cz.feldis.sdkandroidtests.explore

import androidx.test.filters.RequiresDevice
import com.sygic.sdk.navigation.NavigationManager
import com.sygic.sdk.navigation.NavigationManagerProvider
import com.sygic.sdk.navigation.explorer.RouteExplorer
import com.sygic.sdk.navigation.explorer.RouteExplorerProvider
import com.sygic.sdk.navigation.traffic.TrafficManager
import com.sygic.sdk.navigation.traffic.TrafficManagerProvider
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.route.ChargingStation
import com.sygic.sdk.route.RoutingOptions
import com.sygic.sdk.route.simulator.PositionSimulator
import com.sygic.sdk.route.simulator.RouteDemonstrateSimulatorProvider
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.ktx.NavigationManagerKtx
import cz.feldis.sdkandroidtests.ktx.TrafficManagerKtx
import cz.feldis.sdkandroidtests.mapInstaller.MapDownloadHelper
import cz.feldis.sdkandroidtests.routing.RouteComputeHelper
import cz.feldis.sdkandroidtests.utils.RouteDemonstrateSimulatorAdapter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
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

        RouteExplorerProvider.getInstance().get().exploreTrafficOnRoute(route, listener)

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

        RouteExplorerProvider.getInstance().get().exploreTrafficOnRoute(route, listener)

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

        RouteExplorerProvider.getInstance().get().explorePlacesOnRoute(route, list, listener)

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
        RouteExplorerProvider.getInstance().get().exploreIncidentsOnRoute(route, emptyList(), listener)

        verify(
            listener,
            Mockito.timeout(30_000L)
        )
            .onExploreIncidentsLoaded(anyList(), eq(100))

        verify(listener, never())
            .onExploreIncidentsError(any())
    }

    @Test
    @RequiresDevice
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
            Mockito.timeout(50_000L).atLeastOnce()
        )
            .onPlaceInfoChanged(argThat {
                val isNonEmpty = this.isNotEmpty()
                if (isNonEmpty) {
                    // Calculate elapsed time
                    val elapsedTime = System.currentTimeMillis() - startTime
                    Timber.i("Time elapsed waiting for PoR: $elapsedTime ms")
                    simulator.stop(completeListener)
                    verify(completeListener, timeout(5_000L).atLeastOnce()).onComplete()
                }
                return@argThat isNonEmpty
            })
    }

    @Test
    fun exploreChargingStationsOnRoute() = runBlocking {
        disableOnlineMaps()
        val listener: RouteExplorer.OnExploreChargingStationsOnRouteListener =
            mock(verboseLogging = true)

        val mapDownloadHelper = MapDownloadHelper()
        mapDownloadHelper.installAndLoadMap("sk")

        val route =
            routeCompute.offlineRouteCompute(
                GeoCoordinates(48.12749909071542, 17.126906729580128),
                GeoCoordinates(48.962803073321275, 18.162986338115697),
                routingOptions = RoutingOptions().apply {
                    vehicleProfile =
                        RouteComputeHelper().createDefaultElectricVehicleProfile(50f, 50f)
                }
            )

        // Store the aggregated list of charging stations
        val aggregatedChargingStations = mutableListOf<ChargingStation>()
        var previousSize = 0
        var firstInvocationSize = -1
        var lastInvocationSize = -1

        doAnswer { invocation ->
            val chargingStations = invocation.getArgument<List<ChargingStation>>(0)
            val progress = invocation.getArgument<Int>(1)

            // Capture the size during the first callback
            if (firstInvocationSize == -1 && chargingStations.isNotEmpty()) {
                firstInvocationSize = chargingStations.size
            }

            // Check that the list size grows with each callback
            assertTrue(chargingStations.size >= previousSize)
            aggregatedChargingStations.addAll(chargingStations)
            previousSize = chargingStations.size

            // Capture the size during the last invocation when progress is 100
            if (progress == 100) {
                lastInvocationSize = chargingStations.size

                // Ensure that the last invocation contains more charging stations than the first
                assertTrue(lastInvocationSize > firstInvocationSize)

                // Optionally, you can also check the final aggregated list here
            }
        }.whenever(listener).onExploreChargingStationsLoaded(any(), any())

        RouteExplorerProvider.getInstance().get().exploreChargingStationsOnRoute(
            route,
            RouteComputeHelper().createDefaultElectricVehicleProfile(50f, 50f),
            listener
        )

        verify(listener, never()).onExploreChargingStationsError(any())
        verify(listener, timeout(50_000L)).onExploreChargingStationsLoaded(any(), eq(100))

        // Ensure that the first and last invocation checks were performed
        assertTrue(firstInvocationSize >= 0)  // Ensure that the first invocation was recorded
        assertTrue(lastInvocationSize >= 0)   // Ensure that the last invocation was recorded
    }
}