package cz.feldis.sdkandroidtests.navigation

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.atMost
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.timeout
import com.nhaarman.mockitokotlin2.verify
import com.sygic.sdk.incidents.SpeedCamera
import com.sygic.sdk.map.Camera
import com.sygic.sdk.map.CameraState
import com.sygic.sdk.map.LogisticInfoSettings
import com.sygic.sdk.map.MapAnimation
import com.sygic.sdk.map.MapCenter
import com.sygic.sdk.map.MapCenterSettings
import com.sygic.sdk.map.MapView
import com.sygic.sdk.map.listeners.OnMapInitListener
import com.sygic.sdk.navigation.NavigationManager
import com.sygic.sdk.navigation.NavigationManager.OnRouteChangedListener
import com.sygic.sdk.navigation.NavigationManager.OnWaypointPassListener
import com.sygic.sdk.navigation.NavigationManagerProvider
import com.sygic.sdk.navigation.StreetDetail
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.position.PositionManagerProvider
import com.sygic.sdk.route.Route
import com.sygic.sdk.route.RouteManeuver
import com.sygic.sdk.route.Router
import com.sygic.sdk.route.RouterProvider
import com.sygic.sdk.route.Waypoint
import com.sygic.sdk.route.listeners.RouteComputeListener
import com.sygic.sdk.route.simulator.NmeaLogSimulatorProvider
import com.sygic.sdk.route.simulator.RouteDemonstrateSimulatorProvider
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.SygicActivity
import cz.feldis.sdkandroidtests.TestMapFragment
import cz.feldis.sdkandroidtests.mapInstaller.MapDownloadHelper
import cz.feldis.sdkandroidtests.routing.RouteComputeHelper
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.mockito.AdditionalMatchers
import org.mockito.InOrder
import org.mockito.Mockito
import timber.log.Timber

class OfflineNavigationTests : BaseTest() {
    private lateinit var routeCompute: RouteComputeHelper
    private lateinit var mapDownload: MapDownloadHelper

    @Before
    override fun setUp() {
        super.setUp()
        routeCompute = RouteComputeHelper()
        mapDownload = MapDownloadHelper()
        disableOnlineMaps()
    }

    @Test
    fun onSharpCurveListenerTest() {
        mapDownload.installAndLoadMap("sk")
        val navigation = NavigationManagerProvider.getInstance().get()
        val listener: NavigationManager.OnSharpCurveListener = mock(verboseLogging = true)

        val route = routeCompute.offlineRouteCompute(
            GeoCoordinates(48.1384, 17.3184),
            GeoCoordinates(48.132, 17.3009)
        )

        navigation.setRouteForNavigation(route)
        navigation.addOnSharpCurveListener(listener)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        simulator.start()
        simulator.setSpeedMultiplier(4F)

        Mockito.verify(
            listener, Mockito.timeout(10_000L)
        ).onSharpCurveInfoChanged(argThat {
            if (this.angle != 0.0) {
                return@argThat true
            }
            false
        })

        simulator.stop()
        simulator.destroy()
        navigation.removeOnSharpCurveListener(listener)
        navigation.stopNavigation()
        PositionManagerProvider.getInstance().get().stopPositionUpdating()
    }

    /**
     * Navigation test on direction info changed
     *
     * In this test we compute an offline route and set it for navigation.
     * Via simulator provider we set this route and start demonstrate. We verify that onDirectionInfoChanged
     * contains direction info with primary nextRoadName "Einsteinova".
     */
    @Test
    @Ignore("fsdkfjsdf")
    fun onDirectionInfoChangedTest() {
        mapDownload.installAndLoadMap("sk")
        val directionListener: NavigationManager.OnDirectionListener = mock(verboseLogging = true)
        val navigation = NavigationManagerProvider.getInstance().get()

        val route = routeCompute.offlineRouteCompute(
            GeoCoordinates(48.132310, 17.114100),
            GeoCoordinates(48.131733, 17.109952)
        )

        navigation.setRouteForNavigation(route)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        simulator.start()
        navigation.addOnDirectionListener(directionListener)

        Mockito.verify(
            directionListener, Mockito.timeout(15_000L)
        ).onDirectionInfoChanged(argThat {
            if (this.primary.nextRoadName != "Einsteinova") {
                Timber.e("Primary road name is not equal to Einsteinova.")
                return@argThat false
            }
            true
        })

        navigation.removeOnDirectionListener(directionListener)
        navigation.stopNavigation()
        simulator.stop()
        simulator.destroy()
    }

