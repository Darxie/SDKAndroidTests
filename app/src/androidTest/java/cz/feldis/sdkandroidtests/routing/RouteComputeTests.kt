package cz.feldis.sdkandroidtests.routing

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.isNotNull
import com.nhaarman.mockitokotlin2.isNull
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.timeout
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.withSettings
import com.sygic.sdk.places.EVConnector
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.route.BatteryProfile
import com.sygic.sdk.route.ChargingWaypoint
import com.sygic.sdk.route.EVProfile
import com.sygic.sdk.route.GuidedRouteProfile
import com.sygic.sdk.route.PrimaryRouteRequest
import com.sygic.sdk.route.Route
import com.sygic.sdk.route.RouteDeserializerError
import com.sygic.sdk.route.RouteRequest
import com.sygic.sdk.route.RouteWarning
import com.sygic.sdk.route.Router
import com.sygic.sdk.route.RouterProvider
import com.sygic.sdk.route.RoutingOptions
import com.sygic.sdk.route.RoutingOptions.NearestAccessiblePointStrategy
import com.sygic.sdk.route.RoutingOptions.RoutingType
import com.sygic.sdk.route.RoutingOptions.TransportMode
import com.sygic.sdk.route.RoutingOptions.TransportMode.Pedestrian
import com.sygic.sdk.route.RoutingOptions.VehicleRestrictions
import com.sygic.sdk.route.TransitCountryInfo
import com.sygic.sdk.route.Waypoint
import com.sygic.sdk.route.listeners.GeometryListener
import com.sygic.sdk.route.listeners.RouteComputeFinishedListener
import com.sygic.sdk.route.listeners.RouteComputeListener
import com.sygic.sdk.route.listeners.RouteDurationListener
import com.sygic.sdk.route.listeners.RouteElementsListener
import com.sygic.sdk.route.listeners.RouteRequestDeserializedListener
import com.sygic.sdk.route.listeners.RouteWarningsListener
import com.sygic.sdk.route.listeners.TransitCountriesInfoListener
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.mapInstaller.MapDownloadHelper
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
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class RouteComputeTests : BaseTest() {
    private lateinit var mapDownloadHelper: MapDownloadHelper

    override fun setUp() {
        super.setUp()
        mapDownloadHelper = MapDownloadHelper()
    }

    private fun newEvProfile(remainingCapacity: Float): EVProfile {
        return EVProfile(
            batteryProfile = BatteryProfile(
                batteryCapacity = 30f,
                remainingCapacity = remainingCapacity,
                batteryChargingThreshold = .2f,
                batteryFullChargeThreshold = .8f,
                batteryMinimumReserveThreshold = .05f
            ),
            chargingMaxPower = 100,
            connector = setOf(EVConnector.ConnectorType.Type2_any),
            power = setOf(EVConnector.PowerType.DC, EVConnector.PowerType.AC),
            weight = 1600.0,
            frontalArea = 2.5,
            coefAD = .25,
            coefRR = .015,
            nee1 = .85,
            nee2 = .8,
            Ka = .28,
            V1 = -1.0,
            Kv1 = -1.0,
            V2 = -1.0,
            Kv2 = -1.0
        )
    }

    @Ignore("Does not work")
    @Test
    fun computeNextDurationsTestOnline() {
        val listener: RouteDurationListener = mock(verboseLogging = true)
        val routeCompute = RouteComputeHelper()
        val router = RouterProvider.getInstance().get()

        val start = GeoCoordinates(48.145718, 17.118669)
        val destination = GeoCoordinates(48.190322, 16.401080)
        val route = routeCompute.onlineComputeRoute(start, destination)

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
    fun computeNextDurationsTestOffline() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("sk")
        mapDownloadHelper.installAndLoadMap("at")
        val listener: RouteDurationListener = mock(verboseLogging = true)
        val routeCompute = RouteComputeHelper()
        val router = RouterProvider.getInstance().get()

        val start = GeoCoordinates(48.145718, 17.118669)
        val destination = GeoCoordinates(48.190322, 16.401080)
        val route = routeCompute.offlineRouteCompute(start, destination)

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
        val routeCompute = RouteComputeHelper()

        val start = GeoCoordinates(63.556092, -19.794962)
        val destination = GeoCoordinates(63.420816, -19.001375)
        val route = routeCompute.onlineComputeRoute(start, destination)

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
    fun getRouteElementsIcelandOffline() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("is")

        val elementsListener: RouteElementsListener = mock(verboseLogging = true)

        val start = GeoCoordinates(63.556092, -19.794962)
        val destination = GeoCoordinates(63.420816, -19.001375)
        val routeCompute = RouteComputeHelper()

        val route = routeCompute.offlineRouteCompute(
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
        mapDownloadHelper.uninstallMap("is")
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
            transportMode = TransportMode.Car
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
            argThat { this == Router.RouteComputeStatus.Success }
        )
        verify(listener, never()).onComputeFinished(
            isNull(),
            argThat { this != Router.RouteComputeStatus.Success }
        )
    }

    @Test
    fun computeReykjavikToVikOffline() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("is")
        val start = GeoCoordinates(64.114341, -21.871153)
        val destination = GeoCoordinates(63.417836, -19.002209)
        val routeCompute = RouteComputeHelper()
        val routingOptions = RoutingOptions().apply {
            transportMode = TransportMode.Car
        }

        val route = routeCompute.offlineRouteCompute(
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
        val routeCompute = RouteComputeHelper()
        val routingOptions = RoutingOptions().apply {
            transportMode = TransportMode.Car
        }

        val route = routeCompute.offlineRouteCompute(
            start,
            destination,
            routingOptions = routingOptions
        )
        route.getRouteGeometry(true, listener)
        verify(listener, timeout(5_000L)).onGeometry(argThat {
            for (geoCoordinates in this) {
                assertFalse(geoCoordinates.altitude < 0.0 && geoCoordinates.altitude > 9000.0)
            }
            return@argThat true
        })
    }

    @Test
    fun routePlanFromJSONOnline() {
        val start = GeoCoordinates(48.145718, 17.118669)
        val destination = GeoCoordinates(48.190322, 16.401080)
        val routeCompute = RouteComputeHelper()
        val originalRoute =
            routeCompute.onlineComputeRoute(start, destination)
        val listener: RouteRequestDeserializedListener = mock(verboseLogging = true)

        val routeJson = originalRoute.serializeToBriefJSON()

        RouteRequest.createRouteRequestFromJSONString(json = routeJson, listener)
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
        val start = GeoCoordinates(50.062084, 14.432741)
        val destination = GeoCoordinates(48.144826, 17.100258)

        val listener: RouteComputeListener = mock(verboseLogging = true)
        val routeComputeFinishedListener: RouteComputeFinishedListener = mock(verboseLogging = true)

        val options = RoutingOptions()
        options.apply {
            isUnpavedRoadAvoided = true
            transportMode = TransportMode.TransportTruck
            routingService = RoutingOptions.RoutingService.Online
            this.addDimensionalRestriction(VehicleRestrictions.TotalWeight, 50000)
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
            isUnpavedRoadAvoided = true
            transportMode = TransportMode.Car
            routingService = RoutingOptions.RoutingService.Offline

            napStrategy = NearestAccessiblePointStrategy.Disabled
            setUseSpeedProfiles(true)
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
    @Ignore("Returns Unreachable target")
    fun computeWrongFromPointOnline() {
        val start = GeoCoordinates(34.764518085578196, 18.03834181295307)
        val destination = GeoCoordinates(47.99919432978094, 18.164403416068332)
        val listener: RouteComputeListener = mock(verboseLogging = true)
        val routeComputeFinishedListener: RouteComputeFinishedListener = mock(verboseLogging = true)

        val options = RoutingOptions()
        options.apply {
            routingService = RoutingOptions.RoutingService.Online
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
    @Ignore("Crashes - SDC-8559")
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
            null,
            eq(Router.RouteComputeStatus.UnspecifiedFault)
        )
    }

    @Test
    fun camperAndTruckETADifferentComparison() {
        mapDownloadHelper.installAndLoadMap("sk")
        val start = GeoCoordinates(48.13116130573944, 17.11782382599132)
        val destination = GeoCoordinates(49.05314733520812, 18.325403607220828)
        val routeCompute = RouteComputeHelper()
        val routingOptions = RoutingOptions().apply {
            transportMode = TransportMode.TransportTruck
            setMaxspeed(90)
        }

        val routeTruck = routeCompute.offlineRouteCompute(
            start,
            destination,
            routingOptions = routingOptions
        )
        val timeToEndTruck = routeTruck.routeInfo.waypointDurations.last().withSpeedProfiles

        routingOptions.apply {
            transportMode = TransportMode.Camper
            setMaxspeed(130)
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
    fun onlineRoutingGetLastManeuverCountry() {
        val start = GeoCoordinates(48.13204503419638, 17.09786238379282)
        val destination = GeoCoordinates(51.491340, -0.102940)
        val routeCompute = RouteComputeHelper()

        val route = routeCompute.onlineComputeRoute(
            start,
            destination
        )

        val maneuvers = route.maneuvers
        assertEquals("gb", maneuvers.last().toIso)
    }

    @Test
    fun countriesInfoOrderAtSkHu() {
        mapDownloadHelper.installAndLoadMap("at")
        mapDownloadHelper.installAndLoadMap("sk")
        mapDownloadHelper.installAndLoadMap("hu")
        val start = GeoCoordinates(48.133521125857136, 16.904835360462567)
        val waypoint = GeoCoordinates(48.12917385634974, 17.19439161086379)
        val destination = GeoCoordinates(47.71987275813502, 17.653115614211107)

        val routeCompute = RouteComputeHelper()

        val route = routeCompute.offlineRouteCompute(
            start,
            destination,
            waypoint = waypoint
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
            waypoint = waypoint
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
    fun changeWeightAtWaypoint() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("sk")

        val start = GeoCoordinates(48.19159435449465, 17.223366296005075)
        val waypoint = GeoCoordinates(48.19136102846576, 17.226362460836786)
        val destination = GeoCoordinates(48.19159237577265, 17.22143784412721)
        val routingOptions = RoutingOptions().apply {
            addDimensionalRestriction(VehicleRestrictions.TotalWeight, 50000)
            transportMode = TransportMode.TransportTruck
            napStrategy = NearestAccessiblePointStrategy.Disabled
            useEndpointProtection()
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
            addDimensionalRestriction(VehicleRestrictions.TotalWeight, 50000)
            transportMode = TransportMode.TransportTruck
            napStrategy = NearestAccessiblePointStrategy.Disabled
            useEndpointProtection()
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
        verify(listener, timeout(10_000L)).onComputeFinished(
            captor.capture(), eq(Router.RouteComputeStatus.SuccessWithWarnings)
        )

        val route = captor.firstValue

        route.getRouteWarnings(routeWarningsListener)
        verify(routeWarningsListener, timeout(10_000L)).onRouteWarnings(captorWarnings.capture())

        assert(captorWarnings.firstValue.size > 1)
    }

    @Test
    fun preferenceViolationWarningEVTest() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("sk")
        val routeWarningsListener: RouteWarningsListener = mock(verboseLogging = true)
        val evProfile = newEvProfile(20f)
        val routeCompute = RouteComputeHelper()
        val evPreferences = routeCompute.newEVPreferencesHighChargeRange()
        val start = GeoCoordinates(48.14548507020328, 17.126529723864405)
        val destination = GeoCoordinates(49.00162457306762, 22.157874201012863)

        val route = routeCompute.evRouteCompute(
            start,
            destination,
            evProfile = evProfile,
            evPreferences = evPreferences
        )

        route.getRouteWarnings(routeWarningsListener)
        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(argThat {
            this.find { it is RouteWarning.LocationWarning.EVPreferenceViolation } != null
        })
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
        val start = GeoCoordinates(48.10180335214629, 17.233888233640172)
        val destination = GeoCoordinates(48.145628357674845, 17.12695279400835)

        val ecoRoutingOptions = RoutingOptions().apply {
            consumptionData.consumptionCurve = consumptionCurve
            routingType = RoutingType.Economic
        }

        val routeCompute = RouteComputeHelper()

        val ecoRoute = routeCompute.offlineRouteCompute(
            start,
            destination,
            routingOptions = ecoRoutingOptions
        )

        val fastestRoutingOptions = RoutingOptions().apply {
            routingType = RoutingType.Fastest
        }

        val fastestRoute = routeCompute.offlineRouteCompute(
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
        val routeCompute = RouteComputeHelper()

        val evProfile = routeCompute.createEVProfile()

        val evPreferences = routeCompute.newEVPreferencesTruck()
        val start = GeoCoordinates(48.24135577878832, 16.99083981234057)
        val destination = GeoCoordinates(49.06008227080942, 20.315811448409608)

        val options = RoutingOptions().apply {
            transportMode = TransportMode.TransportTruck
            setUseEndpointProtection(true)
            napStrategy = NearestAccessiblePointStrategy.Disabled
        }

        val route = routeCompute.evRouteCompute(
            start,
            destination,
            evProfile = evProfile,
            evPreferences = evPreferences,
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
        val routeCompute = RouteComputeHelper()

        val route = routeCompute.offlineRouteCompute(
            start,
            destination
        )
        assertNotNull(route)
    }

    /**
     * Brief test to test if pedestrian routing doesn't fail
     */
    @Test
    fun testPedestrianRouting() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("sk")

        val start = GeoCoordinates(48.139, 17.0761)
        val destination = GeoCoordinates(48.1533, 17.1375)
        val routeCompute = RouteComputeHelper()
        val options = RoutingOptions().apply {
            transportMode = Pedestrian
        }

        val route = routeCompute.offlineRouteCompute(
            start,
            destination,
            routingOptions = options
        )
        assertNotNull(route)
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
            transportMode = TransportMode.Car
            isTollRoadAvoided = true
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

        assertEquals(7, route.maneuvers.size) // 7 maneuvers in March 2023 maps
        for (maneuver in route.maneuvers) {
            assertFalse(maneuver.roadName == "Thomas-Mann-Straße")
        }
    }

    @Test
    fun googlePolylineParsingTestSlovakia() {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("sk")

        val polyline = "cfpdHgxmgBtBFDaFB_@@O?UCY?q@J}ID}BJS?MG[KI?UTcGVY`@g@^]pIkDfAi@JkD"
        val decodedPath: List<LatLng> = PolyUtil.decode(polyline)
        val decodedPolyline = decodedPath.map { GeoCoordinates(it.latitude, it.longitude) }

        val listener: RouteComputeListener = mock(verboseLogging = true)
        val routeComputeFinishedListener: RouteComputeFinishedListener = mock(verboseLogging = true)

        val guidedRouteProfile = GuidedRouteProfile(decodedPolyline)
        val routeRequest = RouteRequest(guidedRouteProfile)

        val primaryRouteRequest = PrimaryRouteRequest(routeRequest, listener)
        val router = RouterProvider.getInstance().get()

        val captor = argumentCaptor<Route>()

        router.computeRouteWithAlternatives(primaryRouteRequest, null, routeComputeFinishedListener)
        verify(listener, timeout(10_000L)).onComputeFinished(
            captor.capture(),
            eq(Router.RouteComputeStatus.Success)
        )
        val route = captor.firstValue
        assertNotNull(route)

        assertEquals(8, route.maneuvers[0].type)
        assertEquals(8, route.maneuvers[1].type)
        assertEquals(19, route.maneuvers[2].type)
        assertEquals(2, route.maneuvers[2].roundaboutExit)
        assertEquals(19, route.maneuvers[3].type)
        assertEquals(2, route.maneuvers[2].roundaboutExit)
        assertEquals(17, route.maneuvers[4].type)
        assertEquals(2, route.maneuvers[2].roundaboutExit)
        assertEquals(8, route.maneuvers[5].type)
        assertEquals(4, route.maneuvers[6].type)
    }

    private suspend fun getRouteRequest(path: String): RouteRequest =
        suspendCoroutine { continuation ->
            RouteRequest.createRouteRequestFromJSONString(
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