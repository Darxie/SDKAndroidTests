package cz.feldis.sdkandroidtests.navigation

import org.mockito.kotlin.*
import com.sygic.sdk.navigation.NavigationManager
import com.sygic.sdk.navigation.NavigationManager.OnRouteProgressListener
import com.sygic.sdk.navigation.NavigationManagerProvider
import com.sygic.sdk.navigation.routeeventnotifications.HighwayExitInfo
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.route.simulator.NmeaLogSimulatorProvider
import com.sygic.sdk.route.simulator.RouteDemonstrateSimulatorProvider
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.NmeaFileDataProvider
import cz.feldis.sdkandroidtests.ktx.NavigationManagerKtx
import cz.feldis.sdkandroidtests.ktx.PositionManagerKtx
import cz.feldis.sdkandroidtests.mapInstaller.MapDownloadHelper
import cz.feldis.sdkandroidtests.routing.RouteComputeHelper
import cz.feldis.sdkandroidtests.utils.NmeaLogSimulatorAdapter
import cz.feldis.sdkandroidtests.utils.RouteDemonstrateSimulatorAdapter
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.InOrder
import org.mockito.Mockito

class OnlineNavigationTests : BaseTest() {
    private lateinit var routeCompute: RouteComputeHelper
    private lateinit var mapDownload: MapDownloadHelper
    private val navigationManagerKtx = NavigationManagerKtx()
    private val positionManagerKtx = PositionManagerKtx()
    private lateinit var navigation: NavigationManager

    @Before
    override fun setUp() {
        super.setUp()
        routeCompute = RouteComputeHelper()
        mapDownload = MapDownloadHelper()
        mapDownload.ensureMapNotInstalled("sk")
        navigation = NavigationManagerProvider.getInstance().get()
    }

    @Test
    fun testGetRouteProgressAsync() = runBlocking {
        val listener: OnRouteProgressListener = mock(verboseLogging = true)

        val start = GeoCoordinates(48.101936, 17.233684)
        val destination = GeoCoordinates(48.145644, 17.127011)
        val routeCompute = RouteComputeHelper()
        val route = routeCompute.onlineComputeRoute(start, destination)
        navigationManagerKtx.setRouteForNavigation(route, navigation)

        NavigationManagerProvider.getInstance().get().getRouteProgress(
            listener
        )

        verify(listener, timeout(5_000)).onRouteProgress(any())
    }

    /**
     * Navigation test on sign post changed
     *
     * In this test we compute route and set it for navigation.
     * Via simulator provider we set this route and start demonstrate navigation.
     * We verify that onSignpostChanged was invoked.
     */
    @Test
    fun onSignpostChangedTest() = runBlocking {
        val listener: NavigationManager.OnSignpostListener = mock(verboseLogging = true)
        val route =
            routeCompute.onlineComputeRoute(
                GeoCoordinates(48.143133, 17.175447),
                GeoCoordinates(48.171427, 17.191148)
            )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        val demonstrateSimulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.startSimulator(demonstrateSimulatorAdapter)
        navigation.addOnSignpostListener(listener)

        Mockito.verify(listener, Mockito.timeout(STATUS_TIMEOUT))
            .onSignpostChanged(argThat {
                forEach {
                    if (it.signElements.isNotEmpty()) {
                        return@argThat true
                    }
                }
                false
            })

        navigation.removeOnSignpostListener(listener)
        navigationManagerKtx.stopNavigation(navigation)
        navigationManagerKtx.stopSimulator(demonstrateSimulatorAdapter)
        simulator.destroy()
        
    }

    /**
     * Navigation test on direction info changed
     *
     * In this test we compute route and set it for navigation.
     * Via simulator provider we set this route and start demonstrate navigation.
     * We verify that onDirectionInfoChanged was invoked.
     */
    @Test
    fun onDirectionInfoChangedTest() = runBlocking {
        val listener = Mockito.mock(
            NavigationManager.OnDirectionListener::class.java,
            Mockito.withSettings().verboseLogging()
        )
        val route =
            routeCompute.onlineComputeRoute(
                GeoCoordinates(48.0977, 17.2382),
                GeoCoordinates(48.0986, 17.2345)
            )
        Assert.assertNotNull(route)

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        val demonstrateSimulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.startSimulator(demonstrateSimulatorAdapter)
        navigation.addOnDirectionListener(listener)

        Mockito.verify(listener, Mockito.timeout(STATUS_TIMEOUT))
            .onDirectionInfoChanged(argThat {
                if (this.primary.nextRoadName == "Hlavná") {
                    return@argThat true
                }
                false
            })

        navigation.removeOnDirectionListener(listener)
        navigationManagerKtx.stopNavigation(navigation)
        navigationManagerKtx.stopSimulator(demonstrateSimulatorAdapter)
        simulator.destroy()
        
    }

