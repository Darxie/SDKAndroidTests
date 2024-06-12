package cz.feldis.sdkandroidtests.routing

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.timeout
import com.nhaarman.mockitokotlin2.verify
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.route.PrimaryRouteRequest
import com.sygic.sdk.route.Route
import com.sygic.sdk.route.RouteRequest
import com.sygic.sdk.route.Router
import com.sygic.sdk.route.RouterProvider
import com.sygic.sdk.route.RoutingOptions
import com.sygic.sdk.route.listeners.RouteComputeFinishedListener
import com.sygic.sdk.route.listeners.RouteComputeListener
import com.sygic.sdk.utils.EnforceableAttribute
import com.sygic.sdk.vehicletraits.VehicleProfile
import com.sygic.sdk.vehicletraits.general.GeneralVehicleTraits
import com.sygic.sdk.vehicletraits.general.SpecializedVehicleAttributes
import com.sygic.sdk.vehicletraits.general.VehicleType
import com.sygic.sdk.vehicletraits.powertrain.Battery
import com.sygic.sdk.vehicletraits.powertrain.ChargingCurrent
import com.sygic.sdk.vehicletraits.powertrain.ChargingPreferences
import com.sygic.sdk.vehicletraits.powertrain.Connector
import com.sygic.sdk.vehicletraits.powertrain.ConnectorType
import com.sygic.sdk.vehicletraits.powertrain.ConsumptionData
import com.sygic.sdk.vehicletraits.powertrain.EuropeanEmissionStandard
import com.sygic.sdk.vehicletraits.powertrain.FuelType
import com.sygic.sdk.vehicletraits.powertrain.PowerRange
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
        verify(listener, timeout(30_000L)).onComputeFinished(
            captor.capture(), argThat { this == Router.RouteComputeStatus.Success || this == Router.RouteComputeStatus.SuccessWithWarnings }
        )
        verify(listener, never()).onComputeFinished(eq(null), any())

        return captor.value
    }

    fun createCombustionVehicleProfile(): VehicleProfile {
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

    fun createDefaultElectricVehicleProfile(batteryCapacity: Float, remainingCapacity: Float): VehicleProfile {
        val battery = Battery(
            capacity = batteryCapacity,
            remainingCapacity = remainingCapacity,
            mapOf()
        )
        val connectors = listOf(
            Connector(100F, ConnectorType.Type2Any, ChargingCurrent.AC),
            Connector(100F, ConnectorType.Type2Any, ChargingCurrent.DC)
        )
        val chargingPreferences = ChargingPreferences(
            fullChargeThreshold = 0.8F,
            chargingThreshold = 0.2F,
            reserveThreshold = 0.05F,
            batteryMinimumDestinationThreshold = 0.3F,
            powerRange = EnforceableAttribute(PowerRange(500F, 600F), true)
        )
        val consumptionData = ConsumptionData(
            consumptionCurve = mapOf(1.0 to 1.0, 100.0 to 1.0),
            weightFactors = mapOf(1000.0 to 0.5, 5000.0 to 1.0, 10000.0 to 1.0)
        )

        return VehicleProfile().apply {
            generalVehicleTraits.vehicleType = VehicleType.Car
            powertrainTraits = PowertrainTraits.ElectricPowertrain(battery, connectors, chargingPreferences, consumptionData)
        }
    }

    fun createElectricVehicleProfileForPreferenceViolation(batteryCapacity: Float, remainingCapacity: Float): VehicleProfile {
        val battery = Battery(
            capacity = batteryCapacity,
            remainingCapacity = remainingCapacity,
            mapOf()
        )
        val connectors = listOf(
            Connector(100F, ConnectorType.Type2Any, ChargingCurrent.AC),
            Connector(100F, ConnectorType.Type2Any, ChargingCurrent.DC)
        )
        val chargingPreferences = ChargingPreferences(
            fullChargeThreshold = 0.9F,
            chargingThreshold = 0.8F,
            reserveThreshold = 0.05F,
            batteryMinimumDestinationThreshold = 0.3F,
            powerRange = EnforceableAttribute(PowerRange(999F, 1000F), false)
        )
        val consumptionData = ConsumptionData(
            consumptionCurve = mapOf(1.0 to 1.0, 100.0 to 1.0),
            weightFactors = mapOf(1000.0 to 0.5, 5000.0 to 1.0, 10000.0 to 1.0)
        )

        return VehicleProfile().apply {
            generalVehicleTraits.vehicleType = VehicleType.Car
            powertrainTraits = PowertrainTraits.ElectricPowertrain(battery, connectors, chargingPreferences, consumptionData)
        }
    }

    fun createElectricVehicleProfileTruck(batteryCapacity: Float = 350f, remainingCapacity: Float = 100f): VehicleProfile {
        val battery = Battery(
            capacity = batteryCapacity,
            remainingCapacity = remainingCapacity,
            mapOf()
        )
        val connectors = listOf(
            Connector(500f, ConnectorType.Type2Any, ChargingCurrent.AC),
            Connector(500f, ConnectorType.Type2Any, ChargingCurrent.DC),
            Connector(500f, ConnectorType.Ccs2, ChargingCurrent.AC),
            Connector(500f, ConnectorType.Ccs2, ChargingCurrent.DC),
        )
        val chargingPreferences = ChargingPreferences(
            fullChargeThreshold = 0.9F,
            chargingThreshold = 0.2F,
            reserveThreshold = 0.05F,
            batteryMinimumDestinationThreshold = 0.3F,
            powerRange = EnforceableAttribute(PowerRange(100F, 600F), false)
        )
        val consumptionData = ConsumptionData(
            consumptionCurve = mapOf(1.0 to 1.0, 100.0 to 1.0),
            weightFactors = mapOf(1000.0 to 0.5, 5000.0 to 1.0, 10000.0 to 1.0)
        )

        return VehicleProfile().apply {
            generalVehicleTraits.vehicleType = VehicleType.Truck
            powertrainTraits = PowertrainTraits.ElectricPowertrain(battery, connectors, chargingPreferences, consumptionData)
        }
    }
}