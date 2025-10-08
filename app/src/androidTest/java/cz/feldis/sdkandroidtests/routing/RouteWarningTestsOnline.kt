package cz.feldis.sdkandroidtests.routing

import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.route.RouteAvoids
import com.sygic.sdk.route.RouteWarning
import com.sygic.sdk.route.RoutingOptions
import com.sygic.sdk.route.RoutingOptions.NearestAccessiblePointStrategy
import com.sygic.sdk.route.RoutingOptions.RoutingType
import com.sygic.sdk.route.listeners.RouteWarningsListener
import com.sygic.sdk.vehicletraits.VehicleProfile
import com.sygic.sdk.vehicletraits.general.VehicleType
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.mapInstaller.MapDownloadHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify

class RouteWarningTestsOnline : BaseTest() {
    private lateinit var mapDownloadHelper: MapDownloadHelper
    private lateinit var routeComputeHelper: RouteComputeHelper
    override val betaRouting = true

    override fun setUp() {
        super.setUp()
        mapDownloadHelper = MapDownloadHelper()
        routeComputeHelper = RouteComputeHelper()
    }

    /**
     * TC618
     */
    @Test
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

        val route = routeComputeHelper.onlineRouteCompute(
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

        val route = routeComputeHelper.onlineRouteCompute(
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

        val route = routeComputeHelper.onlineRouteCompute(
            start,
            destination,
            routingOptions = routingOptions
        )

        route.getRouteWarnings(routeWarningsListener)
        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(argThat {
            this.isEmpty()
        })
    }

    @Test
    fun tollRoadAvoidWarningTestOnline() {
        val routeWarningsListener: RouteWarningsListener = mock(verboseLogging = true)

        val start = GeoCoordinates(48.07473125945471, 17.121696472685443)
        val destination = GeoCoordinates(48.41623783484128, 17.747376207492863)
        val routingOptions = RoutingOptions().apply {
            routeAvoids.globalRouteAvoids = mutableSetOf(RouteAvoids.Type.TollRoad)
        }

        val route = routeComputeHelper.onlineRouteCompute(
            start,
            destination,
            routingOptions = routingOptions
        )

        route.getRouteWarnings(routeWarningsListener)
        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(argThat {
            this.find { it is RouteWarning.SectionWarning.GlobalAvoidViolation.UnavoidableTollRoad } != null
        })
        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(argThat {
            this.isNotEmpty()
        })
    }

    @Test
    fun tollRoadCountryAvoidWarningTestOnline() {
        val routeWarningsListener: RouteWarningsListener = mock(verboseLogging = true)

        val start = GeoCoordinates(48.1083, 17.2206)
        val destination = GeoCoordinates(51.9035, -0.47722)
        val routingOptions = RoutingOptions().apply {
            routeAvoids.countryRouteAvoids =
                mutableMapOf("gb" to mutableSetOf(RouteAvoids.Type.Highway))
        }

        val captor = argumentCaptor<List<RouteWarning>>()
        val route = routeComputeHelper.onlineRouteCompute(
            start,
            destination,
            routingOptions = routingOptions
        )

        route.getRouteWarnings(routeWarningsListener)
        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(captor.capture())
        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(argThat {
            this.find { it is RouteWarning.SectionWarning.CountryAvoidViolation.UnavoidableHighway } != null
        })
        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(argThat {
            this.isNotEmpty()
        })
        val restriction = captor.allValues.flatten()
            .first() as RouteWarning.SectionWarning.CountryAvoidViolation.UnavoidableHighway
        assertTrue(restriction.iso == "gb")
    }

    /**
     * TC169
     * In this test we check that there are no toll roads on the route
     * if we avoid toll roads.
     */
    @Test
    fun tollRoadAvoidWarningOnlineTest() = runBlocking {
        val start = GeoCoordinates(48.0935, 17.1165)
        val destination = GeoCoordinates(48.1209, 16.5627)
        val routingOptions = RoutingOptions().apply {
            this.routeAvoids.globalRouteAvoids = mutableSetOf(RouteAvoids.Type.TollRoad)
            this.routingType = RoutingType.Fastest
            this.napStrategy = NearestAccessiblePointStrategy.Disabled
        }

        val route = routeComputeHelper.onlineRouteCompute(
            start,
            destination,
            routingOptions = routingOptions
        )

        val warnings = route.getRouteWarnings()

        assertFalse(
            "Route with toll road avoidance enabled should not contain an UnavoidableTollRoad warning.",
            warnings.any { it is RouteWarning.SectionWarning.GlobalAvoidViolation.UnavoidableTollRoad }
        )
    }
}