    /**
     * Navigation test on speed limit info changed
     *
     * In this test we compute route and set it for navigation.
     * Via simulator provider we set this route and start demonstrate navigation.
     * We verify that onSpeedLimitInfoChanged was invoked with a non-null object.
     *
     */
    @Test
    fun onSpeedLimitInfoChanged() = runBlocking {

        val listener: NavigationManager.OnSpeedLimitListener = mock(verboseLogging = true)
        val route =
            routeCompute.onlineComputeRoute(
                GeoCoordinates(48.128027, 17.094285),
                GeoCoordinates(48.131233, 17.112298)
            )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        val demonstrateSimulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.setSpeedMultiplier(demonstrateSimulatorAdapter, 4F)
        navigationManagerKtx.startSimulator(demonstrateSimulatorAdapter)
        navigation.addOnSpeedLimitListener(listener)

        Mockito.verify(
            listener, timeout(STATUS_TIMEOUT)
        ) // first call contains previous values
            .onSpeedLimitInfoChanged(
                argThat {
                    return@argThat this.nextSpeedLimit == 70.0f
                }
            )

        navigation.removeOnSpeedLimitListener(listener)
        navigationManagerKtx.stopNavigation(navigation)
        navigationManagerKtx.stopSimulator(demonstrateSimulatorAdapter)
        simulator.destroy()
        
    }

    /**
     * Navigation test on railway crossing
     *
     * In this test we compute route and set it for navigation.
     * Via simulator provider we set this route and start demonstrate navigation.
     * We verify that onRailwayCrossingInfoChanged was invoked with a RailwayCrossingInfo
     * that has a valid position.
     */
    @Test
    fun onRailwayCrossingTest() = runBlocking {

        val listener: NavigationManager.OnRailwayCrossingListener = mock(verboseLogging = true)
        val route =
            routeCompute.onlineComputeRoute(
                GeoCoordinates(48.133798, 17.168522),
                GeoCoordinates(48.136994, 17.155435)
            )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        navigation.addOnRailwayCrossingListener(listener)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        val demonstrateSimulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.startSimulator(demonstrateSimulatorAdapter)

        Mockito.verify(
            listener,
            Mockito.timeout(STATUS_TIMEOUT)
        )
            .onRailwayCrossingInfoChanged(argThat {
                if (this.position.isValid) {
                    return@argThat true
                }
                false
            })

        navigationManagerKtx.stopSimulator(demonstrateSimulatorAdapter)
        simulator.destroy()
        navigation.removeOnRailwayCrossingListener(listener)
        navigationManagerKtx.stopNavigation(navigation)
        
    }

    /**
     * Navigation test on highway exit
     *
     * In this test we compute route and set it for navigation.
     * Via simulator provider we set this route and start demonstrate navigation.
     * We verify that onHighwayExitInfoChanged was invoked with a non-null list.
     */
    @Test
    fun onHighwayExitTest() = runBlocking {

        val listener: NavigationManager.OnHighwayExitListener = mock(verboseLogging = true)
        val route =
            routeCompute.onlineComputeRoute(
                GeoCoordinates(48.1581, 17.1822),
                GeoCoordinates(48.1647, 17.1837)
            )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        navigation.addOnHighwayExitListener(listener)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        val demonstrateSimulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.startSimulator(demonstrateSimulatorAdapter)

        Mockito.verify(
            listener,
            Mockito.timeout(STATUS_TIMEOUT)
        )
            .onHighwayExitInfoChanged(argThat {
                for (exit in this) {
                    if (exit.exitNumber == "10" && exit.exitSide == HighwayExitInfo.ExitSide.Right) {
                        return@argThat true
                    }
                }
                false
            })

        navigationManagerKtx.stopSimulator(demonstrateSimulatorAdapter)
        simulator.destroy()
        navigation.removeOnHighwayExitListener(listener)
        navigationManagerKtx.stopNavigation(navigation)
        
    }

