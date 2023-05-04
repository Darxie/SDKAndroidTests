package cz.feldis.sdkandroidtests.routing

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.timeout
import com.nhaarman.mockitokotlin2.verify
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.route.*
import com.sygic.sdk.route.listeners.RouteComputeFinishedListener
import com.sygic.sdk.route.listeners.RouteComputeListener
import cz.feldis.sdkandroidtests.BaseTest
import org.mockito.ArgumentCaptor

class RouteComputeHelper : BaseTest() {
    private val mRouter = RouterProvider.getInstance().get()

    fun onlineComputeRoute(
        start: GeoCoordinates,
        destination: GeoCoordinates,
        waypoint: GeoCoordinates? = null
    ): Route {

        val request = RouteRequest().apply {
            this.setStart(start)
            this.setDestination(destination)
            waypoint?.let { this.addViaPoint(it) }
            routingOptions.routingService = RoutingOptions.RoutingService.Online
        }

        val listener: RouteComputeListener = mock(verboseLogging = true)
        val routeComputeFinishedListener: RouteComputeFinishedListener = mock(verboseLogging = true)
        val primaryRouteRequest = PrimaryRouteRequest(request, listener)

        val captor: ArgumentCaptor<Route> = ArgumentCaptor.forClass(Route::class.java)

        mRouter.computeRouteWithAlternatives(
            primaryRouteRequest,
            null,
            routeComputeFinishedListener
        )
        verify(listener, timeout(10_000L)).onComputeFinished(
            captor.capture(), argThat { this == Router.RouteComputeStatus.Success }
        )

        return captor.value
    }

    fun offlineRouteCompute(
        start: GeoCoordinates,
        destination: GeoCoordinates,
        waypoint: GeoCoordinates? = null,
        routingOptions: RoutingOptions = RoutingOptions()
    ): Route {
        val request = RouteRequest().apply {
            this.setStart(start)
            this.setDestination(destination)
            waypoint?.let { this.addViaPoint(it) }
            this.routingOptions = routingOptions
            this.routingOptions.routingService = RoutingOptions.RoutingService.Offline
        }
        val listener: RouteComputeListener = mock(verboseLogging = true)
        val routeComputeFinishedListener: RouteComputeFinishedListener = mock(verboseLogging = true)
        val primaryRouteRequest = PrimaryRouteRequest(request, listener)

        val captor: ArgumentCaptor<Route> = ArgumentCaptor.forClass(Route::class.java)

        mRouter.computeRouteWithAlternatives(
            primaryRouteRequest,
            null,
            routeComputeFinishedListener
        )
        verify(listener, timeout(10_000L)).onComputeFinished(
            captor.capture(), argThat { this == Router.RouteComputeStatus.Success || this == Router.RouteComputeStatus.SuccessWithWarnings }
        )
        verify(listener, never()).onComputeFinished(eq(null), any())

        return captor.value
    }
}