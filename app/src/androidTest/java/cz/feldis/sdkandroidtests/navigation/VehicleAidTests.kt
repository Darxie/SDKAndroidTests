package cz.feldis.sdkandroidtests.navigation

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.timeout
import com.nhaarman.mockitokotlin2.verify
import com.sygic.sdk.map.Camera
import com.sygic.sdk.map.CameraState
import com.sygic.sdk.map.LogisticInfoSettings
import com.sygic.sdk.map.MapAnimation
import com.sygic.sdk.map.MapCenter
import com.sygic.sdk.map.MapCenterSettings
import com.sygic.sdk.map.MapView
import com.sygic.sdk.map.listeners.OnMapInitListener
import com.sygic.sdk.navigation.NavigationManager
import com.sygic.sdk.navigation.NavigationManagerProvider
import com.sygic.sdk.navigation.routeeventnotifications.RestrictionInfo
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.position.GeoPosition
import com.sygic.sdk.route.RoutingOptions
import com.sygic.sdk.route.RoutingOptions.NearestAccessiblePointStrategy
import com.sygic.sdk.route.RoutingOptions.RoutingService
import com.sygic.sdk.route.RoutingOptions.TransportMode
import com.sygic.sdk.route.RoutingOptions.VehicleRestrictions
import com.sygic.sdk.route.simulator.NmeaLogSimulatorProvider
import com.sygic.sdk.route.simulator.PositionSimulator
import com.sygic.sdk.route.simulator.PositionSimulator.PositionSimulatorListener
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.SygicActivity
import cz.feldis.sdkandroidtests.TestMapFragment
import cz.feldis.sdkandroidtests.mapInstaller.MapDownloadHelper
import cz.feldis.sdkandroidtests.routing.RouteComputeHelper
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import timber.log.Timber

class VehicleAidTests: BaseTest() {
    private lateinit var routeCompute: RouteComputeHelper
    private lateinit var mapDownload: MapDownloadHelper

    @Before
    override fun setUp() {
        super.setUp()
        routeCompute = RouteComputeHelper()
        mapDownload = MapDownloadHelper()
        disableOnlineMaps()
    }

    @Test
    fun vehicleAidMaxHeight() {
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        // create test scenario with activity & map fragment
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager
                .beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }

        mapDownload.installAndLoadMap("sk")

        val mapView = getMapView(mapFragment)
        val logisticInfoSettings = LogisticInfoSettings()
        logisticInfoSettings.maximumHeight = 8000
        logisticInfoSettings.vehicleType = LogisticInfoSettings.VehicleType.Truck
        mapView.setLogisticInfoSettings(logisticInfoSettings)
        mapView.cameraModel.position = GeoCoordinates( 48.7703, 18.6136)

        val listener = mock<NavigationManager.OnVehicleAidListener>(verboseLogging = true)

        val route = routeCompute.offlineRouteCompute(
            start = GeoCoordinates(48.76948041613606, 18.617953844571446),
            destination = GeoCoordinates( 48.7703, 18.6136),
            routingOptions = RoutingOptions().apply {
                transportMode = TransportMode.TransportTruck
                addDimensionalRestriction(VehicleRestrictions.Height, 8000)
                routingService = RoutingService.Offline
                setUseEndpointProtection(true)
                napStrategy = NearestAccessiblePointStrategy.Disabled
            }
        )

        NavigationManagerProvider.getInstance().get().setRouteForNavigation(route)
        NavigationManagerProvider.getInstance().get().addOnVehicleAidListener(listener)

        verify(listener, timeout(10_000)).onVehicleAidInfo(argThat {
            for (vehicleAidInfo in this) {
                if (vehicleAidInfo.restriction.type == RestrictionInfo.RestrictionType.DimensionalHeightMax) {
                    if (vehicleAidInfo.restriction.value == 3600)
                        return@argThat true
                }
            }
            return@argThat false
        })

        NavigationManagerProvider.getInstance().get().removeOnVehicleAidListener(listener)
        NavigationManagerProvider.getInstance().get().stopNavigation()
        scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    @Test
    fun vehicleAidMaxHeightCheckRestrictedRoad() {
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        // create test scenario with activity & map fragment
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager
                .beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }

        mapDownload.installAndLoadMap("sk")

        val mapView = getMapView(mapFragment)
        val logisticInfoSettings = LogisticInfoSettings()
        logisticInfoSettings.maximumHeight = 8000
        logisticInfoSettings.vehicleType = LogisticInfoSettings.VehicleType.Truck
        mapView.setLogisticInfoSettings(logisticInfoSettings)
        mapView.cameraModel.position = GeoCoordinates( 48.113, 17.2198)

        val listener = mock<NavigationManager.OnVehicleAidListener>(verboseLogging = true)

        val route = routeCompute.offlineRouteCompute(
            start = GeoCoordinates(48.113, 17.2198),
            destination = GeoCoordinates( 48.1153, 17.2172),
            routingOptions = RoutingOptions().apply {
                transportMode = TransportMode.TransportTruck
                addDimensionalRestriction(VehicleRestrictions.Height, 8000)
                routingService = RoutingService.Offline
                setUseEndpointProtection(true)
                napStrategy = NearestAccessiblePointStrategy.Disabled
            }
        )

        NavigationManagerProvider.getInstance().get().setRouteForNavigation(route)
        NavigationManagerProvider.getInstance().get().addOnVehicleAidListener(listener)

        verify(listener, timeout(10_000)).onVehicleAidInfo(argThat {
            for (vehicleAidInfo in this) {
                if (vehicleAidInfo.restriction.type == RestrictionInfo.RestrictionType.DimensionalHeightMax) {
                    if (vehicleAidInfo.restrictedRoad)
                        return@argThat true
                }
            }
            return@argThat false
        })

        NavigationManagerProvider.getInstance().get().removeOnVehicleAidListener(listener)
        NavigationManagerProvider.getInstance().get().stopNavigation()
        scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    private fun getInitialCameraState(): CameraState {
        return CameraState.Builder().apply {
            setPosition(GeoCoordinates(48.15132, 17.07665))
            setMapCenterSettings(
                MapCenterSettings(
                    MapCenter(0.5f, 0.5f),
                    MapCenter(0.5f, 0.5f),
                    MapAnimation.NONE, MapAnimation.NONE
                )
            )
            setMapPadding(0.0f, 0.0f, 0.0f, 0.0f)
            setRotation(0f)
            setZoomLevel(14F)
            setMovementMode(Camera.MovementMode.Free)
            setRotationMode(Camera.RotationMode.Free)
            setTilt(0f)
        }.build()
    }

    private fun getMapView(mapFragment: TestMapFragment): MapView {
        val mapInitListener : OnMapInitListener = mock(verboseLogging = true)
        val mapViewCaptor = argumentCaptor<MapView>()

        mapFragment.getMapAsync(mapInitListener)
        verify(mapInitListener, timeout(5_000L)).onMapReady(
            mapViewCaptor.capture()
        )
        return mapViewCaptor.firstValue
    }
}