    /**
     * Navigation test on route changed
     *
     * In this test we compute route and set it for navigation.
     * Via Nmea Log Recorder we set route from assets/SVK-Kosicka.nmea and start nmea log simulation.
     * We verify that the onRouteChanged callback is called and the route that we get is not null.
     */
    @Test
    fun onRouteChangedTest() = runBlocking {

        val listener: NavigationManager.OnRouteChangedListener = mock(verboseLogging = true)

        val route = routeCompute.onlineComputeRoute(
            GeoCoordinates(48.1447, 17.1317),
            GeoCoordinates(48.1461, 17.1285)
        )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        val nmeaDataProvider = NmeaFileDataProvider(appContext, "SVK-Kosicka.nmea")
        val logSimulator = NmeaLogSimulatorProvider.getInstance(nmeaDataProvider).get()
        val logSimulatorAdapter = NmeaLogSimulatorAdapter(logSimulator)
        navigationManagerKtx.startSimulator(logSimulatorAdapter)
        navigation.addOnRouteChangedListener(listener)
        navigationManagerKtx.setSpeedMultiplier(logSimulatorAdapter, 2F)

        Mockito.verify(
            listener,
            Mockito.timeout(STATUS_TIMEOUT)
        )
            .onRouteChanged(argThat {
                if (this != route) {
                    return@argThat true
                } else false
            }, eq(NavigationManager.RouteUpdateStatus.Success))

        navigationManagerKtx.stopSimulator(logSimulatorAdapter)
        logSimulator.destroy()
        navigation.removeOnRouteChangedListener(listener)
        navigationManagerKtx.stopNavigation(navigation)
        
    }

//    @Test
//    fun onGuidedRouteChangedTest() {
//        
//        val listener: NavigationManager.OnRouteChangedListener = mock(verboseLogging = true)
//
//        val start = GeoCoordinates(48.1432, 17.1308)
//        val destination = GeoCoordinates(48.1455, 17.1263)
//
//        val guidedRouteProfile = GuidedRouteProfile()
//        val routeRequest = RouteRequest()
//
//        val route = routeCompute.onlineComputeRoute(
//            GeoCoordinates(48.1432, 17.1308),
//            GeoCoordinates(48.1455, 17.1263)
//        )
//
//        navigationManagerKtx.setRouteForNavigation(route, navigation)
//        val logSimulator = NmeaLogSimulatorProvider.getInstance("SVK-Kosicka.nmea").get()
//        logval demonstrateSimulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
//        navigationManagerKtx.startSimulator(demonstrateSimulatorAdapter)
//        navigation.addOnRouteChangedListener(listener)
//        lognavigationManagerKtx.setSpeedMultiplier(demonstrateSimulatorAdapter, 2F)
//
//        Mockito.verify(
//            listener,
//            Mockito.timeout(STATUS_TIMEOUT)
//        )
//            .onRouteChanged(argThat {
//                if (this != route)  {
//                    return@argThat true
//                }
//                else false
//            }, eq(NavigationManager.RouteUpdateStatus.Success))
//
//        lognavigationManagerKtx.stopSimulator(demonstrateSimulatorAdapter)
//        logSimulator.destroy()
//        navigation.removeOnRouteChangedListener(listener)
//        navigation.stopNavigation()
//    }


    /**
     * Navigation test on lane listener
     *
     * In this test we compute route and set it for navigation.
     * Via simulator provider we set this route and start demonstrate navigation.
     * We verify that onLaneInfoChanged was invoked.
     */
    @Test
    fun onLaneListenerTest() = runBlocking {

        val listener: NavigationManager.OnLaneListener = mock(verboseLogging = true)

        val route =
            routeCompute.onlineComputeRoute(
                GeoCoordinates(48.147682401781026, 17.14365655304184),
                GeoCoordinates(48.15310362223699, 17.147190865317768)
            )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        navigation.addOnLaneListener(listener)

        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        val demonstrateSimulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.setSpeedMultiplier(demonstrateSimulatorAdapter, 4F)
        navigationManagerKtx.startSimulator(demonstrateSimulatorAdapter)

        Mockito.verify(
            listener,
            Mockito.timeout(STATUS_TIMEOUT)
        )
            .onLaneInfoChanged(argThat {
                if (this.simpleLanesInfo?.lanes?.isNotEmpty() == true) {
                    return@argThat true
                }
                false
            })

        navigationManagerKtx.stopSimulator(demonstrateSimulatorAdapter)
        simulator.destroy()
        navigation.removeOnLaneListener(listener)
        navigationManagerKtx.stopNavigation(navigation)
        
    }

