package cz.feldis.sdkandroidtests.navigation

import com.nhaarman.mockitokotlin2.*
import com.sygic.sdk.navigation.NavigationManager
import com.sygic.sdk.navigation.NavigationManagerProvider
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.route.simulator.NmeaLogSimulatorProvider
import com.sygic.sdk.route.simulator.RouteDemonstrateSimulatorProvider
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.mapInstaller.MapDownloadHelper
import cz.feldis.sdkandroidtests.routing.RouteComputeHelper
import junit.framework.Assert.assertNotNull
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.InOrder
import org.mockito.Mockito

class OnlineNavigationTests : BaseTest() {
    private lateinit var routeCompute: RouteComputeHelper
    private lateinit var mapDownload: MapDownloadHelper

    @Before
    override fun setUp() {
        super.setUp()
        routeCompute = RouteComputeHelper()
        mapDownload = MapDownloadHelper()
    }

    @Test
    fun testGetRouteProgress() {
        val start = GeoCoordinates(48.101936, 17.233684)
        val destination = GeoCoordinates(48.145644, 17.127011)
        val routeCompute = RouteComputeHelper()
        val route = routeCompute.onlineComputeRoute(start, destination)
        NavigationManagerProvider.getInstance().get().setRouteForNavigation(route)
        assertNotNull(
            NavigationManagerProvider.getInstance().get().routeProgress
        )
    }

