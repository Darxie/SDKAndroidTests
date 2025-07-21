package cz.feldis.sdkandroidtests.hereMapTests

import com.sygic.sdk.navigation.NavigationManager
import com.sygic.sdk.navigation.NavigationManagerProvider
import com.sygic.sdk.navigation.routeeventnotifications.RestrictionInfo
import com.sygic.sdk.position.GeoBoundingBox
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.route.RouteAvoids
import com.sygic.sdk.route.RouteManeuver
import com.sygic.sdk.route.RouteWarning
import com.sygic.sdk.route.RoutingOptions
import com.sygic.sdk.route.RoutingOptions.NearestAccessiblePointStrategy
import com.sygic.sdk.route.RoutingOptions.RoutingService
import com.sygic.sdk.route.listeners.RouteWarningsListener
import com.sygic.sdk.route.simulator.RouteDemonstrateSimulatorProvider
import com.sygic.sdk.vehicletraits.VehicleProfile
import com.sygic.sdk.vehicletraits.dimensional.Axle
import com.sygic.sdk.vehicletraits.dimensional.DimensionalTraits
import com.sygic.sdk.vehicletraits.dimensional.SemiTrailer
import com.sygic.sdk.vehicletraits.dimensional.Trailer
import com.sygic.sdk.vehicletraits.general.GeneralVehicleTraits
import com.sygic.sdk.vehicletraits.general.VehicleType
import com.sygic.sdk.vehicletraits.hazmat.HazmatTraits
import com.sygic.sdk.vehicletraits.powertrain.ConsumptionData
import com.sygic.sdk.vehicletraits.powertrain.EuropeanEmissionStandard
import com.sygic.sdk.vehicletraits.powertrain.FuelType
import com.sygic.sdk.vehicletraits.powertrain.PowertrainTraits
import cz.feldis.sdkandroidtests.ktx.NavigationManagerKtx
import cz.feldis.sdkandroidtests.mapInstaller.MapDownloadHelper
import cz.feldis.sdkandroidtests.routing.RouteComputeHelper
import cz.feldis.sdkandroidtests.utils.GeoUtils
import cz.feldis.sdkandroidtests.utils.RouteDemonstrateSimulatorAdapter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify

class HereTests : BaseHereTest() {

    private lateinit var mapDownloadHelper: MapDownloadHelper
    private lateinit var routeComputeHelper: RouteComputeHelper
    private val navigationManagerKtx = NavigationManagerKtx()
    private lateinit var navigation: NavigationManager

    override fun setUp() {
        super.setUp()
        mapDownloadHelper = MapDownloadHelper()
        routeComputeHelper = RouteComputeHelper()
        navigation = runBlocking { NavigationManagerProvider.getInstance() }
        disableOnlineMaps()
    }

    @Test
    fun compositeHazmatWeightExceededTest() {
        mapDownloadHelper.installAndLoadMap("fr-10")

        val routeWarningsListener: RouteWarningsListener = mock(verboseLogging = true)

        val start = GeoCoordinates(48.62069, 2.97320)
        val destination = GeoCoordinates(48.62257, 2.97555)
        val routingOptions = RoutingOptions().apply {
            vehicleProfile = routeComputeHelper.createCombustionVehicleProfile().apply {
                generalVehicleTraits.vehicleType = VehicleType.Truck
                dimensionalTraits = DimensionalTraits().apply {
                    totalWeight = 20_000F
                }
                hazmatTraits = HazmatTraits().apply {
                    hazmatClasses = HazmatTraits.GeneralHazardousMaterialClasses
                }
            }
            useEndpointProtection = true
            napStrategy = NearestAccessiblePointStrategy.Disabled
        }

        val route = routeComputeHelper.offlineRouteCompute(
            start,
            destination,
            routingOptions = routingOptions
        )

        val captor = argumentCaptor<List<RouteWarning>>()

        route.getRouteWarnings(routeWarningsListener)
        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(argThat {
            this.find { it is RouteWarning.SectionWarning.WeightRestriction.ExceededGrossWeight } != null
        })
        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(argThat {
            this.isNotEmpty()
        })
        verify(routeWarningsListener, timeout(5_000)).onRouteWarnings(captor.capture())

        val restriction =
            captor.firstValue[0] as RouteWarning.SectionWarning.WeightRestriction.ExceededGrossWeight
        assertTrue(restriction.limitValue == 10_000F)
        assertTrue(restriction.realValue == 20_000F)
        assertTrue(restriction.iso == "fr-10")
    }

