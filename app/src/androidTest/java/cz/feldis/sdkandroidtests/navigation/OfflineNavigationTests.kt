package cz.feldis.sdkandroidtests.navigation

import android.graphics.Color
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.sygic.sdk.incidents.SpeedCamera
import com.sygic.sdk.map.Camera
import com.sygic.sdk.map.CameraState
import com.sygic.sdk.map.IncidentWarningSettings
import com.sygic.sdk.map.MapAnimation
import com.sygic.sdk.map.MapCenter
import com.sygic.sdk.map.MapCenterSettings
import com.sygic.sdk.map.MapView
import com.sygic.sdk.map.listeners.OnMapInitListener
import com.sygic.sdk.map.`object`.MapRoute
import com.sygic.sdk.map.`object`.data.RouteData
import com.sygic.sdk.navigation.NavigationManager
import com.sygic.sdk.navigation.NavigationManagerProvider
import com.sygic.sdk.navigation.StreetDetail
import com.sygic.sdk.navigation.explorer.RouteExplorerProvider
import com.sygic.sdk.navigation.explorer.results.ExplorePlacesOnRouteData
import com.sygic.sdk.navigation.routeeventnotifications.HighwayExitInfo
import com.sygic.sdk.navigation.routeeventnotifications.SpeedLimitInfo
import com.sygic.sdk.places.PlacesManager
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.route.Route
import com.sygic.sdk.route.Router
import com.sygic.sdk.route.RouterProvider
import com.sygic.sdk.route.RoutingOptions
import com.sygic.sdk.route.RoutingOptions.NearestAccessiblePointStrategy
import com.sygic.sdk.route.Waypoint
import com.sygic.sdk.route.listeners.RouteComputeListener
import com.sygic.sdk.route.simulator.NmeaLogSimulatorProvider
import com.sygic.sdk.route.simulator.RouteDemonstrateSimulatorProvider
import com.sygic.sdk.vehicletraits.VehicleProfile
import com.sygic.sdk.vehicletraits.general.GeneralVehicleTraits
import com.sygic.sdk.vehicletraits.listeners.SetVehicleProfileListener
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.NmeaFileDataProvider
import cz.feldis.sdkandroidtests.SygicActivity
import cz.feldis.sdkandroidtests.TestMapFragment
import cz.feldis.sdkandroidtests.ktx.NavigationManagerKtx
import cz.feldis.sdkandroidtests.mapInstaller.MapDownloadHelper
import cz.feldis.sdkandroidtests.routing.RouteComputeHelper
import cz.feldis.sdkandroidtests.utils.NmeaLogSimulatorAdapter
import cz.feldis.sdkandroidtests.utils.RouteDemonstrateSimulatorAdapter
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class OfflineNavigationTests : BaseTest() {
    companion object {
        private const val TAG = "OfflineNavigationTests"
    }

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
        try {
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
        } finally {
            navigationManagerKtx.stopSimulator(demonstrateSimulatorAdapter)
            navigation.removeOnIncidentListener(listener)
            navigationManagerKtx.stopNavigation(navigation)
        }
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
        try {
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
        } finally {
            navigationManagerKtx.stopSimulator(demonstrateSimulatorAdapter)
            navigation.removeOnIncidentListener(listener)
            navigationManagerKtx.stopNavigation(navigation)
        }
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
        NavigationManagerProvider.getInstance().setRouteForNavigation(route)
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

        navigationManagerKtx.stopSimulator(logSimulatorAdapter)
        navigation.removeOnWaypointPassListener(waypointPassListener)
        navigation.removeOnRouteChangedListener(routeChangedListener)
        navigationManagerKtx.stopNavigation(navigation)
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
        val loggingListener = object : NavigationManager.OnRouteRecomputeListener {
            override fun onRouteRecomputeStarted(data: NavigationManager.OnRouteRecomputeListener.RecomputeStartedData) {
                Log.d(TAG, "[recompute] started reason=${data.reason}")
            }

            override fun onRouteRecomputeProgress(data: NavigationManager.OnRouteRecomputeListener.RecomputeProgressData) {
                Log.d(TAG, "[recompute] progress=${data.progress}")
            }

            override fun onRouteRecomputeFinished(data: NavigationManager.OnRouteRecomputeListener.RecomputeFinishedData) {
                Log.d(TAG, "[recompute] finished result=${data.result}")
            }
        }
        val route = routeCompute.offlineRouteCompute(
            GeoCoordinates(48.1447, 17.1317),
            GeoCoordinates(48.1461, 17.1285)
        )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        navigation.addOnRouteRecomputeProgressListener(listener)
        navigation.addOnRouteRecomputeProgressListener(loggingListener)
        val nmeaDataProvider = NmeaFileDataProvider(appContext, "SVK-Kosicka.nmea")
        val logSimulator = NmeaLogSimulatorProvider.getInstance(nmeaDataProvider)
        val logSimulatorAdapter = NmeaLogSimulatorAdapter(logSimulator)
        Log.d(TAG, "[recompute] starting simulator for onRouteRecomputeProgressOffline")
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
        Log.d(TAG, "[recompute] verified finished(success)")

        Mockito.verify(
            listener, never()
        )
            .onRouteRecomputeFinished(eq(recomputeFinishedFailedData))

        navigationManagerKtx.stopSimulator(logSimulatorAdapter)
        navigation.removeOnRouteRecomputeProgressListener(listener)
        navigation.removeOnRouteRecomputeProgressListener(loggingListener)
        navigationManagerKtx.stopNavigation(navigation)
    }

    /**
     * Ensures a recompute cycle is finalized in the expected order:
     * progress(100) -> finished.
     */
    @Test
    fun onRouteRecomputeProgress100BeforeFinishedOffline() {
        runBlocking {
            mapDownload.installAndLoadMap("sk")

            val route = routeCompute.offlineRouteCompute(
                GeoCoordinates(48.1447, 17.1317),
                GeoCoordinates(48.1461, 17.1285)
            )
            val expectedProgress100 =
                NavigationManager.OnRouteRecomputeListener.RecomputeProgressData(route, 100)

            val finishedCount = AtomicInteger(0)
            val orderingViolation = AtomicReference<String?>(null)
            val seenProgress100InCurrentCycle = AtomicBoolean(false)

            val listener = object : NavigationManager.OnRouteRecomputeListener {
                override fun onRouteRecomputeStarted(data: NavigationManager.OnRouteRecomputeListener.RecomputeStartedData) {
                    seenProgress100InCurrentCycle.set(false)
                    Log.d(TAG, "[recompute-order] started reason=${data.reason}")
                }

                override fun onRouteRecomputeProgress(data: NavigationManager.OnRouteRecomputeListener.RecomputeProgressData) {
                    Log.d(TAG, "[recompute-order] progress=${data.progress}")
                    if (data == expectedProgress100) {
                        seenProgress100InCurrentCycle.set(true)
                        Log.d(TAG, "[recompute-order] progress 100 observed")
                    }
                }

                override fun onRouteRecomputeFinished(data: NavigationManager.OnRouteRecomputeListener.RecomputeFinishedData) {
                    finishedCount.incrementAndGet()
                    Log.d(
                        TAG,
                        "[recompute-order] finished result=${data.result}, seen100=${seenProgress100InCurrentCycle.get()}"
                    )
                    if (!seenProgress100InCurrentCycle.get()) {
                        orderingViolation.compareAndSet(
                            null,
                            "onRouteRecomputeFinished arrived before progress 100"
                        )
                    }
                }
            }

            navigationManagerKtx.setRouteForNavigation(route, navigation)
            navigation.addOnRouteRecomputeProgressListener(listener)

            val nmeaDataProvider = NmeaFileDataProvider(appContext, "SVK-Kosicka.nmea")
            val logSimulator = NmeaLogSimulatorProvider.getInstance(nmeaDataProvider)
            val logSimulatorAdapter = NmeaLogSimulatorAdapter(logSimulator)

            try {
                Log.d(TAG, "[recompute-order] starting simulator")
                navigationManagerKtx.startSimulator(logSimulatorAdapter)

                withTimeout(25_000L) {
                    while (finishedCount.get() == 0 && orderingViolation.get() == null) {
                        delay(100)
                    }
                }

                assertTrue(
                    orderingViolation.get() ?: "Expected at least one recompute finished callback",
                    finishedCount.get() > 0 && orderingViolation.get() == null
                )
                Log.d(TAG, "[recompute-order] verification passed; finishedCount=${finishedCount.get()}")
            } finally {
                Log.d(TAG, "[recompute-order] cleanup")
                navigationManagerKtx.stopSimulator(logSimulatorAdapter)
                navigation.removeOnRouteRecomputeProgressListener(listener)
                navigationManagerKtx.stopNavigation(navigation)
            }
        }
    }

    /**
     * Stress variant of recompute callback order check.
     * Runs multiple independent attempts to increase chance of catching nondeterministic ordering issues.
     */
    @Test
    fun onRouteRecomputeProgress100BeforeFinishedOfflineRepeatedly() {
        runBlocking {
            mapDownload.installAndLoadMap("sk")
            val attempts = 10

            repeat(attempts) { index ->
                val attempt = index + 1
                Log.d(TAG, "[recompute-order][attempt $attempt/$attempts] setup")

                val route = routeCompute.offlineRouteCompute(
                    GeoCoordinates(48.1447, 17.1317),
                    GeoCoordinates(48.1461, 17.1285)
                )
                val expectedProgress100 =
                    NavigationManager.OnRouteRecomputeListener.RecomputeProgressData(route, 100)

                val finishedCount = AtomicInteger(0)
                val orderingViolation = AtomicReference<String?>(null)
                val seenProgress100InCurrentCycle = AtomicBoolean(false)

                val listener = object : NavigationManager.OnRouteRecomputeListener {
                    override fun onRouteRecomputeStarted(data: NavigationManager.OnRouteRecomputeListener.RecomputeStartedData) {
                        seenProgress100InCurrentCycle.set(false)
                        Log.d(TAG, "[recompute-order][attempt $attempt] started reason=${data.reason}")
                    }

                    override fun onRouteRecomputeProgress(data: NavigationManager.OnRouteRecomputeListener.RecomputeProgressData) {
                        Log.d(TAG, "[recompute-order][attempt $attempt] progress=${data.progress}")
                        if (data == expectedProgress100) {
                            seenProgress100InCurrentCycle.set(true)
                            Log.d(TAG, "[recompute-order][attempt $attempt] progress 100 observed")
                        }
                    }

                    override fun onRouteRecomputeFinished(data: NavigationManager.OnRouteRecomputeListener.RecomputeFinishedData) {
                        finishedCount.incrementAndGet()
                        Log.d(
                            TAG,
                            "[recompute-order][attempt $attempt] finished result=${data.result}, seen100=${seenProgress100InCurrentCycle.get()}"
                        )
                        if (!seenProgress100InCurrentCycle.get()) {
                            orderingViolation.compareAndSet(
                                null,
                                "Attempt $attempt: onRouteRecomputeFinished arrived before progress 100"
                            )
                        }
                    }
                }

                navigationManagerKtx.setRouteForNavigation(route, navigation)
                navigation.addOnRouteRecomputeProgressListener(listener)

                val nmeaDataProvider = NmeaFileDataProvider(appContext, "SVK-Kosicka.nmea")
                val logSimulator = NmeaLogSimulatorProvider.getInstance(nmeaDataProvider)
                val logSimulatorAdapter = NmeaLogSimulatorAdapter(logSimulator)

                try {
                    Log.d(TAG, "[recompute-order][attempt $attempt] starting simulator")
                    navigationManagerKtx.startSimulator(logSimulatorAdapter)

                    withTimeout(25_000L) {
                        while (finishedCount.get() == 0 && orderingViolation.get() == null) {
                            delay(100)
                        }
                    }

                    val isValid = finishedCount.get() > 0 && orderingViolation.get() == null
                    assertTrue(
                        orderingViolation.get()
                            ?: "Attempt $attempt: expected at least one recompute finished callback",
                        isValid
                    )
                    Log.d(TAG, "[recompute-order][attempt $attempt] passed; finishedCount=${finishedCount.get()}")
                } finally {
                    Log.d(TAG, "[recompute-order][attempt $attempt] cleanup")
                    navigationManagerKtx.stopSimulator(logSimulatorAdapter)
                    navigation.removeOnRouteRecomputeProgressListener(listener)
                    navigationManagerKtx.stopNavigation(navigation)
                    delay(300)
                }
            }
        }
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

    @Test
    fun setIncidentWarningSettingsAfterPassingSpeedCam(): Unit = runBlocking {
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        // create test scenario with activity & map fragment
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager
                .beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }

        val nmeaDataProvider = NmeaFileDataProvider(appContext, "speedcamnmnv.nmea")
        val logSimulator = NmeaLogSimulatorProvider.getInstance(nmeaDataProvider)
        val logSimulatorAdapter = NmeaLogSimulatorAdapter(logSimulator)

        try {
            val mapView = getMapView(mapFragment)
            mapDownload.installAndLoadMap("sk")

            navigationManagerKtx.startSimulator(logSimulatorAdapter)
            navigationManagerKtx.setSpeedMultiplier(logSimulatorAdapter, 2F)

            mapView.cameraModel.setMovementMode(Camera.MovementMode.FollowGpsPosition)
            mapView.cameraModel.setRotationMode(Camera.RotationMode.Vehicle)
            mapView.cameraModel.setZoomLevel(19F)
            mapView.cameraModel.setTilt(45F)

            val incidentsFlow = NavigationManagerProvider.getInstance().incidents()
            val targetIncident = withTimeout(20_000) {
                incidentsFlow
                    .filter { it.isNotEmpty() }
                    .map { it.first() }
                    .first()
            }

            var lastDistance = Int.MAX_VALUE
            var seenAtLeastOnce = false

            withTimeout(20_000) {
                incidentsFlow
                    .map { list ->
                        list.find { it.incident.id == targetIncident.incident.id }
                    }
                    .onEach { info ->
                        if (info != null) {
                            seenAtLeastOnce = true
                            val distance = info.distance
                            assertTrue(
                                "Distance should diminish (before=$lastDistance, now=$distance)",
                                distance <= lastDistance

                            )
                            lastDistance = distance
                        }
                    }
                    .first { info -> seenAtLeastOnce && info == null }
            }

            delay(2_000)

            val incidentWarningSettings = IncidentWarningSettings()
            mapView.setIncidentWarningSettings(incidentWarningSettings)

            delay(2_000)


        } finally {
            scenario.moveToState(Lifecycle.State.DESTROYED)
            navigationManagerKtx.stopSimulator(logSimulatorAdapter)
            navigationManagerKtx.stopNavigation(navigation)
        }
    }

    @Test
    fun navigationInterruptedByMapReloadTest(): Unit = runBlocking {
        mapDownload.installAndLoadMap("sk")

        val start = GeoCoordinates(48.1486, 17.1077)
        val destination = GeoCoordinates(48.7164, 21.2611)

        val route = routeCompute.offlineRouteCompute(
            start,
            destination,
            routingOptions = RoutingOptions().apply {
                useEndpointProtection = true
                napStrategy = NearestAccessiblePointStrategy.Disabled
                useTraffic = false
                useSpeedProfiles = false
            }
        )

        navigationManagerKtx.setRouteForNavigation(route, navigation)

        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route)
        val simulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)

        try {
            navigationManagerKtx.startSimulator(simulatorAdapter)

            val flow = RouteExplorerProvider.getInstance().explorePlacesOnRoute(route, listOf())
            delay(1000)
            mapDownload.unloadMap("sk")
            val error = flow
                .onEach {
                    if (it is ExplorePlacesOnRouteData.PlacesLoaded) {
                        Log.d("SYGIC", "PROGRESS - ${it.progress}")
                    }
                }
                .filterIsInstance<ExplorePlacesOnRouteData.Error>()
                .onEach { Log.d("SYGIC", "ERRROR ----- ${it.errorCode.name}") }
                .first({ it.errorCode == PlacesManager.ErrorCode.CORRUPTED_DATA })
            delay(1_000)

            assertTrue(error.errorCode == PlacesManager.ErrorCode.CORRUPTED_DATA)
        } finally {
            navigationManagerKtx.stopSimulator(simulatorAdapter)
            navigationManagerKtx.stopNavigation(navigation)
        }
    }

    @Test
    fun onWaypointPassRestrictedDestinationTestOffline() = runBlocking {
        disableOnlineMaps()
        mapDownload.installAndLoadMap("at")
        val listener: NavigationManager.OnWaypointPassListener = mock(verboseLogging = true)
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager
                .beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }
        val mapView = getMapView(mapFragment)
        var demonstrateSimulatorAdapter: RouteDemonstrateSimulatorAdapter? = null

        try {
            val route = routeCompute.offlineRouteCompute(
                GeoCoordinates(48.258950, 16.457700),
                GeoCoordinates(48.257590, 16.455430),
                listOf(GeoCoordinates(48.258140, 16.456600)),
                routingOptions = RoutingOptions().apply {
                    useEndpointProtection = true
                    napStrategy = NearestAccessiblePointStrategy.Disabled
                    arriveInDrivingSide = true
                    useTraffic = true
                    useSpeedProfiles = true
                }
            )

            navigationManagerKtx.setRouteForNavigation(route, navigation)
            navigation.addOnWaypointPassListener(listener)
            mapView.cameraModel.setRotationMode(Camera.RotationMode.Vehicle)
            mapView.cameraModel.setMovementMode(Camera.MovementMode.FollowGpsPositionWithAutozoom)
            mapView.cameraModel.setTilt(45F)
            mapView.mapDataModel.addMapObject(MapRoute(RouteData(route = route)))

            val simulator = RouteDemonstrateSimulatorProvider.getInstance(route)
            demonstrateSimulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
            navigationManagerKtx.setSpeedMultiplier(demonstrateSimulatorAdapter, 6F)
            navigationManagerKtx.startSimulator(demonstrateSimulatorAdapter)

            // check the order of calls: first onWaypointPassed, then onFinishReached
            val inOrder: InOrder = inOrder(listener)
            inOrder.verify(listener, timeout(60_000L)).onWaypointPassed(any())
            inOrder.verify(listener, timeout(60_000L)).onFinishReached()
        } finally {
            demonstrateSimulatorAdapter?.let { navigationManagerKtx.stopSimulator(it) }
            navigation.removeOnWaypointPassListener(listener)
            navigationManagerKtx.stopNavigation(navigation)
            scenario.moveToState(Lifecycle.State.DESTROYED)
        }
    }

    /**
     * Stress reproducer for native teardown race in position source switching.
     *
     * This repeatedly alternates RouteDemonstrate and NMEA simulators while
     * stopping/setting navigation in between to exercise SetPositionDataSource
     * and ResetRoadSnapping paths under pressure.
     */
    @Test
    fun positionSourceSwitchStressReproducer_noNativeCrash() = runBlocking {
        disableOnlineMaps()
        mapDownload.installAndLoadMap("sk")

        val route = routeCompute.offlineRouteCompute(
            GeoCoordinates(48.147682401781026, 17.14365655304184),
            GeoCoordinates(48.15310362223699, 17.147190865317768)
        )

        val routeSimulator = RouteDemonstrateSimulatorAdapter(
            RouteDemonstrateSimulatorProvider.getInstance(route)
        )
        val nmeaSimulator = NmeaLogSimulatorAdapter(
            NmeaLogSimulatorProvider.getInstance(
                NmeaFileDataProvider(appContext, "SVK-Kosicka.nmea")
            )
        )

        navigationManagerKtx.setRouteForNavigation(route, navigation)

        try {
            repeat(40) { index ->
                val simulator = if (index % 2 == 0) routeSimulator else nmeaSimulator
                navigationManagerKtx.setSpeedMultiplier(simulator, 6F)
                navigationManagerKtx.startSimulator(simulator)
                delay(150)
                navigationManagerKtx.stopSimulator(simulator)

                // Periodically force navigation teardown/re-setup to stress source switching.
                if (index % 5 == 0) {
                    navigationManagerKtx.stopNavigation(navigation)
                    navigationManagerKtx.setRouteForNavigation(route, navigation)
                }
            }
        } finally {
            runCatching { navigationManagerKtx.stopSimulator(routeSimulator) }
            runCatching { navigationManagerKtx.stopSimulator(nmeaSimulator) }
            runCatching { navigationManagerKtx.stopNavigation(navigation) }
        }

        // Test is successful if process remains alive and reaches this point.
        assertTrue(true)
    }


    @Test
    fun checkSpeedUnitsGbRoundingOfflineTest(): Unit = runBlocking {
        mapDownload.installAndLoadMap("gb")

        val route = routeCompute.offlineRouteCompute(
            GeoCoordinates(51.68334, -0.04733),
            GeoCoordinates(51.68254, -0.03761)
        )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route)
        val demonstrateSimulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)

        try {
            navigationManagerKtx.setSpeedMultiplier(demonstrateSimulatorAdapter, 1F)
            navigationManagerKtx.startSimulator(demonstrateSimulatorAdapter)

            val timeoutMs = 25_000L
            var lastObserved = "<no speedLimits emission>"
            Log.d(TAG, "[speed-units][gb-rounding] waiting for next limit 70 mph / 113 km/h")

            try {
                withTimeout(timeoutMs) {
                    navigation.speedLimits()
                        .onEach {
                            val snapshot =
                                "countryUnits=${it.countrySpeedUnits}, currentKmh=${it.getSpeedLimit(SpeedLimitInfo.SpeedUnits.Kilometers)}, currentMph=${it.getSpeedLimit(SpeedLimitInfo.SpeedUnits.Miles)}, nextKmh=${it.getNextSpeedLimit(SpeedLimitInfo.SpeedUnits.Kilometers)}, nextMph=${it.getNextSpeedLimit(SpeedLimitInfo.SpeedUnits.Miles)}"
                            lastObserved = snapshot
                            Log.d(TAG, "[speed-units][gb-rounding] $snapshot")
                        }
                        .first {
                            val nextKmh = it.getNextSpeedLimit(SpeedLimitInfo.SpeedUnits.Kilometers)
                            val nextMph = it.getNextSpeedLimit(SpeedLimitInfo.SpeedUnits.Miles)

                            nextKmh == 113 && nextMph == 70
                        }
                }
            } catch (e: TimeoutCancellationException) {
                throw AssertionError(
                    "Timed out after ${timeoutMs}ms waiting for next speed limit 70 mph / 113 km/h. Last observed emission: $lastObserved",
                    e
                )
            }
        } finally {
            navigationManagerKtx.stopSimulator(demonstrateSimulatorAdapter)
            navigationManagerKtx.stopNavigation(navigation)
        }
    }

    @Test
    fun checkSpeedUnitsGbCountryUnitsOfflineTest() = runBlocking {
        mapDownload.installAndLoadMap("gb")

        val route = routeCompute.offlineRouteCompute(
            GeoCoordinates(51.68334, -0.04733),
            GeoCoordinates(51.68254, -0.03761)
        )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route)
        val demonstrateSimulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)

        try {
            navigationManagerKtx.setSpeedMultiplier(demonstrateSimulatorAdapter, 1F)
            navigationManagerKtx.startSimulator(demonstrateSimulatorAdapter)

            val timeoutMs = 25_000L
            var lastObserved = "<no speedLimits emission>"

            val info = try {
                withTimeout(timeoutMs) {
                    navigation.speedLimits()
                        .onEach {
                            val snapshot =
                                "countryUnits=${it.countrySpeedUnits}, nextKmh=${it.getNextSpeedLimit(SpeedLimitInfo.SpeedUnits.Kilometers)}, nextMph=${it.getNextSpeedLimit(SpeedLimitInfo.SpeedUnits.Miles)}"
                            lastObserved = snapshot
                            Log.d(TAG, "[speed-units][gb-country] $snapshot")
                        }
                        .first { it.getNextSpeedLimit(SpeedLimitInfo.SpeedUnits.Miles) > 0 }
                }
            } catch (e: TimeoutCancellationException) {
                throw AssertionError(
                    "Timed out after ${timeoutMs}ms waiting for GB country speed units. Last observed emission: $lastObserved",
                    e
                )
            }

            assertEquals(SpeedLimitInfo.SpeedUnits.Miles, info.countrySpeedUnits)
        } finally {
            navigationManagerKtx.stopSimulator(demonstrateSimulatorAdapter)
            navigationManagerKtx.stopNavigation(navigation)
        }
    }

    @Test
    fun checkSpeedUnitsGbNextLimitRawAndConvertedOfflineTest() = runBlocking {
        mapDownload.installAndLoadMap("gb")

        val route = routeCompute.offlineRouteCompute(
            GeoCoordinates(51.68334, -0.04733),
            GeoCoordinates(51.68254, -0.03761)
        )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route)
        val demonstrateSimulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)

        try {
            navigationManagerKtx.setSpeedMultiplier(demonstrateSimulatorAdapter, 1F)
            navigationManagerKtx.startSimulator(demonstrateSimulatorAdapter)

            val timeoutMs = 25_000L
            var lastObserved = "<no speedLimits emission>"

            val info = try {
                withTimeout(timeoutMs) {
                    navigation.speedLimits()
                        .onEach {
                            val snapshot =
                                "rawNextKmh=${it.nextSpeedLimit}, nextKmh=${it.getNextSpeedLimit(SpeedLimitInfo.SpeedUnits.Kilometers)}, nextMph=${it.getNextSpeedLimit(SpeedLimitInfo.SpeedUnits.Miles)}"
                            lastObserved = snapshot
                            Log.d(TAG, "[speed-units][gb-next-raw] $snapshot")
                        }
                        .first { it.getNextSpeedLimit(SpeedLimitInfo.SpeedUnits.Miles) == 70 }
                }
            } catch (e: TimeoutCancellationException) {
                throw AssertionError(
                    "Timed out after ${timeoutMs}ms waiting for GB next speed limit 70 mph. Last observed emission: $lastObserved",
                    e
                )
            }

            assertEquals(113, info.getNextSpeedLimit(SpeedLimitInfo.SpeedUnits.Kilometers))
            assertTrue("Expected raw next km/h near 113, was ${info.nextSpeedLimit}", info.nextSpeedLimit in 112.5f..113.5f)
        } finally {
            navigationManagerKtx.stopSimulator(demonstrateSimulatorAdapter)
            navigationManagerKtx.stopNavigation(navigation)
        }
    }

    /**
     * https://jira.sygic.com/browse/CI-3803
     * TC929
     *
     * Verifies that average speed camera (section camera) warnings are delivered
     * via OnIncidentListener during navigation simulation in Czech Republic.
     */
    @Test
    fun averageSpeedCameraWarningCzechRepublic() = runBlocking {
        mapDownload.installAndLoadMap("cz")
        val listener: NavigationManager.OnIncidentListener = mock(verboseLogging = true)

        val route = routeCompute.offlineRouteCompute(
            GeoCoordinates(48.952460, 16.522900),
            GeoCoordinates(48.980110, 16.512800)
        )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        navigation.addOnIncidentListener(listener)

        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route)
        val demonstrateSimulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
        try {
            navigationManagerKtx.setSpeedMultiplier(demonstrateSimulatorAdapter, 4F)
            navigationManagerKtx.startSimulator(demonstrateSimulatorAdapter)

            verify(listener, timeout(20_000L).atLeastOnce()).onIncidentsInfoChanged(argThat {
                this.any { (it.incident as? SpeedCamera)?.category == "SYRadarStaticAverageSpeed" }
            })
        } finally {
            navigationManagerKtx.stopSimulator(demonstrateSimulatorAdapter)
            navigation.removeOnIncidentListener(listener)
            navigationManagerKtx.stopNavigation(navigation)
        }
    }
}
