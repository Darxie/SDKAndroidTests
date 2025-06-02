package cz.feldis.sdkandroidtests.navigation

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.sygic.sdk.map.Camera
import com.sygic.sdk.map.CameraState
import com.sygic.sdk.map.MapAnimation
import com.sygic.sdk.map.MapCenter
import com.sygic.sdk.map.MapCenterSettings
import com.sygic.sdk.map.MapView
import com.sygic.sdk.map.listeners.OnMapInitListener
import com.sygic.sdk.navigation.NavigationManager
import com.sygic.sdk.navigation.NavigationManager.OnRouteProgressListener
import com.sygic.sdk.navigation.NavigationManagerProvider
import com.sygic.sdk.navigation.StreetDetail
import com.sygic.sdk.navigation.routeeventnotifications.HighwayExitInfo
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.route.RoutingOptions
import com.sygic.sdk.route.Waypoint
import com.sygic.sdk.route.simulator.NmeaLogSimulatorProvider
import com.sygic.sdk.route.simulator.RouteDemonstrateSimulatorProvider
import com.sygic.sdk.vehicletraits.dimensional.DimensionalTraits
import com.sygic.sdk.vehicletraits.general.VehicleType
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.NmeaFileDataProvider
import cz.feldis.sdkandroidtests.SygicActivity
import cz.feldis.sdkandroidtests.TestMapFragment
import cz.feldis.sdkandroidtests.ktx.NavigationManagerKtx
import cz.feldis.sdkandroidtests.mapInstaller.MapDownloadHelper
import cz.feldis.sdkandroidtests.routing.RouteComputeHelper
import cz.feldis.sdkandroidtests.utils.NmeaLogSimulatorAdapter
import cz.feldis.sdkandroidtests.utils.RouteDemonstrateSimulatorAdapter
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.InOrder
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atMost
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import java.util.Locale

class OnlineNavigationTests : BaseTest() {
    private lateinit var routeCompute: RouteComputeHelper
    private lateinit var mapDownload: MapDownloadHelper
    private val navigationManagerKtx = NavigationManagerKtx()
    private lateinit var navigation: NavigationManager

    @Before
    override fun setUp() {
        super.setUp()
        routeCompute = RouteComputeHelper()
        mapDownload = MapDownloadHelper()
        mapDownload.unloadAllMaps()
        navigation = NavigationManagerProvider.getInstance().get()
    }

