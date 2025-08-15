package cz.feldis.sdkandroidtests.routing

import com.sygic.sdk.position.GeoBoundingBox
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.route.ChargingWaypoint
import com.sygic.sdk.route.GuidedRouteProfile
import com.sygic.sdk.route.PrimaryRouteRequest
import com.sygic.sdk.route.Route
import com.sygic.sdk.route.RouteAvoids
import com.sygic.sdk.route.RouteDeserializerError
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
import com.sygic.sdk.route.listeners.EVRangeListener
import com.sygic.sdk.route.listeners.GeometryListener
import com.sygic.sdk.route.listeners.RouteComputeFinishedListener
import com.sygic.sdk.route.listeners.RouteComputeListener
import com.sygic.sdk.route.listeners.RouteDurationListener
import com.sygic.sdk.route.listeners.RouteElementsListener
import com.sygic.sdk.route.listeners.RouteRequestDeserializedListener
import com.sygic.sdk.route.listeners.RouteWarningsListener
import com.sygic.sdk.route.listeners.TransitCountriesInfoListener
import com.sygic.sdk.vehicletraits.dimensional.Axle
import com.sygic.sdk.vehicletraits.dimensional.DimensionalTraits
import com.sygic.sdk.vehicletraits.dimensional.Trailer
import com.sygic.sdk.vehicletraits.general.VehicleType
import com.sygic.sdk.vehicletraits.hazmat.HazmatTraits
import com.sygic.sdk.vehicletraits.hazmat.TunnelCategory
import com.sygic.sdk.vehicletraits.powertrain.ConsumptionData
import com.sygic.sdk.vehicletraits.powertrain.EuropeanEmissionStandard
import com.sygic.sdk.vehicletraits.powertrain.FuelType
import com.sygic.sdk.vehicletraits.powertrain.PowertrainTraits
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.mapInstaller.MapDownloadHelper
import cz.feldis.sdkandroidtests.utils.GeoUtils
import junit.framework.Assert.assertNotNull
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import timber.log.Timber
import java.util.Date
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class RouteComputeTests : BaseTest() {
    private lateinit var mapDownloadHelper: MapDownloadHelper
    private lateinit var routeComputeHelper: RouteComputeHelper

    override fun setUp() {
        super.setUp()
        mapDownloadHelper = MapDownloadHelper()
        routeComputeHelper = RouteComputeHelper()
    }

    @Test
    fun computeNextDurationsTestOffline() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("sk")
        val listener: RouteDurationListener = mock(verboseLogging = true)
        val router = RouterProvider.getInstance().get()

        val start = GeoCoordinates(48.145718, 17.118669)
        val destination = GeoCoordinates(48.190322, 16.401080)
        val route = routeComputeHelper.offlineRouteCompute(start, destination)

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
    fun getRouteElementsIcelandOffline() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("is")

        val elementsListener: RouteElementsListener = mock(verboseLogging = true)

        val start = GeoCoordinates(63.556092, -19.794962)
        val destination = GeoCoordinates(63.420816, -19.001375)

        val route = routeComputeHelper.offlineRouteCompute(
            start,
            destination
        )
        assertNotNull(route)
        route.getRouteElements(elementsListener)
        verify(elementsListener, timeout(40_000L)).onRouteElementsRetrieved(
            argThat {
                if (this.isEmpty()) {
                    return@argThat false
                }
                true
            }
        )
    }

    @Test
    fun computeReykjavikToVikOffline() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("is")
        val start = GeoCoordinates(64.114341, -21.871153)
        val destination = GeoCoordinates(63.417836, -19.002209)
        val routingOptions = RoutingOptions().apply {
            vehicleProfile = routeComputeHelper.createCombustionVehicleProfile()
        }

        val route = routeComputeHelper.offlineRouteCompute(
            start,
            destination,
            routingOptions = routingOptions
        )
        Assert.assertNotNull(route)
    }

    @Test
    fun computeReykjavikToVikOfflineGetAltitude() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("is")
        val listener: GeometryListener = mock(verboseLogging = true)
        val start = GeoCoordinates(64.114341, -21.871153)
        val destination = GeoCoordinates(63.417836, -19.002209)
        val routingOptions = RoutingOptions().apply {
            vehicleProfile = routeComputeHelper.createCombustionVehicleProfile()
        }

        val route = routeComputeHelper.offlineRouteCompute(
            start,
            destination,
            routingOptions = routingOptions
        )
        route.getRouteGeometry(true, listener)
        verify(listener, timeout(5_000L)).onGeometry(argThat {
            for (geoCoordinates in this) {
                assertFalse((geoCoordinates.altitude < 0.0) || (geoCoordinates.altitude > 9000.0))
            }
            return@argThat true
        })
    }

    @Test
    fun mapNotAvailableTruckTestOffline() {
        disableOnlineMaps()
        val start = GeoCoordinates(48.9844, 22.1844)
        val destination = GeoCoordinates(47.1518, 9.81344)

        mapDownloadHelper.ensureMapNotInstalled("at")
        mapDownloadHelper.installAndLoadMap("sk")

        val listener: RouteComputeListener = mock(verboseLogging = true)
        val routeComputeFinishedListener: RouteComputeFinishedListener = mock(verboseLogging = true)

        val options = RoutingOptions()
        options.apply {
            routeAvoids.globalRouteAvoids = mutableSetOf(RouteAvoids.Type.UnpavedRoad)
            vehicleProfile = routeComputeHelper.createCombustionVehicleProfile()
            routingService = RoutingOptions.RoutingService.Offline

            napStrategy = NearestAccessiblePointStrategy.Disabled
            useSpeedProfiles = true
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

        verify(listener, Mockito.timeout(50_000L)).onComputeFinished(
            isNull(),
            argThat { this == Router.RouteComputeStatus.SelectionOutsideOfMap }
        )
    }

    @Test
    fun computeWrongFromPointOffline() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("sk")
        val start = GeoCoordinates(34.764518085578196, 18.03834181295307)
        val destination = GeoCoordinates(47.99919432978094, 18.164403416068332)
        val listener: RouteComputeListener = mock(verboseLogging = true)
        val routeComputeFinishedListener: RouteComputeFinishedListener = mock(verboseLogging = true)

        val options = RoutingOptions()
        options.apply {
            routingService = RoutingOptions.RoutingService.Offline
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
            argThat { this == Router.RouteComputeStatus.SelectionOutsideOfMap }
        )
    }

    @Test
    fun cancelOfflineCompute() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("sk")
        val start = GeoCoordinates(48.14096139265543, 17.154151725057243)
        val destination = GeoCoordinates(48.734914147394626, 21.260367789890452)
        val listener: RouteComputeListener = mock(verboseLogging = true)
        val routeComputeFinishedListener: RouteComputeFinishedListener = mock(verboseLogging = true)

        val options = RoutingOptions()
        options.apply {
            routingService = RoutingOptions.RoutingService.Offline
        }

        val routeRequest = RouteRequest()
        routeRequest.apply {
            setStart(start)
            setDestination(destination)
            routingOptions = options
        }

        val primaryRouteRequest = PrimaryRouteRequest(routeRequest, listener)
        val router = RouterProvider.getInstance().get()

        val task = router.computeRouteWithAlternatives(
            primaryRouteRequest,
            null,
            routeComputeFinishedListener
        )
        verify(listener, timeout(10_000L).atLeast(5)).onProgress(any())
        task.cancel()

        verify(listener, timeout(10_000L)).onComputeFinished(
            isNull(),
            argThat { this == Router.RouteComputeStatus.UserCanceled }
        )
    }

    @Test
    fun computeGuidedRouteWithEmptyPolyline() {
        val polyline = mutableListOf<GeoCoordinates>()
        val listener: RouteComputeListener = mock(verboseLogging = true)
        val routeComputeFinishedListener: RouteComputeFinishedListener = mock(verboseLogging = true)

        val guidedRouteProfile = GuidedRouteProfile(polyline)
        val routeRequest = RouteRequest(guidedRouteProfile)

        val primaryRouteRequest = PrimaryRouteRequest(routeRequest, listener)
        val router = RouterProvider.getInstance().get()

        router.computeRouteWithAlternatives(primaryRouteRequest, null, routeComputeFinishedListener)
        verify(listener, timeout(10_000L)).onComputeFinished(
            eq(null),
            eq(Router.RouteComputeStatus.PathNotFound)
        )
    }

    @Test
    fun computeGuidedRouteExpectLargeGapInPolylineErrorOffline() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("sk")
        val polyline = mutableListOf(
            GeoCoordinates(48.14255480489253, 17.125204056355585),
            GeoCoordinates(48.14578621767613, 17.12975400149264),
            GeoCoordinates(48.145800992710754, 17.13600542325981),
            GeoCoordinates(48.132321417025096, 17.216446443991128),
            GeoCoordinates(48.1550953747085, 17.049768575766283),
            GeoCoordinates(48.155677390369334, 17.048702682030516),
        )
        val listener: RouteComputeListener = mock(verboseLogging = true)
        val routeComputeFinishedListener: RouteComputeFinishedListener = mock(verboseLogging = true)

        val guidedRouteProfile = GuidedRouteProfile(polyline)
        val routeRequest = RouteRequest(guidedRouteProfile)

        val primaryRouteRequest = PrimaryRouteRequest(routeRequest, listener)
        val router = RouterProvider.getInstance().get()

        router.computeRouteWithAlternatives(primaryRouteRequest, null, routeComputeFinishedListener)
        verify(listener, timeout(10_000L)).onComputeFinished(
            eq(null),
            eq(Router.RouteComputeStatus.LargeGapInPolyline)
        )
    }

    @Test
    fun camperAndTruckETADifferentComparison() {
        mapDownloadHelper.installAndLoadMap("sk")
        val start = GeoCoordinates(48.13116130573944, 17.11782382599132)
        val destination = GeoCoordinates(49.05314733520812, 18.325403607220828)
        val routeCompute = RouteComputeHelper()
        val routingOptions = RoutingOptions().apply {
            vehicleProfile = routeComputeHelper.createCombustionVehicleProfile().apply {
                generalVehicleTraits.vehicleType = VehicleType.Truck
                generalVehicleTraits.maximalSpeed = 90
            }
        }

        val routeTruck = routeCompute.offlineRouteCompute(
            start,
            destination,
            routingOptions = routingOptions
        )
        val timeToEndTruck = routeTruck.routeInfo.waypointDurations.last().withSpeedProfiles

        routingOptions.apply {
            vehicleProfile = routeComputeHelper.createCombustionVehicleProfile().apply {
                generalVehicleTraits.vehicleType = VehicleType.Car
                generalVehicleTraits.maximalSpeed = 130
                dimensionalTraits?.trailers = listOf(
                    Trailer(
                        1000, true, listOf(
                            Axle(4, 250F, 4)
                        )
                    )
                )
            }
        }

        val routeCamper = routeCompute.offlineRouteCompute(
            start,
            destination,
            routingOptions = routingOptions
        )
        val timeToEndCamper = routeCamper.routeInfo.waypointDurations.last().withSpeedProfiles

        assertTrue(
            "camper: $timeToEndCamper, truck: $timeToEndTruck ",
            timeToEndCamper + 100 < timeToEndTruck
        )
    }

    @Test
    fun countriesInfoOrderAtSkHu() {
        mapDownloadHelper.installAndLoadMap("at")
        mapDownloadHelper.installAndLoadMap("sk")
        mapDownloadHelper.installAndLoadMap("hu")
        val start = GeoCoordinates(48.133521125857136, 16.904835360462567)
        val waypoint = GeoCoordinates(48.12917385634974, 17.19439161086379)
        val destination = GeoCoordinates(47.71987275813502, 17.653115614211107)

        val route = routeComputeHelper.offlineRouteCompute(
            start,
            destination,
            waypoints = listOf(waypoint)
        )

        val expectedCountries = listOf(
            TransitCountryInfo("at", emptyList()), // "at" should be the first
            TransitCountryInfo("sk", emptyList()), // "sk" should be the second
            TransitCountryInfo("hu", emptyList())  // "hu" should be the third
        )

        val transitCountriesInfoListener: TransitCountriesInfoListener = mock(verboseLogging = true)
        route.getTransitCountriesInfo(transitCountriesInfoListener)
        verify(transitCountriesInfoListener, timeout(5_000L)).onTransitCountriesInfo(
            expectedCountries
        )
    }

    @Test
    fun countriesInfoOrderHuAtSk() {
        mapDownloadHelper.installAndLoadMap("at")
        mapDownloadHelper.installAndLoadMap("sk")
        mapDownloadHelper.installAndLoadMap("hu")
        val start = GeoCoordinates(47.591, 16.8735)
        val waypoint = GeoCoordinates(48.0184, 16.9746)
        val destination = GeoCoordinates(48.12917385634974, 17.19439161086379)

        val routeCompute = RouteComputeHelper()

        val route = routeCompute.offlineRouteCompute(
            start,
            destination,
            waypoints = listOf(waypoint)
        )

        val expectedCountries = listOf(
            TransitCountryInfo("hu", emptyList()),
            TransitCountryInfo("at", emptyList()),
            TransitCountryInfo("sk", emptyList())
        )

        val transitCountriesInfoListener: TransitCountriesInfoListener = mock(verboseLogging = true)
        route.getTransitCountriesInfo(transitCountriesInfoListener)
        verify(transitCountriesInfoListener, timeout(5_000L)).onTransitCountriesInfo(
            expectedCountries
        )
    }

    @Test
    fun transitCountriesPreserveOrderTest_ThroughLiechtensteinToHungary() {
        mapDownloadHelper.installAndLoadMap("ch") // Switzerland
        mapDownloadHelper.installAndLoadMap("li") // Liechtenstein
        mapDownloadHelper.installAndLoadMap("at") // Austria
        mapDownloadHelper.installAndLoadMap("sk") // Slovakia
        mapDownloadHelper.installAndLoadMap("hu") // Hungary

        // Define route: start in Switzerland, end in Hungary
        val start = GeoCoordinates(47.3769, 8.5417) // Zurich
        val waypoint1 = GeoCoordinates(47.1416, 9.5215) // Liechtenstein
        val waypoint2 = GeoCoordinates(47.3878886618401, 13.101404296427072) // Austria
        val waypoint3 = GeoCoordinates(48.1486, 17.1077) // Slovakia
        val destination = GeoCoordinates(47.4979, 19.0402) // Budapest

        val route = RouteComputeHelper().offlineRouteCompute(
            start,
            destination,
            waypoints = listOf(waypoint1, waypoint2, waypoint3)
        )

        // Expected order of transition countries
        val expectedCountries = listOf(
            TransitCountryInfo("ch", emptyList()),
            TransitCountryInfo("li", emptyList()),
            TransitCountryInfo("at", emptyList()),
            TransitCountryInfo("sk", emptyList()),
            TransitCountryInfo("hu", emptyList())
        )

        // Mock and verify the transit countries info listener
        val listener: TransitCountriesInfoListener = mock(verboseLogging = true)
        route.getTransitCountriesInfo(listener)

        verify(listener, timeout(5_000L)).onTransitCountriesInfo(expectedCountries)
    }

    /**
     * https://jira.sygic.com/browse/SDC-13368
     *
     * Validates that the computed route does not apply the "Country" avoidable type
     * to the start and destination countries.
     */
    @Test
    fun avoidableCountryTest() {
        mapDownloadHelper.installAndLoadMap("at")
        mapDownloadHelper.installAndLoadMap("sk")
        mapDownloadHelper.installAndLoadMap("hu")
        val start = GeoCoordinates(47.591, 16.8735) // hungary
        val waypoint = GeoCoordinates(48.0184, 16.9746) // austria
        val destination = GeoCoordinates(48.12917385634974, 17.19439161086379) // slovakia

        val routeCompute = RouteComputeHelper()

        val route = routeCompute.offlineRouteCompute(
            start,
            destination,
            waypoints = listOf(waypoint)
        )

        val startCountry = "hu"
        val destinationCountry = "sk"

        val countryRouteAvoidables =
            route.routeRequest.routingOptions.routeAvoids.countryRouteAvoidables

        listOf(startCountry, destinationCountry).forEach { country ->
            countryRouteAvoidables.find { it.first == country }?.second?.forEach { avoidType ->
                assert(avoidType != RouteAvoids.Type.Country) { "Country avoidable type found in $country" }
            }
        }
    }

    @Test
    fun avoidableCountryTest_startAndEndCountryMustNotBeAvoided() {
        mapDownloadHelper.installAndLoadMap("ch") // Switzerland
        mapDownloadHelper.installAndLoadMap("at") // Austria
        mapDownloadHelper.installAndLoadMap("sk") // Slovakia
        mapDownloadHelper.installAndLoadMap("hu") // Hungary

        val start = GeoCoordinates(47.35823094740036, 8.588782585276224) // Zurich
        val destination = GeoCoordinates(48.607066673016575, 21.3232184832135) // Cana, SVK

        val route = RouteComputeHelper().offlineRouteCompute(
            start,
            destination
        )

        val startCountry = "ch"
        val destinationCountry = "sk"

        val countryRouteAvoidables: List<Pair<String, Set<RouteAvoids.Type>>> =
            route.routeRequest.routingOptions.routeAvoids.countryRouteAvoidables

        // ch and sk should not have "Country" avoidables
        listOf(startCountry, destinationCountry).forEach { country ->
            countryRouteAvoidables.find { it.first == country }?.second?.forEach { avoidType ->
                assert(avoidType != RouteAvoids.Type.Country) {
                    "Country avoidable type should not be applied to $country"
                }
            }
        }

        // at and hu should have "Country" avoidables
        listOf("at", "hu").forEach { country ->
            val avoids = countryRouteAvoidables.find { it.first == country }?.second
            assert(avoids != null && RouteAvoids.Type.Country in avoids) {
                "Expected $country to be marked as avoidable with type 'Country'"
            }
        }
    }

    @Test
    fun changeWeightAtWaypoint() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("sk")

        val start = GeoCoordinates(48.19159435449465, 17.223366296005075)
        val waypoint = GeoCoordinates(48.19136102846576, 17.226362460836786)
        val destination = GeoCoordinates(48.19159237577265, 17.22143784412721)
        val routingOptions = RoutingOptions().apply {
            vehicleProfile = routeComputeHelper.createCombustionVehicleProfile().apply {
                generalVehicleTraits.vehicleType = VehicleType.Truck
                dimensionalTraits = DimensionalTraits().apply {
                    totalWeight = 50_000F
                }
            }
            napStrategy = NearestAccessiblePointStrategy.Disabled
            useEndpointProtection = true
            routingService = RoutingOptions.RoutingService.Offline
        }
        val request = RouteRequest().apply {
            this.setStart(start)
            this.addViaPoint(waypoint)
            this.setDestination(destination)
            this.routingOptions = routingOptions
            this.getViaPoints()[0].vehicleInfo = Waypoint.VehicleInfo(3000) // we unload here
        }

        val listener: RouteComputeListener = mock(verboseLogging = true)
        val routeWarningsListener: RouteWarningsListener = mock(verboseLogging = true)
        val routeComputeFinishedListener: RouteComputeFinishedListener = mock(verboseLogging = true)
        val primaryRouteRequest = PrimaryRouteRequest(request, listener)

        val captor = argumentCaptor<Route>()
        val captorWarnings = argumentCaptor<List<RouteWarning>>()

        RouterProvider.getInstance().get().computeRouteWithAlternatives(
            primaryRouteRequest,
            null,
            routeComputeFinishedListener
        )
        verify(listener, timeout(10_000L)).onComputeFinished(
            captor.capture(), eq(Router.RouteComputeStatus.SuccessWithWarnings)
        )

        val route = captor.firstValue

        route.getRouteWarnings(routeWarningsListener)
        verify(routeWarningsListener, timeout(10_000L)).onRouteWarnings(captorWarnings.capture())

        assert(captorWarnings.firstValue.size == 1)
        // as we get back through the same road, there would be two warnings if we didn't change the weight
        assertEquals(
            34000.0F,
            (captorWarnings.firstValue[0] as RouteWarning.SectionWarning.WeightRestriction).limitValue
        )
        assertEquals(
            50000.0F,
            (captorWarnings.firstValue[0] as RouteWarning.SectionWarning.WeightRestriction).realValue
        )
    }

    @Test
    fun doNotChangeWeightAtWaypoint() {
        // this is an anti-change weight at waypoint test that confirms that the another test works as expected
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("sk")

        val start = GeoCoordinates(48.19159435449465, 17.223366296005075)
        val waypoint = GeoCoordinates(48.19136102846576, 17.226362460836786)
        val destination = GeoCoordinates(48.19159237577265, 17.22143784412721)
        val routingOptions = RoutingOptions().apply {
            vehicleProfile = routeComputeHelper.createCombustionVehicleProfile().apply {
                generalVehicleTraits.vehicleType = VehicleType.Truck
                dimensionalTraits = DimensionalTraits().apply {
                    totalWeight = 50_000F
                }
            }
            napStrategy = NearestAccessiblePointStrategy.Disabled
            useEndpointProtection = true
            routingService = RoutingOptions.RoutingService.Offline
        }
        val request = RouteRequest().apply {
            this.setStart(start)
            this.addViaPoint(waypoint)
            this.setDestination(destination)
            this.routingOptions = routingOptions
            // we do not change the weight at waypoint
        }

        val listener: RouteComputeListener = mock(verboseLogging = true)
        val routeWarningsListener: RouteWarningsListener = mock(verboseLogging = true)
        val routeComputeFinishedListener: RouteComputeFinishedListener = mock(verboseLogging = true)
        val primaryRouteRequest = PrimaryRouteRequest(request, listener)

        val captor = argumentCaptor<Route>()
        val captorWarnings = argumentCaptor<List<RouteWarning>>()

        RouterProvider.getInstance().get().computeRouteWithAlternatives(
            primaryRouteRequest,
            null,
            routeComputeFinishedListener
        )
        verify(listener, timeout(30_000L)).onComputeFinished(
            captor.capture(), eq(Router.RouteComputeStatus.SuccessWithWarnings)
        )

        val route = captor.firstValue

        route.getRouteWarnings(routeWarningsListener)
        verify(routeWarningsListener, timeout(10_000L)).onRouteWarnings(captorWarnings.capture())

        assert(captorWarnings.firstValue.size > 1)
    }

    @Test
    fun rovinkaToSygicEcoRouteComparison() {
        mapDownloadHelper.installAndLoadMap("sk")
        val consumptionCurve = mutableMapOf(
            40.0 to 0.09,
            50.0 to 0.075,
            80.0 to 0.065,
            90.0 to 0.07,
            130.0 to 0.092
        )
        val weightFactors = mutableMapOf(1.0 to 1.0)
        val start = GeoCoordinates(48.10180335214629, 17.233888233640172)
        val destination = GeoCoordinates(48.145628357674845, 17.12695279400835)

        val ecoRoutingOptions = RoutingOptions().apply {
            vehicleProfile = routeComputeHelper.createCombustionVehicleProfile().apply {
                powertrainTraits = PowertrainTraits.InternalCombustionPowertrain(
                    FuelType.Petrol,
                    EuropeanEmissionStandard.Euro5,
                    ConsumptionData(consumptionCurve, weightFactors)
                )
            }
            routingType = RoutingType.Economic
        }

        val ecoRoute = routeComputeHelper.offlineRouteCompute(
            start,
            destination,
            routingOptions = ecoRoutingOptions
        )

        val fastestRoutingOptions = RoutingOptions().apply {
            routingType = RoutingType.Fastest
        }

        val fastestRoute = routeComputeHelper.offlineRouteCompute(
            start,
            destination,
            routingOptions = fastestRoutingOptions
        )

        assertTrue(fastestRoute.waypoints[1].distanceFromStart in 20001 downTo 14999) // uses highway
        assertTrue(ecoRoute.waypoints[1].distanceFromStart in 14000 downTo 10999) // uses a shorter way
        assertNotEquals(
            fastestRoute.maneuvers,
            ecoRoute.maneuvers
        ) // we check that the maneuvers are different
    }

    @Test
    fun serializedRouteRequestVerifyFirstWPPassed() = runBlocking {
        val routeRequest = getRouteRequest("routeFirstWPPassed.rt")
        val (firstViaPoint, secondViaPoint) = routeRequest.getViaPoints()

        with(firstViaPoint) {
            assertEquals(status, Waypoint.Status.Reached)
            assertEquals(originalPosition, GeoCoordinates(48.14228, 17.12758))
        }

        with(secondViaPoint) {
            assertEquals(status, Waypoint.Status.Ahead)
            assertEquals(originalPosition, GeoCoordinates(48.14374, 17.13119))
        }
    }

    @Test
    fun getStateOfChargeAtWaypoint() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("sk")

        val start = GeoCoordinates(48.24135577878832, 16.99083981234057)
        val destination = GeoCoordinates(49.06008227080942, 20.315811448409608)

        val options = RoutingOptions().apply {
            vehicleProfile = routeComputeHelper.createElectricVehicleProfileTruck(350f, 100f)
            useEndpointProtection = true
            napStrategy = NearestAccessiblePointStrategy.Disabled
        }

        val route = routeComputeHelper.offlineRouteCompute(
            start,
            destination,
            routingOptions = options
        )

        route.waypoints.forEach {
            if (it is ChargingWaypoint) {
                assertTrue(it.stateOfCharge > 0.1)
                assertTrue(it.chargingTime > 0)
            }
        }
    }

    /**
     * https://jira.sygic.com/browse/SDC-4695
     * Test Case TC643
     * Start: 3227 N Oconto Avenue, Chicago, IL
     * Destination: 5815 S Maryland Avenue, Chicago, IL
     *
     * In this test case, we only check that the route is computed as there was a problem with graph levels in the past.
     */
    @Test
    fun testIllinoisOcontoToMaryland() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("us-il")

        val start = GeoCoordinates(41.93884551765079, -87.80773891426024)
        val destination = GeoCoordinates(41.78921314499593, -87.60408102443468)

        val route = routeComputeHelper.offlineRouteCompute(
            start,
            destination
        )
        assertNotNull(route)
    }

    /**
     * https://jira.sygic.com/browse/SDC-10680
     * There was a problem that the route shape was evaluated as a U-Turn so
     * the routing tried to avoid it. Most of the trucks can turn left here, so
     * we need to check that the route only has three maneuvers.
     */
    @Test
    fun testNotUturnDirection() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("de-07")

        val start = GeoCoordinates(49.064409322443794, 8.290126424786548)
        val destination = GeoCoordinates(49.06390230789947, 8.293707382366419)

        val options = createDefaultTruckRoutingOptions().apply {
            this.vehicleProfile?.dimensionalTraits = DimensionalTraits().apply {
                totalLength = 16500
            }
        }

        val route = routeComputeHelper.offlineRouteCompute(
            start,
            destination,
            routingOptions = options
        )

        assertEquals(3, route.maneuvers.size)
    }

    /**
     * https://jira.sygic.com/browse/SDC-6686
     * Test Case TC641
     * Start: Tovarenska, Bratislava, Slovakia
     * Destination: Vienna International Airport, Austria
     *
     * In this test case, we set a global Toll Road avoid and check whether the route is
     * computed correctly without avoiding any toll road avoids.
     */
    @Test
    fun testBratislavaToSchwechatAvoidTolls() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("sk")
        mapDownloadHelper.installAndLoadMap("at")

        val start = GeoCoordinates(48.0935, 17.1165)
        val destination = GeoCoordinates(48.1209, 16.5627)
        val options = RoutingOptions().apply {
            vehicleProfile = routeComputeHelper.createCombustionVehicleProfile()
            routeAvoids.globalRouteAvoids = mutableSetOf(RouteAvoids.Type.TollRoad)
        }

        val request = RouteRequest().apply {
            this.setStart(start)
            this.setDestination(destination)
            this.routingOptions = options
        }

        val routeWarningsListener: RouteWarningsListener = mock(verboseLogging = true)
        val listener: RouteComputeListener = mock(verboseLogging = true)
        val routeComputeFinishedListener: RouteComputeFinishedListener = mock(verboseLogging = true)
        val primaryRouteRequest = PrimaryRouteRequest(request, listener)

        val captor = argumentCaptor<Route>()
        val captorWarnings = argumentCaptor<List<RouteWarning>>()

        RouterProvider.getInstance().get().computeRouteWithAlternatives(
            primaryRouteRequest,
            null,
            routeComputeFinishedListener
        )
        verify(listener, timeout(10_000L)).onComputeFinished(
            captor.capture(), argThat {
                return@argThat (this == Router.RouteComputeStatus.SuccessWithWarnings || this == Router.RouteComputeStatus.Success)
            })

        val route = captor.firstValue
        route.getRouteWarnings(routeWarningsListener)
        verify(routeWarningsListener, timeout(10_000L)).onRouteWarnings(captorWarnings.capture())

        // assert if there is a GlobalAvoidViolation.UnavoidableTollRoad
        for (warnings in captorWarnings.allValues) {
            for (warning in warnings) {
                assertFalse(warning is RouteWarning.SectionWarning.GlobalAvoidViolation.UnavoidableTollRoad)
            }
        }
    }

    /**
     * https://jira.sygic.com/browse/SDC-4444
     * Test Case TC177
     * Start: Leichendorf, Germany
     * Destination: Zirndorf, Germany
     *
     * In this test case, we compute a route from Leichendorf to Zirndorf, expecting that
     * the route will not pass through residential areas where the speed limit is too low.
     */
    @Test
    fun leichendorfToZirndorf() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("de-02")

        val start = GeoCoordinates(49.4339, 10.9345)
        val destination = GeoCoordinates(49.4425, 10.9459)
        val routeCompute = RouteComputeHelper()

        val route = routeCompute.offlineRouteCompute(
            start,
            destination,
        )

        assertEquals(6, route.maneuvers.size) // 6 maneuvers since october 2024 maps
        for (maneuver in route.maneuvers) {
            assertFalse(maneuver.roadName == "Thomas-Mann-Straße")
        }
    }

    @Test
    fun fromLeichendorfToZirndorf() = runBlocking {
        mapDownloadHelper.installAndLoadMap("de-02")
        val routeCompute = RouteComputeHelper()

        val route = routeCompute.offlineRouteCompute(
            GeoCoordinates(49.4339, 10.9345),
            GeoCoordinates(49.4425, 10.9459),
            routingOptions = RoutingOptions().apply {
                this.routingType = RoutingType.Fastest
            }
        )
        val leftTurnExists = route.maneuvers.count {
            it.type == RouteManeuver.Type.Left &&
                    it.roundaboutExit == 0 &&
                    it.roadName == "Schwabacher Straße"
        } > 0

        val roundaboutExit2Exists = route.maneuvers.count {
            it.type == RouteManeuver.Type.RoundaboutN &&
                    it.roundaboutExit == 2
        } > 0

        val roundaboutExit1Exists = route.maneuvers.count {
            it.type == RouteManeuver.Type.RoundaboutE &&
                    it.roundaboutExit == 1 &&
                    it.nextRoadName == "Banderbacher Straße"
        } > 0

        val hasExpectedManeuvers = leftTurnExists && roundaboutExit2Exists && roundaboutExit1Exists
        assertTrue(hasExpectedManeuvers)
    }

    @Test
    fun routingViaRoadsOfLowerQualityPortugal() = runBlocking {
        mapDownloadHelper.installAndLoadMap("pt")
        val routeCompute = RouteComputeHelper()

        val route = routeCompute.offlineRouteCompute(
            GeoCoordinates(39.8021, -8.09799),
            GeoCoordinates(39.6256, -8.16204),
            routingOptions = RoutingOptions().apply {
                this.routingType = RoutingType.Fastest
            }
        )
        val roundaboutExit2Exists = route.maneuvers.count {
            it.type == RouteManeuver.Type.RoundaboutN &&
                    it.roundaboutExit == 2 &&
                    it.roadName == "Rua de Proença a Nova"
        } > 0

        val leftTurnExists = route.maneuvers.count {
            it.type == RouteManeuver.Type.Left &&
                    it.roundaboutExit == 0 &&
                    it.nextRoadNumbers.contains("N2")
        } > 0

        val secondLeftTurnExists = route.maneuvers.count {
            it.type == RouteManeuver.Type.Left &&
                    it.roundaboutExit == 0 &&
                    it.nextRoadNumbers.contains("N2")
        } > 0

        val hasExpectedManeuvers = roundaboutExit2Exists && leftTurnExists && secondLeftTurnExists
        assertTrue(hasExpectedManeuvers)
    }

    /**
     * TC864
     * https://jira.sygic.com/browse/SDC-12136
     **/
    @Test
    fun carRoutingViaRC4RoadsTest() = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")
        val routeCompute = RouteComputeHelper()

        val route = routeCompute.offlineRouteCompute(
            GeoCoordinates(48.146380, 17.125170),
            GeoCoordinates(48.145140, 17.123600),
            routingOptions = RoutingOptions().apply {
                this.routingType = RoutingType.Fastest
            }
        )
        val maneuverRight = route.maneuvers.count {
            it.type == RouteManeuver.Type.Right &&
                    it.roadName == "Továrenská"
        } > 0

        val maneuverRight2 = route.maneuvers.count {
            it.type == RouteManeuver.Type.Right &&
                    it.roadName == "Továrenská" &&
                    it.nextRoadName == "Karadžičova"
        } > 0

        val maneuverRight3 = route.maneuvers.count {
            it.roadName == "Landererova" ||
                    it.nextRoadName == "Landererova"
        } > 0

        val hasExpectedManeuvers = maneuverRight && maneuverRight2 && !maneuverRight3
        assertTrue(hasExpectedManeuvers)
    }

    @Test
    fun shortestRouteInSlovakiaTest() = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")
        val routeCompute = RouteComputeHelper()

        val route = routeCompute.offlineRouteCompute(
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
     * Computes a route from Eurovea to Kuchajda and checks that route duration is less than
     * 5400 seconds (1h 30min) but longer than 1200s (20min).
     * The meaning of this test is to ensure that the pedestrian duration is not calculated
     * in the same way as a car and also is not too high.
     */
    @Test
    fun testPedestrianRouteDurationLongerThanCar() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("sk")

        val start = GeoCoordinates(48.14119, 17.12485)
        val destination = GeoCoordinates(48.16873, 17.14387)
        val routeCompute = RouteComputeHelper()

        val route = routeCompute.offlineRouteCompute(
            start,
            destination,
            routingOptions = RoutingOptions().apply {
                setPedestrian()
            }
        )

        val pedestrianDuration = route.routeInfo.waypointDurations.last().ideal
        val carDuration = 1200

        assertTrue(
            "Pedestrian route duration is not as expected",
            pedestrianDuration in (carDuration + 1)..5400
        )
    }

    private fun createDefaultTruckRoutingOptions(): RoutingOptions {
        return RoutingOptions().apply {
            vehicleProfile = routeComputeHelper.createCombustionVehicleProfile().apply {
                generalVehicleTraits.vehicleType = VehicleType.Truck
            }
            useEndpointProtection = true
            napStrategy = NearestAccessiblePointStrategy.Disabled
        }
    }

    @Test
    fun testSpiderRangeWeightFactorsDifference(): Unit = runBlocking {
        disableOnlineMaps()
        MapDownloadHelper().installAndLoadMap("sk")

        val listener: EVRangeListener = mock(verboseLogging = true)

        val vehicleProfile =
            RouteComputeHelper().createDefaultElectricVehicleProfile(5F, 5F).apply {
                dimensionalTraits = DimensionalTraits().apply {
                    totalWeight = 1000F
                }
            }

        RouterProvider.getInstance().get().computeEVRange(
            GeoCoordinates(48.10095535808773, 17.234824479529344),
            listOf(5.0),
            RoutingOptions().apply {
                this.vehicleProfile = vehicleProfile
                this.routingService = RoutingOptions.RoutingService.Offline
            },
            listener
        )

        val captor = argumentCaptor<List<List<GeoCoordinates>>>()
        verify(listener, timeout(10_000L)).onEVRangeComputed(captor.capture())
        val isochrones1 = captor.firstValue[0]

        val listener2: EVRangeListener = mock(verboseLogging = true)

        RouterProvider.getInstance().get().computeEVRange(
            GeoCoordinates(48.10095535808773, 17.234824479529344),
            listOf(5.0),
            RoutingOptions().apply {
                this.vehicleProfile = vehicleProfile.apply {
                    dimensionalTraits = DimensionalTraits().apply {
                        totalWeight = 5000F
                    }
                }
                this.routingService = RoutingOptions.RoutingService.Offline
            },
            listener2
        )

        val captor2 = argumentCaptor<List<List<GeoCoordinates>>>()
        verify(listener2, timeout(10_000L)).onEVRangeComputed(captor2.capture())
        val isochrones2 = captor2.firstValue[0]

        // Assert that isochrones1 and isochrones2 are different
        assertNotEquals(isochrones1.size, isochrones2.size)

        val areDifferent = isochrones1.zip(isochrones2).any { (coord1, coord2) ->
            coord1.latitude != coord2.latitude || coord1.longitude != coord2.longitude
        }

        assertTrue("Isochrones should be different, but they appear identical.", areDifferent)
    }

    /**
     * https://jira.sygic.com/browse/SDC-14224
     * TC892
     * In this test we check that route doesn't lead through tunnel cat. E by following way:
     * restricted route and normal route shouldn't have the same length and difference should be > 1 km
     */
    @Test
    fun tunnelCategoryERouteLengthDifferenceTest() {
        mapDownloadHelper.installAndLoadMap("gb-03")

        val start = GeoCoordinates(51.49906, -0.05638)
        val destination = GeoCoordinates(51.51246, -0.04123)

        val normalRoute = routeComputeHelper.offlineRouteCompute(
            start,
            destination
        )
        val lengthNormal = normalRoute.routeInfo.length

        val restrictedRoute = routeComputeHelper.offlineRouteCompute(
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
    fun tunnelCategoryCTest() {
        mapDownloadHelper.installAndLoadMap("gb-03")

        val start = GeoCoordinates(51.45571, 0.23953)
        val destination = GeoCoordinates(51.48845, 0.26980)
        val routingOptions = RoutingOptions().apply {
            vehicleProfile = routeComputeHelper.createCombustionVehicleProfile().apply {
                generalVehicleTraits.vehicleType = VehicleType.Truck
                hazmatTraits = HazmatTraits(emptySet(), TunnelCategory.C)
            }
            useEndpointProtection = true
            napStrategy = NearestAccessiblePointStrategy.Disabled
        }

        val boundingBox = GeoBoundingBox(
            topLeft = GeoCoordinates(51.45313, 0.24168),
            bottomRight = GeoCoordinates(51.45145, 0.24507)
        )

        val route = routeComputeHelper.offlineRouteCompute(
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

    /**
     * https://jira.sygic.com/browse/SDC-13864
     */
    @Test
    fun shortBusRoutingTest() {
        disableOnlineMaps()
        MapDownloadHelper().installAndLoadMap("sk")

        val routingOptions = RoutingOptions().apply {
            vehicleProfile = routeComputeHelper.createCombustionVehicleProfile().apply {
                generalVehicleTraits.vehicleType = VehicleType.Bus
                generalVehicleTraits.maximalSpeed = 90
            }
        }

        val start = GeoCoordinates(48.1237, 17.1991)
        val destination = GeoCoordinates(48.1021, 17.2321)

        val route = routeComputeHelper.offlineRouteCompute(
            start,
            destination,
            routingOptions = routingOptions
        )
        val actualLength = route.routeInfo.length
        assertTrue(
            "Expected route length < 4000, but was $actualLength",
            route.routeInfo.length < 4000
        )
    }

    /**
     * https://jira.sygic.com/browse/SDC-14656
     * TC895
     * In this test we check that route length is < 80 km (because leads through shortest mountain road)
     */
    @Test
    fun fuzzyDomainFranceTest() {
        disableOnlineMaps()
        MapDownloadHelper().installAndLoadMap("fr-06")

        val start = GeoCoordinates(45.822810, 6.533240)
        val destination = GeoCoordinates(45.594250, 6.880690)

        val route = routeComputeHelper.offlineRouteCompute(
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
            "Expected route length < 80 km, but was $actualLength",
            route.routeInfo.length < 80000
        )
    }

    /**
     * https://jira.sygic.com/browse/SN-35601
     * TC899
     * In this test we check that route length is < 30 km
     * (because leads through shortest mountain road)
     */
    @Test
    fun fuzzyDomainSloveniaTest() {
        disableOnlineMaps()
        MapDownloadHelper().installAndLoadMap("si")

        val start = GeoCoordinates(46.484720, 13.782650)
        val destination = GeoCoordinates(46.357730, 13.702640)

        val route = routeComputeHelper.offlineRouteCompute(
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
            "Expected route length < 30 km, but was $actualLength",
            route.routeInfo.length < 30000
        )
    }

    @Test
    fun arriveInDirectionTest() {
        mapDownloadHelper.installAndLoadMap("sk")

        val start = GeoCoordinates(48.14689, 17.22613)
        val destination = GeoCoordinates(48.13879, 17.27926)

        val normalRoute = routeComputeHelper.offlineRouteCompute(
            start,
            destination,
            routingOptions = RoutingOptions().apply {
                useEndpointProtection = true
                napStrategy = NearestAccessiblePointStrategy.Disabled
                arriveInDrivingSide = false
            }
        )
        val lengthWithoutArriveInDirection = normalRoute.routeInfo.length

        val arriveInDirectionRoute = routeComputeHelper.offlineRouteCompute(
            start,
            destination,
            routingOptions = RoutingOptions().apply {
                useEndpointProtection = true
                napStrategy = NearestAccessiblePointStrategy.Disabled
                arriveInDrivingSide = true
            }
        )
        val lengthWithArriveInDirection = arriveInDirectionRoute.routeInfo.length

        val difference = lengthWithArriveInDirection - lengthWithoutArriveInDirection

        assertTrue(
            "Expected significant difference (> 1000 m) due to arrive in direction, but got $difference meters",
            difference > 1_000
        )
    }


    /***
     * https://eurowag-cloud.atlassian.net/browse/NE-190
     * The ferry doesn't operate between 10pm-06am and therefore the route computed at this time
     * should be MUCH longer.
     */
    @Test
    @Ignore("is not fixed, yet")
    fun ferryComputedInTimeDifference() {
        mapDownloadHelper.installAndLoadMap("sk")
        mapDownloadHelper.installAndLoadMap("at")
        val start = GeoCoordinates(48.38222326230946, 16.838396718192747)
        val destination = GeoCoordinates(48.38053985562736, 16.828783604161984)
        val routeCompute = RouteComputeHelper()
        val routingOptions = RoutingOptions().apply {
            this.departureTime = Date(1755213023) // 2025-08-15 01:10:23
        }

        val routeAtNight = routeCompute.offlineRouteCompute(
            start,
            destination,
            routingOptions = routingOptions
        )
        val timeToEndAtNight = routeAtNight.routeInfo.waypointDurations.last().withSpeedProfiles

        routingOptions.apply {
            this.departureTime = Date(1755178839) // 2025-08-14 15:40:39
        }

        val routeDay = routeCompute.offlineRouteCompute(
            start,
            destination,
            routingOptions = routingOptions
        )
        val timeToEndDay = routeDay.routeInfo.waypointDurations.last().withSpeedProfiles

        assertTrue(
            "day: $timeToEndDay, night: $timeToEndAtNight",
            timeToEndDay < timeToEndAtNight
        )
    }

    private suspend fun getRouteRequest(path: String): RouteRequest =
        suspendCoroutine { continuation ->
            RouterProvider.getInstance().get().createRouteRequestFromJSONString(
                readJson(path),
                object : RouteRequestDeserializedListener {
                    override fun onError(error: RouteDeserializerError) {
                        Assert.fail("Deserialization error: $error")
                    }

                    override fun onSuccess(routeRequest: RouteRequest) {
                        continuation.resume(routeRequest)
                    }
                }
            )
        }
}