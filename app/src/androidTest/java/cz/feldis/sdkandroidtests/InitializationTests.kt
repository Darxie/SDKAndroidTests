package cz.feldis.sdkandroidtests

import androidx.test.filters.FlakyTest
import com.nhaarman.mockitokotlin2.*
import com.sygic.sdk.navigation.NavigationManagerProvider
import com.sygic.sdk.navigation.explorer.RouteExplorer
import com.sygic.sdk.navigation.traffic.TrafficManagerProvider
import com.sygic.sdk.places.PlaceCategories
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.route.*
import com.sygic.sdk.route.listeners.*
import com.sygic.sdk.search.PlaceRequest
import junit.framework.Assert.assertNotNull
import org.junit.Assert
import org.junit.Test
import org.mockito.Mockito
import timber.log.Timber

class InitializationTests : BaseTest() {

    private lateinit var searchHelper: SearchHelper
    private lateinit var mapDownloadHelper: MapDownloadHelper

    override fun setUp() {
        super.setUp()
        searchHelper = SearchHelper()
        mapDownloadHelper = MapDownloadHelper()
    }

    @Test
    fun computeDoPraceZDomu() {
        val start = GeoCoordinates(48.101713, 17.234017)
        val destination = GeoCoordinates(48.145644, 17.127011)
        val listener =
            Mockito.mock(RouteComputeListener::class.java, withSettings().verboseLogging())
        val routeComputeFinishedListener = Mockito.mock(RouteComputeFinishedListener::class.java)
        val options = RoutingOptions()
        options.apply {
            transportMode = RoutingOptions.TransportMode.Car
            routingService = RoutingOptions.RoutingService.Online
            napStrategy = RoutingOptions.NearestAccessiblePointStrategy.ChangeWaypointTargetRoads
//            urlOverride = "https://ptv-routing-testing.api.sygic.com"
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
    }

    @Test
    fun computeNextDurationTest() {

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
    fun getRouteElementsIcelandOnline(){
        val elementsListener : RouteElementsListener = mock(verboseLogging = true)
        val routeCompute = RouteComputeHelper()

        val start = GeoCoordinates(63.556092, -19.794962)
        val destination = GeoCoordinates(63.420816, -19.001375)
        val route = routeCompute.onlineComputeRoute(start, destination)

        route.getRouteElements(elementsListener)
        verify(elementsListener, timeout(40_000L)).onRouteElementsRetrieved(
            argThat {
                if (this.isEmpty()){
                    return@argThat false
                }
                true
            }
        )
    }

    @Test
    fun getRouteElementsIcelandOffline(){
        mapDownloadHelper.installAndLoadMap("is")

        val elementsListener : RouteElementsListener = mock(verboseLogging = true)

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
                if (this.isEmpty()){
                    return@argThat false
                }
                true
            }
        )
        mapDownloadHelper.uninstallMap("is")
    }

    @Test
    fun computeReykjavikToVikOffline() {
        mapDownloadHelper.installAndLoadMap("is")
        val start = GeoCoordinates(64.114341, -21.871153)
        val destination = GeoCoordinates(63.417836, -19.002209)
        val routeCompute = RouteComputeHelper()
        val route = routeCompute.offlineRouteCompute(
            start,
            destination,
            transportMode = RoutingOptions.TransportMode.Car
        )
        Assert.assertNotNull(route)
        mapDownloadHelper.uninstallMap("is")
    }

    @Test
    fun exploreTrafficOnRoute() {
        TrafficManagerProvider.getInstance().get().enableTrafficService()
        val routeCompute = RouteComputeHelper()

        val listener : RouteExplorer.OnExploreTrafficOnRouteListener = mock(verboseLogging = true)

        val route = routeCompute.onlineComputeRoute(
            GeoCoordinates(48.155195, 17.136827),
            GeoCoordinates(48.289024, 17.264717)
        )

        RouteExplorer.exploreTrafficOnRoute(route, listener)

        Mockito.verify(listener, Mockito.timeout(10_000L))
            .onExploreTrafficLoaded(any())

        Mockito.verify(listener, never())
            .onExploreTrafficError(any())
    }