    @Test
    fun vehicleAidMaxTrailers() = runBlocking {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("se")
        val vehicleProfile = VehicleProfile().apply {
            this.dimensionalTraits = DimensionalTraits().apply {
                this.totalHeight = 8000
                this.trailers = listOf(
                    Trailer(1000, false, listOf(Axle(2, 500F, 2))),
                    Trailer(1000, false, listOf(Axle(2, 500F, 2))),
                    Trailer(1000, false, listOf(Axle(2, 500F, 2))),
                    Trailer(1000, false, listOf(Axle(2, 500F, 2))),
                    Trailer(1000, false, listOf(Axle(2, 500F, 2)))
                )
            }
            this.generalVehicleTraits = GeneralVehicleTraits().apply {
                this.vehicleType = VehicleType.Truck
            }
        }

        val listener = mock<NavigationManager.OnVehicleAidListener>(verboseLogging = true)

        val route = routeComputeHelper.offlineRouteCompute(
            start = GeoCoordinates(57.6456, 14.9804),
            destination = GeoCoordinates(57.6452, 14.9781),
            routingOptions = RoutingOptions().apply {
                this.vehicleProfile = vehicleProfile
                routingService = RoutingService.Offline
                useEndpointProtection = true
                napStrategy = NearestAccessiblePointStrategy.Disabled
            }
        )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        navigation.addOnVehicleAidListener(listener)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route)
        val demonstrateSimulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.setSpeedMultiplier(demonstrateSimulatorAdapter, 1F)
        navigationManagerKtx.startSimulator(demonstrateSimulatorAdapter)

        verify(listener, timeout(10_000)).onVehicleAidInfo(argThat {
            for (vehicleAidInfo in this) {
                if (vehicleAidInfo.restriction.type == RestrictionInfo.RestrictionType.LimitsMaxTrailers) {
                    return@argThat true
                }
            }
            return@argThat false
        })

