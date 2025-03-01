package cz.feldis.sdkandroidtests.navigation

import com.sygic.sdk.navigation.NavigationManager
import com.sygic.sdk.navigation.NavigationManagerProvider
import com.sygic.sdk.navigation.routeeventnotifications.RestrictionInfo
import com.sygic.sdk.navigation.routeeventnotifications.VehicleZoneInfo
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.route.RoutingOptions
import com.sygic.sdk.route.RoutingOptions.NearestAccessiblePointStrategy
import com.sygic.sdk.route.RoutingOptions.RoutingService
import com.sygic.sdk.route.simulator.NmeaLogSimulatorProvider
import com.sygic.sdk.route.simulator.RouteDemonstrateSimulatorProvider
import com.sygic.sdk.vehicletraits.VehicleProfile
import com.sygic.sdk.vehicletraits.dimensional.DimensionalTraits
import com.sygic.sdk.vehicletraits.general.GeneralVehicleTraits
import com.sygic.sdk.vehicletraits.general.VehicleType
import com.sygic.sdk.vehicletraits.hazmat.HazmatTraits
import com.sygic.sdk.vehicletraits.listeners.SetVehicleProfileListener
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.NmeaFileDataProvider
import cz.feldis.sdkandroidtests.ktx.NavigationManagerKtx
import cz.feldis.sdkandroidtests.mapInstaller.MapDownloadHelper
import cz.feldis.sdkandroidtests.routing.RouteComputeHelper
import cz.feldis.sdkandroidtests.utils.NmeaLogSimulatorAdapter
import cz.feldis.sdkandroidtests.utils.RouteDemonstrateSimulatorAdapter
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify

class VehicleAidTests : BaseTest() {
    private lateinit var routeCompute: RouteComputeHelper
    private lateinit var mapDownload: MapDownloadHelper
    private val navigationManagerKtx = NavigationManagerKtx()
    private lateinit var navigation: NavigationManager

    @Before
    override fun setUp() {
        super.setUp()
        routeCompute = RouteComputeHelper()
        mapDownload = MapDownloadHelper()
        disableOnlineMaps()
        navigation = NavigationManagerProvider.getInstance().get()
    }

