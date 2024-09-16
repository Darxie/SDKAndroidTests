package cz.feldis.sdkandroidtests.navigation

import org.mockito.kotlin.*
import com.sygic.sdk.navigation.NavigationManager
import com.sygic.sdk.navigation.NavigationManagerProvider
import com.sygic.sdk.navigation.routeeventnotifications.RestrictionInfo
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.route.RoutingOptions
import com.sygic.sdk.route.RoutingOptions.NearestAccessiblePointStrategy
import com.sygic.sdk.route.RoutingOptions.RoutingService
import com.sygic.sdk.vehicletraits.VehicleProfile
import com.sygic.sdk.vehicletraits.dimensional.Axle
import com.sygic.sdk.vehicletraits.dimensional.DimensionalTraits
import com.sygic.sdk.vehicletraits.dimensional.Trailer
import com.sygic.sdk.vehicletraits.general.GeneralVehicleTraits
import com.sygic.sdk.vehicletraits.general.VehicleType
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.ktx.NavigationManagerKtx
import cz.feldis.sdkandroidtests.mapInstaller.MapDownloadHelper
import cz.feldis.sdkandroidtests.routing.RouteComputeHelper
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

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

    @Test
    fun vehicleAidMaxTrailers() = runBlocking {
        disableOnlineMaps()
        mapDownload.installAndLoadMap("se")
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

        val route = routeCompute.offlineRouteCompute(
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

        verify(listener, timeout(10_000)).onVehicleAidInfo(argThat {
            for (vehicleAidInfo in this) {
                if (vehicleAidInfo.restriction.type == RestrictionInfo.RestrictionType.LimitsMaxTrailers) {
                    return@argThat true
                }
            }
            return@argThat false
        })

        navigation.removeOnVehicleAidListener(listener)
        navigationManagerKtx.stopNavigation(navigation)
    }
}