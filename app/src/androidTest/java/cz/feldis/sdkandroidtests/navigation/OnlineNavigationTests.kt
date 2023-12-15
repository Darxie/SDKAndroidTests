package cz.feldis.sdkandroidtests.navigation

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.timeout
import com.nhaarman.mockitokotlin2.verify
import com.sygic.sdk.navigation.NavigationManager
import com.sygic.sdk.navigation.NavigationManager.OnRouteProgressListener
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
import org.mockito.ArgumentMatchers.anyList
import org.mockito.InOrder
import org.mockito.Mockito
import timber.log.Timber

class OnlineNavigationTests : BaseTest() {
    private lateinit var routeCompute: RouteComputeHelper
    private lateinit var mapDownload: MapDownloadHelper

    @Before
    override fun setUp() {
        super.setUp()
        routeCompute = RouteComputeHelper()
        mapDownload = MapDownloadHelper()
        mapDownload.ensureMapNotInstalled("sk")
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

    @Test
    fun testGetRouteProgressAsync() {
        val listener : OnRouteProgressListener = mock(verboseLogging = true)

        val start = GeoCoordinates(48.101936, 17.233684)
        val destination = GeoCoordinates(48.145644, 17.127011)
        val routeCompute = RouteComputeHelper()
        val route = routeCompute.onlineComputeRoute(start, destination)
        NavigationManagerProvider.getInstance().get().setRouteForNavigation(route)

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
    fun onSignpostChangedTest() {
        val navigation = NavigationManagerProvider.getInstance().get()
        val listener: NavigationManager.OnSignpostListener = mock(verboseLogging = true)
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
            .onSignpostChanged(argThat {
                forEach {
                    if (it.signElements.isNotEmpty()) {
                        return@argThat true
                    }
                }
                false
            })

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
                GeoCoordinates(48.132310, 17.114100),
                GeoCoordinates(48.131733, 17.109952)
            )
        Assert.assertNotNull(route)

        navigation.setRouteForNavigation(route)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        simulator.start()
        navigation.addOnDirectionListener(listener)

        Mockito.verify(listener, Mockito.timeout(STATUS_TIMEOUT))
            .onDirectionInfoChanged(argThat {
                if (this.primary.nextRoadName != "Einsteinova") {
                    Timber.e("Primary road name is not equal to Einsteinova.")
                    return@argThat false
                }
                true
            })

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
     * We verify that onSpeedLimitInfoChanged was invoked with a non-null object.
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
            listener, timeout(STATUS_TIMEOUT).atLeast(2) // first call contains previous values
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
     * We verify that onRailwayCrossingInfoChanged was invoked with a RailwayCrossingInfo
     * that has a valid position.
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
            .onRailwayCrossingInfoChanged(argThat {
                if (this.position.isValid) {
                    return@argThat true
                }
                false
            })

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
     * We verify that onHighwayExitInfoChanged was invoked with a non-null list.
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
            .onHighwayExitInfoChanged(anyList())

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
        val logSimulator = NmeaLogSimulatorProvider.getInstance("$appDataPath/SVK-Kosicka.nmea").get()
        logSimulator.start()
        navigation.addOnRouteChangedListener(listener)
        logSimulator.setSpeedMultiplier(2F)

        Mockito.verify(
            listener,
            Mockito.timeout(STATUS_TIMEOUT)
        )
            .onRouteChanged(argThat {
                if (this != route) {
                    return@argThat true
                } else false
            }, eq(NavigationManager.RouteUpdateStatus.Success))

        logSimulator.stop()
        logSimulator.destroy()
        navigation.removeOnRouteChangedListener(listener)
        navigation.stopNavigation()
    }

//    @Test
//    fun onGuidedRouteChangedTest() {
//        val navigation = NavigationManagerProvider.getInstance().get()
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
//        navigation.setRouteForNavigation(route)
//        val logSimulator = NmeaLogSimulatorProvider.getInstance("SVK-Kosicka.nmea").get()
//        logSimulator.start()
//        navigation.addOnRouteChangedListener(listener)
//        logSimulator.setSpeedMultiplier(2F)
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
//        logSimulator.stop()
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
    fun onLaneListenerTest() {
        val navigation = NavigationManagerProvider.getInstance().get()
        val listener: NavigationManager.OnLaneListener = mock(verboseLogging = true)

        val route =
            routeCompute.onlineComputeRoute(
                GeoCoordinates(48.147682401781026, 17.14365655304184),
                GeoCoordinates(48.15310362223699, 17.147190865317768)
            )

        navigation.setRouteForNavigation(route)
        navigation.addOnLaneListener(listener)

        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        simulator.setSpeedMultiplier(4F)
        simulator.start()

        Mockito.verify(
            listener,
            Mockito.timeout(STATUS_TIMEOUT)
        )
            .onLaneInfoChanged(argThat {
                if (this.simpleLanesInfo?.lanes?.isNotEmpty() == true){
                    return@argThat true
                }
                false
            })

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
        simulator.setSpeedMultiplier(4F)

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
    fun onIncidentListenerTestOnlineNavigation() {
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
            .onIncidentsInfoChanged(anyList())

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
        val listener: NavigationManager.OnRouteRecomputeProgressListener =
            mock(verboseLogging = true)

        val route = routeCompute.onlineComputeRoute(
            GeoCoordinates(48.143397, 17.130936),
            GeoCoordinates(48.147486, 17.133397)
        )

        navigation.setRouteForNavigation(route)
        navigation.addOnRouteRecomputeProgressListener(listener)
        val logSimulator = NmeaLogSimulatorProvider.getInstance("$appDataPath/SVK-Kosicka.nmea").get()
        logSimulator.start()

        Mockito.verify(
            listener, Mockito.timeout(30_000L).times(1)
        )
            .onRouteRecomputeProgress(eq(0), eq(NavigationManager.RouteRecomputeStatus.Started))

        Mockito.verify(
            listener, Mockito.timeout(30_000L).times(1)
        )
            .onRouteRecomputeProgress(eq(0), eq(NavigationManager.RouteRecomputeStatus.Computing))

        Mockito.verify(
            listener, Mockito.timeout(30_000L).times(1)
        )
            .onRouteRecomputeProgress(eq(100), eq(NavigationManager.RouteRecomputeStatus.Computing))

        Mockito.verify(
            listener, Mockito.timeout(30_000L).times(1)
        )
            .onRouteRecomputeProgress(eq(100), eq(NavigationManager.RouteRecomputeStatus.Finished))

        Mockito.verify(
            listener, never()
        )
            .onRouteRecomputeProgress(any(), eq(NavigationManager.RouteRecomputeStatus.Failed))

        logSimulator.stop()
        logSimulator.destroy()
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

        val myList: MutableList<Int> = mutableListOf()

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

    @Test
    fun testGetCurrentRouteWaypointsAsync() {
        val navigation = NavigationManagerProvider.getInstance().get()
        val listener: NavigationManager.OnWaypointsListener = mock(verboseLogging = true)

        val route = routeCompute.onlineComputeRoute(
            GeoCoordinates(48.457323, 17.739210),
            GeoCoordinates(48.448209, 17.738767),
            GeoCoordinates(48.123,17.723)
        )

        navigation.setRouteForNavigation(route)
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