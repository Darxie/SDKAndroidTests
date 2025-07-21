package cz.feldis.sdkandroidtests.navigation

import android.graphics.Color
import com.sygic.sdk.incidents.SpeedCamera
import com.sygic.sdk.navigation.NavigationManager
import com.sygic.sdk.navigation.NavigationManagerProvider
import com.sygic.sdk.navigation.StreetDetail
import com.sygic.sdk.navigation.routeeventnotifications.HighwayExitInfo
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.route.Route
import com.sygic.sdk.route.Router
import com.sygic.sdk.route.RouterProvider
import com.sygic.sdk.route.RoutingOptions
import com.sygic.sdk.route.Waypoint
import com.sygic.sdk.route.listeners.RouteComputeListener
import com.sygic.sdk.route.simulator.NmeaLogSimulatorProvider
import com.sygic.sdk.route.simulator.RouteDemonstrateSimulatorProvider
import com.sygic.sdk.vehicletraits.VehicleProfile
import com.sygic.sdk.vehicletraits.general.GeneralVehicleTraits
import com.sygic.sdk.vehicletraits.listeners.SetVehicleProfileListener
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.NmeaFileDataProvider
import cz.feldis.sdkandroidtests.ktx.NavigationManagerKtx
import cz.feldis.sdkandroidtests.mapInstaller.MapDownloadHelper
import cz.feldis.sdkandroidtests.routing.RouteComputeHelper
import cz.feldis.sdkandroidtests.utils.NmeaLogSimulatorAdapter
import cz.feldis.sdkandroidtests.utils.RouteDemonstrateSimulatorAdapter
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.mockito.AdditionalMatchers
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

class OfflineNavigationTests : BaseTest() {
    private lateinit var routeCompute: RouteComputeHelper
    private lateinit var mapDownload: MapDownloadHelper
    private lateinit var navigation: NavigationManager
    private val navigationManagerKtx = NavigationManagerKtx()

    @Before
    override fun setUp() {
        super.setUp()
        routeCompute = RouteComputeHelper()
        mapDownload = MapDownloadHelper()
        navigation = runBlocking { NavigationManagerProvider.getInstance() }
        disableOnlineMaps()
    }

    @Test
    fun onSharpCurveListenerTest() = runBlocking {
        mapDownload.installAndLoadMap("sk")
        val listener: NavigationManager.OnSharpCurveListener = mock(verboseLogging = true)

        val route = routeCompute.offlineRouteCompute(
            GeoCoordinates(48.1384, 17.3184),
            GeoCoordinates(48.132, 17.3009)
        )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        navigation.addOnSharpCurveListener(listener)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route)
        val demonstrateSimulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.startSimulator(demonstrateSimulatorAdapter)
        navigationManagerKtx.setSpeedMultiplier(demonstrateSimulatorAdapter, 4F)

