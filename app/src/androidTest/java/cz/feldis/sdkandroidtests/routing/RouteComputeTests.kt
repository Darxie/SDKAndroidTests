package cz.feldis.sdkandroidtests.routing

import com.nhaarman.mockitokotlin2.*
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.route.*
import com.sygic.sdk.route.RoutingOptions.NearestAccessiblePointStrategy
import com.sygic.sdk.route.RoutingOptions.TransportMode
import com.sygic.sdk.route.RoutingOptions.VehicleRestrictions
import com.sygic.sdk.route.listeners.*
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.mapInstaller.MapDownloadHelper
import junit.framework.Assert.assertNotNull
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.mockito.Mockito
import timber.log.Timber

class RouteComputeTests : BaseTest() {
    private lateinit var mapDownloadHelper: MapDownloadHelper

    override fun setUp() {
        super.setUp()
        mapDownloadHelper = MapDownloadHelper()
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
    @Ignore("Selection outside of map")
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
            argThat { this == Router.RouteComputeStatus.WrongFromPoint }
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
    @Ignore("returns FR, lol - SDC-8884")
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
}