    @Test
    fun testGetRouteProgressAsyncOnline() = runBlocking {
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
    fun onSignpostChangedTestOnline() = runBlocking {
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
    }

    /**
     * Navigation test on direction info changed
     *
     * In this test we compute route and set it for navigation.
     * Via simulator provider we set this route and start demonstrate navigation.
     * We verify that onDirectionInfoChanged was invoked.
     */
    @Test
    fun onDirectionInfoChangedTestOnline() = runBlocking {
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
    fun onSpeedLimitInfoChangedOnline() = runBlocking {

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
    fun onRailwayCrossingTestOnline() = runBlocking {

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
    fun onHighwayExitTestOnline() = runBlocking {

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
    fun onRouteChangedTestOnline() = runBlocking {

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
//        val demonstrateSimulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
//        navigationManagerKtx.startSimulator(demonstrateSimulatorAdapter)
//        navigation.addOnRouteChangedListener(listener)
//        navigationManagerKtx.setSpeedMultiplier(demonstrateSimulatorAdapter, 2F)
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
//        navigationManagerKtx.stopSimulator(demonstrateSimulatorAdapter)
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
    fun onLaneListenerTestOnline() = runBlocking {
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
    fun onSharpCurveListenerTestOnline() = runBlocking {
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
    fun onIncidentListenerTestOnlineNavigationOnline() = runBlocking {

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
    fun onPlaceListenerTestOnline() = runBlocking {
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
        navigation.removeOnPlaceListener(listener)
        navigationManagerKtx.stopNavigation(navigation)
    }

    /**
     * Navigation test on route recompute progress
     *
     * In this test we compute online route and set it for navigation.
     * Via Nmea Log Recorder we set route from assets/SVK-Kosicka.nmea and start nmea log simulation.
     * We verify that the recompute started, was in progress from 0 to 100 and then finished without error.
     */
    @Test
    fun onRouteRecomputeProgressOnline() = runBlocking {
        val listener: NavigationManager.OnRouteRecomputeListener =
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

        val recomputeStartedData = NavigationManager.OnRouteRecomputeListener.RecomputeStartedData(
            route, NavigationManager.RouteRecomputeReason.VehicleOutOfRoute
        )
        val recomputeProgressData1 =
            NavigationManager.OnRouteRecomputeListener.RecomputeProgressData(
                route, 0
            )
        val recomputeProgressData2 =
            NavigationManager.OnRouteRecomputeListener.RecomputeProgressData(
                route, 100
            )
        val recomputeFinishedData =
            NavigationManager.OnRouteRecomputeListener.RecomputeFinishedData(
                route, NavigationManager.RouteRecomputeResult.Success
            )
        val recomputeFinishedFailedData =
            NavigationManager.OnRouteRecomputeListener.RecomputeFinishedData(
                route,
                NavigationManager.RouteRecomputeResult.Error(
                    NavigationManager.RouteRecomputeResult.Error.Reason.Failed,
                    "Recompute failed"
                )
            )

        Mockito.verify(
            listener, Mockito.timeout(20_000L).times(1)
        ).onRouteRecomputeStarted(eq(recomputeStartedData))

        Mockito.verify(
            listener, Mockito.timeout(20_000L).times(1)
        )
            .onRouteRecomputeProgress(eq(recomputeProgressData1))

        Mockito.verify(
            listener, Mockito.timeout(20_000L).times(1)
        )
            .onRouteRecomputeProgress(eq(recomputeProgressData2))

        Mockito.verify(
            listener, Mockito.timeout(20_000L).times(1)
        )
            .onRouteRecomputeFinished(eq(recomputeFinishedData))

        Mockito.verify(
            listener, never()
        )
            .onRouteRecomputeFinished(eq(recomputeFinishedFailedData))

        navigationManagerKtx.stopSimulator(logSimulatorAdapter)
        navigation.removeOnRouteRecomputeProgressListener(listener)
        navigationManagerKtx.stopNavigation(navigation)
    }

    /**
     * Navigation test on route recompute reason
     *
     * In this test we compute online route and set it for navigation.
     * Via Nmea Log Recorder we set route from assets/SVK-Kosicka.nmea and start nmea log simulation.
     * Then we change the language of mapView.
     * We verify that the recompute was invoked with status Language Changed.
     */
    @Test
    fun onRouteRecomputeStartedReasonLanguageChangedOnline(): Unit = runBlocking {
        val listener: NavigationManager.OnRouteRecomputeListener =
            mock(verboseLogging = true)
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager
                .beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }
        val mapView = getMapView(mapFragment)

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
        delay(2000)
        mapView.setMapLanguage(Locale.FRENCH)

        val recomputeStartedData = NavigationManager.OnRouteRecomputeListener.RecomputeStartedData(
            route, NavigationManager.RouteRecomputeReason.LanguageChanged
        )
        val recomputeFinishedData =
            NavigationManager.OnRouteRecomputeListener.RecomputeFinishedData(
                route, NavigationManager.RouteRecomputeResult.Success
            )

        Mockito.verify(
            listener, Mockito.timeout(20_000L).times(1)
        ).onRouteRecomputeStarted(eq(recomputeStartedData))

        Mockito.verify(
            listener, timeout(20_000L).times(1)
        ).onRouteRecomputeFinished(
            eq(recomputeFinishedData)
        )

        navigationManagerKtx.stopSimulator(logSimulatorAdapter)
        navigation.removeOnRouteRecomputeProgressListener(listener)
        navigationManagerKtx.stopNavigation(navigation)
        mapView.setMapLanguage(Locale.ENGLISH)
        scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    /**
     * Navigation test on waypoint pass
     *
     * In this test we compute route with waypoint and set it for navigation.
     * Via simulator provider we set this route for simulation and start demonstrate navigation.
     * We verify that onWaypointPassed was invoked.
     */
    @Test
    fun onWaypointPassTestOnline() = runBlocking {
        val listener: NavigationManager.OnWaypointPassListener = mock(verboseLogging = true)

        val route = routeCompute.onlineComputeRoute(
            GeoCoordinates(48.296103, 17.304851),
            GeoCoordinates(48.296446, 17.306706),
            listOf(GeoCoordinates(48.297194, 17.313073))
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
    fun onPlaceSplitDistanceTestOnline() = runBlocking {
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
        navigationManagerKtx.stopNavigation(navigation)
        navigation.removeOnPlaceListener(listener)
    }

    @Test
    fun testGetCurrentRouteWaypointsAsyncOnline() = runBlocking {
        val listener: NavigationManager.OnWaypointsListener = mock(verboseLogging = true)

        val route = routeCompute.onlineComputeRoute(
            GeoCoordinates(48.457323, 17.739210),
            GeoCoordinates(48.123, 17.723),
            listOf(GeoCoordinates(48.448209, 17.738767))
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

    @Test
    fun passTwoWaypointsOnline() = runBlocking {
        val navigation = NavigationManagerProvider.getInstance().get()
        val listener: NavigationManager.OnWaypointPassListener = mock(verboseLogging = true)
        val waypoints = listOf(
            GeoCoordinates(48.148760, 17.124930), // Waypoint 1
            GeoCoordinates(48.155020, 17.125310)  // Waypoint 2
        )
        val routingOptions = RoutingOptions().apply {
            vehicleProfile = RouteComputeHelper().createCombustionVehicleProfile().apply {
                generalVehicleTraits.vehicleType = VehicleType.Truck
                dimensionalTraits = DimensionalTraits().apply {
                    totalWeight = 10_000F
                }
            }
        }
        val route = routeCompute.onlineComputeRoute(
            start = GeoCoordinates(48.146255528464, 17.1273927454307),
            waypoints = waypoints,
            destination = GeoCoordinates(48.157031, 17.121155),
            routingOptions = routingOptions
        )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        navigation.addOnWaypointPassListener(listener)

        val nmeaDataProvider = NmeaFileDataProvider(appContext, "sygic-legionarska.nmea")
        val logSimulator = NmeaLogSimulatorProvider.getInstance(nmeaDataProvider).get()
        val logSimulatorAdapter = NmeaLogSimulatorAdapter(logSimulator)
        navigationManagerKtx.startSimulator(logSimulatorAdapter)
        navigationManagerKtx.setSpeedMultiplier(logSimulatorAdapter, 3F)

        val inOrder: InOrder = inOrder(listener)

        inOrder.verify(listener, timeout(20_000L)).onWaypointPassed(argThat {
            this.type == Waypoint.Type.Via && this.originalPosition == waypoints[0]
        })

        inOrder.verify(listener, timeout(20_000L)).onWaypointPassed(argThat {
            this.type == Waypoint.Type.Via && this.originalPosition == waypoints[1]
        })

        inOrder.verify(listener, timeout(20_000L)).onFinishReached()

        navigationManagerKtx.stopSimulator(logSimulatorAdapter)
        navigation.removeOnWaypointPassListener(listener)
        navigationManagerKtx.stopNavigation(navigation)
    }

    @Test
    fun testStopNavigationWhileDemonstratingOnline() = runBlocking {
        val route =
            routeCompute.onlineComputeRoute(
                GeoCoordinates(48.147260, 17.150520),
                GeoCoordinates(48.413651171955465, 16.927561108197466)
            )
        val navigation = NavigationManagerProvider.getInstance().get()
        navigationManagerKtx.setRouteForNavigation(route, navigation)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        val demonstrateSimulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.setSpeedMultiplier(demonstrateSimulatorAdapter, 2F)
        navigationManagerKtx.startSimulator(demonstrateSimulatorAdapter)
        delay(2000)
        navigationManagerKtx.stopNavigation(navigation) // shouldn't crash
        delay(500)
    }

    @Test
    fun testStreetChangedListenerOnline() = runBlocking {
        val navigation = NavigationManagerProvider.getInstance().get()
        val listener: NavigationManager.StreetChangedListener = mock(verboseLogging = true)

        val route = routeCompute.onlineComputeRoute(
            GeoCoordinates(48.1209419355147, 17.207606308128618),
            GeoCoordinates(48.12276083935055, 17.207632634218143),
        )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        navigation.addStreetChangedListener(listener)

        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        val demonstrateSimulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.setSpeedMultiplier(demonstrateSimulatorAdapter, 1F)
        navigationManagerKtx.startSimulator(demonstrateSimulatorAdapter)

        verify(listener, timeout(10_000L)).onStreetChanged(argThat {
            return@argThat this.street == "Mramorová"
        })

        navigationManagerKtx.stopSimulator(demonstrateSimulatorAdapter)
        navigation.removeStreetChangedListener(listener)
        navigationManagerKtx.stopNavigation(navigation)
    }

    @Test
    fun leichendorfToZirndorfOnline() {

        val start = GeoCoordinates(49.4339, 10.9345)
        val destination = GeoCoordinates(49.4425, 10.9459)
        val routeCompute = RouteComputeHelper()

        val route = routeCompute.onlineComputeRoute(start, destination)

        assertEquals(6, route.maneuvers.size) // 6 maneuvers since october 2024 maps
        for (maneuver in route.maneuvers) {
            assertFalse(maneuver.roadName == "Thomas-Mann-Straße")
        }
    }

    @Test
    fun onJunctionPassedStandaloneListenerInvocationWithoutRouteOnline() = runBlocking {

        val listener: NavigationManager.JunctionPassedListener = mock(verboseLogging = true)
        val navigation = NavigationManagerProvider.getInstance().get()

        val nmeaDataProvider = NmeaFileDataProvider(appContext, "rovinka.nmea")
        val logSimulator = NmeaLogSimulatorProvider.getInstance(nmeaDataProvider).get()
        val logSimulatorAdapter = NmeaLogSimulatorAdapter(logSimulator)
        navigationManagerKtx.setSpeedMultiplier(logSimulatorAdapter, 4f)
        Thread.sleep(3000)
        navigationManagerKtx.startSimulator(logSimulatorAdapter)

        navigation.addJunctionPassedListener(listener)

        verify(
            listener, timeout(60_000L).atLeast(2)
        ).onJunctionPassed(eq(StreetDetail.JunctionType.Junction))
        verify(
            listener, atMost(10)
        ).onJunctionPassed(eq(StreetDetail.JunctionType.Junction))
        verify(
            listener, timeout(60_000L).times(1)
        ).onJunctionPassed(
            eq(StreetDetail.JunctionType.EnteringUrbanArea)
        )

        navigationManagerKtx.stopSimulator(logSimulatorAdapter)
        navigation.removeJunctionPassedListener(listener)
        navigationManagerKtx.stopNavigation(navigation)
    }

    companion object {
        private const val STATUS_TIMEOUT: Long = 30000
    }

    private fun getInitialCameraState(): CameraState {
        return CameraState.Builder().apply {
            setPosition(GeoCoordinates(48.15132, 17.07665))
            setMapCenterSettings(
                MapCenterSettings(
                    MapCenter(0.5f, 0.5f),
                    MapCenter(0.5f, 0.5f),
                    MapAnimation.NONE, MapAnimation.NONE
                )
            )
            setMapPadding(0.0f, 0.0f, 0.0f, 0.0f)
            setRotation(0f)
            setZoomLevel(14F)
            setMovementMode(Camera.MovementMode.Free)
            setRotationMode(Camera.RotationMode.Free)
            setTilt(0f)
        }.build()
    }

    private fun getMapView(mapFragment: TestMapFragment): MapView {
        val mapInitListener: OnMapInitListener = mock(verboseLogging = true)
        val mapViewCaptor = argumentCaptor<MapView>()

        mapFragment.getMapAsync(mapInitListener)
        verify(mapInitListener, timeout(5_000L)).onMapReady(
            mapViewCaptor.capture()
        )
        return mapViewCaptor.firstValue
    }
}