        Mockito.verify(
            listener, Mockito.timeout(10_000L)
        ).onSharpCurveInfoChanged(argThat {
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
     * Navigation test on direction info changed
     *
     * In this test we compute an offline route and set it for navigation.
     * Via simulator provider we set this route and start demonstrate. We verify that onDirectionInfoChanged
     * contains direction info with primary nextRoadName "Hlavná".
     */
    @Test
    fun onDirectionInfoChangedTest() = runBlocking {
        mapDownload.installAndLoadMap("sk")
        val directionListener: NavigationManager.OnDirectionListener = mock(verboseLogging = true)

        val route = routeCompute.offlineRouteCompute(
            GeoCoordinates(48.0977, 17.2382),
            GeoCoordinates(48.0986, 17.2345)
        )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route)
        val demonstrateSimulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.startSimulator(demonstrateSimulatorAdapter)
        navigation.addOnDirectionListener(directionListener)

        Mockito.verify(
            directionListener, Mockito.timeout(15_000L)
        ).onDirectionInfoChanged(argThat {
            if (this.primary.nextRoadName == "Hlavná") {
                return@argThat true
            }
            false
        })

        navigation.removeOnDirectionListener(directionListener)
        navigationManagerKtx.stopNavigation(navigation)
        navigationManagerKtx.stopSimulator(demonstrateSimulatorAdapter)
    }

    /**
     * Navigation test on direction info changed - colors
     *
     * In this test we compute an offline route and set it for navigation.
     * Via simulator provider we set this route and start demonstrate. We verify that onDirectionInfoChanged
     * contains direction info with colors from signpost.
     */
    @Test
    fun onDirectionInfoChangedCheckColorsTest() = runBlocking {
        mapDownload.installAndLoadMap("sk")
        val directionListener: NavigationManager.OnDirectionListener = mock(verboseLogging = true)
        val expectedBorderColor = Color.WHITE
        val expectedBackgroundColor = 0xff009966.toInt()
        val expectedTextColor = Color.WHITE

        val route = routeCompute.offlineRouteCompute(
            GeoCoordinates(49.0849, 18.4182),
            GeoCoordinates(49.0936, 18.4215)
        )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        navigation.addOnDirectionListener(directionListener)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route)
        val demonstrateSimulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.startSimulator(demonstrateSimulatorAdapter)

        Mockito.verify(
            directionListener, Mockito.timeout(30_000L)
        ).onDirectionInfoChanged(argThat {
            if (this.primary.borderColor == expectedBorderColor && this.primary.backgroundColor == expectedBackgroundColor && this.primary.textColor == expectedTextColor) {
                return@argThat true
            }
            false
        })

        navigation.removeOnDirectionListener(directionListener)
        navigationManagerKtx.stopNavigation(navigation)
        navigationManagerKtx.stopSimulator(demonstrateSimulatorAdapter)
    }

    /**
     * Navigation test on route changed
     *
     * In this test we compute an offline route and set it for navigation.
     * Using the Nmea Log Recorder we set a route from assets/SVK-Kosicka.nmea and start the simulation.
     * We verify that the onRouteChanged callback is called and the status is Success.
     */
    @Test
    fun onRouteChangedTest() = runBlocking {
        mapDownload.installAndLoadMap("sk")
        val listener: NavigationManager.OnRouteChangedListener = mock(verboseLogging = true)
        val route = routeCompute.offlineRouteCompute(
            GeoCoordinates(48.1447, 17.1317),
            GeoCoordinates(48.1461, 17.1285)
        )

        val nmeaDataProvider = NmeaFileDataProvider(appContext, "SVK-Kosicka.nmea")
        val logSimulator = NmeaLogSimulatorProvider.getInstance(nmeaDataProvider)
        val logSimulatorAdapter = NmeaLogSimulatorAdapter(logSimulator)
        navigationManagerKtx.setSpeedMultiplier(logSimulatorAdapter, 2F)
        navigationManagerKtx.setRouteForNavigation(route, navigation)
        navigation.addOnRouteChangedListener(listener)
        navigationManagerKtx.startSimulator(logSimulatorAdapter)

        Mockito.verify(
            listener, Mockito.timeout(30_000L).atLeast(1)
        ).onRouteChanged(
            AdditionalMatchers.not(eq(route)),
            eq(NavigationManager.RouteUpdateStatus.Success)
        )

        navigationManagerKtx.stopSimulator(logSimulatorAdapter)
        navigation.removeOnRouteChangedListener(listener)
        navigationManagerKtx.stopNavigation(navigation)
    }

