package cz.feldis.sdkandroidtests.routing

import com.sygic.sdk.position.GeoBoundingBox
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.route.PrimaryRouteRequest
import com.sygic.sdk.route.RouteRequest
import com.sygic.sdk.route.Router
import com.sygic.sdk.route.RouterProvider
import com.sygic.sdk.route.RoutingOptions
import com.sygic.sdk.route.RoutingOptions.NearestAccessiblePointStrategy
import com.sygic.sdk.route.TransitCountryInfo
import com.sygic.sdk.route.listeners.RouteComputeFinishedListener
import com.sygic.sdk.route.listeners.RouteComputeListener
import com.sygic.sdk.route.listeners.RouteDurationListener
import com.sygic.sdk.route.listeners.RouteElementsListener
import com.sygic.sdk.route.listeners.RouteRequestDeserializedListener
import com.sygic.sdk.route.listeners.TransitCountriesInfoListener
import com.sygic.sdk.vehicletraits.VehicleProfile
import com.sygic.sdk.vehicletraits.dimensional.DimensionalTraits
import com.sygic.sdk.vehicletraits.general.GeneralVehicleTraits
import com.sygic.sdk.vehicletraits.general.VehicleType
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.mapInstaller.MapDownloadHelper
import cz.feldis.sdkandroidtests.utils.GeoUtils
import junit.framework.Assert.assertNotNull
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.argThat
import org.mockito.kotlin.isNotNull
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.withSettings
import timber.log.Timber

class RouteComputeTestsOnline : BaseTest() {
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
    fun computeNextDurationsTestOnline() {
        val listener: RouteDurationListener = mock(verboseLogging = true)
        val router = RouterProvider.getInstance().get()

        val start = GeoCoordinates(48.145718, 17.118669)
        val destination = GeoCoordinates(48.190322, 16.401080)
        val route = routeComputeHelper.onlineComputeRoute(start, destination)

        val times =
            listOf(
                System.currentTimeMillis() / 1000 + 1800,
                System.currentTimeMillis() / 1000 + 3700
            )

        router.computeNextDurations(route, times, listener)

        verify(listener, timeout(35_000L))
            .onRouteDurations(argThat {
                if (this != route) {
                    Timber.e("Route is not equal to the original route.")
                    return@argThat false
                }
                true
            }, argThat {
                if (this.size != 2) {
                    Timber.e("List of durations is not equal to 2, List size is ${this.size}")
                    return@argThat false
                }
                true
            })
    }

    @Test
    fun getRouteElementsIcelandOnline() {
        val elementsListener: RouteElementsListener = mock(verboseLogging = true)

        val start = GeoCoordinates(63.556092, -19.794962)
        val destination = GeoCoordinates(63.420816, -19.001375)
        val route = routeComputeHelper.onlineComputeRoute(start, destination)

        route.getRouteElements(elementsListener)
        verify(elementsListener, timeout(10_000L)).onRouteElementsRetrieved(
            argThat {
                if (this.isEmpty()) {
                    return@argThat false
                }
                true
            }
        )
    }

    @Test
    fun computeDoPraceZDomuOnline() {
        val start = GeoCoordinates(48.101713, 17.234017)
        val destination = GeoCoordinates(48.145644, 17.127011)
        val listener =
            Mockito.mock(RouteComputeListener::class.java, withSettings().verboseLogging())
        val routeComputeFinishedListener = Mockito.mock(RouteComputeFinishedListener::class.java)
        val options = RoutingOptions()
        options.apply {
            vehicleProfile = routeComputeHelper.createCombustionVehicleProfile()
            routingService = RoutingOptions.RoutingService.Online
            napStrategy = NearestAccessiblePointStrategy.ChangeWaypointTargetRoads
        }
        val routeRequest = RouteRequest()
        routeRequest.apply {
            setStart(start)
            setDestination(destination)
            routingOptions = options
        }

        val primaryRouteRequest = PrimaryRouteRequest(routeRequest, listener)

        val router = RouterProvider.getInstance().get()

        router.computeRouteWithAlternatives(primaryRouteRequest, null, routeComputeFinishedListener)

        verify(listener, Mockito.timeout(10_000L)).onComputeFinished(
            isNotNull(),
            argThat { this == Router.RouteComputeStatus.Success || this == Router.RouteComputeStatus.SuccessWithWarnings }
        )
    }

    @Test
    fun routePlanFromJSONOnline() {
        val start = GeoCoordinates(48.145718, 17.118669)
        val destination = GeoCoordinates(48.190322, 16.401080)
        val originalRoute =
            routeComputeHelper.onlineComputeRoute(start, destination)
        val listener: RouteRequestDeserializedListener = mock(verboseLogging = true)

        val routeJson = originalRoute.serializeToBriefJSON()

        RouterProvider.getInstance().get()
            .createRouteRequestFromJSONString(json = routeJson, listener)
        verify(listener, timeout(1_000L)).onSuccess(
            argThat {
                if (this.start?.originalPosition == start && this.destination?.originalPosition == destination) {
                    return@argThat true
                }
                false
            }
        )
    }

