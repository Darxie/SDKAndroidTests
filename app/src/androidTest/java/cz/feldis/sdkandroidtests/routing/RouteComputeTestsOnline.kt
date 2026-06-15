package cz.feldis.sdkandroidtests.routing

import android.util.Log
import com.sygic.sdk.position.GeoBoundingBox
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.route.PrimaryRouteRequest
import com.sygic.sdk.route.RouteAvoids
import com.sygic.sdk.route.RouteManeuver
import com.sygic.sdk.route.RouteRequest
import com.sygic.sdk.route.RouteWarning
import com.sygic.sdk.route.Router
import com.sygic.sdk.route.RouterProvider
import com.sygic.sdk.route.RoutingOptions
import com.sygic.sdk.route.RoutingOptions.NearestAccessiblePointStrategy
import com.sygic.sdk.route.RoutingOptions.RoutingType
import com.sygic.sdk.route.TransitCountryInfo
import com.sygic.sdk.route.Waypoint
import com.sygic.sdk.route.listeners.RouteComputeFinishedListener
import com.sygic.sdk.route.listeners.RouteComputeListener
import com.sygic.sdk.route.listeners.RouteRequestDeserializedListener
import com.sygic.sdk.route.listeners.TransitCountriesInfoListener
import com.sygic.sdk.vehicletraits.VehicleProfile
import com.sygic.sdk.vehicletraits.dimensional.DimensionalTraits
import com.sygic.sdk.vehicletraits.general.GeneralVehicleTraits
import com.sygic.sdk.vehicletraits.general.VehicleType
import com.sygic.sdk.vehicletraits.hazmat.HazmatTraits
import com.sygic.sdk.vehicletraits.hazmat.TunnelCategory
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.mapInstaller.MapDownloadHelper
import cz.feldis.sdkandroidtests.utils.GeoUtils
import junit.framework.Assert.assertNotNull
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import java.util.Date

class RouteComputeTestsOnline : BaseTest() {
    private lateinit var mapDownloadHelper: MapDownloadHelper
    private lateinit var routeComputeHelper: RouteComputeHelper
    private lateinit var router: Router

    override fun setUp() {
        super.setUp()
        mapDownloadHelper = MapDownloadHelper()
        routeComputeHelper = RouteComputeHelper()
        router = runBlocking { RouterProvider.getInstance() }
    }

    @Test
    fun computeNextDurationsTestOnline() = runBlocking {
        val start = GeoCoordinates(48.145718, 17.118669)
        val destination = GeoCoordinates(48.190322, 16.401080)
        val route = routeComputeHelper.onlineRouteCompute(start, destination)

        val times =
            listOf(
                System.currentTimeMillis() / 1000 + 1800,
                System.currentTimeMillis() / 1000 + 3700
            )

        val (newRoute, durations) = router.computeNextDurations(route, times)

        assertEquals("Expected route is not equal to the actual route", route, newRoute)
        assertEquals("Expected 2 returned times, but got ${durations.size}",2, durations.size)
    }

    @Test
    fun getRouteElementsIcelandOnline() = runBlocking {
        val start = GeoCoordinates(63.556092, -19.794962)
        val destination = GeoCoordinates(63.420816, -19.001375)
        val route = routeComputeHelper.onlineRouteCompute(start, destination)
        val routeElements = runBlocking { route.getRouteElements() }
        assertTrue(routeElements.isNotEmpty())
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

        router.computeRouteWithAlternatives(primaryRouteRequest, null, routeComputeFinishedListener)

        verify(listener, Mockito.timeout(10_000L)).onComputeFinished(
            isNotNull(),
            argThat { this == Router.RouteComputeStatus.Success || this == Router.RouteComputeStatus.SuccessWithWarnings }
        )
    }

    @Test
    fun routePlanFromJSONOnline() = runBlocking {
        val start = GeoCoordinates(48.145718, 17.118669)
        val destination = GeoCoordinates(48.190322, 16.401080)
        val originalRoute =
            routeComputeHelper.onlineRouteCompute(start, destination)
        val listener: RouteRequestDeserializedListener = mock(verboseLogging = true)

        val routeJson = originalRoute.serializeToBriefJSON()

        router
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

        router.computeRouteWithAlternatives(primaryRouteRequest, null, routeComputeFinishedListener)

        verify(listener, timeout(20_000L)).onComputeFinished(
            isNull(),
            argThat { this == Router.RouteComputeStatus.WrongFromPoint }
        )
    }

