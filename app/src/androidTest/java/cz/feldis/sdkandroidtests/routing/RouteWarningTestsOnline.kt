package cz.feldis.sdkandroidtests.routing

import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.route.RouteAvoids
import com.sygic.sdk.route.RouteWarning
import com.sygic.sdk.route.RoutingOptions
import com.sygic.sdk.route.RoutingOptions.NearestAccessiblePointStrategy
import com.sygic.sdk.route.listeners.RouteWarningsListener
import com.sygic.sdk.vehicletraits.VehicleProfile
import com.sygic.sdk.vehicletraits.general.VehicleType
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.mapInstaller.MapDownloadHelper
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import org.junit.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import kotlin.collections.find

class RouteWarningTestsOnline : BaseTest() {
    private lateinit var mapDownloadHelper: MapDownloadHelper
    private lateinit var routeComputeHelper: RouteComputeHelper
    override val betaRouting = true

    override fun setUp() {
        super.setUp()
        mapDownloadHelper = MapDownloadHelper()
        routeComputeHelper = RouteComputeHelper()
        mapDownloadHelper.unloadAllMaps()
    }

    @Test
    @Ignore("TASK TBD")
    fun testSpecialTollRoadWarningOnline() = runBlocking {

        val routeWarningsListener: RouteWarningsListener = mock(verboseLogging = true)

        val start = GeoCoordinates(48.1655149641659, 17.151219976297632)
        val destination = GeoCoordinates(48.376850, 17.599600)
        val routingOptions = RoutingOptions().apply {
            routeAvoids.globalRouteAvoids = mutableSetOf(RouteAvoids.Type.TollRoad)
            napStrategy = NearestAccessiblePointStrategy.Disabled
            useEndpointProtection = true
            vehicleProfile = VehicleProfile().apply {
                generalVehicleTraits.vehicleType = VehicleType.Truck
            }
        }

        val route = routeComputeHelper.onlineComputeRoute(
            start,
            destination,
            routingOptions = routingOptions
        )

        route.getRouteWarnings(routeWarningsListener)
        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(argThat {
            this.find { it is RouteWarning.SectionWarning.GlobalAvoidViolation.UnavoidableTollRoad } != null
        })
    }

    @Test
    fun testSpecialTollRoadWarningCarNegativeOnline() = runBlocking {

        val routeWarningsListener: RouteWarningsListener = mock(verboseLogging = true)

        val start = GeoCoordinates(48.1655149641659, 17.151219976297632)
        val destination = GeoCoordinates(48.376850, 17.599600)
        val routingOptions = RoutingOptions().apply {
            routeAvoids.globalRouteAvoids = mutableSetOf(RouteAvoids.Type.TollRoad)
            napStrategy = NearestAccessiblePointStrategy.Disabled
            useEndpointProtection = true
        }

        val route = routeComputeHelper.onlineComputeRoute(
            start,
            destination,
            routingOptions = routingOptions
        )

        route.getRouteWarnings(routeWarningsListener)
        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(argThat {
            this.find { it is RouteWarning.SectionWarning.GlobalAvoidViolation.UnavoidableTollRoad } == null
        })
    }

    @Test
    fun testShouldNotGetProhibitedZonesForCar() = runBlocking {
        val routeWarningsListener: RouteWarningsListener = mock(verboseLogging = true)

        val start = GeoCoordinates(48.14563204144804, 17.127418475984047)
        val destination = GeoCoordinates(48.100719596404204, 17.234918702389646)
        val routingOptions = RoutingOptions().apply {
            napStrategy = NearestAccessiblePointStrategy.Disabled
            useEndpointProtection = true
        }

        val route = routeComputeHelper.onlineComputeRoute(
            start,
            destination,
            routingOptions = routingOptions
        )

        route.getRouteWarnings(routeWarningsListener)
        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(argThat {
            this.isEmpty()
        })
    }
}