    @Test
    fun unreachableTargetTruckWeightTestOnline() {
        val start = GeoCoordinates(44.84705, 13.86997)
        val destination = GeoCoordinates(44.27155, 13.619992)

        val listener: RouteComputeListener = mock(verboseLogging = true)
        val routeComputeFinishedListener: RouteComputeFinishedListener = mock(verboseLogging = true)

        val options = RoutingOptions()
        options.apply {
            vehicleProfile = routeComputeHelper.createCombustionVehicleProfile().apply {
                generalVehicleTraits.vehicleType = VehicleType.Truck
                dimensionalTraits = DimensionalTraits().apply {
                    totalWeight = 50_000F
                }
            }
            routingService = RoutingOptions.RoutingService.Online
            napStrategy = NearestAccessiblePointStrategy.ChangeWaypointTargetRoads
        }

        val routeRequest = RouteRequest()
        routeRequest.apply {
            setStart(start)
            setDestination(destination)
            routingOptions = options
        }

        val primaryRouteRequest = PrimaryRouteRequest(routeRequest, listener)
        val router = RouterProvider.getInstance().get()

        router.computeRouteWithAlternatives(primaryRouteRequest, null, routeComputeFinishedListener)

        verify(listener, timeout(20_000L)).onComputeFinished(
            isNull(),
            argThat { this == Router.RouteComputeStatus.UnreachableTarget }
        )
    }

    @Test
    @Ignore("returns unreachable target")
    fun computeWrongFromPointOnline() {
        val start = GeoCoordinates(34.764518085578196, 18.03834181295307)
        val destination = GeoCoordinates(47.99919432978094, 18.164403416068332)
        val listener: RouteComputeListener = mock(verboseLogging = true)
        val routeComputeFinishedListener: RouteComputeFinishedListener = mock(verboseLogging = true)

        val options = RoutingOptions()
        options.apply {
            routingService = RoutingOptions.RoutingService.Online
            napStrategy = NearestAccessiblePointStrategy.Disabled
            useEndpointProtection = true
        }

        val routeRequest = RouteRequest()
        routeRequest.apply {
            setStart(start)
            setDestination(destination)
            routingOptions = options
        }

        val primaryRouteRequest = PrimaryRouteRequest(routeRequest, listener)
        val router = RouterProvider.getInstance().get()

        router.computeRouteWithAlternatives(primaryRouteRequest, null, routeComputeFinishedListener)

        verify(listener, timeout(20_000L)).onComputeFinished(
            isNull(),
            argThat { this == Router.RouteComputeStatus.WrongFromPoint }
        )
    }

    @Test
    fun onlineRoutingGetLastManeuverCountry() {
        val start = GeoCoordinates(48.13204503419638, 17.09786238379282)
        val destination = GeoCoordinates(51.491340, -0.102940)

        val route = routeComputeHelper.onlineComputeRoute(
            start,
            destination
        )

        val maneuvers = route.maneuvers
        assertEquals("gb", maneuvers.last().nextIso)
    }

    /**
     * https://jira.sygic.com/browse/SDC-4695
     * Test Case TC170
     * Start: 3227 N Oconto Avenue, Chicago, IL
     * Destination: 5815 S Maryland Avenue, Chicago, IL
     *
     * In this test case, we only check that the route is computed.
     */
    @Test
    fun testIllinoisOcontoToMarylandOnline() = runBlocking{
        val start = GeoCoordinates(41.938650,-87.807700)
        val destination = GeoCoordinates(41.788500,-87.605030)

        val route = routeComputeHelper.onlineComputeRoute(
            start,
            destination
        )
        assertNotNull(route)
    }

    /**
     * https://jira.sygic.com/browse/CI-1580
     * Test Case TC171
     *
     * In this test case, we check that the fastest pedestrian route in city of Frejus is computed.
     */
    @Test
    fun testFastestPedestrianRoutingFrejusOnline() = runBlocking{

        val start = GeoCoordinates(43.4337,6.73637)
        val destination = GeoCoordinates(43.4335,6.73851)

        val route = routeComputeHelper.onlineComputeRoute(
            start,
            destination,
            routingOptions = RoutingOptions().apply {
                vehicleProfile = null
                this.routingType = RoutingOptions.RoutingType.Fastest
            }
        )
        assertNotNull(route)
    }

    /**
     * https://jira.sygic.com/browse/SDC-5641
     * Test Case TC172
     *
     * In this test case, we check that the fastest pedestrian route in Bratislava Petržalka is computed.
     */
    @Test
    fun testFastestPedestrianRoutingPetrzalkaOnline() = runBlocking{

        val start = GeoCoordinates(48.0967,17.1192)
        val destination = GeoCoordinates(48.0988,17.117)

        val route = routeComputeHelper.onlineComputeRoute(
            start,
            destination,
            routingOptions = RoutingOptions().apply {
                vehicleProfile = null
                this.routingType = RoutingOptions.RoutingType.Fastest
            }
        )
        assertNotNull(route)
    }