    @Test
    fun onJunctionPassedStandaloneListenerInvocationWithoutRoute() = runBlocking {
        mapDownload.installAndLoadMap("sk")
        val listener: NavigationManager.JunctionPassedListener = mock(verboseLogging = true)

        val nmeaDataProvider = NmeaFileDataProvider(appContext, "rovinka.nmea")
        val logSimulator = NmeaLogSimulatorProvider.getInstance(nmeaDataProvider)
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

    /**
     * Navigation test on lane listener
     *
     * In this test we compute route and set it for navigation.
     * Via simulator provider we set this route and start demonstrate navigation.
     * We verify that onLaneInfoChanged was invoked.
     */
    @Test
    fun onLaneListenerTestOffline() = runBlocking {
        mapDownload.installAndLoadMap("sk")
        val listener: NavigationManager.OnLaneListener = mock(verboseLogging = true)

        val route = routeCompute.offlineRouteCompute(
            GeoCoordinates(48.147682401781026, 17.14365655304184),
            GeoCoordinates(48.15310362223699, 17.147190865317768)
        )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        navigation.addOnLaneListener(listener)

        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route)
        val demonstrateSimulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.setSpeedMultiplier(demonstrateSimulatorAdapter, 4F)
        navigationManagerKtx.startSimulator(demonstrateSimulatorAdapter)


        Mockito.verify(
            listener, Mockito.timeout(30_000L)
        ).onLaneInfoChanged(argThat {
            if (this.simpleLanesInfo?.lanes?.isNotEmpty() == true) {
                return@argThat true
            }
            false
        })

        navigationManagerKtx.stopSimulator(demonstrateSimulatorAdapter)
        navigation.removeOnLaneListener(listener)
        navigationManagerKtx.stopNavigation(navigation)
    }

    @Test
    fun sectionCameraTest() = runBlocking {
        mapDownload.installAndLoadMap("be")
        val listener: NavigationManager.OnIncidentListener = mock(verboseLogging = true)

        val route = routeCompute.offlineRouteCompute(
            GeoCoordinates(51.007530, 3.175810),
            GeoCoordinates(51.001320, 3.203420)
        )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        navigation.addOnIncidentListener(listener)

        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route)
        val demonstrateSimulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.setSpeedMultiplier(demonstrateSimulatorAdapter, 4F)
        navigationManagerKtx.startSimulator(demonstrateSimulatorAdapter)

