package cz.feldis.sdkandroidtests.routing

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.timeout
import com.nhaarman.mockitokotlin2.verify
import com.sygic.sdk.places.EVConnector
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.route.BatteryProfile
import com.sygic.sdk.route.EVPreferences
import com.sygic.sdk.route.EVProfile
import com.sygic.sdk.route.PrimaryRouteRequest
import com.sygic.sdk.route.Route
import com.sygic.sdk.route.RouteRequest
import com.sygic.sdk.route.Router
import com.sygic.sdk.route.RouterProvider
import com.sygic.sdk.route.RoutingOptions
import com.sygic.sdk.route.listeners.RouteComputeFinishedListener
import com.sygic.sdk.route.listeners.RouteComputeListener
import cz.feldis.sdkandroidtests.BaseTest
import org.mockito.ArgumentCaptor

class RouteComputeHelper : BaseTest() {
    private val mRouter = RouterProvider.getInstance().get()

    fun onlineComputeRoute(
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
            this.routingOptions.routingService = RoutingOptions.RoutingService.Online
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

    fun evRouteCompute(
        start: GeoCoordinates,
        destination: GeoCoordinates,
        waypoint: GeoCoordinates? = null,
        routingOptions: RoutingOptions = RoutingOptions(),
        evProfile: EVProfile,
        evPreferences: EVPreferences
    ): Route {
        val request = RouteRequest(evProfile, evPreferences).apply {
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
        verify(listener, timeout(60_000L)).onComputeFinished(
            captor.capture(), argThat { this == Router.RouteComputeStatus.Success || this == Router.RouteComputeStatus.SuccessWithWarnings }
        )
        verify(listener, never()).onComputeFinished(eq(null), any())

        return captor.value
    }

    fun newEVPreferencesHighChargeRange(): EVPreferences {
        return EVPreferences(
            chargeRangeLowVal = 500.0,
            chargeRangeUpperVal = 600.0,
            preferredProvider = listOf(),
            chargerPermission = EVPreferences.EVChargerAccessType.Any,
            payType = EVPreferences.EVPayType.Any
        )
    }

    fun newEVPreferencesTruck(): EVPreferences {
        return EVPreferences(
            chargeRangeLowVal = 100.0,
            chargeRangeUpperVal = 600.0,
            preferredProvider = listOf(),
            chargerPermission = EVPreferences.EVChargerAccessType.Any,
            payType = EVPreferences.EVPayType.Any
        )
    }

    fun createEVProfile(): EVProfile {
        val batteryProfile = BatteryProfile(350.0F, 100.0F, 0.2F, 0.9F, 0.05F)
        val connectors = setOf(
            EVConnector.ConnectorType.Ccs2, EVConnector.ConnectorType.Type3,
            EVConnector.ConnectorType.Type2_any, EVConnector.ConnectorType.Ccs1
        )
        val powerTypes = setOf(EVConnector.PowerType.DC, EVConnector.PowerType.AC)
        return EVProfile(batteryProfile, 500, connectors, powerTypes,
            consumptionCurve = mapOf(1.0 to 1.0, 100.0 to 1.0),
            weightFactors = mapOf(1000.0 to 0.5, 5000.0 to 1.0, 10000.0 to 1.0),
            batteryMinimumDestinationThreshold = 0.3
        )
    }
}