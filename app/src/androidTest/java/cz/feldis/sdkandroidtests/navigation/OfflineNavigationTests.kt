package cz.feldis.sdkandroidtests.navigation

import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.atMost
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.timeout
import com.nhaarman.mockitokotlin2.verify
import com.sygic.sdk.incidents.SpeedCamera
import com.sygic.sdk.navigation.NavigationManager
import com.sygic.sdk.navigation.NavigationManagerProvider
import com.sygic.sdk.navigation.StreetDetail
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.position.PositionManagerProvider
import com.sygic.sdk.route.simulator.NmeaLogSimulatorProvider
import com.sygic.sdk.route.simulator.RouteDemonstrateSimulatorProvider
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.mapInstaller.MapDownloadHelper
import cz.feldis.sdkandroidtests.routing.RouteComputeHelper
import org.junit.Before
import org.junit.Test
import org.mockito.AdditionalMatchers
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
    }

    /**
     * Navigation test on direction info changed
     *
     * In this test we compute an offline route and set it for navigation.
     * Via simulator provider we set this route and start demonstrate. We verify that onDirectionInfoChanged
     * contains direction info with primary nextRoadName "Einsteinova".
     */
    @Test
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
        mapDownload.uninstallMap("sk")
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

        val logSimulator = NmeaLogSimulatorProvider.getInstance("$appDataPath/SVK-Kosicka.nmea").get()
        logSimulator.setSpeedMultiplier(2F)
        navigation.setRouteForNavigation(route)
        navigation.addOnRouteChangedListener(listener)
        logSimulator.start()

        Mockito.verify(
            listener, Mockito.timeout(30_000L).atLeast(1)
        ).onRouteChanged(AdditionalMatchers.not(eq(route)), eq(NavigationManager.RouteUpdateStatus.Success))


        logSimulator.stop()
        logSimulator.destroy()
        navigation.removeOnRouteChangedListener(listener)
        navigation.stopNavigation()
    }

    @Test
    fun onJunctionPassedStandaloneListenerInvocationWithoutRoute() {
        mapDownload.installAndLoadMap("sk")
        val listener: NavigationManager.JunctionPassedListener = mock(verboseLogging = true)
        val navigation = NavigationManagerProvider.getInstance().get()
        PositionManagerProvider.getInstance().get().startPositionUpdating()

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
    }

    @Test
    fun sectionCameraTest() {
        mapDownload.installAndLoadMap("sk")
        val navigation = NavigationManagerProvider.getInstance().get()
        val listener: NavigationManager.OnIncidentListener = mock(verboseLogging = true)

        val route = routeCompute.offlineRouteCompute(
            GeoCoordinates(48.212465230469, 17.03545199713536),
            GeoCoordinates(48.18179480984319, 17.05224437941669)
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
    }

    @Test
    fun checkSpeedLimitOfRealCamera() {
        mapDownload.installAndLoadMap("sk")
        val navigation = NavigationManagerProvider.getInstance().get()
        val listener: NavigationManager.OnIncidentListener = mock(verboseLogging = true)

        val route = routeCompute.offlineRouteCompute(
            GeoCoordinates(48.649189548913924, 17.842829374576407), GeoCoordinates(48.662019021892746, 17.869242810014157)
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
    }
}