        var count = 0
        // verify that the callback has been called at least 5 times with value different than -1
        verify(listener, timeout(20_000L).atLeastOnce()).onIncidentsInfoChanged(argThat {
            this.forEach {
                if (it.recommendedSpeed != -1) {
                    count += 1
                }
                if (count >= 5)
                    return@argThat true
            }
            false
        })
    }

    @Test
    fun checkSpeedLimitOfRealCamera() = runBlocking {
        mapDownload.installAndLoadMap("sk")
        val listener: NavigationManager.OnIncidentListener = mock(verboseLogging = true)

        val route = routeCompute.offlineRouteCompute(
            GeoCoordinates(48.7429, 17.8603),
            GeoCoordinates(48.7457, 17.86)
        )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        navigation.addOnIncidentListener(listener)

        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route)
        val demonstrateSimulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.setSpeedMultiplier(demonstrateSimulatorAdapter, 4F)
        navigationManagerKtx.startSimulator(demonstrateSimulatorAdapter)

        verify(listener, timeout(20_000L)).onIncidentsInfoChanged(argThat {
            this.forEach {
                if (it.incident is SpeedCamera) {
                    val expectedSpeedcam = it.incident as SpeedCamera
                    if (expectedSpeedcam.speedLimit == 130) return@argThat true
                }
            }
            false
        })
    }

    @Test
    fun changeMaxSpeedAndCheckSpeedLimit() = runBlocking {
        val profileListener: SetVehicleProfileListener = mock(verboseLogging = true)
        val vehicleProfile = VehicleProfile().apply {
            this.generalVehicleTraits = GeneralVehicleTraits(
                maximalSpeed = 80
            )
        }

        mapDownload.installAndLoadMap("sk")
        navigation.setVehicleProfile(vehicleProfile, profileListener)
        verify(profileListener, timeout(5_000L)).onSuccess()
        val listener: NavigationManager.OnSpeedLimitListener = mock(verboseLogging = true)

        val route = routeCompute.offlineRouteCompute(
            GeoCoordinates(48.18180777150043, 17.05352048126561),
            GeoCoordinates(48.18417452255745, 17.04909691425327),
            routingOptions = RoutingOptions().apply {
                this.vehicleProfile = vehicleProfile
            }
        )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        navigation.addOnSpeedLimitListener(listener)

        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route)
        val demonstrateSimulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.setSpeedMultiplier(demonstrateSimulatorAdapter, 1F)
        navigationManagerKtx.startSimulator(demonstrateSimulatorAdapter)

        verify(listener, timeout(20_000L)).onSpeedLimitInfoChanged(
            argThat {
                return@argThat this.nextSpeedLimit == 80.0f
            }
        )

        navigationManagerKtx.stopSimulator(demonstrateSimulatorAdapter)
        navigation.removeOnSpeedLimitListener(listener)
        navigationManagerKtx.stopNavigation(navigation)

    }

    @Test
    fun onWaypointAndFinishReached() = runBlocking {
        mapDownload.installAndLoadMap("sk")
        val listener: NavigationManager.OnWaypointPassListener = mock(verboseLogging = true)

        val route = routeCompute.offlineRouteCompute(
            GeoCoordinates(48.10044188518012, 17.24304412091042),
            GeoCoordinates(48.10047492032134, 17.24460232012754),
            listOf(GeoCoordinates(48.100524472993364, 17.243852076060037))
        )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        navigation.addOnWaypointPassListener(listener)

        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route)
        val demonstrateSimulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.setSpeedMultiplier(demonstrateSimulatorAdapter, 2F)
        navigationManagerKtx.startSimulator(demonstrateSimulatorAdapter)

        val inOrder: InOrder = inOrder(listener)

        inOrder.verify(listener, timeout(60_000L)).onWaypointPassed(argThat {
            return@argThat this.type == Waypoint.Type.Via
        })

        inOrder.verify(listener, timeout(60_000L)).onFinishReached()

        navigationManagerKtx.stopSimulator(demonstrateSimulatorAdapter)
        navigation.removeOnWaypointPassListener(listener)
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
        mapDownload.installAndLoadMap("sk")
        val listener: NavigationManager.OnHighwayExitListener = mock(verboseLogging = true)
        val route =
            routeCompute.offlineRouteCompute(
                GeoCoordinates(48.1581, 17.1822),
                GeoCoordinates(48.1647, 17.1837)
            )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        navigation.addOnHighwayExitListener(listener)

        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route)
        val demonstrateSimulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.startSimulator(demonstrateSimulatorAdapter)

        Mockito.verify(
            listener,
            Mockito.timeout(10_000L)
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
        navigationManagerKtx.stopNavigation(navigation)
        navigation.removeOnHighwayExitListener(listener)
    }

    @Test
    fun onHighwayExitTestWithoutRoute() = runBlocking {
        mapDownload.installAndLoadMap("sk")
        val listener: NavigationManager.OnHighwayExitListener = mock(verboseLogging = true)

        navigation.addOnHighwayExitListener(listener)

        val nmeaDataProvider = NmeaFileDataProvider(appContext, "highwayExitWithoutRoute.nmea")
        val logSimulator = NmeaLogSimulatorProvider.getInstance(nmeaDataProvider)
        val logSimulatorAdapter = NmeaLogSimulatorAdapter(logSimulator)
        navigationManagerKtx.startSimulator(logSimulatorAdapter)

        Mockito.verify(
            listener,
            Mockito.timeout(20_000L)
        )
            .onHighwayExitInfoChanged(argThat {
                if (this.size == 3) { // this should find 10 exits, but due to time length of test we only search for 3
                    return@argThat true
                }
                false
            })

        navigationManagerKtx.stopSimulator(logSimulatorAdapter)
        navigationManagerKtx.stopNavigation(navigation)
        navigation.removeOnHighwayExitListener(listener)

    }

    @Test
    fun testWaypointPassAudioNotification() = runBlocking {
        mapDownload.installAndLoadMap("sk")
        val listener: NavigationManager.OnWaypointPassListener = mock(verboseLogging = true)

        val route = routeCompute.offlineRouteCompute(
            GeoCoordinates(48.10044188518012, 17.24304412091042),
            GeoCoordinates(48.10047492032134, 17.24460232012754),
            listOf(GeoCoordinates(48.100524472993364, 17.243852076060037))
        )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        navigation.addOnWaypointPassListener(listener)

        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route)
        val demonstrateSimulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.setSpeedMultiplier(demonstrateSimulatorAdapter, 2F)
        navigationManagerKtx.startSimulator(demonstrateSimulatorAdapter)

        val inOrder: InOrder = inOrder(listener)

        inOrder.verify(listener, timeout(60_000L)).onWaypointPassed(argThat {
            return@argThat this.type == Waypoint.Type.Via
        })

        inOrder.verify(listener, timeout(60_000L)).onFinishReached()

        navigationManagerKtx.stopSimulator(demonstrateSimulatorAdapter)
        navigation.removeOnWaypointPassListener(listener)
        navigationManagerKtx.stopNavigation(navigation)

    }

    /**
     * https://jira.sygic.com/browse/SDC-12305
     */
    @Test
    fun testSaveBriefJsonAfterPassWaypointRecompute() = runBlocking {
        val waypointPassListener: NavigationManager.OnWaypointPassListener =
            mock(verboseLogging = true)
        val routeChangedListener: NavigationManager.OnRouteChangedListener =
            mock(verboseLogging = true)
        val routeComputeListener: RouteComputeListener = mock(verboseLogging = true)
        val currentRouteListener: NavigationManager.OnGetCurrentRoute = mock(verboseLogging = true)
        lateinit var briefJson: String
        mapDownload.installAndLoadMap("sk")
        val route =
            routeCompute.offlineRouteCompute(
                start = GeoCoordinates(48.14227359686909, 17.13214634678706),
                waypoints = listOf(GeoCoordinates(48.14407288637856, 17.131352923937925)),
                destination = GeoCoordinates(48.14648207079094, 17.138648964532695)
            )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        navigation.addOnRouteChangedListener(routeChangedListener)
        navigation.addOnWaypointPassListener(waypointPassListener)

        val nmeaDataProvider = NmeaFileDataProvider(appContext, "precision_hdop_output.nmea")
        val logSimulator = NmeaLogSimulatorProvider.getInstance(nmeaDataProvider)
        val logSimulatorAdapter = NmeaLogSimulatorAdapter(logSimulator)
        navigationManagerKtx.setSpeedMultiplier(logSimulatorAdapter, 4F)
        navigationManagerKtx.setRouteForNavigation(route, navigation)
        navigationManagerKtx.startSimulator(logSimulatorAdapter)

        verify(waypointPassListener, timeout(20_000L)).onWaypointPassed(any())

        verify(routeChangedListener, timeout(30_000L)).onRouteChanged(
            any(),
            eq(NavigationManager.RouteUpdateStatus.Success)
        )
        navigation.getCurrentRoute(currentRouteListener)
        val currentRouteCaptor = argumentCaptor<Route>()
        verify(currentRouteListener, timeout(5_000L)).onCurrentRoute(currentRouteCaptor.capture())
        briefJson = currentRouteCaptor.lastValue.serializeToBriefJSON()
        assertFalse(briefJson.isEmpty())

        val routeCaptor = argumentCaptor<Route>()

        runBlocking { RouterProvider.getInstance() }
            .computeRouteFromJSONString(briefJson, routeComputeListener)
        verify(routeComputeListener, timeout(10_000L)).onComputeFinished(
            routeCaptor.capture(), eq(Router.RouteComputeStatus.Success)
        )

        val finalRoute = routeCaptor.lastValue
        assertEquals(finalRoute.waypoints[0].status, Waypoint.Status.Reached)
        assertEquals(finalRoute.waypoints[1].status, Waypoint.Status.Reached)
        assertEquals(finalRoute.waypoints[2].status, Waypoint.Status.Ahead)
    }

    @Test
    fun testStopNavigationWhileDemonstratingOffline() = runBlocking {
        disableOnlineMaps()
        mapDownload.installAndLoadMap("sk")
        val route =
            routeCompute.offlineRouteCompute(
                GeoCoordinates(48.147260, 17.150520),
                GeoCoordinates(48.147230, 17.150120)
            )
        navigationManagerKtx.setRouteForNavigation(route, navigation)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route)
        val demonstrateSimulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.setSpeedMultiplier(demonstrateSimulatorAdapter, 2F)
        navigationManagerKtx.startSimulator(demonstrateSimulatorAdapter)
        delay(2000)
        navigationManagerKtx.stopNavigation(navigation) // shouldn't crash
        delay(500)
    }

    @Test
    fun testStreetChangedListenerOffline() = runBlocking {
        mapDownload.installAndLoadMap("sk")
        val listener: NavigationManager.StreetChangedListener = mock(verboseLogging = true)

        val route = routeCompute.offlineRouteCompute(
            GeoCoordinates(48.1209419355147, 17.207606308128618),
            GeoCoordinates(48.12276083935055, 17.207632634218143),
        )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        navigation.addStreetChangedListener(listener)

        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route)
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

    /**
     * Navigation test on route recompute progress
     *
     * In this test we compute online route and set it for navigation.
     * Via Nmea Log Recorder we set route from assets/SVK-Kosicka.nmea and start nmea log simulation.
     * We verify that the recompute started, was in progress from 0 to 100 and then finished without error.
     */
    @Test
    fun onRouteRecomputeProgressOffline() = runBlocking {
        mapDownload.installAndLoadMap("sk")
        val listener: NavigationManager.OnRouteRecomputeListener =
            mock(verboseLogging = true)
        val route = routeCompute.offlineRouteCompute(
            GeoCoordinates(48.1447, 17.1317),
            GeoCoordinates(48.1461, 17.1285)
        )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        navigation.addOnRouteRecomputeProgressListener(listener)
        val nmeaDataProvider = NmeaFileDataProvider(appContext, "SVK-Kosicka.nmea")
        val logSimulator = NmeaLogSimulatorProvider.getInstance(nmeaDataProvider)
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

    @Test
    fun onRouteChangedTestPedestrian() = runBlocking {
        mapDownload.installAndLoadMap("sk")
        val listener: NavigationManager.OnRouteChangedListener = mock(verboseLogging = true)
        val route = routeCompute.offlineRouteCompute(
            GeoCoordinates(48.1447, 17.1317),
            GeoCoordinates(48.1461, 17.1285),
            routingOptions = RoutingOptions().apply {
                vehicleProfile = null
            }
        )

        val nmeaDataProvider = NmeaFileDataProvider(appContext, "SVK-Kosicka.nmea")
        val logSimulator = NmeaLogSimulatorProvider.getInstance(nmeaDataProvider)
        val logSimulatorAdapter = NmeaLogSimulatorAdapter(logSimulator)
        navigationManagerKtx.setSpeedMultiplier(logSimulatorAdapter, 3F)
        navigationManagerKtx.setRouteForNavigation(route, navigation)
        navigation.addOnRouteChangedListener(listener)
        navigationManagerKtx.startSimulator(logSimulatorAdapter)

        Mockito.verify(
            listener, Mockito.timeout(30_000L).atLeast(1)
        ).onRouteChanged(
            AdditionalMatchers.not(eq(route)),
            eq(NavigationManager.RouteUpdateStatus.Success)
        )

        navigationManagerKtx.stopSimulator(logSimulatorAdapter)
        navigation.removeOnRouteChangedListener(listener)
        navigationManagerKtx.stopNavigation(navigation)
    }
}