    /**
     * Navigation test on route changed
     *
     * In this test we compute an offline route and set it for navigation.
     * Using the Nmea Log Recorder we set a route from assets/SVK-Kosicka.nmea and start the simulation.
     * We verify that the onRouteChanged callback is called and the status is Success.
     */
    @Test
    fun onRouteChangedTest() {
        mapDownload.installAndLoadMap("sk")
        val listener: NavigationManager.OnRouteChangedListener = mock(verboseLogging = true)
        val navigation = NavigationManagerProvider.getInstance().get()
        val route = routeCompute.offlineRouteCompute(
            GeoCoordinates(48.14364765102184, 17.131080867348153),
            GeoCoordinates(48.14852112743662, 17.13397077018316)
        )

        val logSimulator =
            NmeaLogSimulatorProvider.getInstance("$appDataPath/SVK-Kosicka.nmea").get()
        logSimulator.setSpeedMultiplier(2F)
        navigation.setRouteForNavigation(route)
        navigation.addOnRouteChangedListener(listener)
        logSimulator.start()

        Mockito.verify(
            listener, Mockito.timeout(30_000L).atLeast(1)
        ).onRouteChanged(
            AdditionalMatchers.not(eq(route)),
            eq(NavigationManager.RouteUpdateStatus.Success)
        )


        logSimulator.stop()
        logSimulator.destroy()
        navigation.removeOnRouteChangedListener(listener)
        navigation.stopNavigation()
        PositionManagerProvider.getInstance().get().stopPositionUpdating()
    }

    @Test
    fun onJunctionPassedStandaloneListenerInvocationWithoutRoute() {
        mapDownload.installAndLoadMap("sk")
        val listener: NavigationManager.JunctionPassedListener = mock(verboseLogging = true)
        val navigation = NavigationManagerProvider.getInstance().get()

        val logSimulator = NmeaLogSimulatorProvider.getInstance("$appDataPath/rovinka.nmea").get()
        Thread.sleep(3000)
        logSimulator.start()

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

        logSimulator.stop()
        logSimulator.destroy()
        navigation.removeJunctionPassedListener(listener)
        navigation.stopNavigation()
        PositionManagerProvider.getInstance().get().stopPositionUpdating()
    }

    /**
     * Navigation test on lane listener
     *
     * In this test we compute route and set it for navigation.
     * Via simulator provider we set this route and start demonstrate navigation.
     * We verify that onLaneInfoChanged was invoked.
     */
    @Test
    fun onLaneListenerTestOffline() {
        mapDownload.installAndLoadMap("sk")
        val navigation = NavigationManagerProvider.getInstance().get()
        val listener: NavigationManager.OnLaneListener = mock(verboseLogging = true)

        val route = routeCompute.offlineRouteCompute(
            GeoCoordinates(48.147682401781026, 17.14365655304184),
            GeoCoordinates(48.15310362223699, 17.147190865317768)
        )

        navigation.setRouteForNavigation(route)
        navigation.addOnLaneListener(listener)

        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        simulator.setSpeedMultiplier(4F)
        simulator.start()


        Mockito.verify(
            listener, Mockito.timeout(30_000L)
        ).onLaneInfoChanged(argThat {
            if (this.simpleLanesInfo?.lanes?.isNotEmpty() == true) {
                return@argThat true
            }
            false
        })

        simulator.stop()
        simulator.destroy()
        navigation.removeOnLaneListener(listener)
        navigation.stopNavigation()
        PositionManagerProvider.getInstance().get().stopPositionUpdating()
    }