    @Test
    fun routePlanFromJSON() {
        val start = GeoCoordinates(48.145718, 17.118669)
        val destination = GeoCoordinates(48.190322, 16.401080)
        val routeCompute = RouteComputeHelper()
        val originalRoute =
            routeCompute.onlineComputeRoute(start, destination)
        val listener: RouteRequestDeserializedListener = mock(verboseLogging = true)

        val routeJson = originalRoute.serializeToBriefJSON()

        RouteRequest.createRouteRequestFromJSONString(json = routeJson, listener)
        verify(listener, timeout(1000)).onSuccess(
            argThat {
                if (this.start?.originalPosition == start && this.destination?.originalPosition == destination) {
                    return@argThat true
                }
                false
            }
        )
    }

    @Test
    fun testGetRouteProgress() {
        val start = GeoCoordinates(48.101936, 17.233684)
        val destination = GeoCoordinates(48.145644, 17.127011)
        val routeCompute = RouteComputeHelper()
        val route = routeCompute.onlineComputeRoute(start, destination)
        NavigationManagerProvider.getInstance().get().setRouteForNavigation(route)
        assertNotNull(NavigationManagerProvider.getInstance().get().routeProgress)
    }

    @FlakyTest
    @Test
    fun unreachableTargetTruckWeightTest() {
        val start = GeoCoordinates(50.062084, 14.432741)
        val destination = GeoCoordinates(48.144826, 17.100258)

        val listener = Mockito.mock(RouteComputeListener::class.java)
        val routeComputeFinishedListener = Mockito.mock(RouteComputeFinishedListener::class.java)

        val options = RoutingOptions()
        options.apply {
            isUnpavedRoadAvoided = true
            transportMode = RoutingOptions.TransportMode.TransportTruck
            routingService = RoutingOptions.RoutingService.Online
            this.addDimensionalRestriction(RoutingOptions.VehicleRestrictions.TotalWeight, 50000)
            napStrategy = RoutingOptions.NearestAccessiblePointStrategy.ChangeWaypointTargetRoads
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

        verify(listener, Mockito.timeout(5000)).onComputeFinished(
            isNull(),
            argThat { this == Router.RouteComputeStatus.UnreachableTarget }
        )
    }

//    @FlakyTest
//    @Test
//    fun frontEmptyTruckTest() {
//        val start = GeoCoordinates(48.9844, 22.1844)
//        val destination = GeoCoordinates(47.1518, 9.81344)
//
//        installAndLoadMap("at")
//        installAndLoadMap("sk")
//
//        val listener : RouteComputeListener = mock(verboseLogging = true)
//        val routeComputeFinishedListener : RouteComputeFinishedListener = mock(verboseLogging = true)
//        val alternativeListener: RouteComputeListener = mock(verboseLogging = true)
//
//        val options = RoutingOptions()
//        options.apply {
//            isUnpavedRoadAvoided = true
//            transportMode = RoutingOptions.TransportMode.Car
//            routingService = RoutingOptions.RoutingService.Offline
////            this.addDimensionalRestriction(RoutingOptions.VehicleRestrictions.TotalWeight, 44000)
////            this.addDimensionalRestriction(RoutingOptions.VehicleRestrictions.TotalLength, 16500)
////            this.addDimensionalRestriction(RoutingOptions.VehicleRestrictions.Width, 2500)
////            this.addDimensionalRestriction(RoutingOptions.VehicleRestrictions.Height, 4300)
//
////            napStrategy = RoutingOptions.NearestAccessiblePointStrategy.Disabled
//            setUseSpeedProfiles(true)
//        }
//
//
//        val alternativeRouteRequest = AlternativeRouteRequest(
//            AlternativeRouteRequest.RouteAlternativeType.Avoid,
//            alternativeListener
//        )
//
//        val routeRequest = RouteRequest()
//        routeRequest.apply {
//            setStart(start)
//            setDestination(destination)
//            routingOptions = options
//        }
//
//        val primaryRouteRequest = PrimaryRouteRequest(routeRequest, listener)
//
//        val router = RouterProvider.getInstance().get()
//
//        router.computeRouteWithAlternatives(primaryRouteRequest, null, routeComputeFinishedListener)
//
//        verify(listener, Mockito.timeout(50_000L)).onComputeFinished(
//            isNull(),
//            argThat { this == Router.RouteComputeStatus.UnreachableTarget }
//        )
//    }





    @Test
    fun initializeAndDestroy() {
        tearDown()
    }
}