package cz.feldis.sdkandroidtests.routing

import android.util.Log
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.route.Route
import com.sygic.sdk.route.RouteRequest
import com.sygic.sdk.route.Router
import com.sygic.sdk.route.RouterProvider
import com.sygic.sdk.route.RoutingOptions
import com.sygic.sdk.route.Waypoint
import com.sygic.sdk.route.results.ComputeRouteWithAlternativesData
import com.sygic.sdk.utils.EnforceableAttribute
import com.sygic.sdk.vehicletraits.VehicleProfile
import com.sygic.sdk.vehicletraits.general.GeneralVehicleTraits
import com.sygic.sdk.vehicletraits.general.SpecializedVehicleAttributes
import com.sygic.sdk.vehicletraits.general.VehicleType
import com.sygic.sdk.vehicletraits.powertrain.Battery
import com.sygic.sdk.vehicletraits.powertrain.ChargingCurrent
import com.sygic.sdk.vehicletraits.powertrain.ChargingPreferences
import com.sygic.sdk.vehicletraits.powertrain.Connector
import com.sygic.sdk.vehicletraits.powertrain.ConnectorFormat
import com.sygic.sdk.vehicletraits.powertrain.ConnectorType
import com.sygic.sdk.vehicletraits.powertrain.ConsumptionData
import com.sygic.sdk.vehicletraits.powertrain.EuropeanEmissionStandard
import com.sygic.sdk.vehicletraits.powertrain.FuelType
import com.sygic.sdk.vehicletraits.powertrain.PowerRange
import com.sygic.sdk.vehicletraits.powertrain.PowertrainTraits
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking

class RouteComputeHelper {
    private val router = runBlocking { RouterProvider.getInstance() }

    suspend fun onlineRouteCompute(
        start: GeoCoordinates,
        destination: GeoCoordinates,
        waypoints: List<GeoCoordinates> = emptyList(),
        waypointObjects: List<Waypoint> = emptyList(),
        routingOptions: RoutingOptions = RoutingOptions()
    ): Route {
        val request = RouteRequest().apply {
            this.setStart(start)
            this.setDestination(destination)
            waypoints.forEach { this.addViaPoint(it) }
            waypointObjects.forEach { this.addViaPoint(it) }
            this.routingOptions = routingOptions
            this.routingOptions.routingService = RoutingOptions.RoutingService.Online
        }

        val flow = router.computeRouteWithAlternatives(request)
        return flow
            .filterIsInstance<ComputeRouteWithAlternativesData.RouteComputePrimaryFinished>()
            .onEach {
                if (it.status != Router.RouteComputeStatus.Success &&
                    it.status != Router.RouteComputeStatus.SuccessWithWarnings
                ) {
                    throw Exception("Route not computed, error: ${it.status}")
                }
            }
            .map { it.route }
            .filterNotNull()
            .onEach {
                Log.d("SYGIC", "Route successfully computed with length: ${it.routeInfo.length}")
            }
            .first()
    }

    suspend fun offlineRouteCompute(
        start: GeoCoordinates,
        destination: GeoCoordinates,
        waypoints: List<GeoCoordinates> = emptyList(),
        waypointObjects: List<Waypoint> = emptyList(),
        routingOptions: RoutingOptions = RoutingOptions()
    ): Route {
        val request = RouteRequest().apply {
            this.setStart(start)
            this.setDestination(destination)
            waypoints.forEach { this.addViaPoint(it) }
            waypointObjects.forEach { this.addViaPoint(it) }
            this.routingOptions = routingOptions
            this.routingOptions.routingService = RoutingOptions.RoutingService.Offline
        }
        val flow = router.computeRouteWithAlternatives(request)
        return flow
            .filterIsInstance<ComputeRouteWithAlternativesData.RouteComputePrimaryFinished>()
            .onEach {
                if (it.status != Router.RouteComputeStatus.Success &&
                    it.status != Router.RouteComputeStatus.SuccessWithWarnings
                ) {
                    throw Exception("Route not computed, error: ${it.status}")
                }
            }
            .map { it.route }
            .filterNotNull()
            .onEach {
                Log.d("SYGIC", "Route successfully computed with length: ${it.routeInfo.length}")
            }
            .first()
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
                    isHighOccupancyVehicle = false,
                )
            ),
            hazmatTraits = null,
            dimensionalTraits = null,
            powertrainTraits = internalCombustionPowertrain
        )
    }

    fun createDefaultElectricVehicleProfile(
        batteryCapacity: Float,
        remainingCapacity: Float
    ): VehicleProfile {
        val battery = Battery(
            capacity = batteryCapacity,
            remainingCapacity = remainingCapacity,
            chargingCurve = mapOf(1.0 to 1.0, 100.0 to 1.0)
        )
        val connectors = listOf(
            Connector(100F, ConnectorType.Type2, ConnectorFormat.Unknown, ChargingCurrent.AC),
            Connector(100F, ConnectorType.Type2, ConnectorFormat.Unknown, ChargingCurrent.DC)
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
            powertrainTraits = PowertrainTraits.ElectricPowertrain(
                battery,
                connectors,
                chargingPreferences,
                consumptionData
            )
        }
    }

    fun createElectricVehicleProfileForPreferenceViolation(
        batteryCapacity: Float,
        remainingCapacity: Float
    ): VehicleProfile {
        val battery = Battery(
            capacity = batteryCapacity,
            remainingCapacity = remainingCapacity,
            mapOf()
        )
        val connectors = listOf(
            Connector(100F, ConnectorType.Type2, ConnectorFormat.Unknown, ChargingCurrent.AC),
            Connector(100F, ConnectorType.Type2, ConnectorFormat.Unknown, ChargingCurrent.DC)
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
            powertrainTraits = PowertrainTraits.ElectricPowertrain(
                battery,
                connectors,
                chargingPreferences,
                consumptionData
            )
        }
    }

    fun createElectricVehicleProfileTruck(
        batteryCapacity: Float = 350f,
        remainingCapacity: Float = 100f
    ): VehicleProfile {
        val battery = Battery(
            capacity = batteryCapacity,
            remainingCapacity = remainingCapacity,
            mapOf()
        )
        val connectors = listOf(
            Connector(500f, ConnectorType.Type2, ConnectorFormat.Socket, ChargingCurrent.AC),
            Connector(500f, ConnectorType.Ccs2, ConnectorFormat.Unknown, ChargingCurrent.DC)
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
            powertrainTraits = PowertrainTraits.ElectricPowertrain(
                battery,
                connectors,
                chargingPreferences,
                consumptionData
            )
        }
    }

    fun createEVProfileForInsufficientBattery(
        batteryCapacity: Float,
        remainingCapacity: Float
    ): VehicleProfile {
        val battery = Battery(
            capacity = batteryCapacity,
            remainingCapacity = remainingCapacity,
            chargingCurve = mapOf(1.0 to 1.0, 100.0 to 1.0)
        )
        val connectors = listOf(
            Connector(
                1F,
                ConnectorType.HouseholdTypeCeeBlue,
                ConnectorFormat.Cable,
                ChargingCurrent.DC
            )
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
            powertrainTraits = PowertrainTraits.ElectricPowertrain(
                battery,
                connectors,
                chargingPreferences,
                consumptionData
            )
        }
    }
}