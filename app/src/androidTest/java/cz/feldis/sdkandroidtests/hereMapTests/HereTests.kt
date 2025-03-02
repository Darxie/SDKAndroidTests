package cz.feldis.sdkandroidtests.hereMapTests

import com.sygic.sdk.navigation.NavigationManager
import com.sygic.sdk.navigation.NavigationManagerProvider
import com.sygic.sdk.navigation.routeeventnotifications.RestrictionInfo
import com.sygic.sdk.position.GeoCoordinates
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
import cz.feldis.sdkandroidtests.ktx.NavigationManagerKtx
import cz.feldis.sdkandroidtests.mapInstaller.MapDownloadHelper
import cz.feldis.sdkandroidtests.routing.RouteComputeHelper
import cz.feldis.sdkandroidtests.utils.RouteDemonstrateSimulatorAdapter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
        navigation = NavigationManagerProvider.getInstance().get()
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
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
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

        verify(listener, timeout(10_000)).onVehicleAidInfo(argThat {
            for (vehicleAidInfo in this) {
                if (vehicleAidInfo.restriction.type == RestrictionInfo.RestrictionType.LimitsMaxWheels) {
                    if (vehicleAidInfo.restriction.value == 5)
                        return@argThat true
                }
            }
            return@argThat false
        })
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
}