    /**
     * Navigation test on lane listener
     *
     * In this test we compute route and set it for navigation.
     * Via simulator provider we set this route for simulation with
     * 2 speed limit multiplier and start demonstrate navigation.
     * We verify that onSharpCurveInfoChanged was invoked.
     */
    @Test
    fun onSharpCurveListenerTest() = runBlocking {

        val listener: NavigationManager.OnSharpCurveListener = mock(verboseLogging = true)

        val route = routeCompute.onlineComputeRoute(
            GeoCoordinates(48.1384, 17.3184),
            GeoCoordinates(48.132, 17.3009)
        )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        navigation.addOnSharpCurveListener(listener)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        val demonstrateSimulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.startSimulator(demonstrateSimulatorAdapter)
        navigationManagerKtx.setSpeedMultiplier(demonstrateSimulatorAdapter, 4F)

        Mockito.verify(
            listener,
            Mockito.timeout(STATUS_TIMEOUT)
        )
            .onSharpCurveInfoChanged(argThat {
                if (this.angle != 0.0) {
                    return@argThat true
                }
                false
            })

        navigationManagerKtx.stopSimulator(demonstrateSimulatorAdapter)
        simulator.destroy()
        navigation.removeOnSharpCurveListener(listener)
        navigationManagerKtx.stopNavigation(navigation)
        
    }

    /**
     * Navigation test on incident listener
     *
     * In this test we compute route and set it for navigation. Via simulator provider
     * we set this route for simulation and start demonstrate navigation.
     * We verify that onIncidentInfoChanged was invoked.
     */
    @Test
    fun onIncidentListenerTestOnlineNavigation() = runBlocking {

        val listener: NavigationManager.OnIncidentListener = mock(verboseLogging = true)

        val route =
            routeCompute.onlineComputeRoute(
                GeoCoordinates(48.7429, 17.8603),
                GeoCoordinates(48.7457, 17.86)
            )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        navigation.addOnIncidentListener(listener)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        val demonstrateSimulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.startSimulator(demonstrateSimulatorAdapter)

        Mockito.verify(
            listener,
            Mockito.timeout(STATUS_TIMEOUT)
        )
            .onIncidentsInfoChanged(anyList())

        navigationManagerKtx.stopSimulator(demonstrateSimulatorAdapter)
        simulator.destroy()
        navigation.removeOnIncidentListener(listener)
        navigationManagerKtx.stopNavigation(navigation)
        
    }

    /**
     * Navigation test on place listener
     *
     * In this test we compute route and set it for navigation. Via simulator provider
     * we set this route for simulation and start demonstrate navigation.
     * We verify that onPlaceInfoChanged was invoked.
     */
    @Test
    fun onPlaceListenerTest() = runBlocking {
        val listener: NavigationManager.OnPlaceListener = mock(verboseLogging = true)

        val route =
            routeCompute.onlineComputeRoute(
                GeoCoordinates(48.142020, 17.139852),
                GeoCoordinates(48.146196, 17.137438)
            )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        navigation.addOnPlaceListener(listener)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        val demonstrateSimulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.startSimulator(demonstrateSimulatorAdapter)

        Mockito.verify(
            listener,
            timeout(10_000L).atLeastOnce()
        )
            .onPlaceInfoChanged(argThat {
                return@argThat this.isNotEmpty()
            })

        navigationManagerKtx.stopSimulator(demonstrateSimulatorAdapter)
        simulator.destroy()
        navigation.removeOnPlaceListener(listener)
        navigationManagerKtx.stopNavigation(navigation)
        
    }

    /**
     * Navigation test on route recompute progress
     *
     * In this test we compute online route and set it for navigation.
     * Via Nmea Log Recorder we set route from assets/SVK-Kosicka.nmea and start nmea log simulation.
     * We verify that the onRouteRecomputeProgress was invoked with RouteRecomputeStatus Started and Finished.
     */
    @Test
    fun onRouteRecomputeProgress() = runBlocking {

        val listener: NavigationManager.OnRouteRecomputeProgressListener =
            mock(verboseLogging = true)

        val route = routeCompute.onlineComputeRoute(
            GeoCoordinates(48.1447, 17.1317),
            GeoCoordinates(48.1461, 17.1285)
        )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        navigation.addOnRouteRecomputeProgressListener(listener)
        val nmeaDataProvider = NmeaFileDataProvider(appContext, "SVK-Kosicka.nmea")
        val logSimulator = NmeaLogSimulatorProvider.getInstance(nmeaDataProvider).get()
        val logSimulatorAdapter = NmeaLogSimulatorAdapter(logSimulator)
        navigationManagerKtx.startSimulator(logSimulatorAdapter)

        Mockito.verify(
            listener, Mockito.timeout(20_000L).times(1)
        )
            .onRouteRecomputeProgress(eq(0), eq(NavigationManager.RouteRecomputeStatus.Started))

        Mockito.verify(
            listener, Mockito.timeout(20_000L).times(1)
        )
            .onRouteRecomputeProgress(eq(0), eq(NavigationManager.RouteRecomputeStatus.Computing))

        Mockito.verify(
            listener, Mockito.timeout(20_000L).times(1)
        )
            .onRouteRecomputeProgress(eq(100), eq(NavigationManager.RouteRecomputeStatus.Computing))

        Mockito.verify(
            listener, Mockito.timeout(20_000L).times(1)
        )
            .onRouteRecomputeProgress(eq(100), eq(NavigationManager.RouteRecomputeStatus.Finished))

        Mockito.verify(
            listener, never()
        )
            .onRouteRecomputeProgress(any(), eq(NavigationManager.RouteRecomputeStatus.Failed))

        navigationManagerKtx.stopSimulator(logSimulatorAdapter)
        logSimulator.destroy()
        navigation.removeOnRouteRecomputeProgressListener(listener)
        navigationManagerKtx.stopNavigation(navigation)
        
    }