    @Test
    fun sectionCameraTest() {
        mapDownload.installAndLoadMap("be")
        val navigation = NavigationManagerProvider.getInstance().get()
        val listener: NavigationManager.OnIncidentListener = mock(verboseLogging = true)

        val route = routeCompute.offlineRouteCompute(
            GeoCoordinates(51.007530, 3.175810),
            GeoCoordinates(51.001320, 3.203420)
        )

        navigation.setRouteForNavigation(route)
        navigation.addOnIncidentListener(listener)

        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        simulator.setSpeedMultiplier(4F)
        simulator.start()

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

        simulator.stop()
        simulator.destroy()
        navigation.removeOnIncidentListener(listener)
        navigation.stopNavigation()
        PositionManagerProvider.getInstance().get().stopPositionUpdating()
    }

    @Test
    fun checkSpeedLimitOfRealCamera() {
        mapDownload.installAndLoadMap("sk")
        val navigation = NavigationManagerProvider.getInstance().get()
        val listener: NavigationManager.OnIncidentListener = mock(verboseLogging = true)

        val route = routeCompute.offlineRouteCompute(
            GeoCoordinates(48.7429, 17.8603),
            GeoCoordinates(48.7457, 17.86)
        )

        navigation.setRouteForNavigation(route)
        navigation.addOnIncidentListener(listener)

        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        simulator.setSpeedMultiplier(4F)
        simulator.start()

        verify(listener, timeout(20_000L)).onIncidentsInfoChanged(argThat {
            this.forEach {
                if (it.incident is SpeedCamera) {
                    val expectedSpeedcam = it.incident as SpeedCamera
                    if (expectedSpeedcam.speedLimit == 130) return@argThat true
                }
            }
            false
        })

        simulator.stop()
        simulator.destroy()
        navigation.removeOnIncidentListener(listener)
        navigation.stopNavigation()
        PositionManagerProvider.getInstance().get().stopPositionUpdating()
    }

    @Test
    fun changeMaxSpeedAndCheckSpeedLimit() {
        val mapFragment = TestMapFragment.newInstance(
            getInitialCameraState(
                GeoCoordinates(48.18180777150043, 17.05352048126561)
            )
        )
        // create test scenario with activity & map fragment
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager
                .beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }

        val mapView = getMapView(mapFragment)

        val logisticSettings = LogisticInfoSettings()
        logisticSettings.specialSpeedRestriction = 80

        mapView.setLogisticInfoSettings(logisticSettings)

        mapDownload.installAndLoadMap("sk")
        val navigation = NavigationManagerProvider.getInstance().get()
        val listener: NavigationManager.OnSpeedLimitListener = mock(verboseLogging = true)

        val route = routeCompute.offlineRouteCompute(
            GeoCoordinates(48.18180777150043, 17.05352048126561),
            GeoCoordinates(48.18417452255745, 17.04909691425327),
        )

        navigation.setRouteForNavigation(route)
        navigation.addOnSpeedLimitListener(listener)

        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        simulator.setSpeedMultiplier(1F)
        simulator.start()

        verify(listener, timeout(20_000L)).onSpeedLimitInfoChanged(
            argThat {
                return@argThat this.nextSpeedLimit == 80.0f
            }
        )

        simulator.stop()
        simulator.destroy()
        navigation.removeOnSpeedLimitListener(listener)
        navigation.stopNavigation()
        PositionManagerProvider.getInstance().get().stopPositionUpdating()