    @Test
    fun vehicleAidMaxHeight() = runBlocking {
        mapDownload.installAndLoadMap("sk")

        val vehicleProfile = VehicleProfile().apply {
            this.dimensionalTraits = DimensionalTraits().apply {
                this.totalHeight = 8000
            }
            this.generalVehicleTraits = GeneralVehicleTraits().apply {
                this.vehicleType = VehicleType.Truck
            }
        }

        val listener = mock<NavigationManager.OnVehicleAidListener>(verboseLogging = true)

        val route = routeCompute.offlineRouteCompute(
            start = GeoCoordinates(48.7708, 18.6103),
            destination = GeoCoordinates(48.7703, 18.6136),
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
                if (vehicleAidInfo.restriction.type == RestrictionInfo.RestrictionType.DimensionalHeightMax) {
                    if (vehicleAidInfo.restriction.value == 3600)
                        return@argThat true
                }
            }
            return@argThat false
        })

        navigation.removeOnVehicleAidListener(listener)
        navigationManagerKtx.stopNavigation(navigation)
    }

    @Test
    fun vehicleAidMaxHeightCheckRestrictedRoad() = runBlocking {
        val vehicleProfile = VehicleProfile().apply {
            this.dimensionalTraits = DimensionalTraits().apply {
                this.totalHeight = 8000
            }
            this.generalVehicleTraits = GeneralVehicleTraits().apply {
                this.vehicleType = VehicleType.Truck
            }
        }

        mapDownload.installAndLoadMap("sk")

        val listener = mock<NavigationManager.OnVehicleAidListener>(verboseLogging = true)

        val route = routeCompute.offlineRouteCompute(
            start = GeoCoordinates(48.113, 17.2198),
            destination = GeoCoordinates(48.1153, 17.2172),
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
                if (vehicleAidInfo.restriction.type == RestrictionInfo.RestrictionType.DimensionalHeightMax) {
                    if (vehicleAidInfo.restrictedRoad)
                        return@argThat true
                }
            }
            return@argThat false
        })

        navigation.removeOnVehicleAidListener(listener)
        navigationManagerKtx.stopNavigation(navigation)
    }

    //HANKA1
    @Test
    fun vehicleAidMaxLength() = runBlocking {
        mapDownload.installAndLoadMap("sk")

        val vehicleProfile = VehicleProfile().apply {
            this.dimensionalTraits = DimensionalTraits().apply {
                this.totalLength = 16500
            }
            this.generalVehicleTraits = GeneralVehicleTraits().apply {
                this.vehicleType = VehicleType.Truck
            }
        }

        val listener = mock<NavigationManager.OnVehicleAidListener>(verboseLogging = true)

        val route = routeCompute.offlineRouteCompute(
            start = GeoCoordinates(48.3182, 17.2444),
            destination = GeoCoordinates(48.3258, 17.2351),
            routingOptions = RoutingOptions().apply {
                this.vehicleProfile = vehicleProfile
                useEndpointProtection = true
                useSpeedProfiles = false
                useTraffic = false
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
                if (vehicleAidInfo.restriction.type == RestrictionInfo.RestrictionType.DimensionalLengthMax) {
                    if (vehicleAidInfo.restriction.value == 10000)
                        return@argThat true
                }
            }
            return@argThat false
        })

        navigationManagerKtx.stopSimulator(demonstrateSimulatorAdapter)
        navigation.removeOnVehicleAidListener(listener)
        navigationManagerKtx.stopNavigation(navigation)
    }

    //HANKA2
    @Test
    fun vehicleAidMaxWeight() = runBlocking {
        mapDownload.installAndLoadMap("sk")

        val vehicleProfile = VehicleProfile().apply {
            this.dimensionalTraits = DimensionalTraits().apply {
                this.totalWeight = 44000.0F
            }
            this.generalVehicleTraits = GeneralVehicleTraits().apply {
                this.vehicleType = VehicleType.Truck
            }
        }

        val listener = mock<NavigationManager.OnVehicleAidListener>(verboseLogging = true)

        val route = routeCompute.offlineRouteCompute(
            start = GeoCoordinates(48.3182, 17.2444),
            destination = GeoCoordinates(48.3258, 17.2351),
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
                if (vehicleAidInfo.restriction.type == RestrictionInfo.RestrictionType.WeightMax) {
                    if (vehicleAidInfo.restriction.value == 7500)
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
    fun testVehicleZoneSitinaWithoutRoute() = runBlocking {
        mapDownload.installAndLoadMap("sk")
        val setVehicleListener: SetVehicleProfileListener = mock(verboseLogging = true)
        val vehicleZoneListener: NavigationManager.OnVehicleZoneListener =
            mock(verboseLogging = true)
        val vehicleProfile = VehicleProfile().apply {
            this.hazmatTraits = HazmatTraits(HazmatTraits.GeneralHazardousMaterialClasses)
            this.generalVehicleTraits = GeneralVehicleTraits().apply {
                this.vehicleType = VehicleType.Truck
            }
        }

        navigation.setVehicleProfile(vehicleProfile, setVehicleListener)
        verify(setVehicleListener, timeout(3_000)).onSuccess()
        verify(setVehicleListener, never()).onError()
        navigation.addOnVehicleZoneListener(vehicleZoneListener)

        val nmeaDataProvider = NmeaFileDataProvider(appContext, "sitina.nmea")
        val logSimulator = NmeaLogSimulatorProvider.getInstance(nmeaDataProvider).get()
        val logSimulatorAdapter = NmeaLogSimulatorAdapter(logSimulator)
        navigationManagerKtx.setSpeedMultiplier(logSimulatorAdapter, 1F)
        navigationManagerKtx.startSimulator(logSimulatorAdapter)

        verify(vehicleZoneListener, timeout(10_000L)).onVehicleZoneInfo(argThat {
            this.forEach {
                if (it.restriction.type == RestrictionInfo.RestrictionType.CargoHazmat && it.eventType == VehicleZoneInfo.EventType.In) {
                    return@argThat true
                }
            }
            return@argThat false
        })

        navigationManagerKtx.stopSimulator(logSimulatorAdapter)
        navigation.removeOnVehicleZoneListener(vehicleZoneListener)
        navigationManagerKtx.stopNavigation(navigation)
    }
}