    /**
     * Navigation test on waypoint pass
     *
     * In this test we compute route with waypoint and set it for navigation.
     * Via simulator provider we set this route for simulation and start demonstrate navigation.
     * We verify that onWaypointPassed was invoked.
     */
    @Test
    fun onWaypointPassTest() = runBlocking {

        val listener: NavigationManager.OnWaypointPassListener = mock(verboseLogging = true)

        val route = routeCompute.onlineComputeRoute(
            GeoCoordinates(48.296103, 17.304851),
            GeoCoordinates(48.297194, 17.313073),
            GeoCoordinates(48.296446, 17.306706)
        )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        navigation.addOnWaypointPassListener(listener)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        val demonstrateSimulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.setSpeedMultiplier(demonstrateSimulatorAdapter, 2F)
        navigationManagerKtx.startSimulator(demonstrateSimulatorAdapter)

        Mockito.verify(
            listener,
            Mockito.timeout(STATUS_TIMEOUT)
        )
            .onWaypointPassed(any())

        navigationManagerKtx.stopSimulator(demonstrateSimulatorAdapter)
        simulator.destroy()
        navigation.removeOnWaypointPassListener(listener)
        navigationManagerKtx.stopNavigation(navigation)
        
    }

    /**
     * Navigation test on place listener
     *
     * In this test we compute online route and set it for navigation. Via simulator provider we set this route
     * for simulation and start demonstrate navigation. We verify that onPlaceInfoChanged was invoked
     * with Place Info. We also verify, that distance to Place on route is increasing.
     */
    @Test
    fun onPlaceSplitDistanceTest() = runBlocking {

        val listener: NavigationManager.OnPlaceListener = mock(verboseLogging = true)

        val route = routeCompute.onlineComputeRoute(
            GeoCoordinates(48.457323, 17.739210),
            GeoCoordinates(48.448209, 17.738767)
        )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        navigation.addOnPlaceListener(listener)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        val demonstrateSimulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.startSimulator(demonstrateSimulatorAdapter)
        navigationManagerKtx.setSpeedMultiplier(demonstrateSimulatorAdapter, 2F)

        val myList: MutableList<Int> = mutableListOf()

        navigation.addOnPlaceListener({ listPlaceInfo ->
            if (listPlaceInfo.isNotEmpty()) {
                myList.add(listPlaceInfo[0].distance)
            }
        })

        val inOrder: InOrder = inOrder(listener)
        inOrder.verify(listener, Mockito.timeout(STATUS_TIMEOUT).atLeastOnce())
            .onPlaceInfoChanged(argThat { this.isNotEmpty() })
        inOrder.verify(listener, Mockito.timeout(STATUS_TIMEOUT).atLeastOnce())
            .onPlaceInfoChanged(emptyList())

        for (position in 1 until myList.size) {
            Assert.assertTrue(myList[position - 1] > myList[position])
        }

        navigationManagerKtx.stopSimulator(demonstrateSimulatorAdapter)
        simulator.destroy()
        navigationManagerKtx.stopNavigation(navigation)
        navigation.removeOnPlaceListener(listener)
        
    }

    @Test
    fun testGetCurrentRouteWaypointsAsync() = runBlocking {

        val listener: NavigationManager.OnWaypointsListener = mock(verboseLogging = true)

        val route = routeCompute.onlineComputeRoute(
            GeoCoordinates(48.457323, 17.739210),
            GeoCoordinates(48.448209, 17.738767),
            GeoCoordinates(48.123, 17.723)
        )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        navigation.getCurrentRouteWaypoints(listener)

        verify(listener, timeout(5_000)).onWaypoints(argThat {
            if (this.isNotEmpty()) {
                return@argThat true
            }
            false
        })
    }

    companion object {
        private const val STATUS_TIMEOUT: Long = 30000
    }
}