    /**
     * Navigation test on sign post changed
     *
     * In this test we compute route and set it for navigation.
     * Via simulator provider we set this route and start demonstrate navigation.
     * We verify that onSignpostChanged was invoked.
     */
    @Test
    fun onSignpostChangedTest() {
        val navigation = NavigationManagerProvider.getInstance().get()
        val listener : NavigationManager.OnSignpostListener = mock(verboseLogging = true)
        val route =
            routeCompute.onlineComputeRoute(
                GeoCoordinates(48.143133, 17.175447),
                GeoCoordinates(48.171427, 17.191148)
            )

        navigation.setRouteForNavigation(route)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        simulator.start()
        navigation.addOnSignpostListener(listener)

        Mockito.verify(listener, Mockito.timeout(STATUS_TIMEOUT))
            .onSignpostChanged(any())

        navigation.removeOnSignpostListener(listener)
        navigation.stopNavigation()
        simulator.stop()
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
    fun onDirectionInfoChangedTest() {
        val navigation = NavigationManagerProvider.getInstance().get()
        val listener = Mockito.mock(
            NavigationManager.OnDirectionListener::class.java,
            Mockito.withSettings().verboseLogging()
        )
        val route =
            routeCompute.onlineComputeRoute(
                GeoCoordinates(48.152466, 17.125885),
                GeoCoordinates(48.154026, 17.127838)
            )
        Assert.assertNotNull(route)

        navigation.setRouteForNavigation(route)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        simulator.start()
        navigation.addOnDirectionListener(listener)

        Mockito.verify(listener, Mockito.timeout(STATUS_TIMEOUT))
            .onDirectionInfoChanged(any())

        navigation.removeOnDirectionListener(listener)
        navigation.stopNavigation()
        simulator.stop()
        simulator.destroy()
    }

    /**
     * Navigation test on speed limit info changed
     *
     * In this test we compute route and set it for navigation.
     * Via simulator provider we set this route and start demonstrate navigation.
     * We verify that onSpeedLimitInfoChanged was invoked.
     *
     */
    @Test
    fun onSpeedLimitInfoChanged() {
        val navigation = NavigationManagerProvider.getInstance().get()
        val listener: NavigationManager.OnSpeedLimitListener = mock(verboseLogging = true)
        val route =
            routeCompute.onlineComputeRoute(
                GeoCoordinates(48.128027, 17.094285),
                GeoCoordinates(48.131233, 17.112298)
            )

        navigation.setRouteForNavigation(route)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        simulator.start()
        navigation.addOnSpeedLimitListener(listener)

        Mockito.verify(
            listener,
            Mockito.timeout(STATUS_TIMEOUT)
        )
            .onSpeedLimitInfoChanged(any())

        navigation.removeOnSpeedLimitListener(listener)
        navigation.stopNavigation()
        simulator.stop()
        simulator.destroy()
    }

    /**
     * Navigation test on railway crossing
     *
     * In this test we compute route and set it for navigation.
     * Via simulator provider we set this route and start demonstrate navigation.
     * We verify that onRailwayCrossingInfoChanged was invoked.
     */
    @Test
    fun onRailwayCrossingTest() {
        val navigation = NavigationManagerProvider.getInstance().get()
        val listener: NavigationManager.OnRailwayCrossingListener = mock(verboseLogging = true)
        val route =
            routeCompute.onlineComputeRoute(
                GeoCoordinates(48.133798, 17.168522),
                GeoCoordinates(48.136994, 17.155435)
            )

        navigation.setRouteForNavigation(route)
        navigation.addOnRailwayCrossingListener(listener)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        simulator.start()

        Mockito.verify(
            listener,
            Mockito.timeout(STATUS_TIMEOUT)
        )
            .onRailwayCrossingInfoChanged(any())

        simulator.stop()
        simulator.destroy()
        navigation.removeOnRailwayCrossingListener(listener)
        navigation.stopNavigation()
    }

    /**
     * Navigation test on highway exit
     *
     * In this test we compute route and set it for navigation.
     * Via simulator provider we set this route and start demonstrate navigation.
     * We verify that onHighwayExitInfoChanged was invoked.
     */
    @Test
    fun onHighwayExitTest() {
        val navigation = NavigationManagerProvider.getInstance().get()
        val listener: NavigationManager.OnHighwayExitListener = mock(verboseLogging = true)
        val route =
            routeCompute.onlineComputeRoute(
                GeoCoordinates(48.143110, 17.175367),
                GeoCoordinates(48.146051, 17.186027)
            )

        navigation.setRouteForNavigation(route)
        navigation.addOnHighwayExitListener(listener)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        simulator.start()

        Mockito.verify(
            listener,
            Mockito.timeout(STATUS_TIMEOUT)
        )
            .onHighwayExitInfoChanged(any())

        simulator.stop()
        simulator.destroy()
        navigation.removeOnHighwayExitListener(listener)
        navigation.stopNavigation()
    }

    /**
     * Navigation test on route changed
     *
     * In this test we compute route and set it for navigation.
     * Via Nmea Log Recorder we set route from assets/SVK-Kosicka.nmea and start nmea log simulation.
     * We verify that the onRouteChanged callback is called and the route that we get is not null.
     */
    @Test
    fun onRouteChangedTest() {
        val navigation = NavigationManagerProvider.getInstance().get()
        val listener: NavigationManager.OnRouteChangedListener = mock(verboseLogging = true)

        val route = routeCompute.onlineComputeRoute(
            GeoCoordinates(48.1432, 17.1308),
            GeoCoordinates(48.1455, 17.1263)
        )

        navigation.setRouteForNavigation(route)
        val mLogSimulator = NmeaLogSimulatorProvider.getInstance("SVK-Kosicka.nmea").get()
        mLogSimulator.start()
        navigation.addOnRouteChangedListener(listener)
        mLogSimulator.setSpeedMultiplier(4F)

        Mockito.verify(
            listener,
            Mockito.timeout(STATUS_TIMEOUT)
        )
            .onRouteChanged(any(), any())

        mLogSimulator.stop()
        mLogSimulator.destroy()
        navigation.removeOnRouteChangedListener(listener)
        navigation.stopNavigation()
    }


    /**
     * Navigation test on lane listener
     *
     * In this test we compute route and set it for navigation.
     * Via simulator provider we set this route and start demonstrate navigation.
     * We verify that onLaneInfoChanged was invoked.
     */
    @Test
    fun onLaneListenerTest() {
        val navigation = NavigationManagerProvider.getInstance().get()
        val listener: NavigationManager.OnLaneListener = mock(verboseLogging = true)

        val route =
            routeCompute.onlineComputeRoute(
                GeoCoordinates(48.151204, 17.106735),
                GeoCoordinates(48.149639, 17.110376)
            )

        navigation.setRouteForNavigation(route)
        navigation.addOnLaneListener(listener)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        simulator.start()

        Mockito.verify(
            listener,
            Mockito.timeout(STATUS_TIMEOUT)
        )
            .onLaneInfoChanged(any())

        simulator.stop()
        simulator.destroy()
        navigation.removeOnLaneListener(listener)
        navigation.stopNavigation()
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
    fun onSharpCurveListenerTest() {
        val navigation = NavigationManagerProvider.getInstance().get()
        val listener: NavigationManager.OnSharpCurveListener = mock(verboseLogging = true)

        val route = routeCompute.onlineComputeRoute(
            GeoCoordinates(48.1384, 17.3184),
            GeoCoordinates(48.132, 17.3009)
        )

        navigation.setRouteForNavigation(route)
        navigation.addOnSharpCurveListener(listener)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        simulator.start()
        simulator.setSpeedMultiplier(2F)

        Mockito.verify(
            listener,
            Mockito.timeout(STATUS_TIMEOUT)
        )
            .onSharpCurveInfoChanged(any())

        simulator.stop()
        simulator.destroy()
        navigation.removeOnSharpCurveListener(listener)
        navigation.stopNavigation()
    }

    /**
     * Navigation test on incident listener
     *
     * In this test we compute route and set it for navigation. Via simulator provider
     * we set this route for simulation and start demonstrate navigation.
     * We verify that onIncidentInfoChanged was invoked.
     */
    @Test
    fun onIncidentListenerTest() {
        val navigation = NavigationManagerProvider.getInstance().get()
        val listener: NavigationManager.OnIncidentListener = mock(verboseLogging = true)

        val route =
            routeCompute.onlineComputeRoute(
                GeoCoordinates(48.1863, 17.0468),
                GeoCoordinates(48.178768, 17.060133)
            )

        navigation.setRouteForNavigation(route)
        navigation.addOnIncidentListener(listener)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        simulator.start()

        Mockito.verify(
            listener,
            Mockito.timeout(STATUS_TIMEOUT)
        )
            .onIncidentInfoChanged(any())

        simulator.stop()
        simulator.destroy()
        navigation.removeOnIncidentListener(listener)
        navigation.stopNavigation()
    }

    /**
     * Navigation test on place listener
     *
     * In this test we compute route and set it for navigation. Via simulator provider
     * we set this route for simulation and start demonstrate navigation.
     * We verify that onPlaceInfoChanged was invoked.
     */
    @Test
    fun onPlaceListenerTest() {
        val navigation = NavigationManagerProvider.getInstance().get()
        val listener: NavigationManager.OnPlaceListener = mock(verboseLogging = true)

        val route =
            routeCompute.onlineComputeRoute(
                GeoCoordinates(48.142020, 17.139852),
                GeoCoordinates(48.146196, 17.137438)
            )

        navigation.setRouteForNavigation(route)
        navigation.addOnPlaceListener(listener)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        simulator.start()

        Mockito.verify(
            listener,
            Mockito.timeout(STATUS_TIMEOUT)
        )
            .onPlaceInfoChanged(any())

        simulator.stop()
        simulator.destroy()
        navigation.removeOnPlaceListener(listener)
        navigation.stopNavigation()
    }

    /**
     * Navigation test on route recompute progress
     *
     * In this test we compute online route and set it for navigation.
     * Via Nmea Log Recorder we set route from assets/SVK-Kosicka.nmea and start nmea log simulation.
     * We verify that the onRouteRecomputeProgress was invoked with RouteRecomputeStatus Started and Finished.
     */
    @Test
    fun onRouteRecomputeProgress() {
        val navigation = NavigationManagerProvider.getInstance().get()
        val listener: NavigationManager.OnRouteRecomputeProgressListener = mock(verboseLogging = true)

        val route = routeCompute.onlineComputeRoute(
            GeoCoordinates(48.143397, 17.130936),
            GeoCoordinates(48.147486, 17.133397)
        )

        navigation.setRouteForNavigation(route)
        navigation.addOnRouteRecomputeProgressListener(listener)
        val mLogSimulator = NmeaLogSimulatorProvider.getInstance("SVK-Kosicka.nmea").get()
        mLogSimulator.start()

        Mockito.verify(
            listener, Mockito.timeout(STATUS_TIMEOUT).times(1)
        )
            .onRouteRecomputeProgress(eq(0), eq(NavigationManager.RouteRecomputeStatus.Started))

        Mockito.verify(
            listener, Mockito.timeout(STATUS_TIMEOUT).times(1)
        )
            .onRouteRecomputeProgress(eq(100), eq(NavigationManager.RouteRecomputeStatus.Finished))

        Mockito.verify(
            listener, never()
        )
            .onRouteRecomputeProgress(any(), eq(NavigationManager.RouteRecomputeStatus.Failed))

        mLogSimulator.stop()
        mLogSimulator.destroy()
        navigation.removeOnRouteRecomputeProgressListener(listener)
        navigation.stopNavigation()
    }

    /**
     * Navigation test on waypoint pass
     *
     * In this test we compute route with waypoint and set it for navigation.
     * Via simulator provider we set this route for simulation and start demonstrate navigation.
     * We verify that onWaypointPassed was invoked.
     */
    @Test
    fun onWaypointPassTest() {
        val navigation = NavigationManagerProvider.getInstance().get()
        val listener: NavigationManager.OnWaypointPassListener = mock(verboseLogging = true)

        val route = routeCompute.onlineComputeRoute(
                GeoCoordinates(48.296103, 17.304851),
                GeoCoordinates(48.297194, 17.313073),
                GeoCoordinates(48.296446, 17.306706)
            )

        navigation.setRouteForNavigation(route)
        navigation.addOnWaypointPassListener(listener)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        simulator.setSpeedMultiplier(2F)
        simulator.start()

        Mockito.verify(
            listener,
            Mockito.timeout(STATUS_TIMEOUT)
        )
            .onWaypointPassed(any())

        simulator.stop()
        simulator.destroy()
        navigation.removeOnWaypointPassListener(listener)
        navigation.stopNavigation()
    }

    /**
     * Navigation test on place listener
     *
     * In this test we compute online route and set it for navigation. Via simulator provider we set this route
     * for simulation and start demonstrate navigation. We verify that onPlaceInfoChanged was invoked
     * with Place Info. We also verify, that distance to Place on route is increasing.
     */
    @Test
    fun onPlaceSplitDistanceTest() {
        val navigation = NavigationManagerProvider.getInstance().get()
        val listener: NavigationManager.OnPlaceListener = mock(verboseLogging = true)

        val route = routeCompute.onlineComputeRoute(
            GeoCoordinates(48.457323, 17.739210),
            GeoCoordinates(48.448209, 17.738767)
        )

        navigation.setRouteForNavigation(route)
        navigation.addOnPlaceListener(listener)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        simulator.start()
        simulator.setSpeedMultiplier(2F)

        var myList: MutableList<Int> = mutableListOf()

        navigation.addOnPlaceListener { listPlaceInfo ->
            if (listPlaceInfo.isNotEmpty()) {
                myList.add(listPlaceInfo[0].distance)
            }
        }

        val inOrder: InOrder = inOrder(listener)
        inOrder.verify(listener, Mockito.timeout(STATUS_TIMEOUT).atLeastOnce())
            .onPlaceInfoChanged(argThat { this.isNotEmpty() })
        inOrder.verify(listener, Mockito.timeout(STATUS_TIMEOUT).atLeastOnce())
            .onPlaceInfoChanged(emptyList())

        for (position in 1 until myList.size) {
            Assert.assertTrue(myList[position - 1] > myList[position])
        }

        simulator.stop()
        simulator.destroy()
        navigation.stopNavigation()
        navigation.removeOnPlaceListener(listener)
    }

    companion object {
        private const val STATUS_TIMEOUT: Long = 60000
    }
}