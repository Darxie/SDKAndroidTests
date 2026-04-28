package cz.feldis.sdkandroidtests.routing

import android.util.Log
import com.sygic.sdk.navigation.NavigationManager
import com.sygic.sdk.navigation.NavigationManagerProvider
import com.sygic.sdk.navigation.results.SetVehicleProfileResult
import com.sygic.sdk.navigation.results.WaypointPassedData
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.route.ChargingWaypoint
import com.sygic.sdk.route.Router
import com.sygic.sdk.route.RouterProvider
import com.sygic.sdk.route.RoutingOptions
import com.sygic.sdk.route.RoutingOptions.NearestAccessiblePointStrategy
import com.sygic.sdk.route.simulator.RouteDemonstrateSimulatorProvider
import com.sygic.sdk.vehicletraits.VehicleProfile
import com.sygic.sdk.vehicletraits.general.VehicleType
import com.sygic.sdk.vehicletraits.powertrain.Battery
import com.sygic.sdk.vehicletraits.powertrain.ChargingCurrent
import com.sygic.sdk.vehicletraits.powertrain.ChargingPreferences
import com.sygic.sdk.vehicletraits.powertrain.Connector
import com.sygic.sdk.vehicletraits.powertrain.ConnectorFormat
import com.sygic.sdk.vehicletraits.powertrain.ConnectorType
import com.sygic.sdk.vehicletraits.powertrain.ConsumptionData
import com.sygic.sdk.vehicletraits.powertrain.PowertrainTraits
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.ktx.NavigationManagerKtx
import cz.feldis.sdkandroidtests.mapInstaller.MapDownloadHelper
import cz.feldis.sdkandroidtests.utils.RouteDemonstrateSimulatorAdapter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ElectricVehicleNavigationTests : BaseTest() {
    companion object {
        private const val TAG = "EVNavTests"

        /**
         * Maximum simulator speed multiplier that stays stable. Going above leads to skipped
         * simulator ticks and flaky event emission.
         */
        private const val MAX_STABLE_SPEED: Float = 8F
    }

    private lateinit var routeComputeHelper: RouteComputeHelper
    private lateinit var mapDownloadHelper: MapDownloadHelper
    private lateinit var navigation: NavigationManager
    private lateinit var router: Router
    private val navigationManagerKtx = NavigationManagerKtx()

    // Long cross-Slovakia route used to give the simulator room for observable battery drops.
    private val longRouteStart = GeoCoordinates(48.24135577878832, 16.99083981234057)
    private val longRouteDestination = GeoCoordinates(49.06008227080942, 20.315811448409608)

    override fun setUp() {
        super.setUp()
        routeComputeHelper = RouteComputeHelper()
        mapDownloadHelper = MapDownloadHelper()
        navigation = runBlocking { NavigationManagerProvider.getInstance() }
        router = runBlocking { RouterProvider.getInstance() }
        disableOnlineMaps()
    }

    /**
     * As the simulator drives along an EV route, `batteryCapacity()` must emit decreasing
     * remaining-kWh samples — the SDK tracks live consumption. We disable auto-charging so
     * the battery monotonically decreases over the observation window.
     */
    @Test
    fun batteryCapacityFlowEmitsDecreasingValuesDuringDrive() = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")

        val route = routeComputeHelper.offlineRouteCompute(
            longRouteStart, longRouteDestination,
            routingOptions = drivingOnlyRoutingOptions(capacity = 100F, remaining = 100F)
        )
        navigationManagerKtx.setRouteForNavigation(route, navigation)

        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route)
        val adapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.startSimulator(adapter)
        navigationManagerKtx.setSpeedMultiplier(adapter, MAX_STABLE_SPEED)

        try {
            val samples = withTimeout(180_000L) {
                navigation.batteryCapacity()
                    .onEach { Log.d(TAG, "battery sample: $it kWh") }
                    .take(3)
                    .toList()
            }
            assertTrue("Expected >= 3 battery samples, got $samples", samples.size >= 3)
            assertTrue(
                "Battery must drop between first and last sample: $samples",
                samples.last() < samples.first()
            )
        } finally {
            navigationManagerKtx.stopSimulator(adapter)
            navigationManagerKtx.stopNavigation(navigation)
        }
    }

    /**
     * When the simulator drives past a ChargingWaypoint, the `passedWaypoints()` flow emits
     * that waypoint (preserving its ChargingWaypoint subtype). We seek close to the first
     * charging stop to keep the test short while respecting the simulator's speed cap.
     */
    @Test
    fun drivingPastChargingWaypointEmitsIt() = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")

        val route = routeComputeHelper.offlineRouteCompute(
            longRouteStart, longRouteDestination,
            routingOptions = smallBatteryRoutingOptions()
        )
        val firstCharging = route.waypoints.filterIsInstance<ChargingWaypoint>().firstOrNull()
        assertNotNull("Route must have at least one ChargingWaypoint", firstCharging)
        val routeLength = route.routeInfo.length
        assertTrue("route length must be > 0", routeLength > 0)

        // Seek to ~2 percentage points before the first charging waypoint so the simulator
        // reaches it within a short real-time window.
        val firstChargingPercent =
            ((firstCharging!!.distanceFromStart.toDouble() / routeLength) * 100).toInt()
        val seekPercent = (firstChargingPercent - 2).coerceAtLeast(0)
        Log.d(
            TAG,
            "firstChargingPercent=$firstChargingPercent seekPercent=$seekPercent " +
                    "firstChargingDist=${firstCharging.distanceFromStart} routeLen=$routeLength"
        )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route)
        val adapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.startSimulator(adapter)
        navigationManagerKtx.setSpeedMultiplier(adapter, MAX_STABLE_SPEED)
        simulator.seekTo(seekPercent)

        try {
            val passed = withTimeout(120_000L) {
                navigation.passedWaypoints()
                    .filterIsInstance<WaypointPassedData.WaypointPassed>()
                    .onEach { Log.d(TAG, "passed waypoint: ${it.waypoint}") }
                    .map { it.waypoint }
                    .filterIsInstance<ChargingWaypoint>()
                    .first()
            }
            assertNotNull("Expected to pass at least one ChargingWaypoint", passed)
        } finally {
            navigationManagerKtx.stopSimulator(adapter)
            navigationManagerKtx.stopNavigation(navigation)
        }
    }

    /**
     * Dropping the remaining battery capacity via `setVehicleProfile` during an active
     * navigation must fire a `waypointOutOfRange` event — the SDK reports unreachable waypoints.
     */
    @Test
    fun loweringBatteryDuringNavigationFiresWaypointOutOfRange() = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")

        // Precondition: fully-charged big battery → no auto-charging stops.
        val route = routeComputeHelper.offlineRouteCompute(
            longRouteStart, longRouteDestination,
            routingOptions = bigBatteryRoutingOptions()
        )
        assertTrue(
            "Precondition: full battery route should have no charging stops",
            route.waypoints.filterIsInstance<ChargingWaypoint>().isEmpty()
        )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route)
        val adapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.startSimulator(adapter)
        navigationManagerKtx.setSpeedMultiplier(adapter, MAX_STABLE_SPEED)

        try {
            delay(2_000L)  // let navigation initialize

            val starvedProfile = buildCarElectricProfile(
                batteryCapacity = 500F,
                remainingCapacity = 2F
            )
            val setResult = withTimeout(5_000L) { navigation.setVehicleProfile(starvedProfile) }
            assertEquals(SetVehicleProfileResult.Success, setResult)

            val outOfRange = withTimeout(30_000L) { navigation.waypointOutOfRange().first() }
            assertTrue(
                "waypointOutOfRange must report a capacity >= 0, got ${outOfRange.capacity}",
                outOfRange.capacity >= 0
            )
        } finally {
            navigationManagerKtx.stopSimulator(adapter)
            navigationManagerKtx.stopNavigation(navigation)
        }
    }

    /**
     * End-to-end flow: after the battery drop, an integrator reacts by recomputing the route
     * with the same starved profile — the new route must contain at least one ChargingWaypoint
     * whereas the original big-battery route had none.
     */
    @Test
    fun recomputeAfterBatteryDropAddsChargingWaypoints() = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")

        val bigBatteryRoute = routeComputeHelper.offlineRouteCompute(
            longRouteStart, longRouteDestination,
            routingOptions = bigBatteryRoutingOptions()
        )
        assertTrue(
            "Precondition: big-battery route has no charging stops",
            bigBatteryRoute.waypoints.filterIsInstance<ChargingWaypoint>().isEmpty()
        )

        navigationManagerKtx.setRouteForNavigation(bigBatteryRoute, navigation)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(bigBatteryRoute)
        val adapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.startSimulator(adapter)
        navigationManagerKtx.setSpeedMultiplier(adapter, MAX_STABLE_SPEED)

        try {
            delay(2_000L)
            val starvedProfile = buildCarElectricProfile(
                batteryCapacity = 500F,
                remainingCapacity = 3F
            )
            val setResult = withTimeout(5_000L) { navigation.setVehicleProfile(starvedProfile) }
            assertEquals(SetVehicleProfileResult.Success, setResult)

            // Integrator-initiated recompute with the updated profile.
            val reroutedRoute = routeComputeHelper.offlineRouteCompute(
                longRouteStart, longRouteDestination,
                routingOptions = RoutingOptions().apply {
                    vehicleProfile = starvedProfile
                    useEndpointProtection = true
                    napStrategy = NearestAccessiblePointStrategy.Disabled
                }
            )
            assertTrue(
                "After battery drop, recompute must add ChargingWaypoints",
                reroutedRoute.waypoints.filterIsInstance<ChargingWaypoint>().isNotEmpty()
            )
        } finally {
            navigationManagerKtx.stopSimulator(adapter)
            navigationManagerKtx.stopNavigation(navigation)
        }
    }

    /**
     * When the driver deviates slightly (simulated by shifting the start ~200 m), recomputing
     * with the same EV profile must keep at least one of the originally-planned charging stops
     * in the new plan — drivers expect their charging plan to stay stable if they make a minor
     * detour.
     */
    @Test
    fun recomputingFromOffsetPositionKeepsReachableChargingStations() = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")

        val originalRoute = routeComputeHelper.offlineRouteCompute(
            longRouteStart, longRouteDestination,
            routingOptions = smallBatteryRoutingOptions()
        )
        val originalStops = originalRoute.waypoints.filterIsInstance<ChargingWaypoint>()
        assertTrue("Original route must have charging stops", originalStops.isNotEmpty())

        val offsetStart = GeoCoordinates(
            longRouteStart.latitude + 0.002,
            longRouteStart.longitude + 0.002
        )
        val reroutedRoute = routeComputeHelper.offlineRouteCompute(
            offsetStart, longRouteDestination,
            routingOptions = smallBatteryRoutingOptions()
        )
        val reroutedStops = reroutedRoute.waypoints.filterIsInstance<ChargingWaypoint>()
        assertTrue("Rerouted route must have charging stops", reroutedStops.isNotEmpty())

        val matches = originalStops.count { orig ->
            val origPos = orig.place?.position ?: return@count false
            reroutedStops.any { rr ->
                rr.place?.position?.distanceTo(origPos)?.let { it < 100.0 } == true
            }
        }
        assertTrue(
            "At least one original charging stop must appear in the rerouted plan. " +
                    "original=${originalStops.size}, rerouted=${reroutedStops.size}, matches=$matches",
            matches > 0
        )
    }

    // ----- Helpers -----

    private fun bigBatteryRoutingOptions(): RoutingOptions = RoutingOptions().apply {
        vehicleProfile = buildCarElectricProfile(batteryCapacity = 500F, remainingCapacity = 500F)
        useEndpointProtection = true
        napStrategy = NearestAccessiblePointStrategy.Disabled
    }

    private fun smallBatteryRoutingOptions(): RoutingOptions = RoutingOptions().apply {
        vehicleProfile = buildCarElectricProfile(batteryCapacity = 50F, remainingCapacity = 15F)
        useEndpointProtection = true
        napStrategy = NearestAccessiblePointStrategy.Disabled
    }

    /**
     * Routing options that disable automatic charging waypoints — used when we want to observe
     * the raw battery drop along a route without the SDK inserting charger stops that would
     * bump remaining capacity back up.
     */
    private fun drivingOnlyRoutingOptions(capacity: Float, remaining: Float): RoutingOptions =
        RoutingOptions().apply {
            vehicleProfile = buildCarElectricProfile(
                batteryCapacity = capacity,
                remainingCapacity = remaining
            )
            useAutomaticChargingWaypoints = false
            useEndpointProtection = true
            napStrategy = NearestAccessiblePointStrategy.Disabled
        }

    private fun buildCarElectricProfile(
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
            Connector(100F, ConnectorType.Ccs2, ConnectorFormat.Unknown, ChargingCurrent.DC)
        )
        val preferences = ChargingPreferences(
            fullChargeThreshold = 0.8F,
            chargingThreshold = 0.2F,
            reserveThreshold = 0.05F,
            batteryMinimumDestinationThreshold = 0.3F
        )
        val consumption = ConsumptionData(
            consumptionCurve = mapOf(1.0 to 1.0, 100.0 to 1.0),
            weightFactors = mapOf(1000.0 to 0.5, 5000.0 to 1.0, 10000.0 to 1.0)
        )
        return VehicleProfile().apply {
            generalVehicleTraits.vehicleType = VehicleType.Car
            powertrainTraits = PowertrainTraits.ElectricPowertrain(
                battery, connectors, preferences, consumption
            )
        }
    }
}
