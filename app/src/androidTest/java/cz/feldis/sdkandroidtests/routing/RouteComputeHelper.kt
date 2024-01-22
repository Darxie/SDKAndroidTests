package cz.feldis.sdkandroidtests.routing

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.timeout
import com.nhaarman.mockitokotlin2.verify
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
import com.sygic.sdk.vehicletraits.VehicleProfile
import com.sygic.sdk.vehicletraits.general.GeneralVehicleTraits
import com.sygic.sdk.vehicletraits.general.SpecializedVehicleAttributes
import com.sygic.sdk.vehicletraits.general.VehicleType
import com.sygic.sdk.vehicletraits.powertrain.ChargingCurrent
import com.sygic.sdk.vehicletraits.powertrain.ConnectorType
import com.sygic.sdk.vehicletraits.powertrain.ConsumptionData
import com.sygic.sdk.vehicletraits.powertrain.EuropeanEmissionStandard
import com.sygic.sdk.vehicletraits.powertrain.FuelType
import com.sygic.sdk.vehicletraits.powertrain.PowertrainTraits
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
        val vehicleProfile = VehicleProfile(
            GeneralVehicleTraits(
                maximalSpeed = 255,
                yearOfManufacture = 2009,
                vehicleType = VehicleType.Car,
                SpecializedVehicleAttributes(isTaxi = false, isEmergencyVehicle = false, isHighOccupancyVehicle = false, isCamper = false)
            ),
            null,
            null,
            null
        )
        val routeRequest = RouteRequest().apply {
            this.setStart(start)
            this.setDestination(destination)
            waypoint?.let { this.addViaPoint(it) }
            this.routingOptions = routingOptions
            this.routingOptions.routingService = RoutingOptions.RoutingService.Offline
        }

        val listener: RouteComputeListener = mock(verboseLogging = true)
        val routeComputeFinishedListener: RouteComputeFinishedListener = mock(verboseLogging = true)
        val primaryRouteRequest = PrimaryRouteRequest(routeRequest, listener)

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
            ConnectorType.Ccs2, ConnectorType.Type3,
            ConnectorType.Type2Any, ConnectorType.Ccs1
        )
        val powerTypes = setOf(ChargingCurrent.DC, ChargingCurrent.AC)
        return EVProfile(
            batteryProfile, 500, connectors, powerTypes,
            consumptionCurve = mapOf(1.0 to 1.0, 100.0 to 1.0),
            weightFactors = mapOf(1000.0 to 0.5, 5000.0 to 1.0, 10000.0 to 1.0),
            batteryMinimumDestinationThreshold = 0.3
        )
    }

    fun newDefaultVehicleProfile(): VehicleProfile {
        val internalCombustionPowertrain = PowertrainTraits.InternalCombustionPowertrain(
            fuelType = FuelType.Petrol,
            europeanEmissionStandard = EuropeanEmissionStandard.Euro5,
            consumptionData = ConsumptionData()
        )
        return VehicleProfile(
            generalVehicleTraits = GeneralVehicleTraits(
                255,
                2017,
                VehicleType.Car,
                SpecializedVehicleAttributes(
                    isTaxi = false,
                    isEmergencyVehicle = false,
                    isHighOccupancyVehicle = false,
                    isCamper = false
                )
            ),
            hazmatTraits = null,
            dimensionalTraits = null,
            powertrainTraits = internalCombustionPowertrain
        )
    }
}