        //close scenario & activity
        scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    @Test
    fun onWaypointAndFinishReached() {
        mapDownload.installAndLoadMap("sk")
        val navigation = NavigationManagerProvider.getInstance().get()
        val listener: NavigationManager.OnWaypointPassListener = mock(verboseLogging = true)

        val route = routeCompute.offlineRouteCompute(
            GeoCoordinates(48.10044188518012, 17.24304412091042),
            GeoCoordinates(48.100524472993364, 17.243852076060037),
            GeoCoordinates(48.10047492032134, 17.24460232012754)
        )

        navigation.setRouteForNavigation(route)
        navigation.addOnWaypointPassListener(listener)

        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        simulator.setSpeedMultiplier(2F)
        simulator.start()

        val inOrder: InOrder = inOrder(listener)

        inOrder.verify(listener, timeout(60_000L)).onWaypointPassed(argThat {
            return@argThat this.type == Waypoint.Type.Via
        })

        inOrder.verify(listener, timeout(60_000L)).onFinishReached()

        simulator.stop()
        simulator.destroy()
        navigation.removeOnWaypointPassListener(listener)
        navigation.stopNavigation()
        PositionManagerProvider.getInstance().get().stopPositionUpdating()
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
        mapDownload.installAndLoadMap("sk")
        val navigation = NavigationManagerProvider.getInstance().get()
        val listener: NavigationManager.OnHighwayExitListener = mock(verboseLogging = true)
        val route =
            routeCompute.offlineRouteCompute(
                GeoCoordinates(48.1581, 17.1822),
                GeoCoordinates(48.1647, 17.1837)
            )

        navigation.setRouteForNavigation(route)
        navigation.addOnHighwayExitListener(listener)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        simulator.start()

        Mockito.verify(
            listener,
            Mockito.timeout(10_000L)
        )
            .onHighwayExitInfoChanged(argThat {
                for (exit in this) {
                    if (exit.exitNumber == "10" && exit.exitSide == 1) {
                        return@argThat true
                    }
                }
                false
            })

        simulator.stop()
        simulator.destroy()
        navigation.stopNavigation()
        navigation.removeOnHighwayExitListener(listener)
        PositionManagerProvider.getInstance().get().stopPositionUpdating()
    }

    @Test
    @Ignore("Prototype - doesnt apply to TomTom maps")
    fun correctUTurnInstructionBajkalska() {
        mapDownload.installAndLoadMap("sk")
        val route =
            routeCompute.offlineRouteCompute(
                GeoCoordinates(48.147260, 17.150520),
                GeoCoordinates(48.147230, 17.150120)
            )
        assertEquals(route.maneuvers[0].type, RouteManeuver.Type.UTurnLeft)
    }

    /**
     * https://jira.sygic.com/browse/SDC-12305
     */
    @Test
    fun testSaveBriefJsonAfterPassWaypointRecompute() = runBlocking {
        val waypointPassListener: OnWaypointPassListener = mock(verboseLogging = true)
        val routeChangedListener: OnRouteChangedListener = mock(verboseLogging = true)
        val routeComputeListener: RouteComputeListener = mock(verboseLogging = true)
        lateinit var briefJson: String
        mapDownload.installAndLoadMap("sk")
        val route =
            routeCompute.offlineRouteCompute(
                start = GeoCoordinates(48.14227359686909, 17.13214634678706),
                waypoint = GeoCoordinates(48.14407288637856, 17.131352923937925),
                destination = GeoCoordinates(48.14648207079094, 17.138648964532695)
            )

        val navigation = NavigationManagerProvider.getInstance().get()
        navigation.setRouteForNavigation(route)
        navigation.addOnRouteChangedListener(routeChangedListener)
        navigation.addOnWaypointPassListener(waypointPassListener)

        val logSimulator =
            NmeaLogSimulatorProvider.getInstance("$appDataPath/precision_hdop_output.nmea").get()
        logSimulator.setSpeedMultiplier(4F)
        logSimulator.start()

        verify(waypointPassListener, timeout(15_000L)).onWaypointPassed(any())

        verify(routeChangedListener, timeout(15_000L)).onRouteChanged(any(), eq(0))

        briefJson = navigation.currentRoute!!.serializeToBriefJSON()

        val routeCaptor = argumentCaptor<Route>()

        assertFalse(briefJson.isEmpty())
        RouterProvider.getInstance().get()
            .computeRouteFromJSONString(briefJson, routeComputeListener)
        verify(routeComputeListener, timeout(10_000L)).onComputeFinished(
            routeCaptor.capture(), eq(Router.RouteComputeStatus.Success)
        )

        val finalRoute = routeCaptor.lastValue
        assertEquals(finalRoute.waypoints[0].status, Waypoint.Status.Reached)
        assertEquals(finalRoute.waypoints[1].status, Waypoint.Status.Reached)
        assertEquals(finalRoute.waypoints[2].status, Waypoint.Status.Ahead)
    }

    private fun getInitialCameraState(coordinates: GeoCoordinates): CameraState {
        return CameraState.Builder().apply {
            setPosition(coordinates)
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