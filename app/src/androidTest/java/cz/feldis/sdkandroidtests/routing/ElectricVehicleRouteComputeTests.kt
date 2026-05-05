package cz.feldis.sdkandroidtests.routing

import com.sygic.sdk.navigation.explorer.RouteExplorerProvider
import com.sygic.sdk.navigation.explorer.results.ExploreChargingStationsOnRouteData
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.route.ChargingWaypoint
import com.sygic.sdk.route.RouteRequest
import com.sygic.sdk.route.RouteWarning
import com.sygic.sdk.route.Router
import com.sygic.sdk.route.RouterProvider
import com.sygic.sdk.route.RoutingOptions
import com.sygic.sdk.route.RoutingOptions.NearestAccessiblePointStrategy
import com.sygic.sdk.route.listeners.EVRangeListener
import com.sygic.sdk.route.results.ComputeEVRangeResult
import com.sygic.sdk.route.results.ComputeRouteWithAlternativesData
import com.sygic.sdk.utils.EnforceableAttribute
import com.sygic.sdk.utils.ParcelableBoolean
import com.sygic.sdk.vehicletraits.VehicleProfile
import com.sygic.sdk.vehicletraits.dimensional.DimensionalTraits
import com.sygic.sdk.vehicletraits.general.VehicleType
import com.sygic.sdk.vehicletraits.powertrain.Battery
import com.sygic.sdk.vehicletraits.powertrain.ChargingCurrent
import com.sygic.sdk.vehicletraits.powertrain.ChargingPreferences
import com.sygic.sdk.vehicletraits.powertrain.Connector
import com.sygic.sdk.vehicletraits.powertrain.ConnectorFormat
import com.sygic.sdk.vehicletraits.powertrain.ConnectorType
import com.sygic.sdk.vehicletraits.powertrain.ConsumptionData
import com.sygic.sdk.vehicletraits.powertrain.PowerRange
import com.sygic.sdk.vehicletraits.powertrain.PowertrainTraits
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.mapInstaller.MapDownloadHelper
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify

class ElectricVehicleRouteComputeTests : BaseTest() {
    private lateinit var mapDownloadHelper: MapDownloadHelper
    private lateinit var routeComputeHelper: RouteComputeHelper
    private lateinit var router: Router

    // Long cross-Slovakia route that exceeds the range of a small EV battery — reliably forces
    // at least one charging stop.
    private val longRouteStart = GeoCoordinates(48.24135577878832, 16.99083981234057)
    private val longRouteDestination = GeoCoordinates(49.06008227080942, 20.315811448409608)

    // Short Bratislava route (~3 km) used for LowBatteryAtDestination where we need the route
    // to be reachable but arrive below the destination threshold.
    private val shortRouteStart = GeoCoordinates(48.14548507020328, 17.126529723864405)
    private val shortRouteDestination = GeoCoordinates(48.16589227, 17.13513728)

    override fun setUp() {
        super.setUp()
        mapDownloadHelper = MapDownloadHelper()
        routeComputeHelper = RouteComputeHelper()
        router = runBlocking { RouterProvider.getInstance() }
        disableOnlineMaps()
    }

    // region ----- Tests moved from RouteComputeTests -----

    @Test
    fun getStateOfChargeAtWaypoint() = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")

        val options = RoutingOptions().apply {
            vehicleProfile = routeComputeHelper.createElectricVehicleProfileTruck(350f, 100f)
            useEndpointProtection = true
            napStrategy = NearestAccessiblePointStrategy.Disabled
        }

        val route = routeComputeHelper.offlineRouteCompute(
            longRouteStart,
            longRouteDestination,
            routingOptions = options
        )