    /**
     * Test Case TC173
     *
     * In this test case, we check that the fastest pedestrian route in Bratislava (Old town) is computed.
     */
    @Test
    fun testFastestPedestrianRoutingStareMestoOnline() = runBlocking{

        val start = GeoCoordinates(48.1416,17.1097)
        val destination = GeoCoordinates(48.1444,17.1067)

        val route = routeComputeHelper.onlineComputeRoute(
            start,
            destination,
            routingOptions = RoutingOptions().apply {
                vehicleProfile = null
                this.routingType = RoutingOptions.RoutingType.Fastest
            }
        )
        assertNotNull(route)
    }

    /***
     * https://jira.sygic.com/browse/CI-1668
     * TC324
     */
    @Test
    fun routingViaRoadsOfLowerQualitySlovakiaOnline() = runBlocking {
        val vehicleProfile = VehicleProfile().apply {
            this.generalVehicleTraits = GeneralVehicleTraits().apply {
                vehicleType = VehicleType.Car
            }
        }

        val boundingBox = GeoBoundingBox(
            topLeft = GeoCoordinates(48.77439, 21.29253),
            bottomRight = GeoCoordinates(48.76949, 21.29728)
        )

        val route = routeComputeHelper.onlineComputeRoute(
            GeoCoordinates(48.9329, 21.9153),
            GeoCoordinates(48.7576, 21.2724),
            routingOptions = RoutingOptions().apply {
                this.routingType = RoutingOptions.RoutingType.Fastest
                this.vehicleProfile = vehicleProfile
                this.useEndpointProtection = true
                this.napStrategy = NearestAccessiblePointStrategy.Disabled
            }
        )

        val maneuversInBoundingBox = route.maneuvers.filter { maneuver ->
            GeoUtils.isPointInBoundingBox(maneuver.position, boundingBox)
        }

        assertTrue(
            "Route contains unexpected maneuvers within the bounding box: $maneuversInBoundingBox",
            maneuversInBoundingBox.isEmpty()
        )

    }

    /**
     * Test Case TC700, TC701
     *
     * In this test case, we check that the fastest and shortest routes in Slovakia are computed and they aren't the same
     */
    @Test
    fun testRoutingInEasternSlovakiaFastestShortestOnline() = runBlocking {
        val vehicleProfile = VehicleProfile().apply {
            this.generalVehicleTraits = GeneralVehicleTraits().apply {
                vehicleType = VehicleType.Car
            }
        }

        val start = GeoCoordinates(48.9329, 21.9153)
        val destination = GeoCoordinates(48.7576, 21.2724)

        val routeFastest = routeComputeHelper.onlineComputeRoute(
            start,
            destination,
            routingOptions = RoutingOptions().apply {
                this.routingType = RoutingOptions.RoutingType.Fastest
                this.vehicleProfile = vehicleProfile
            }
        )
        val estimatedTimeOfArrivalFastest =
            routeFastest.routeInfo.waypointDurations.last().withSpeedProfiles
        assertNotNull(routeFastest)

        val routeShortest = routeComputeHelper.onlineComputeRoute(
            start,
            destination,
            routingOptions = RoutingOptions().apply {
                this.routingType = RoutingOptions.RoutingType.Shortest
                this.vehicleProfile = vehicleProfile
            }
        )
        val estimatedTimeOfArrivalShortest =
            routeShortest.routeInfo.waypointDurations.last().withSpeedProfiles
        assertNotNull(routeShortest)

        assertNotEquals(
            "ETA should differ for fastest and shortest routes",
            estimatedTimeOfArrivalFastest,
            estimatedTimeOfArrivalShortest
        )
    }

    @Test
    fun onlineRoutingGetAllTransitCountries() {
        val start = GeoCoordinates(48.13204503419638, 17.09786238379282)
        val destination = GeoCoordinates(51.491340, -0.102940)

        val route = routeComputeHelper.onlineComputeRoute(
            start,
            destination,
            routingOptions = RoutingOptions().apply { // turn off to always get the same route
                this.useTraffic = false
                this.useSpeedProfiles = false
            }
        )

        val expectedCountries = listOf(
            TransitCountryInfo("sk", emptyList()),
            TransitCountryInfo("at", emptyList()),
            TransitCountryInfo("de", emptyList()),
            TransitCountryInfo("nl", emptyList()),
            TransitCountryInfo("be", emptyList()),
            TransitCountryInfo("fr", emptyList()),
            TransitCountryInfo("gb", emptyList())
        )

        val transitCountriesInfoListener: TransitCountriesInfoListener = mock(verboseLogging = true)
        route.getTransitCountriesInfo(transitCountriesInfoListener)
        verify(transitCountriesInfoListener, timeout(5_000L)).onTransitCountriesInfo(
            expectedCountries
        )
    }
}