    @Test
    fun onlineRoutingGetLastManeuverCountry() = runBlocking {
        val start = GeoCoordinates(48.13204503419638, 17.09786238379282)
        val destination = GeoCoordinates(51.491340, -0.102940)

        val route = routeComputeHelper.onlineRouteCompute(
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
    fun testIllinoisOcontoToMarylandOnline() = runBlocking {
        val start = GeoCoordinates(41.938650, -87.807700)
        val destination = GeoCoordinates(41.788500, -87.605030)

        val route = routeComputeHelper.onlineRouteCompute(
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
    fun testFastestPedestrianRoutingFrejusOnline() = runBlocking {

        val start = GeoCoordinates(43.4337, 6.73637)
        val destination = GeoCoordinates(43.4335, 6.73851)

        val route = routeComputeHelper.onlineRouteCompute(
            start,
            destination,
            routingOptions = RoutingOptions().apply {
                vehicleProfile = null
                this.routingType = RoutingType.Fastest
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
    fun testFastestPedestrianRoutingPetrzalkaOnline() = runBlocking {

        val start = GeoCoordinates(48.0967, 17.1192)
        val destination = GeoCoordinates(48.0988, 17.117)

        val route = routeComputeHelper.onlineRouteCompute(
            start,
            destination,
            routingOptions = RoutingOptions().apply {
                vehicleProfile = null
                this.routingType = RoutingType.Fastest
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
    fun testFastestPedestrianRoutingStareMestoOnline() = runBlocking {

        val start = GeoCoordinates(48.1416, 17.1097)
        val destination = GeoCoordinates(48.1444, 17.1067)

        val route = routeComputeHelper.onlineRouteCompute(
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

        val route = routeComputeHelper.onlineRouteCompute(
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

    /***
     * https://jira.sygic.com/browse/SDC-6167
     * TC432
     * Online routing. The route can't lead through the road 49.221270,16.582800
     */
    @Test
    fun routingInCzechRepublicFastestOnlineTest() = runBlocking {
        val vehicleProfile = VehicleProfile().apply {
            this.generalVehicleTraits = GeneralVehicleTraits().apply {
                vehicleType = VehicleType.Car
            }
        }

        val boundingBox = GeoBoundingBox(
            topLeft = GeoCoordinates(49.22191, 16.58127),
            bottomRight = GeoCoordinates(49.22082, 16.58453)
        )

        val route = routeComputeHelper.onlineRouteCompute(
            GeoCoordinates(49.219570, 16.561400),
            GeoCoordinates(49.262160, 16.579050),
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

    /***
     * https://jira.sygic.com/browse/SDC-11895
     * TC856
     * Online routing. The route can't exit from the highway in coordinates 48.3073,17.5595
     */
    @Test
    fun exitsFromTheRouteSlovakiaOnlineTest() = runBlocking {
        val vehicleProfile = VehicleProfile().apply {
            this.generalVehicleTraits = GeneralVehicleTraits().apply {
                vehicleType = VehicleType.Car
            }
        }

        val boundingBox = GeoBoundingBox(
            topLeft = GeoCoordinates(48.30891, 17.55084),
            bottomRight = GeoCoordinates(48.30719, 17.56776)
        )

        val route = routeComputeHelper.onlineRouteCompute(
            GeoCoordinates(48.211820, 17.263800),
            GeoCoordinates(48.983180, 18.402170),
            routingOptions = RoutingOptions().apply {
                this.routingType = RoutingType.Fastest
                this.vehicleProfile = vehicleProfile
                this.useEndpointProtection = true
                this.napStrategy = NearestAccessiblePointStrategy.Disabled
                this.useTraffic = false
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

    /***
     * https://jira.sygic.com/browse/CI-3488
     * TC900
     * Online routing. The route can't exit from the highway in coordinates 53.28470, -6.45511
     */
    @Test
    fun exitsFromTheRouteIrelandOnlineTest() = runBlocking {
        val vehicleProfile = VehicleProfile().apply {
            this.generalVehicleTraits = GeneralVehicleTraits().apply {
                vehicleType = VehicleType.Truck
            }
        }

        val boundingBox = GeoBoundingBox(
            topLeft = GeoCoordinates(53.28592, -6.46541),
            bottomRight = GeoCoordinates(53.28261, -6.45114)
        )

        val route = routeComputeHelper.onlineRouteCompute(
            GeoCoordinates(53.291460, -6.438910),
            GeoCoordinates(53.279980, -6.483670),
            routingOptions = RoutingOptions().apply {
                this.routingType = RoutingType.Fastest
                this.vehicleProfile = vehicleProfile
                this.useEndpointProtection = true
                this.napStrategy = NearestAccessiblePointStrategy.Disabled
                this.useTraffic = true
                this.useSpeedProfiles = true
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

    /***
     * https://jira.sygic.com/browse/CI-3488
     * TC901 (is a part of TC900)
     * Online routing. The route can't exit from the highway in coordinates 52.39942, -7.91721
     */
    @Test
    fun exitsFromTheRouteIreland2OnlineTest() = runBlocking {
        val vehicleProfile = VehicleProfile().apply {
            this.generalVehicleTraits = GeneralVehicleTraits().apply {
                vehicleType = VehicleType.Truck
            }
        }

        val boundingBox = GeoBoundingBox(
            topLeft = GeoCoordinates(52.40341, -7.92215),
            bottomRight = GeoCoordinates(52.39568, -7.90871)
        )

        val route = routeComputeHelper.onlineRouteCompute(
            GeoCoordinates(52.391330, -7.927900),
            GeoCoordinates(52.410620, -7.913030),
            routingOptions = RoutingOptions().apply {
                this.routingType = RoutingType.Fastest
                this.vehicleProfile = vehicleProfile
                this.useEndpointProtection = true
                this.napStrategy = NearestAccessiblePointStrategy.ChangeWaypointTargetRoads
                this.useTraffic = false
                this.useSpeedProfiles = false
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
     * https://jira.sygic.com/browse/SDC-12572
     * TC832
     * Route should go straight and should no have any detours (which would most likely be to the right).
     * Therefore we check that there is no right turn.
     */
    @Test
    fun lowerHeavyTruckPenaltiesSwedenOnlineTest() = runBlocking {

        val vehicleProfile = VehicleProfile().apply {
            this.dimensionalTraits = DimensionalTraits().apply {
                this.totalWeight = 30000.0F
                this.totalLength = 18000
                this.totalHeight = 4000
                this.totalWidth = 2500
            }
            this.generalVehicleTraits = GeneralVehicleTraits().apply {
                this.vehicleType = VehicleType.Truck
            }
        }

        val route = routeComputeHelper.onlineRouteCompute(
            GeoCoordinates(58.351620, 11.845900),
            GeoCoordinates(58.347790, 11.792080),
            routingOptions = RoutingOptions().apply {
                this.routingType = RoutingOptions.RoutingType.Fastest
                this.vehicleProfile = vehicleProfile
                this.useEndpointProtection = true
                this.useSpeedProfiles = false
                this.useTraffic = false
                this.napStrategy = NearestAccessiblePointStrategy.Disabled
            }
        )
        val unwantedManeuver = route.maneuvers.any { maneuver ->
            maneuver.type == RouteManeuver.Type.Right
        }

        assertFalse(
            "Route contains an unwanted RIGHT maneuver.", unwantedManeuver,
        )
    }

    /**
     * https://jira.sygic.com/browse/SDC-14494
     * TC902
     * There are no warnings after route compute
     */
    @Test
    fun computeWithoutWarningsTestOnline() {
        val start = GeoCoordinates(48.146400, 17.106870)
        val destination = GeoCoordinates(48.373510, 17.594620)
        val listener =
            Mockito.mock(RouteComputeListener::class.java, withSettings().verboseLogging())
        val routeComputeFinishedListener = Mockito.mock(RouteComputeFinishedListener::class.java)
        val options = RoutingOptions()
        options.apply {
            vehicleProfile = routeComputeHelper.createCombustionVehicleProfile().apply {
                generalVehicleTraits.vehicleType = VehicleType.Car
                dimensionalTraits = DimensionalTraits().apply {
                    totalWeight = 30_000F
                    totalLength = 18000
                    totalWidth = 2500
                    totalHeight = 4000
                }
                routingService = RoutingOptions.RoutingService.Online
                napStrategy = NearestAccessiblePointStrategy.ChangeWaypointTargetRoads
                useSpeedProfiles = true
                useTraffic = true
            }
            val routeRequest = RouteRequest()
            routeRequest.apply {
                setStart(start)
                setDestination(destination)
                routingOptions = options
            }

            val primaryRouteRequest = PrimaryRouteRequest(routeRequest, listener)

            router.computeRouteWithAlternatives(
                primaryRouteRequest,
                null,
                routeComputeFinishedListener
            )

            verify(listener, Mockito.timeout(10_000L)).onComputeFinished(
                isNotNull(),
                argThat { this == Router.RouteComputeStatus.Success }
            )
        }
    }

    /**
     * https://jira.sygic.com/browse/CI-2891
     * TC906
     * In this test we check that the route is compute in 3 seconds
     */
    @Test
    fun computeIn3SecondsTestOnline() {
        val start = GeoCoordinates(48.599070, 17.830600)
        val destination = GeoCoordinates(52.235600, 21.010370)
        val listener =
            Mockito.mock(RouteComputeListener::class.java, withSettings().verboseLogging())
        val routeComputeFinishedListener = Mockito.mock(RouteComputeFinishedListener::class.java)
        val options = RoutingOptions()
        options.apply {
            vehicleProfile = routeComputeHelper.createCombustionVehicleProfile().apply {
                generalVehicleTraits.vehicleType = VehicleType.Truck
                dimensionalTraits = DimensionalTraits().apply {
                    totalWeight = 10_000F
                    totalLength = 16500
                    totalWidth = 2500
                    totalHeight = 3000
                }
                routingService = RoutingOptions.RoutingService.Online
                napStrategy = NearestAccessiblePointStrategy.Disabled
                useSpeedProfiles = true
                useTraffic = true
            }
            val routeRequest = RouteRequest()
            routeRequest.apply {
                setStart(start)
                setDestination(destination)
                routingOptions = options
            }

            val primaryRouteRequest = PrimaryRouteRequest(routeRequest, listener)

            router.computeRouteWithAlternatives(
                primaryRouteRequest,
                null,
                routeComputeFinishedListener
            )

            verify(listener, Mockito.timeout(3_000L)).onComputeFinished(
                isNotNull(),
                argThat { this == Router.RouteComputeStatus.Success || this == Router.RouteComputeStatus.SuccessWithWarnings }
            )
        }
    }

    /**
     * https://jira.sygic.com/browse/CI-3366
     * TC903
     * There are no Truck warnings for vehicle type Bus in Routing-Summary window
     */
    @Test
    @Ignore("https://eurowag-cloud.atlassian.net/browse/DNAENG-531")
    fun computeWithoutTruckWarningsForBusTestOnline() {
        val start = GeoCoordinates(47.773290, 12.009410)
        val destination = GeoCoordinates(48.131460, 11.575850)
        val listener =
            Mockito.mock(RouteComputeListener::class.java, withSettings().verboseLogging())
        val routeComputeFinishedListener = Mockito.mock(RouteComputeFinishedListener::class.java)
        val options = RoutingOptions()
        options.apply {
            vehicleProfile = routeComputeHelper.createCombustionVehicleProfile().apply {
                generalVehicleTraits.vehicleType = VehicleType.Bus
                dimensionalTraits = DimensionalTraits().apply {
                    totalWeight = 15_500F
                }
                routingService = RoutingOptions.RoutingService.Online
                napStrategy = NearestAccessiblePointStrategy.ChangeWaypointTargetRoads
                useSpeedProfiles = true
                useTraffic = true
            }
            val routeRequest = RouteRequest()
            routeRequest.apply {
                setStart(start)
                setDestination(destination)
                routingOptions = options
            }

            val primaryRouteRequest = PrimaryRouteRequest(routeRequest, listener)

            router.computeRouteWithAlternatives(
                primaryRouteRequest,
                null,
                routeComputeFinishedListener
            )

            verify(listener, Mockito.timeout(10_000L)).onComputeFinished(
                isNotNull(),
                argThat { this == Router.RouteComputeStatus.Success }
            )
        }
    }

    /***
     * https://jira.sygic.com/browse/CI-3023
     * TC907
     * Online routing. The route must include left maneuver in bounding box
     */
    @Test
    fun routingThroughIntersectionSlovakiaOnlineTest() = runBlocking {

        val boundingBox = GeoBoundingBox(
            topLeft = GeoCoordinates(48.11995, 17.11774),
            bottomRight = GeoCoordinates(48.11929, 17.11949)
        )

        val route = routeComputeHelper.onlineRouteCompute(
            GeoCoordinates(48.117600, 17.120250),
            GeoCoordinates(48.118920, 17.115830),
            routingOptions = RoutingOptions().apply {
                this.routingType = RoutingType.Fastest
                this.useEndpointProtection = true
                this.napStrategy = NearestAccessiblePointStrategy.Disabled
                this.arriveInDrivingSide = false
            }
        )

        val hasLeftTurnInsideBox = route.maneuvers.any {
            it.type == RouteManeuver.Type.Left &&
                    GeoUtils.isPointInBoundingBox(it.position, boundingBox)
        }

        assertTrue(
            "Expected at least one LEFT maneuver inside bounding box, but none found.",
            hasLeftTurnInsideBox
        )
    }

    /***
     * https://jira.sygic.com/browse/CI-3487
     * TC909
     * Online routing. The route can't exit from the highway in coordinates 52.64845, -0.50727
     */
    @Test
    fun exitsFromTheRouteUKOnlineTest() = runBlocking {

        val vehicleProfile = VehicleProfile().apply {
            this.dimensionalTraits = DimensionalTraits().apply {
                this.totalWeight = 40000.0F
                this.totalLength = 16500
                this.totalHeight = 4000
                this.totalWidth = 2500
            }
            this.generalVehicleTraits = GeneralVehicleTraits().apply {
                this.vehicleType = VehicleType.Truck
            }
        }

        val boundingBox = GeoBoundingBox(
            topLeft = GeoCoordinates(52.64931, -0.51330),
            bottomRight = GeoCoordinates(52.64499, -0.49927)
        )

        val route = routeComputeHelper.onlineRouteCompute(
            GeoCoordinates(53.516590, -1.130280),
            GeoCoordinates(52.571690, -0.243670),
            routingOptions = RoutingOptions().apply {
                this.routingType = RoutingType.Fastest
                this.vehicleProfile = vehicleProfile
                this.useEndpointProtection = true
                this.napStrategy = NearestAccessiblePointStrategy.Disabled
                this.useTraffic = true
                this.useSpeedProfiles = true
            }
        )

        val maneuversInBoundingBox = route.maneuvers.filter { maneuver ->
            GeoUtils.isPointInBoundingBox(maneuver.position, boundingBox)
        }

        val actualLength = route.routeInfo.length

        assertTrue(
            "Route contains unexpected maneuvers within the bounding box: $maneuversInBoundingBox",
            maneuversInBoundingBox.isEmpty()
        )

        assertTrue(
            "Route length more that expected (142 km): $actualLength",
            actualLength < 142000
        )

    }

    /***
     * https://jira.sygic.com/browse/SDC-6959
     * TC597
     * Online routing. The route can't lead through the road 49.221270,16.582800
     */
    @Test
    fun routingInNewYorkFastestOnlineTest() = runBlocking {
        val vehicleProfile = VehicleProfile().apply {
            this.generalVehicleTraits = GeneralVehicleTraits().apply {
                vehicleType = VehicleType.Car
            }
        }

        val boundingBox = GeoBoundingBox(
            topLeft = GeoCoordinates(40.71816, -73.49993),
            bottomRight = GeoCoordinates(40.71712, -73.49872)
        )

        val route = routeComputeHelper.onlineRouteCompute(
            GeoCoordinates(40.717030, -73.502540),
            GeoCoordinates(40.732270, -73.448270),
            routingOptions = RoutingOptions().apply {
                this.routingType = RoutingType.Fastest
                this.vehicleProfile = vehicleProfile
                this.useEndpointProtection = true
                this.useTraffic = false
                this.useSpeedProfiles = false
                this.napStrategy = NearestAccessiblePointStrategy.Disabled
            }
        )

        val hasLeftTurnInsideBox = route.maneuvers.any {
            it.type == RouteManeuver.Type.Left &&
                    GeoUtils.isPointInBoundingBox(it.position, boundingBox)
        }

        assertTrue(
            "Expected at least one LEFT maneuver inside bounding box, but none found.",
            hasLeftTurnInsideBox
        )
    }

    /**
     * https://jira.sygic.com/browse/SDC-14656
     * TC895
     * In this test we check that online route length is < 82 km
     * (because leads through shortest mountain road)
     */
    @Test
    fun fuzzyDomainFranceTestOnline() = runBlocking {

        val start = GeoCoordinates(45.822810, 6.533240)
        val destination = GeoCoordinates(45.594250, 6.880690)

        val route = routeComputeHelper.onlineRouteCompute(
            start,
            destination,
            routingOptions = RoutingOptions().apply {
                vehicleProfile = routeComputeHelper.createCombustionVehicleProfile().apply {
                    generalVehicleTraits.vehicleType = VehicleType.Car
                    generalVehicleTraits.maximalSpeed = 150
                }
                useEndpointProtection = true
                useTraffic = false
                useSpeedProfiles = false
                napStrategy = NearestAccessiblePointStrategy.Disabled
            }
        )
        val actualLength = route.routeInfo.length
        assertTrue(
            "Expected route length < 82 km, but was $actualLength",
            route.routeInfo.length < 82000
        )
    }

    /**
     * https://jira.sygic.com/browse/SN-35606
     * TC896
     * In this test we check that online route length is < 105 km
     * (because leads through shortest mountain road)
     */
    @Test
    fun fuzzyDomainUSATestOnline() = runBlocking {

        val start = GeoCoordinates(45.194770, -109.246780)
        val destination = GeoCoordinates(45.019490, -109.934500)

        val route = routeComputeHelper.onlineRouteCompute(
            start,
            destination,
            routingOptions = RoutingOptions().apply {
                vehicleProfile = routeComputeHelper.createCombustionVehicleProfile().apply {
                    generalVehicleTraits.vehicleType = VehicleType.Car
                    generalVehicleTraits.maximalSpeed = 150
                }
                useEndpointProtection = true
                useTraffic = false
                useSpeedProfiles = false
                napStrategy = NearestAccessiblePointStrategy.Disabled
            }
        )
        val actualLength = route.routeInfo.length
        assertTrue(
            "Expected route length < 105 km, but was $actualLength",
            route.routeInfo.length < 105000
        )
    }


    /***
     * TC791
     * Online routing. The route can't lead through the road 50.8026,-0.04953
     */
    @Test
    fun routingInUnitedKingdomOnlineTest() = runBlocking {
        val vehicleProfile = VehicleProfile().apply {
            this.generalVehicleTraits = GeneralVehicleTraits().apply {
                vehicleType = VehicleType.Car
            }
        }

        val boundingBox = GeoBoundingBox(
            topLeft = GeoCoordinates(50.80236, -0.05005),
            bottomRight = GeoCoordinates(50.80165, -0.04889)
        )

        val route = routeComputeHelper.onlineRouteCompute(
            GeoCoordinates(50.801700, -0.048000),
            GeoCoordinates(50.803910, -0.049780),
            routingOptions = RoutingOptions().apply {
                this.routingType = RoutingOptions.RoutingType.Fastest
                this.vehicleProfile = vehicleProfile
                this.useEndpointProtection = true
                this.napStrategy = NearestAccessiblePointStrategy.Disabled
            }
        )

        val hasRightTurnInsideBox = route.maneuvers.any {
            it.type == RouteManeuver.Type.Right &&
                    GeoUtils.isPointInBoundingBox(it.position, boundingBox)
        }

        assertTrue(
            "Expected at least one RIGHT maneuver inside bounding box, but none found.",
            hasRightTurnInsideBox
        )
    }

    /***
     * https://jira.sygic.com/browse/SDC-6211
     * TC431
     * Online routing. The route can't lead through the road 43.1979,5.70984
     */
    @Test
    fun routingInFranceFastestOnlineTest() = runBlocking {
        val vehicleProfile = VehicleProfile().apply {
            this.generalVehicleTraits = GeneralVehicleTraits().apply {
                vehicleType = VehicleType.Car
            }
        }

        val boundingBox = GeoBoundingBox(
            topLeft = GeoCoordinates(43.19814, 5.70945),
            bottomRight = GeoCoordinates(43.19764, 5.71052)
        )

        val route = routeComputeHelper.onlineRouteCompute(
            GeoCoordinates(43.1979, 5.71095),
            GeoCoordinates(43.1983, 5.71006),
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

        val routeFastest = routeComputeHelper.onlineRouteCompute(
            start,
            destination,
            routingOptions = RoutingOptions().apply {
                this.routingType = RoutingOptions.RoutingType.Fastest
                this.vehicleProfile = vehicleProfile
            }
        )
        assertNotNull(routeFastest)
        val estimatedTimeOfArrivalFastest =
            routeFastest.routeInfo.waypointDurations.last().withSpeedProfiles

        val routeShortest = routeComputeHelper.onlineRouteCompute(
            start,
            destination,
            routingOptions = RoutingOptions().apply {
                this.routingType = RoutingOptions.RoutingType.Shortest
                this.vehicleProfile = vehicleProfile
            }
        )
        assertNotNull(routeShortest)
        val estimatedTimeOfArrivalShortest =
            routeShortest.routeInfo.waypointDurations.last().withSpeedProfiles

        assertNotEquals(
            "ETA should differ for fastest and shortest routes",
            estimatedTimeOfArrivalFastest,
            estimatedTimeOfArrivalShortest
        )
    }

    /***
     * TC705
     */
    @Test
    fun testRoutingInIndiaFastestOnline() = runBlocking {
        val vehicleProfile = VehicleProfile().apply {
            this.generalVehicleTraits = GeneralVehicleTraits().apply {
                vehicleType = VehicleType.Car
            }
        }

        val start = GeoCoordinates(28.734, 77.1314)
        val destination = GeoCoordinates(28.5822, 77.1861)

        val routeFastest =
            routeComputeHelper.onlineRouteCompute(
                start,
                destination,
                routingOptions = RoutingOptions().apply {
                    this.routingType = RoutingOptions.RoutingType.Fastest
                    this.vehicleProfile = vehicleProfile
                }
            )
        assertNotNull(routeFastest)
    }

    @Test
    fun shortestRouteInSlovakiaTestOnline() = runBlocking {
        val routeCompute = RouteComputeHelper()

        val route = routeCompute.onlineRouteCompute(
            GeoCoordinates(48.149240, 17.106990),
            GeoCoordinates(48.574280, 19.126600),
            routingOptions = RoutingOptions().apply {
                this.routingType = RoutingType.Shortest
            }
        )

        // Check if there's at least one maneuver matching the criteria
        val hasExpectedRoundabout = route.maneuvers.any { maneuver ->
            maneuver.type == RouteManeuver.Type.RoundaboutNE &&
                    maneuver.roundaboutExit == 2 &&
                    maneuver.roadName == "Senecká cesta"
        }

        // If no maneuver matches, this assertion will fail
        assertTrue(
            "Expected to find a roundabout with exit 2 on 'Senecká cesta' but none was found.",
            hasExpectedRoundabout
        )
    }

    /**
     * https://jira.sygic.com/browse/SDC-14224
     * TC892
     * In this test we check that route doesn't lead through tunnel cat. E by following way:
     * restricted route and normal route shouldn't have the same length and difference should be > 1 km
     */
    @Test
    fun tunnelCategoryERouteLengthDifferenceOnlineTest() = runBlocking {

        val start = GeoCoordinates(51.49906, -0.05638)
        val destination = GeoCoordinates(51.51246, -0.04123)

        val normalRoute = routeComputeHelper.onlineRouteCompute(
            start,
            destination
        )
        val lengthNormal = normalRoute.routeInfo.length

        val restrictedRoute = routeComputeHelper.onlineRouteCompute(
            start,
            destination,
            routingOptions = RoutingOptions().apply {
                vehicleProfile = routeComputeHelper.createCombustionVehicleProfile().apply {
                    generalVehicleTraits.vehicleType = VehicleType.Truck
                    hazmatTraits = HazmatTraits(emptySet(), TunnelCategory.E)
                }
                useEndpointProtection = true
                napStrategy = NearestAccessiblePointStrategy.Disabled
            }
        )
        val lengthRestricted = restrictedRoute.routeInfo.length

        val difference = lengthRestricted - lengthNormal

        assertTrue(
            "Expected significant difference (> 1000 m) due to tunnel restriction, but got $difference meters",
            difference > 1_000
        )
    }

    /**
     * https://jira.sygic.com/browse/SDC-14224
     * TC893
     * In this test we check that route doesn't lead through tunnel cat. C
     */
    @Test
    @Ignore("doesnt work")
    fun tunnelCategoryCOnlineTest() = runBlocking {

        val start = GeoCoordinates(51.45571, 0.23953)
        val destination = GeoCoordinates(51.48845, 0.26980)
        val routingOptions = RoutingOptions().apply {
            vehicleProfile = routeComputeHelper.createCombustionVehicleProfile().apply {
                generalVehicleTraits.vehicleType = VehicleType.Truck
                hazmatTraits = HazmatTraits(emptySet(), TunnelCategory.C)
            }
            useEndpointProtection = true
            napStrategy = NearestAccessiblePointStrategy.Disabled
            useTraffic = false
            useSpeedProfiles = false
        }

        val boundingBox = GeoBoundingBox(
            topLeft = GeoCoordinates(51.45313, 0.24168),
            bottomRight = GeoCoordinates(51.45145, 0.24507)
        )

        val route = routeComputeHelper.onlineRouteCompute(
            start,
            destination,
            routingOptions = routingOptions
        )

        val hasExitInsideBox = route.maneuvers.any {
            (it.type == RouteManeuver.Type.RoundaboutLeftE &&
                    GeoUtils.isPointInBoundingBox(it.position, boundingBox))
        }

        assertTrue(
            "Expected Roundabout exit maneuver inside bounding box, but none found.",
            hasExitInsideBox
        )
    }

    @Test
    @Ignore("https://dev.azure.com/sygiclbs/Maps%20SDK%20and%20Libraries/_git/sdk/pullrequest/2561")
    fun onlineRoutingGetAllTransitSplitCountries() = runBlocking {
        val start = GeoCoordinates(45.093660, -70.153760)
        val destination = GeoCoordinates(32.621350, -83.303830)

        val route = routeComputeHelper.onlineRouteCompute(
            start,
            destination,
            routingOptions = RoutingOptions().apply {
                this.useTraffic = false
                this.useSpeedProfiles = false
                this.departureTime = Date(1761301182)
            }
        )

        val expectedRegions =
            setOf("me", "nh", "ma", "ct", "ny", "nj", "de", "md", "dc", "va", "nc", "sc", "ga")
        val transitCountries = runBlocking { route.getTransitCountriesInfo() }

        val regions = transitCountries.flatMap { info ->
            when {
                info.country.startsWith("us-") -> listOf(info.country.substringAfter("us-"))
                else -> emptyList()
            }
        }.toSet()

        Log.d("SYGIC", "Actual regions from SDK: $regions")

        assertTrue(
            "Missing regions: ${expectedRegions - regions}",
            regions.containsAll(expectedRegions)
        )
    }

    /***
     * https://jira.sygic.com/browse/CI-3339
     * TC912
     */
    @Test
    fun disconnectedRouteFromTheRoadOnline() = runBlocking {
        val waypoints = listOf(
            GeoCoordinates(48.269350, 16.452510) // Waypoint
        )

        val vehicleProfile = VehicleProfile().apply {
            this.dimensionalTraits = DimensionalTraits().apply {
                totalWeight = 40000.0F
                totalLength = 5000
                totalHeight = 5000
                totalWidth = 2500
            }
            this.generalVehicleTraits = GeneralVehicleTraits().apply {
                vehicleType = VehicleType.Truck
            }
        }

        val boundingBox = GeoBoundingBox(
            topLeft = GeoCoordinates(48.26984, 16.45232),
            bottomRight = GeoCoordinates(48.26900, 16.45270)
        )

        val routingOptions = RoutingOptions().apply {
            this.routingType = RoutingType.Fastest
            this.vehicleProfile = vehicleProfile
            this.useEndpointProtection = true
            this.useSpeedProfiles = true
            this.napStrategy = NearestAccessiblePointStrategy.Disabled
        }

        val route = routeComputeHelper.onlineRouteCompute(
            start = GeoCoordinates(48.268310, 16.454770),
            waypoints = waypoints,
            destination = GeoCoordinates(48.271150, 16.451670),
            routingOptions = routingOptions
        )

        val maneuversInBoundingBox = route.maneuvers.filter { maneuver ->
            GeoUtils.isPointInBoundingBox(maneuver.position, boundingBox)
        }

        assertTrue(
            "Route contains unexpected maneuvers within the bounding box: $maneuversInBoundingBox",
            maneuversInBoundingBox.isEmpty()
        )
    }


    @Test
    fun onlineRoutingGetAllTransitCountries() = runBlocking {
        val start = GeoCoordinates(48.13204503419638, 17.09786238379282)
        val destination = GeoCoordinates(51.491340, -0.102940)

        val route = routeComputeHelper.onlineRouteCompute(
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

        val transitCountriesInfoListener: TransitCountriesInfoListener =
            mock(verboseLogging = true)
        route.getTransitCountriesInfo(transitCountriesInfoListener)
        verify(transitCountriesInfoListener, timeout(5_000L)).onTransitCountriesInfo(
            expectedCountries
        )
    }

    @Test
    fun leichendorfToZirndorfOnline() = runBlocking {

        val start = GeoCoordinates(49.4339, 10.9345)
        val destination = GeoCoordinates(49.4425, 10.9459)
        val routeCompute = RouteComputeHelper()

        val route = routeCompute.onlineRouteCompute(start, destination,
            routingOptions = RoutingOptions().apply {
                this.useSpeedProfiles = false
            }
        )

        assertEquals(6, route.maneuvers.size) // 6 maneuvers since october 2024 maps
        for (maneuver in route.maneuvers) {
            assertFalse(maneuver.roadName == "Thomas-Mann-Straße")
        }
    }

    @Test
    fun waypointDelayInOnlineRoute() = runBlocking {
        val start = GeoCoordinates(48.13119592622264, 17.198513901368113)
        val destination = GeoCoordinates(48.13686855039307, 17.214392816415362)
        val delayOnWaypoint = 50_000L
        val waypoint1 = Waypoint(
            originalPosition = GeoCoordinates(48.1353154681845, 17.20823509289179),
            delay = delayOnWaypoint,
            type = Waypoint.Type.Via,
            status = Waypoint.Status.Ahead
        )

        val route = routeComputeHelper.onlineRouteCompute(
            start,
            destination,
            waypointObjects = listOf(waypoint1),
        )

        val waypointFromComputedRoute = route.waypoints.find { it.type == Waypoint.Type.Via }
        assertTrue(
            "Received waypoint delay does not correspond to original waypoint delay",
            delayOnWaypoint == waypointFromComputedRoute!!.delay)
    }

    @Test
    fun waypointDelayZeroInOnlineRoute() = runBlocking {
        val start = GeoCoordinates(48.13119592622264, 17.198513901368113)
        val destination = GeoCoordinates(48.13686855039307, 17.214392816415362)
        val waypoint = Waypoint(
            originalPosition = GeoCoordinates(48.1353154681845, 17.20823509289179),
            delay = 0L,
            type = Waypoint.Type.Via,
            status = Waypoint.Status.Ahead
        )

        val route = routeComputeHelper.onlineRouteCompute(
            start,
            destination,
            waypointObjects = listOf(waypoint),
        )

        val waypointFromComputedRoute = route.waypoints.find { it.type == Waypoint.Type.Via }
        assertNotNull(waypointFromComputedRoute)
        assertEquals(
            "Expected zero delay to be preserved on waypoint",
            0L,
            waypointFromComputedRoute!!.delay
        )
    }

    @Test
    fun waypointDelayMultipleWaypointsInOnlineRoute() = runBlocking {
        val start = GeoCoordinates(48.13119592622264, 17.198513901368113)
        val destination = GeoCoordinates(48.13686855039307, 17.214392816415362)
        val waypoint1 = Waypoint(
            originalPosition = GeoCoordinates(48.134210, 17.205510),
            delay = 10_000L,
            type = Waypoint.Type.Via,
            status = Waypoint.Status.Ahead
        )
        val waypoint2 = Waypoint(
            originalPosition = GeoCoordinates(48.1353154681845, 17.20823509289179),
            delay = 35_000L,
            type = Waypoint.Type.Via,
            status = Waypoint.Status.Ahead
        )

        val route = routeComputeHelper.onlineRouteCompute(
            start,
            destination,
            waypointObjects = listOf(waypoint1, waypoint2),
        )

        val computedByPosition = route.waypoints.associateBy { it.delay }
        val computed1 = computedByPosition[waypoint1.delay]
        val computed2 = computedByPosition[waypoint2.delay]

        assertNotNull(computed1)
        assertNotNull(computed2)
        assertEquals(
            "Waypoint1 delay should match",
            waypoint1.delay,
            computed1!!.delay
        )
        assertEquals(
            "Waypoint2 delay should match",
            waypoint2.delay,
            computed2!!.delay
        )
    }

    @Test
    //ToDo: Discuss further with routing team
    fun waypointDelayNegativeInOnlineRoute() = runBlocking {
        val start = GeoCoordinates(48.13119592622264, 17.198513901368113)
        val destination = GeoCoordinates(48.13686855039307, 17.214392816415362)
        val delayOnWaypoint = -600_000L
        val waypoint = Waypoint(
            originalPosition = GeoCoordinates(48.1353154681845, 17.20823509289179),
            delay = delayOnWaypoint,
            type = Waypoint.Type.Via,
            status = Waypoint.Status.Ahead
        )

        val route = routeComputeHelper.onlineRouteCompute(
            start,
            destination,
            waypointObjects = listOf(waypoint),
        )

        val waypointFromComputedRoute = route.waypoints.find { it.type == Waypoint.Type.Via }
        assertNotNull(waypointFromComputedRoute)

        val actualDelay = waypointFromComputedRoute!!.delay
        assertTrue(
            "Expected negative delay to be preserved or clamped to 0, but was $actualDelay",
            actualDelay == delayOnWaypoint || actualDelay == 0L
        )
    }

    @Test
    fun waypointDelayAffectsRouteDurationOnline() = runBlocking {
        val start = GeoCoordinates(48.13119592622264, 17.198513901368113)
        val destination = GeoCoordinates(48.13686855039307, 17.214392816415362)
        val delayOnWaypoint = 120_000L
        val waypoint = Waypoint(
            originalPosition = GeoCoordinates(48.1353154681845, 17.20823509289179),
            delay = delayOnWaypoint,
            type = Waypoint.Type.Via,
            status = Waypoint.Status.Ahead
        )
        val waypointNoDelay = Waypoint(
            originalPosition = waypoint.originalPosition,
            delay = 0L,
            type = waypoint.type,
            status = waypoint.status
        )

        val baseRoute = routeComputeHelper.onlineRouteCompute(
            start,
            destination,
            waypointObjects = listOf(waypointNoDelay)
        )

        val delayedRoute = routeComputeHelper.onlineRouteCompute(
            start,
            destination,
            waypointObjects = listOf(waypoint)
        )

        val baseDuration =
            baseRoute.routeInfo.waypointDurations.last().withSpeedProfileAndTraffic
        val delayedDuration =
            delayedRoute.routeInfo.waypointDurations.last().withSpeedProfileAndTraffic

        assertTrue(
            "Expected route duration to include waypoint delay. Base=$baseDuration, delayed=$delayedDuration, delay=$delayOnWaypoint",
            delayedDuration >= baseDuration + delayOnWaypoint
        )
    }

    /***
     * https://jira.sygic.com/browse/CI-3121
     * TC923
     * Route is computed through town Salakas and not through RC4 road at 55.55806, 26.15803
     */
    @Test
    fun routingViaRoadsOfLowerQualityLithuaniaOnline() = runBlocking {
        val vehicleProfile = VehicleProfile().apply {
            this.generalVehicleTraits = GeneralVehicleTraits().apply {
                vehicleType = VehicleType.Car
            }
        }

        val boundingBox = GeoBoundingBox(
            topLeft = GeoCoordinates(55.56056, 26.15553),
            bottomRight = GeoCoordinates(55.55556, 26.16053)
        )

        val route = routeComputeHelper.onlineRouteCompute(
            GeoCoordinates(55.546820, 26.336190),
            GeoCoordinates(55.527450, 26.142320),
            routingOptions = RoutingOptions().apply {
                this.routingType = RoutingType.Fastest
                this.vehicleProfile = vehicleProfile
                this.useEndpointProtection = true
                this.napStrategy = NearestAccessiblePointStrategy.Disabled
            }
        )

        val maneuversInBoundingBox = route.maneuvers.filter { maneuver ->
            GeoUtils.isPointInBoundingBox(maneuver.position, boundingBox)
        }

        assertTrue(
            "Route contains unexpected maneuvers within the RC4 road bounding box: $maneuversInBoundingBox",
            maneuversInBoundingBox.isEmpty()
        )
    }

    /**
     * https://jira.sygic.com/browse/CI-3338
     * TC911
     *
     * Verifies that V3 online routing returns all transit countries for a route
     * from Slovakia to Lithuania (regular car).
     * Expected: sk → pl → lt
     */
    @Test
    fun transitCountriesSkToLt() = runBlocking {
        val start = GeoCoordinates(48.648840, 17.834150)
        val destination = GeoCoordinates(54.671710, 25.162030)

        val route = routeComputeHelper.onlineRouteCompute(
            start,
            destination,
            routingOptions = RoutingOptions().apply {
                this.useTraffic = false
                this.useSpeedProfiles = false
            }
        )

        val expectedCountries = listOf(
            TransitCountryInfo("sk", emptyList()),
            TransitCountryInfo("cz", emptyList()),
            TransitCountryInfo("pl", emptyList()),
            TransitCountryInfo("lt", emptyList())
        )


        val info = route.getTransitCountriesInfo()
        assertEquals(expectedCountries, info)
    }

}