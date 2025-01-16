package cz.feldis.sdkandroidtests.utils

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.sygic.sdk.Routing
import com.sygic.sdk.map.Camera
import com.sygic.sdk.map.Camera.MovementMode
import com.sygic.sdk.map.Camera.RotationMode
import com.sygic.sdk.map.CameraState
import com.sygic.sdk.map.MapAnimation
import com.sygic.sdk.map.MapCenter
import com.sygic.sdk.map.MapCenterSettings
import com.sygic.sdk.map.MapView
import com.sygic.sdk.map.fps.FpsConfig
import com.sygic.sdk.map.listeners.OnMapInitListener
import com.sygic.sdk.map.`object`.MapRoute
import com.sygic.sdk.navigation.NavigationManager
import com.sygic.sdk.navigation.NavigationManagerProvider
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.route.RoutingOptions
import com.sygic.sdk.route.RoutingOptions.NearestAccessiblePointStrategy
import com.sygic.sdk.route.RoutingOptions.RoutingService
import com.sygic.sdk.route.simulator.RouteDemonstrateSimulatorProvider
import com.sygic.sdk.vehicletraits.VehicleProfile
import com.sygic.sdk.vehicletraits.dimensional.DimensionalTraits
import com.sygic.sdk.vehicletraits.general.GeneralVehicleTraits
import com.sygic.sdk.vehicletraits.general.VehicleType
import com.sygic.sdk.vehicletraits.listeners.SetVehicleProfileListener
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.SygicActivity
import cz.feldis.sdkandroidtests.TestMapFragment
import cz.feldis.sdkandroidtests.ktx.NavigationManagerKtx
import cz.feldis.sdkandroidtests.mapInstaller.MapDownloadHelper
import cz.feldis.sdkandroidtests.routing.RouteComputeHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify

@RunWith(MockitoJUnitRunner::class)
class AuxiliaryTests : BaseTest() {

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
    @Ignore("run this only when needed")
    fun testJustNavigationWithMap():Unit = runBlocking {
        mapDownload.installAndLoadMap("sk")

        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        // create test scenario with activity & map fragment
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager
                .beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }

        val vehicleProfile = VehicleProfile().apply {
            this.dimensionalTraits = DimensionalTraits().apply {
                this.totalLength = 16500
            }
            this.generalVehicleTraits = GeneralVehicleTraits().apply {
                this.vehicleType = VehicleType.Truck
            }
        }

        val mapView = getMapView(mapFragment)

        val navigation = NavigationManagerProvider.getInstance().get()

        val route = routeCompute.offlineRouteCompute(
            GeoCoordinates(48.14562613458992, 17.126682063470636),
            GeoCoordinates(48.390008550344, 17.58597217027952),
            routingOptions = RoutingOptions().apply {
                this.vehicleProfile = vehicleProfile
            }
        )

        mapView.setVehicleProfile(vehicleProfile, object: SetVehicleProfileListener {
            override fun onSuccess() {
            }
            override fun onError() {
            }
        })
        mapView.mapDataModel.addMapObject(MapRoute.from(route).setType(MapRoute.RouteType.Primary).build())

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route).get()
        val demonstrateSimulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.startSimulator(demonstrateSimulatorAdapter)

        mapView.cameraModel.rotationMode = RotationMode.Vehicle
        mapView.cameraModel.movementMode = MovementMode.FollowGpsPositionWithAutozoom
        mapView.cameraModel.tilt = 45F
        mapView.fpsLimit = FpsConfig(FpsConfig.FpsMode.PERFORMANCE, 60f)

        delay(600000)
        //close scenario & activity
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
        val mapInitListener: OnMapInitListener = mock(verboseLogging = true)
        val mapViewCaptor = argumentCaptor<MapView>()

        mapFragment.getMapAsync(mapInitListener)
        verify(mapInitListener, timeout(5_000L)).onMapReady(
            mapViewCaptor.capture()
        )
        return mapViewCaptor.firstValue
    }
}