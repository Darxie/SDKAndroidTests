package cz.feldis.sdkandroidtests.routing

import com.nhaarman.mockitokotlin2.*
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.route.*
import com.sygic.sdk.route.listeners.*
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.mapInstaller.MapDownloadHelper
import junit.framework.Assert.assertNotNull
import org.junit.Assert
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
        mapDownloadHelper.ensureMapNotInstalled("is")
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
            transportMode = RoutingOptions.TransportMode.Car
            routingService = RoutingOptions.RoutingService.Online
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

        verify(listener, Mockito.timeout(10_000L)).onComputeFinished(
            isNotNull(),
            argThat { this == Router.RouteComputeStatus.Success }
        )
    }

    @Test
    fun computeReykjavikToVikOffline() {
        mapDownloadHelper.ensureMapNotInstalled("is")
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
    fun routePlanFromJSONOnline() {
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

    @Ignore("Setting of total weight does not work")
    @Test
    fun unreachableTargetTruckWeightTestOnline() {
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

    @Ignore("Does not work - crashes")
    @Test
    fun frontEmptyTruckTestOffline() {
        val start = GeoCoordinates(48.9844, 22.1844)
        val destination = GeoCoordinates(47.1518, 9.81344)

        mapDownloadHelper.ensureMapNotInstalled("sk")
        mapDownloadHelper.ensureMapNotInstalled("at")
        mapDownloadHelper.installAndLoadMap("at")
        mapDownloadHelper.installAndLoadMap("sk")

        val listener : RouteComputeListener = mock(verboseLogging = true)
        val routeComputeFinishedListener : RouteComputeFinishedListener = mock(verboseLogging = true)

        val options = RoutingOptions()
        options.apply {
            isUnpavedRoadAvoided = true
            transportMode = RoutingOptions.TransportMode.Car
            routingService = RoutingOptions.RoutingService.Offline
            this.addDimensionalRestriction(RoutingOptions.VehicleRestrictions.TotalWeight, 44000)
            this.addDimensionalRestriction(RoutingOptions.VehicleRestrictions.TotalLength, 16500)
            this.addDimensionalRestriction(RoutingOptions.VehicleRestrictions.Width, 2500)
            this.addDimensionalRestriction(RoutingOptions.VehicleRestrictions.Height, 4300)

            napStrategy = RoutingOptions.NearestAccessiblePointStrategy.Disabled
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
            argThat { this == Router.RouteComputeStatus.UnreachableTarget }
        )
    }
}