        navigationManagerKtx.stopSimulator(demonstrateSimulatorAdapter)
        navigation.removeOnVehicleAidListener(listener)
        navigationManagerKtx.stopNavigation(navigation)
    }

    //HANKA3
    @Test
    fun vehicleAidWheelCountRestriction() = runBlocking {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("th")

        val vehicleProfile = VehicleProfile().apply {
            this.dimensionalTraits = DimensionalTraits().apply {
                this.semiTrailer = SemiTrailer(
                    60000, false,
                    listOf(Axle(8, 11000F, 8))
                )
            }
            this.generalVehicleTraits = GeneralVehicleTraits().apply {
                this.vehicleType = VehicleType.Truck
            }
        }

        val listener = mock<NavigationManager.OnVehicleAidListener>(verboseLogging = true)

        val route = routeComputeHelper.offlineRouteCompute(
            start = GeoCoordinates(13.79803, 100.56111),
            destination = GeoCoordinates(13.79673, 100.56273),
            routingOptions = RoutingOptions().apply {
                this.vehicleProfile = vehicleProfile
                routingService = RoutingService.Offline
                useEndpointProtection = true
                napStrategy = NearestAccessiblePointStrategy.Disabled
            }
        )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        navigation.addOnVehicleAidListener(listener)

        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route)
        val demonstrateSimulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.setSpeedMultiplier(demonstrateSimulatorAdapter, 1F)
        navigationManagerKtx.startSimulator(demonstrateSimulatorAdapter)

        verify(listener, timeout(10_000)).onVehicleAidInfo(argThat {
            for (vehicleAidInfo in this) {
                if (vehicleAidInfo.restriction.type == RestrictionInfo.RestrictionType.LimitsMaxWheels) {
                    if (vehicleAidInfo.restriction.value == 5)
                        return@argThat true
                }
            }
            return@argThat false
        })

        navigationManagerKtx.stopSimulator(demonstrateSimulatorAdapter)
        navigation.removeOnVehicleAidListener(listener)
        navigationManagerKtx.stopNavigation(navigation)
    }

    @Test
    fun correctUTurnInstructionBajkalska() {
        mapDownloadHelper.installAndLoadMap("sk")
        val route =
            routeComputeHelper.offlineRouteCompute(
                GeoCoordinates(48.147260, 17.150520),
                GeoCoordinates(48.147230, 17.150120)
            )
        assertEquals(route.maneuvers[0].type, RouteManeuver.Type.UTurnLeft)
    }

    @Test
    fun testStreetChangedListenerOfflineHere() = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")
        val navigation = NavigationManagerProvider.getInstance()
        val listener: NavigationManager.StreetChangedListener = mock(verboseLogging = true)

        val route = routeComputeHelper.offlineRouteCompute(
            GeoCoordinates(48.1209419355147, 17.207606308128618),
            GeoCoordinates(48.12276083935055, 17.207632634218143),
        )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        navigation.addStreetChangedListener(listener)

        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route)
        val demonstrateSimulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.setSpeedMultiplier(demonstrateSimulatorAdapter, 1F)
        navigationManagerKtx.startSimulator(demonstrateSimulatorAdapter)

        verify(listener, timeout(10_000L)).onStreetChanged(argThat {
            return@argThat this.street == "Mramorová"
        })

        navigationManagerKtx.stopSimulator(demonstrateSimulatorAdapter)
        navigation.removeStreetChangedListener(listener)
        navigationManagerKtx.stopNavigation(navigation)
    }

    /***
     * https://jira.sygic.com/browse/CI-3412
     */
    @Test
    fun vehicleAidZonePaid() = runBlocking {
        mapDownloadHelper.installAndLoadMap("gb-02")

        val zonePaidCondition = PowertrainTraits.InternalCombustionPowertrain(
            fuelType = FuelType.Diesel,
            europeanEmissionStandard = EuropeanEmissionStandard.Euro2,
            consumptionData = ConsumptionData()
        )

        val vehicleProfile = VehicleProfile().apply {
            this.generalVehicleTraits = GeneralVehicleTraits().apply {
                this.vehicleType = VehicleType.Car
            }
            this.powertrainTraits = zonePaidCondition
        }

        val listener = mock<NavigationManager.OnVehicleAidListener>(verboseLogging = true)

        val route = routeComputeHelper.offlineRouteCompute(
            start = GeoCoordinates(55.945820, -3.206570),
            destination = GeoCoordinates(55.944850, -3.199610),
            routingOptions = RoutingOptions().apply {
                this.vehicleProfile = vehicleProfile
                routingService = RoutingService.Offline
                useEndpointProtection = true
                napStrategy = NearestAccessiblePointStrategy.Disabled
            }
        )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        navigation.addOnVehicleAidListener(listener)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route)
        val demonstrateSimulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.setSpeedMultiplier(demonstrateSimulatorAdapter, 1F)
        navigationManagerKtx.startSimulator(demonstrateSimulatorAdapter)

        verify(listener, timeout(10_000)).onVehicleAidInfo(argThat { aidInfoList ->
            aidInfoList.any { vehicleAidInfo ->
                vehicleAidInfo.restriction.type == RestrictionInfo.RestrictionType.ZonePaid
            }
        })

        navigationManagerKtx.stopSimulator(demonstrateSimulatorAdapter)
        navigation.removeOnVehicleAidListener(listener)
        navigationManagerKtx.stopNavigation(navigation)
    }

    /***
     * https://jira.sygic.com/browse/SDC-12634
     */
    @Test
    @Ignore("doesn't work")
    fun theRouteReturnsToTheHighwaySwedenTest() = runBlocking {
        mapDownloadHelper.installAndLoadMap("se")

        val vehicleProfile = VehicleProfile().apply {
            this.dimensionalTraits = DimensionalTraits().apply {
                this.totalWeight = 10000.0F
                this.totalLength = 16500
                this.totalHeight = 3000
                this.totalWidth = 2500
            }
            this.generalVehicleTraits = GeneralVehicleTraits().apply {
                this.vehicleType = VehicleType.Truck
            }
        }

        val route = routeComputeHelper.offlineRouteCompute(
            GeoCoordinates(57.46712, 12.06938),
            GeoCoordinates(57.499780, 12.052100),
            listOf(GeoCoordinates(57.471980, 12.060330)),
            routingOptions = RoutingOptions().apply {
                this.routingType = RoutingOptions.RoutingType.Fastest
                this.vehicleProfile = vehicleProfile
                useEndpointProtection = true
                useSpeedProfiles = true
                useTraffic = true
                napStrategy = NearestAccessiblePointStrategy.Disabled
            }
        )
        // Check if there's at least one maneuver matching the criteria
        val hasExpectedRoundaboutExit = route.maneuvers.any { maneuver ->
            maneuver.type == RouteManeuver.Type.RoundaboutS &&
                    maneuver.roundaboutExit == 4
        }

        assertTrue(
            hasExpectedRoundaboutExit
        )
    }

    /**
     * https://jira.sygic.com/browse/SDC-12572
     * Route should go straight and should no have any detours (which would most likely be to the right).
     * Therefore we check that there is no right turn.
     */
    @Test
    fun lowerHeavyTruckPenaltiesSwedenTest() = runBlocking {
        mapDownloadHelper.installAndLoadMap("se")

        val vehicleProfile = VehicleProfile().apply {
            this.dimensionalTraits = DimensionalTraits().apply {
                this.totalWeight = 30000.0F
                this.totalLength = 18000
                this.totalHeight = 4000
                this.totalWidth = 2500
            }
            this.generalVehicleTraits = GeneralVehicleTraits().apply {
                this.vehicleType = VehicleType.Truck
            }
        }

        val route = routeComputeHelper.offlineRouteCompute(
            GeoCoordinates(58.351620, 11.845900),
            GeoCoordinates(58.347790, 11.792080),
            routingOptions = RoutingOptions().apply {
                this.routingType = RoutingOptions.RoutingType.Fastest
                this.vehicleProfile = vehicleProfile
                this.useEndpointProtection = true
                this.useSpeedProfiles = false
                this.useTraffic = false
                this.napStrategy = NearestAccessiblePointStrategy.Disabled
            }
        )
        val unwantedManeuver = route.maneuvers.any { maneuver ->
            maneuver.type == RouteManeuver.Type.Right
        }

        assertFalse(
            "Route contains an unwanted RIGHT maneuver.", unwantedManeuver,
        )
    }

    /***
     * https://jira.sygic.com/browse/SDC-12637
     * TC869
     *
     * In this test, we compute a route from Copenhagen to Goteborg. We know that there was
     * a problematic highway detour near Malmo, so we check that there is no maneuver present
     * in the depicted bounding box. To be sure, we also check that there is no maneuver
     * in that specific distance from start range.
     */
    @Test
    fun exitFromTheRouteSwedenDenmarkTest() = runBlocking {
        disableOnlineMaps()
        mapDownloadHelper.installAndLoadMap("se")
        mapDownloadHelper.installAndLoadMap("dk")

        val vehicleProfile = VehicleProfile().apply {
            this.dimensionalTraits = DimensionalTraits().apply {
                totalWeight = 40000.0F
                totalLength = 16500
                totalHeight = 4100
                totalWidth = 2450
            }
            this.generalVehicleTraits = GeneralVehicleTraits().apply {
                vehicleType = VehicleType.Truck
            }
        }

        val boundingBox = GeoBoundingBox(
            topLeft = GeoCoordinates(55.567639303872824, 13.063895982391863),
            bottomRight = GeoCoordinates(55.549896844375866, 13.088053326229309)
        )

        val route = routeComputeHelper.offlineRouteCompute(
            GeoCoordinates(55.614150, 12.505980),
            GeoCoordinates(57.700670, 11.968220),
            routingOptions = RoutingOptions().apply {
                this.routingType = RoutingOptions.RoutingType.Fastest
                this.vehicleProfile = vehicleProfile
                this.useEndpointProtection = true
                this.napStrategy = NearestAccessiblePointStrategy.Disabled
            }
        )

        val maneuversInBoundingBox = route.maneuvers.filter { maneuver ->
            GeoUtils.isPointInBoundingBox(maneuver.position, boundingBox)
        }

        assertTrue(
            "Route contains unexpected maneuvers within the bounding box: $maneuversInBoundingBox",
            maneuversInBoundingBox.isEmpty()
        )

        val unwantedManeuversInRange = route.maneuvers.filter { maneuver ->
            maneuver.distanceFromStart in 39000..41000
        }

        assertTrue(
            "Unexpected maneuvers found within the suspicious distance range: $unwantedManeuversInRange",
            unwantedManeuversInRange.isEmpty(),
        )
    }

    /***
     * https://jira.sygic.com/browse/SDC-11895
     * TC856
     */
    @Test
    fun exitFromTheRouteSlovakiaTest() = runBlocking {
        mapDownloadHelper.installAndLoadMap("sk")

        val vehicleProfile = VehicleProfile().apply {
            this.generalVehicleTraits = GeneralVehicleTraits().apply {
                vehicleType = VehicleType.Car
            }
        }

        val boundingBox = GeoBoundingBox(
            topLeft = GeoCoordinates(48.31055, 17.54599),
            bottomRight = GeoCoordinates(48.30566, 17.57050)
        )

        val route = routeComputeHelper.offlineRouteCompute(
            GeoCoordinates(48.213170, 17.265160),
            GeoCoordinates(48.983180, 18.402170),
            routingOptions = RoutingOptions().apply {
                this.routingType = RoutingOptions.RoutingType.Fastest
                this.vehicleProfile = vehicleProfile
                this.useEndpointProtection = true
                this.napStrategy = NearestAccessiblePointStrategy.Disabled
            }
        )

        val maneuversInBoundingBox = route.maneuvers.filter { maneuver ->
            GeoUtils.isPointInBoundingBox(maneuver.position, boundingBox)
        }

        assertTrue(
            "Route contains unexpected maneuvers within the bounding box: $maneuversInBoundingBox",
            maneuversInBoundingBox.isEmpty()
        )

        val unwantedManeuversInRange = route.maneuvers.filter { maneuver ->
            maneuver.distanceFromStart in 24000..26000
        }

        assertTrue(
            "Unexpected maneuvers found within the suspicious distance range: $unwantedManeuversInRange",
            unwantedManeuversInRange.isEmpty(),
        )
    }

    /**
     * https://jira.sygic.com/browse/SDC-12263
     * TC867
     */
    @Test
    fun busRoutingAustraliaTest() {
        mapDownloadHelper.installAndLoadMap("au-05")
        val start = GeoCoordinates(-27.665280, 153.376730)
        val destination = GeoCoordinates(-27.669720, 153.378950)
        val routeCompute = RouteComputeHelper()
        val routingOptions = RoutingOptions().apply {
            this.vehicleProfile = VehicleProfile().apply {
                this.generalVehicleTraits.vehicleType = VehicleType.Bus
                this.generalVehicleTraits.maximalSpeed = 100
            }
            this.napStrategy = NearestAccessiblePointStrategy.Disabled
            this.useEndpointProtection = true
            this.useSpeedProfiles = true
            this.routeAvoids.globalRouteAvoids = mutableSetOf(RouteAvoids.Type.UnpavedRoad)
        }

        val routeBus = routeCompute.offlineRouteCompute(
            start,
            destination,
            routingOptions = routingOptions
        )
        val estimatedTimeOfArrivalBus =
            routeBus.routeInfo.waypointDurations.last().withSpeedProfileAndTraffic

        assertTrue(
            estimatedTimeOfArrivalBus > 120
        )
    }
}