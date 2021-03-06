package cz.feldis.sdkandroidtests.navigation

import com.nhaarman.mockitokotlin2.*
import com.sygic.sdk.context.CoreInitCallback
import com.sygic.sdk.navigation.NavigationManager
import com.sygic.sdk.navigation.NavigationManagerProvider
import com.sygic.sdk.navigation.StreetDetail
import com.sygic.sdk.navigation.traffic.TrafficManagerProvider
import com.sygic.sdk.position.CustomPositionUpdater
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.position.GeoPosition
import com.sygic.sdk.position.PositionManagerProvider
import com.sygic.sdk.route.*
import com.sygic.sdk.route.simulator.NmeaLogSimulator
import com.sygic.sdk.route.simulator.NmeaLogSimulatorProvider
import com.sygic.sdk.route.simulator.PositionSimulator
import com.sygic.sdk.route.simulator.RouteDemonstrateSimulatorProvider
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.mapInstaller.MapDownloadHelper
import cz.feldis.sdkandroidtests.routing.RouteComputeHelper
import org.junit.Before
import org.junit.Test
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
    }

    @Test
    fun onSharpCurveListenerTest() {
        mapDownload.ensureMapNotInstalled("sk")
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
            listener,
            Mockito.timeout(10_000L)
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
     * Navigation test on direction info changed
     *
     * In this test we compute an offline route and set it for navigation.
     * Via simulator provider we set this route and start demonstrate. We verify that onDirectionInfoChanged
     * contains direction info with primary nextRoadName "Einsteinova".
     */
    @Test
    fun onDirectionInfoChangedTest() {
        mapDownload.ensureMapNotInstalled("sk")
        mapDownload.installAndLoadMap("sk")
        val directionListener: NavigationManager.OnDirectionListener = mock(verboseLogging = true)
        val navigation = NavigationManagerProvider.getInstance().get()

        val route =
            routeCompute.offlineRouteCompute(
                GeoCoordinates(48.132310, 17.114100),
                GeoCoordinates(48.131733, 17.109952)
            )

        navigation.setRouteForNavigation(route)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        simulator.start()
        navigation.addOnDirectionListener(directionListener)

        Mockito.verify(
            directionListener,
            Mockito.timeout(15_000L)
        )
            .onDirectionInfoChanged(argThat {
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
     * TODo toto asi nie uplne korektne funguje
     * Navigation test on route changed
     *
     * In this test we compute an offline route and set it for navigation.
     * Using the Nmea Log Recorder we set a route from assets/SVK-Kosicka.nmea and start the simulation.
     * We verify that the onRouteChanged callback is called and the status is Success.
     */
    @Test
    fun onRouteChangedTest() {
        mapDownload.installAndLoadMap("sk")
        TrafficManagerProvider.getInstance().get().enableTrafficService()
        val listener: NavigationManager.OnRouteChangedListener = mock(verboseLogging = true)
        val navigation = NavigationManagerProvider.getInstance().get()
        val route =
            routeCompute.offlineRouteCompute(
                GeoCoordinates(48.1432, 17.1308),
                GeoCoordinates(48.1455, 17.1263)
            )

        navigation.setRouteForNavigation(route)
        val logSimulator = NmeaLogSimulatorProvider.getInstance("SVK-Kosicka.nmea").get()
        logSimulator.setSpeedMultiplier(2F)
        logSimulator.start()
        navigation.addOnRouteChangedListener(listener)

        Mockito.verify(
            listener,
            Mockito.timeout(15_000L).atLeast(2)
        )
            .onRouteChanged(isNotNull(), eq(NavigationManager.RouteUpdateStatus.Success))

        logSimulator.stop()
        logSimulator.destroy()
        navigation.removeOnRouteChangedListener(listener)
        navigation.stopNavigation()
        TrafficManagerProvider.getInstance().get().disableTrafficService()
        mapDownload.uninstallMap("sk")
    }

    @Test
    fun onJunctionPassedStandaloneListenerInvocationWithoutRoute() {
        mapDownload.installAndLoadMap("sk")
        val listener: NavigationManager.JunctionPassedListener = mock(verboseLogging = true)
        val navigation = NavigationManagerProvider.getInstance().get()
        PositionManagerProvider.getInstance().get().startPositionUpdating()

        val logSimulator = NmeaLogSimulatorProvider.getInstance("rovinka.nmea").get()
        Thread.sleep(3000)
        logSimulator.start()

        navigation.addJunctionPassedListener(listener)

        verify(
            listener,
            timeout(60_000L).atLeast(2)
        )
            .onJunctionPassed(eq(StreetDetail.JunctionType.Junction))
        verify(
            listener,
            atMost(10)
        )
            .onJunctionPassed(eq(StreetDetail.JunctionType.Junction))
        verify(
            listener, timeout(60_000L).times(1)).onJunctionPassed(
            eq(StreetDetail.JunctionType.EnteringUrbanArea)
            )

        logSimulator.stop()
        logSimulator.destroy()
        navigation.removeJunctionPassedListener(listener)
        navigation.stopNavigation()
        PositionManagerProvider.getInstance().get().stopPositionUpdating()
    }

}