        route.waypoints.forEach {
            if (it is ChargingWaypoint) {
                assertTrue(it.stateOfCharge > 0.1)
                assertTrue(it.chargingTime > 0)
            }
        }
    }

    @Test
    fun testSpiderRangeWeightFactorsDifference(): Unit = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")

        val listener: EVRangeListener = mock(verboseLogging = true)

        val vehicleProfile =
            routeComputeHelper.createDefaultElectricVehicleProfile(5F, 5F).apply {
                dimensionalTraits = DimensionalTraits().apply {
                    totalWeight = 1000F
                }
            }

        router.computeEVRange(
            GeoCoordinates(48.10095535808773, 17.234824479529344),
            listOf(5.0),
            RoutingOptions().apply {
                this.vehicleProfile = vehicleProfile
                this.routingService = RoutingOptions.RoutingService.Offline
            },
            listener
        )

        val captor = argumentCaptor<List<List<GeoCoordinates>>>()
        verify(listener, timeout(10_000L)).onEVRangeComputed(captor.capture())
        val isochrones1 = captor.firstValue[0]

        val listener2: EVRangeListener = mock(verboseLogging = true)

        router.computeEVRange(
            GeoCoordinates(48.10095535808773, 17.234824479529344),
            listOf(5.0),
            RoutingOptions().apply {
                this.vehicleProfile = vehicleProfile.apply {
                    dimensionalTraits = DimensionalTraits().apply {
                        totalWeight = 5000F
                    }
                }
                this.routingService = RoutingOptions.RoutingService.Offline
            },
            listener2
        )

        val captor2 = argumentCaptor<List<List<GeoCoordinates>>>()
        verify(listener2, timeout(10_000L)).onEVRangeComputed(captor2.capture())
        val isochrones2 = captor2.firstValue[0]

        assertNotEquals(isochrones1.size, isochrones2.size)

        val areDifferent = isochrones1.zip(isochrones2).any { (coord1, coord2) ->
            coord1.latitude != coord2.latitude || coord1.longitude != coord2.longitude
        }

        assertTrue("Isochrones should be different, but they appear identical.", areDifferent)
    }

    // endregion

    // region ----- ChargingWaypoint exposure & consistency -----

    /**
     * Router.getChargingWaypoints(route) must return the same set of charging waypoints that
     * is embedded in route.waypoints.
     */
    @Test
    fun getChargingWaypointsMatchesRouteWaypoints() = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")

        val route = routeComputeHelper.offlineRouteCompute(
            longRouteStart,
            longRouteDestination,
            routingOptions = smallBatteryCarOptions()
        )

        val fromRouter = router.getChargingWaypoints(route)
        val fromRouteWaypoints = route.waypoints.filterIsInstance<ChargingWaypoint>()

        assertTrue(
            "Expected at least one charging waypoint on a long route with a small battery",
            fromRouter.isNotEmpty()
        )
        assertEquals(fromRouteWaypoints.size, fromRouter.size)
    }

    /**
     * Remaining battery capacity reported by getRemainingBatteryCapacityAt must decrease between
     * the start of the route and the first charging stop (i.e. the vehicle is consuming energy).
     */
    @Test
    fun remainingBatteryCapacityDecreasesBeforeFirstChargingStop() = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")

        val options = smallBatteryCarOptions()
        val route = routeComputeHelper.offlineRouteCompute(
            longRouteStart, longRouteDestination, routingOptions = options
        )
        val profile = options.vehicleProfile!!

        val firstCharging = route.waypoints.filterIsInstance<ChargingWaypoint>().firstOrNull()
        assertNotNull("Expected at least one charging waypoint", firstCharging)

        val start = route.waypoints.first()
        val capacityAtStart = router.getRemainingBatteryCapacityAt(start, profile, route)
        val capacityAtCharging =
            router.getRemainingBatteryCapacityAt(firstCharging!!, profile, route)

        assertTrue(
            "Capacity should drop between start ($capacityAtStart kWh) and the first charging " +
                    "stop ($capacityAtCharging kWh)",
            capacityAtCharging < capacityAtStart
        )
    }

    /**
     * Every auto-generated ChargingWaypoint must report a strictly positive chargingPower.
     */
    @Test
    fun chargingWaypointHasPositiveChargingPower() = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")

        val route = routeComputeHelper.offlineRouteCompute(
            longRouteStart, longRouteDestination,
            routingOptions = smallBatteryCarOptions()
        )
        val chargingWaypoints = route.waypoints.filterIsInstance<ChargingWaypoint>()
        assertTrue("Expected at least one charging waypoint", chargingWaypoints.isNotEmpty())
        chargingWaypoints.forEach {
            assertTrue(
                "chargingPower must be > 0, was ${it.chargingPower}",
                it.chargingPower > 0f
            )
        }
    }

    /**
     * Auto-generated charging waypoints must have type SuggestedByRouting.
     */
    @Test
    fun autoGeneratedChargingWaypointsAreSuggestedByRouting() = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")

        val route = routeComputeHelper.offlineRouteCompute(
            longRouteStart, longRouteDestination,
            routingOptions = smallBatteryCarOptions()
        )
        val chargingWaypoints = route.waypoints.filterIsInstance<ChargingWaypoint>()
        assertTrue("Expected at least one charging waypoint", chargingWaypoints.isNotEmpty())
        chargingWaypoints.forEach {
            assertEquals(
                ChargingWaypoint.ChargingWaypointType.SuggestedByRouting,
                it.chargingWPType
            )
        }
    }

    // endregion

    // region ----- useAutomaticChargingWaypoints behaviour -----

    /**
     * When useAutomaticChargingWaypoints is false, the SDK must not inject any charging waypoints
     * even if the battery range is insufficient.
     */
    @Test
    fun disabledAutomaticChargingWaypointsProducesNoChargingWaypoints() = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")

        val options = smallBatteryCarOptions().apply {
            useAutomaticChargingWaypoints = false
        }
        val route = routeComputeHelper.offlineRouteCompute(
            longRouteStart, longRouteDestination, routingOptions = options
        )
        val chargingWaypoints = route.waypoints.filterIsInstance<ChargingWaypoint>()
        assertTrue(
            "Expected no charging waypoints when automatic charging is disabled, got $chargingWaypoints",
            chargingWaypoints.isEmpty()
        )
    }

    /**
     * The default value of useAutomaticChargingWaypoints is true, and a long trip with a small
     * battery yields at least one charging waypoint.
     */
    @Test
    fun enabledAutomaticChargingWaypointsProducesChargingWaypoint() = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")

        val options = smallBatteryCarOptions()
        assertTrue(
            "useAutomaticChargingWaypoints must default to true",
            options.useAutomaticChargingWaypoints
        )

        val route = routeComputeHelper.offlineRouteCompute(
            longRouteStart, longRouteDestination, routingOptions = options
        )
        val chargingWaypoints = route.waypoints.filterIsInstance<ChargingWaypoint>()
        assertTrue(
            "Expected at least one auto-generated charging waypoint",
            chargingWaypoints.isNotEmpty()
        )
    }

    // endregion

    // region ----- Warnings -----

    /**
     * A reachable short route that arrives below batteryMinimumDestinationThreshold and has no
     * charging stop in between must emit a LowBatteryAtDestination warning.
     */
    @Test
    fun lowBatteryAtDestinationWarningEmitted() = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")

        // Battery is easily large enough to cover the ~3 km route, but the destination threshold
        // is 95% of capacity — arrival SoC cannot meet it, so LowBatteryAtDestination fires.
        val profile = buildCarElectricProfile(
            batteryCapacity = 30F,
            remainingCapacity = 20F,
            chargingPreferences = ChargingPreferences(
                fullChargeThreshold = 0.8F,
                chargingThreshold = 0.2F,
                reserveThreshold = 0.05F,
                batteryMinimumDestinationThreshold = 0.95F
            )
        )
        val options = RoutingOptions().apply {
            vehicleProfile = profile
            useAutomaticChargingWaypoints = false
            useEndpointProtection = true
            napStrategy = NearestAccessiblePointStrategy.Disabled
        }

        val route = routeComputeHelper.offlineRouteCompute(
            shortRouteStart, shortRouteDestination, routingOptions = options
        )
        val warnings = route.getRouteWarnings()
        assertTrue(
            "Expected LowBatteryAtDestination warning, got: $warnings",
            warnings.any { it is RouteWarning.LowBatteryAtDestination }
        )
    }

    // endregion

    // region ----- computeEVRange isochrones -----

    /**
     * computeEVRange with capacities in ascending order must return one isochrone per capacity,
     * and the geographic reach (max distance from origin) must be non-decreasing.
     */
    @Test
    fun computeEVRangeReturnsMonotonicallyGrowingIsochrones() = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")

        val origin = GeoCoordinates(48.14548507020328, 17.126529723864405)
        val capacities = listOf(2.0, 5.0, 10.0)

        val result = router.computeEVRange(
            origin,
            capacities,
            RoutingOptions().apply {
                vehicleProfile = buildCarElectricProfile(
                    batteryCapacity = 50F,
                    remainingCapacity = 50F
                )
                routingService = RoutingOptions.RoutingService.Offline
            }
        )

        val success = result as? ComputeEVRangeResult.Success
            ?: error("Expected Success, got $result")

        assertEquals(
            "Expected one isochrone per capacity",
            capacities.size,
            success.isochrones.size
        )

        val maxReach = success.isochrones.map { polygon ->
            require(polygon.isNotEmpty()) { "Isochrone polygon must not be empty" }
            polygon.maxOf { it.distanceTo(origin) }
        }
        for (i in 1 until maxReach.size) {
            assertTrue(
                "Isochrone reach must grow with capacity: reach[${i - 1}]=${maxReach[i - 1]} m, " +
                        "reach[$i]=${maxReach[i]} m",
                maxReach[i] >= maxReach[i - 1]
            )
        }
    }

    // endregion

    // region ----- Error statuses -----

    /**
     * A route compute with an EV profile whose consumption curve is empty must fail with
     * NoConsumptionCurve.
     */
    @Test
    fun emptyConsumptionCurveFailsWithNoConsumptionCurveStatus() = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")

        val status = computeRouteReturningStatus(
            start = longRouteStart,
            destination = longRouteDestination,
            vehicleProfile = buildCarElectricProfile(
                consumptionData = ConsumptionData(consumptionCurve = emptyMap())
            )
        )
        assertEquals(Router.RouteComputeStatus.NoConsumptionCurve, status)
    }

    /**
     * A route compute with an EV profile that has no connectors must fail with NoConnectors.
     */
    @Test
    fun emptyConnectorsFailsWithNoConnectorsStatus() = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")

        val status = computeRouteReturningStatus(
            start = longRouteStart,
            destination = longRouteDestination,
            vehicleProfile = buildCarElectricProfile(connectors = emptyList())
        )
        assertEquals(Router.RouteComputeStatus.NoConnectors, status)
    }

    /**
     * A route compute with a connector reporting zero maximal charging power must fail with
     * NoConnectorMaxPower.
     */
    @Test
    fun zeroMaxChargingPowerFailsWithNoConnectorMaxPowerStatus() = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")

        val status = computeRouteReturningStatus(
            start = longRouteStart,
            destination = longRouteDestination,
            vehicleProfile = buildCarElectricProfile(
                connectors = listOf(
                    Connector(
                        0F,
                        ConnectorType.Type2,
                        ConnectorFormat.Unknown,
                        ChargingCurrent.AC
                    )
                )
            )
        )
        assertEquals(Router.RouteComputeStatus.NoConnectorMaxPower, status)
    }

    // endregion

    // region ----- Acceptance A: core EV contract -----

    /**
     * The SDK contract for automatic charging: when the trip is feasible, after planned stops the
     * driver reaches the destination with battery at or above the configured reserve threshold.
     *
     * Uses a realistic 0.2 kWh/km consumption (instead of the inflated 1.0 kWh/km used elsewhere
     * for forcing many charging stops) so that the long cross-Slovakia route is clearly feasible
     * with the chosen battery — otherwise the SDK's best-effort behaviour kicks in and emits
     * LowBatteryAtDestination / InsufficientBatteryCharge warnings instead of meeting the contract.
     */
    @Test
    fun routeArrivesAboveReserveThreshold() = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")

        val profile = buildCarElectricProfile(
            batteryCapacity = 50F,
            remainingCapacity = 15F,
            consumptionData = ConsumptionData(
                consumptionCurve = mapOf(1.0 to 0.2, 100.0 to 0.2),
                weightFactors = mapOf(1000.0 to 0.5, 5000.0 to 1.0, 10000.0 to 1.0)
            )
        )
        val options = evRoutingOptions(profile)
        val powertrain = profile.powertrainTraits as PowertrainTraits.ElectricPowertrain
        val reserveThreshold = powertrain.chargingPreferences.reserveThreshold
        val capacity = powertrain.battery.capacity

        val route = routeComputeHelper.offlineRouteCompute(
            longRouteStart, longRouteDestination, routingOptions = options
        )
        val destination = route.waypoints.last()
        val arrivalKwh = router.getRemainingBatteryCapacityAt(destination, profile, route)
        val arrivalSoc = arrivalKwh / capacity
        val warnings = route.getRouteWarnings()

        assertTrue(
            "Arrival SoC ($arrivalSoc) must be >= reserve threshold ($reserveThreshold). " +
                    "Arrival kWh=$arrivalKwh, capacity=$capacity, warnings=$warnings",
            arrivalSoc >= reserveThreshold
        )
    }

    /**
     * A long EV trip must include non-zero driving duration plus non-zero charging time, and
     * driving time must dominate charging time (we expect hours of driving vs. minutes of charging).
     */
    @Test
    fun totalDurationIncludesChargingTime() = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")

        val route = routeComputeHelper.offlineRouteCompute(
            longRouteStart, longRouteDestination,
            routingOptions = smallBatteryCarOptions()
        )
        val chargingWaypoints = route.waypoints.filterIsInstance<ChargingWaypoint>()
        assertTrue("Expected at least one charging stop", chargingWaypoints.isNotEmpty())

        val chargingTimeSum = chargingWaypoints.sumOf { it.chargingTime }
        val durationToDestination =
            route.routeInfo.waypointDurations.last().withSpeedProfiles

        assertTrue("Charging time sum must be > 0", chargingTimeSum > 0)
        assertTrue("Driving duration must be > 0", durationToDestination > 0)
        assertTrue(
            "Driving duration ($durationToDestination s) must exceed sum of charging times " +
                    "($chargingTimeSum)",
            durationToDestination > chargingTimeSum
        )
    }

    /**
     * Charging waypoints must be ordered by their position along the route — downstream UI
     * relies on this to render the stop list.
     */
    @Test
    fun chargingWaypointsInAscendingDistanceOrder() = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")

        val route = routeComputeHelper.offlineRouteCompute(
            longRouteStart, longRouteDestination,
            routingOptions = smallBatteryCarOptions()
        )
        val distances = route.waypoints.filterIsInstance<ChargingWaypoint>()
            .map { it.distanceFromStart }
        assertTrue("Expected at least one charging waypoint", distances.isNotEmpty())
        assertEquals(
            "distanceFromStart must be monotonic across charging waypoints",
            distances.sorted(),
            distances
        )
    }

    /**
     * Every auto-generated ChargingWaypoint must expose a Place with a valid position —
     * otherwise integrators cannot render the stop on the map.
     */
    @Test
    fun chargingWaypointsHaveValidPlace() = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")

        val route = routeComputeHelper.offlineRouteCompute(
            longRouteStart, longRouteDestination,
            routingOptions = smallBatteryCarOptions()
        )
        val chargingWaypoints = route.waypoints.filterIsInstance<ChargingWaypoint>()
        assertTrue("Expected at least one charging waypoint", chargingWaypoints.isNotEmpty())
        chargingWaypoints.forEach { cw ->
            val place = cw.place
            assertNotNull("place must not be null for $cw", place)
            val pos = place!!.position
            assertTrue(
                "place.position ($pos) must be valid (non-zero lat/lng)",
                pos.latitude != 0.0 || pos.longitude != 0.0
            )
        }
    }

    // endregion

    // region ----- Acceptance B: charging preferences enforcement -----

    /**
     * Enforced preferDcChargers: every auto-selected station must expose at least one DC
     * charging connector (customer-visible contract for DC-only setups).
     */
    @Test
    fun enforcedDcChargersOnlyUsesDcCapableStations() = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")

        val profile = buildCarElectricProfile(
            chargingPreferences = ChargingPreferences(
                fullChargeThreshold = 0.8F,
                chargingThreshold = 0.2F,
                reserveThreshold = 0.05F,
                batteryMinimumDestinationThreshold = 0.3F,
                preferDcChargers = EnforceableAttribute(ParcelableBoolean(true), true)
            )
        )

        val route = routeComputeHelper.offlineRouteCompute(
            longRouteStart, longRouteDestination,
            routingOptions = evRoutingOptions(profile)
        )
        val chargingWaypoints = route.waypoints.filterIsInstance<ChargingWaypoint>()
        assertTrue("Expected at least one charging waypoint", chargingWaypoints.isNotEmpty())

        chargingWaypoints.forEach { cw ->
            val connectors = cw.place?.evCharger?.evses
                ?.flatMap { it.chargingConnectors }
                ?: emptyList()
            assertTrue(
                "Station at ${cw.place?.name} (${cw.place?.position}) must expose a DC connector " +
                        "when DC is enforced. Connectors=${connectors.map { "${it.type}/${it.current}" }}",
                connectors.any { it.current == ChargingCurrent.DC }
            )
        }
    }

    /**
     * Enforced powerRange: chargingPower of every auto-generated stop must fit within the range.
     */
    @Test
    fun enforcedPowerRangeLimitsChargingPower() = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")

        val minKw = 50F
        val maxKw = 250F
        val profile = buildCarElectricProfile(
            chargingPreferences = ChargingPreferences(
                fullChargeThreshold = 0.8F,
                chargingThreshold = 0.2F,
                reserveThreshold = 0.05F,
                batteryMinimumDestinationThreshold = 0.3F,
                powerRange = EnforceableAttribute(PowerRange(minKw, maxKw), true)
            )
        )

        val route = routeComputeHelper.offlineRouteCompute(
            longRouteStart, longRouteDestination,
            routingOptions = evRoutingOptions(profile)
        )
        val chargingWaypoints = route.waypoints.filterIsInstance<ChargingWaypoint>()
        assertTrue("Expected at least one charging waypoint", chargingWaypoints.isNotEmpty())
        chargingWaypoints.forEach {
            assertTrue(
                "chargingPower=${it.chargingPower} is out of enforced range [$minKw, $maxKw]",
                it.chargingPower in minKw..maxKw
            )
        }
    }

    /**
     * Connector-type compatibility: EV with only a CCS2 DC connector must pick only
     * CCS2-compatible stations (verified via ChargingWaypoint.isCompatibleWithVehicleProfile).
     */
    @Test
    fun vehicleWithOnlyCcs2PicksOnlyCompatibleStations() = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")

        val profile = buildCarElectricProfile(
            connectors = listOf(
                Connector(350F, ConnectorType.Ccs2, ConnectorFormat.Unknown, ChargingCurrent.DC)
            )
        )

        val route = routeComputeHelper.offlineRouteCompute(
            longRouteStart, longRouteDestination,
            routingOptions = evRoutingOptions(profile)
        )
        val chargingWaypoints = route.waypoints.filterIsInstance<ChargingWaypoint>()
        assertTrue("Expected at least one charging waypoint", chargingWaypoints.isNotEmpty())
        chargingWaypoints.forEach {
            assertTrue(
                "Auto-selected station at ${it.place?.position} must be compatible with the CCS2-only vehicle profile",
                it.isCompatibleWithVehicleProfile(profile)
            )
        }
    }

    // endregion

    // region ----- Acceptance C: battery-state scaling -----

    /**
     * Same trip with a larger battery must need strictly fewer charging stops. A customer
     * upgrading the battery pack must see the charging plan shrink.
     */
    @Test
    fun biggerBatteryProducesFewerChargingStops() = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")

        val smallRoute = routeComputeHelper.offlineRouteCompute(
            longRouteStart, longRouteDestination,
            routingOptions = evRoutingOptions(
                buildCarElectricProfile(batteryCapacity = 30F, remainingCapacity = 10F)
            )
        )
        val bigRoute = routeComputeHelper.offlineRouteCompute(
            longRouteStart, longRouteDestination,
            routingOptions = evRoutingOptions(
                buildCarElectricProfile(batteryCapacity = 300F, remainingCapacity = 250F)
            )
        )

        val smallStops = smallRoute.waypoints.filterIsInstance<ChargingWaypoint>().size
        val bigStops = bigRoute.waypoints.filterIsInstance<ChargingWaypoint>().size
        assertTrue("Small battery should need charging (got $smallStops stops)", smallStops > 0)
        assertTrue(
            "Bigger battery must produce fewer charging stops: bigStops=$bigStops, smallStops=$smallStops",
            bigStops < smallStops
        )
    }

    /**
     * Same trip with higher remaining charge must need fewer-or-equal charging stops.
     */
    @Test
    fun higherRemainingCapacityProducesFewerStops() = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")

        val lowSocRoute = routeComputeHelper.offlineRouteCompute(
            longRouteStart, longRouteDestination,
            routingOptions = evRoutingOptions(
                buildCarElectricProfile(batteryCapacity = 80F, remainingCapacity = 15F)
            )
        )
        val highSocRoute = routeComputeHelper.offlineRouteCompute(
            longRouteStart, longRouteDestination,
            routingOptions = evRoutingOptions(
                buildCarElectricProfile(batteryCapacity = 80F, remainingCapacity = 70F)
            )
        )

        val lowStops = lowSocRoute.waypoints.filterIsInstance<ChargingWaypoint>().size
        val highStops = highSocRoute.waypoints.filterIsInstance<ChargingWaypoint>().size
        assertTrue(
            "Higher remaining charge must not increase charging stops: lowStops=$lowStops, highStops=$highStops",
            highStops <= lowStops
        )
    }

    /**
     * A slower charging curve must yield longer total chargingTime on the same trip.
     */
    @Test
    fun chargingCurveImpactsChargingTime() = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")

        val fastRoute = routeComputeHelper.offlineRouteCompute(
            longRouteStart, longRouteDestination,
            routingOptions = evRoutingOptions(
                buildCarElectricProfile(
                    batteryCapacity = 50F,
                    remainingCapacity = 15F,
                    chargingCurve = mapOf(1.0 to 1.0, 100.0 to 1.0)
                )
            )
        )
        val slowRoute = routeComputeHelper.offlineRouteCompute(
            longRouteStart, longRouteDestination,
            routingOptions = evRoutingOptions(
                buildCarElectricProfile(
                    batteryCapacity = 50F,
                    remainingCapacity = 15F,
                    chargingCurve = mapOf(1.0 to 0.1, 100.0 to 0.1)
                )
            )
        )
        val fastChargingSum = fastRoute.waypoints.filterIsInstance<ChargingWaypoint>()
            .sumOf { it.chargingTime }
        val slowChargingSum = slowRoute.waypoints.filterIsInstance<ChargingWaypoint>()
            .sumOf { it.chargingTime }
        assertTrue("fast curve charging time must be > 0", fastChargingSum > 0)
        assertTrue(
            "Slower charging curve must produce longer charging time: slow=$slowChargingSum, fast=$fastChargingSum",
            slowChargingSum > fastChargingSum
        )
    }

    // endregion

    // region ----- Acceptance D: cross-API consistency -----

    /**
     * RouteExplorer.exploreChargingStationsOnRoute and the router's automatic charging waypoints
     * are different APIs (corridor-scan vs. detour-optimised pick), so a 1:1 station match is not
     * guaranteed — the router can pick a station off the explorer's corridor and vice versa.
     *
     * What we *do* require: both APIs must produce non-empty results on the same EV trip, and
     * there must be at least *some* overlap (within a generous 500 m tolerance) so integrators
     * can correlate the two listings without seeing them as completely disjoint.
     */
    @Test
    fun exploreChargingStationsIncludesRouteWaypointStations() = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")

        val options = smallBatteryCarOptions()
        val profile = options.vehicleProfile!!
        val route = routeComputeHelper.offlineRouteCompute(
            longRouteStart, longRouteDestination, routingOptions = options
        )
        val routeStations = route.waypoints.filterIsInstance<ChargingWaypoint>()
        assertTrue("Need charging waypoints to compare", routeStations.isNotEmpty())

        val explorer = RouteExplorerProvider.getInstance()
        val discovered = explorer.exploreChargingStationsOnRoute(route, profile)
            .filterIsInstance<ExploreChargingStationsOnRouteData.ChargingStationsLoaded>()
            .toList()
            .last()
            .chargingStations
        assertTrue("exploreChargingStationsOnRoute must find stations", discovered.isNotEmpty())

        val tolerance = 500.0
        val overlap = routeStations.count { cw ->
            val cwPos = cw.place?.position ?: return@count false
            discovered.any { it.place.position.distanceTo(cwPos) < tolerance }
        }
        assertTrue(
            "Expected at least one routed charging waypoint to be near an explored station " +
                    "within $tolerance m. Routed=${routeStations.size}, " +
                    "explored=${discovered.size}, overlap=$overlap",
            overlap > 0
        )
    }

    /**
     * InsufficientBatteryCharge warnings must be reported at positions that actually lie on
     * the route geometry — a warning floating off the route would be useless in the UI.
     */
    @Test
    fun insufficientBatteryWarningLocationIsOnRoute() = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")

        val profile = routeComputeHelper.createEVProfileForInsufficientBattery(2f, 2f)
        val options = evRoutingOptions(profile)
        val route = routeComputeHelper.offlineRouteCompute(
            GeoCoordinates(48.13435749214434, 17.139510367591342),
            GeoCoordinates(48.31550136420124, 18.050290453922088),
            routingOptions = options
        )
        val geometry = route.getRouteGeometry(false)
            ?: error("Route geometry must be available")
        val batteryWarnings = route.getRouteWarnings()
            .filterIsInstance<RouteWarning.LocationWarning.InsufficientBatteryCharge>()
        assertTrue("Expected InsufficientBatteryCharge warnings", batteryWarnings.isNotEmpty())
        batteryWarnings.forEach { warn ->
            val minDist = geometry.minOf { it.distanceTo(warn.location) }
            assertTrue(
                "Warning at ${warn.location} is $minDist m from nearest route point — should be on route",
                minDist < 500.0
            )
        }
    }

    // endregion

    // region ----- Acceptance E: scenario-based -----

    /**
     * A cross-border EV trip Bratislava → Vienna on a small battery produces a workable
     * charging plan with stops whose Place has a valid ISO (SK or AT).
     */
    @Test
    fun crossBorderEVRouteHasValidChargingStops() = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")
        mapDownloadHelper.installAndLoadMap("at")

        val start = GeoCoordinates(48.14548507020328, 17.126529723864405)
        val destination = GeoCoordinates(48.20849, 16.37208)

        val route = routeComputeHelper.offlineRouteCompute(
            start, destination,
            routingOptions = evRoutingOptions(
                buildCarElectricProfile(batteryCapacity = 30F, remainingCapacity = 10F)
            )
        )
        val chargingStops = route.waypoints.filterIsInstance<ChargingWaypoint>()
        assertTrue(
            "Cross-border trip with small battery must need at least one charging stop",
            chargingStops.isNotEmpty()
        )
        chargingStops.forEach {
            val iso = it.place?.iso?.lowercase()
            assertNotNull("Charging stop place iso must not be null", iso)
            assertTrue(
                "Charging stop ISO should be sk or at, was $iso",
                iso == "sk" || iso == "at"
            )
        }
    }

    /**
     * Economic vs Fastest routing must produce observably different plans on the same trip —
     * if RoutingType were ignored, both computes would return the same route.
     */
    @Test
    fun economicRoutingDiffersFromFastest() = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")

        val fastest = routeComputeHelper.offlineRouteCompute(
            longRouteStart, longRouteDestination,
            routingOptions = evRoutingOptions(buildCarElectricProfile()).apply {
                routingType = RoutingOptions.RoutingType.Fastest
            }
        )
        val economic = routeComputeHelper.offlineRouteCompute(
            longRouteStart, longRouteDestination,
            routingOptions = evRoutingOptions(buildCarElectricProfile()).apply {
                routingType = RoutingOptions.RoutingType.Economic
            }
        )
        val fastestLen = fastest.routeInfo.length
        val economicLen = economic.routeInfo.length
        val fastestStops = fastest.waypoints.filterIsInstance<ChargingWaypoint>().size
        val economicStops = economic.waypoints.filterIsInstance<ChargingWaypoint>().size
        assertTrue(
            "Economic must differ from Fastest (len=$fastestLen vs $economicLen, " +
                    "stops=$fastestStops vs $economicStops)",
            fastestLen != economicLen || fastestStops != economicStops
        )
    }

    /**
     * Recomputing a route from a mid-route position still yields a valid, ordered charging
     * plan — simulates the SDK handling a reroute when the driver deviates.
     *
     * The mid-route point is picked by accumulated distance (not geometry index), because
     * geometry density varies along a route — index-based picking can land close to the
     * destination on highway-heavy routes and produce a rerouted segment that is shorter than
     * the EV's initial range, which would legitimately need no charging.
     */
    @Test
    fun reroutingFromMidRouteStillPlansCharging() = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")

        val originalRoute = routeComputeHelper.offlineRouteCompute(
            longRouteStart, longRouteDestination,
            routingOptions = smallBatteryCarOptions()
        )
        assertTrue(
            "Original route must need charging",
            originalRoute.waypoints.filterIsInstance<ChargingWaypoint>().isNotEmpty()
        )

        val geometry = originalRoute.getRouteGeometry(false)
            ?: error("No route geometry available")

        val targetDistance = originalRoute.routeInfo.length / 3.0
        var accumulated = 0.0
        var midPoint = geometry.first()
        for (i in 1 until geometry.size) {
            accumulated += geometry[i - 1].distanceTo(geometry[i])
            if (accumulated >= targetDistance) {
                midPoint = geometry[i]
                break
            }
        }

        val reroutedRoute = routeComputeHelper.offlineRouteCompute(
            midPoint, longRouteDestination,
            routingOptions = smallBatteryCarOptions()
        )
        val reroutedLength = reroutedRoute.routeInfo.length
        val reroutedStops = reroutedRoute.waypoints.filterIsInstance<ChargingWaypoint>()
        assertTrue(
            "Rerouted segment must still include at least one charging stop. " +
                    "reroutedLength=$reroutedLength m, stops=${reroutedStops.size}",
            reroutedStops.isNotEmpty()
        )
        val distances = reroutedStops.map { it.distanceFromStart }
        assertEquals(
            "Rerouted charging stops must be ordered by distanceFromStart",
            distances.sorted(),
            distances
        )
    }

    // endregion

    // region ----- Helpers -----

    /**
     * Routing options for a car EV with a small remaining battery forcing charging on the long
     * cross-Slovakia route. Uses permissive charging preferences (no enforced power range) so the
     * router can pick any accessible charger on the map.
     */
    private fun smallBatteryCarOptions(): RoutingOptions = RoutingOptions().apply {
        vehicleProfile = buildCarElectricProfile(batteryCapacity = 50F, remainingCapacity = 15F)
        useEndpointProtection = true
        napStrategy = NearestAccessiblePointStrategy.Disabled
    }

    private fun buildCarElectricProfile(
        batteryCapacity: Float = 50F,
        remainingCapacity: Float = 15F,
        chargingCurve: Map<Double, Double> = mapOf(1.0 to 1.0, 100.0 to 1.0),
        connectors: List<Connector> = listOf(
            Connector(100F, ConnectorType.Type2, ConnectorFormat.Unknown, ChargingCurrent.AC),
            Connector(100F, ConnectorType.Ccs2, ConnectorFormat.Unknown, ChargingCurrent.DC)
        ),
        consumptionData: ConsumptionData = ConsumptionData(
            consumptionCurve = mapOf(1.0 to 1.0, 100.0 to 1.0),
            weightFactors = mapOf(1000.0 to 0.5, 5000.0 to 1.0, 10000.0 to 1.0)
        ),
        chargingPreferences: ChargingPreferences = ChargingPreferences(
            fullChargeThreshold = 0.8F,
            chargingThreshold = 0.2F,
            reserveThreshold = 0.05F,
            batteryMinimumDestinationThreshold = 0.3F
        )
    ): VehicleProfile {
        val battery = Battery(
            capacity = batteryCapacity,
            remainingCapacity = remainingCapacity,
            chargingCurve = chargingCurve
        )
        return VehicleProfile().apply {
            generalVehicleTraits.vehicleType = VehicleType.Car
            powertrainTraits = PowertrainTraits.ElectricPowertrain(
                battery, connectors, chargingPreferences, consumptionData
            )
        }
    }

    /** Standard routing options that use an EV profile + disable NAP/endpoint heuristics. */
    private fun evRoutingOptions(profile: VehicleProfile): RoutingOptions = RoutingOptions().apply {
        vehicleProfile = profile
        useEndpointProtection = true
        napStrategy = NearestAccessiblePointStrategy.Disabled
    }

    /**
     * Compute a route and return its status, not throwing on non-Success statuses (unlike
     * RouteComputeHelper.offlineRouteCompute) so that tests can assert on the specific failure.
     */
    private suspend fun computeRouteReturningStatus(
        start: GeoCoordinates,
        destination: GeoCoordinates,
        vehicleProfile: VehicleProfile
    ): Router.RouteComputeStatus {
        val request = RouteRequest().apply {
            setStart(start)
            setDestination(destination)
            routingOptions = RoutingOptions().apply {
                this.vehicleProfile = vehicleProfile
                this.routingService = RoutingOptions.RoutingService.Offline
            }
        }
        return router.computeRouteWithAlternatives(request)
            .filterIsInstance<ComputeRouteWithAlternativesData.RouteComputePrimaryFinished>()
            .first()
            .status
    }

    // endregion
}
