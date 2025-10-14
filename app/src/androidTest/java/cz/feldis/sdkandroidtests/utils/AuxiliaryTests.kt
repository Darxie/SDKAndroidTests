package cz.feldis.sdkandroidtests.utils

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
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
import com.sygic.sdk.position.PositionManagerProvider
import com.sygic.sdk.position.results.GetRoadsResult
import com.sygic.sdk.position.results.MatchResult
import com.sygic.sdk.route.RoutingOptions
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import java.util.Locale

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
        navigation = runBlocking { NavigationManagerProvider.getInstance() }
    }

    @Test
    @Ignore("run this only when needed")
    fun testJustNavigationWithMap(): Unit = runBlocking {
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

        val route = routeCompute.offlineRouteCompute(
            GeoCoordinates(48.14562613458992, 17.126682063470636),
            GeoCoordinates(48.390008550344, 17.58597217027952),
            routingOptions = RoutingOptions().apply {
                this.vehicleProfile = vehicleProfile
            }
        )

        mapView.setVehicleProfile(vehicleProfile, object : SetVehicleProfileListener {
            override fun onSuccess() {
            }

            override fun onError() {
            }
        })
        mapView.mapDataModel.addMapObject(
            MapRoute.from(route).setType(MapRoute.RouteType.Primary).build()
        )

        navigationManagerKtx.setRouteForNavigation(route, navigation)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route)
        val demonstrateSimulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.startSimulator(demonstrateSimulatorAdapter)

        mapView.cameraModel.setRotationMode(RotationMode.Vehicle)
        mapView.cameraModel.setMovementMode(MovementMode.FollowGpsPositionWithAutozoom)
        mapView.cameraModel.setTilt(45F)
        mapView.setFpsLimit(FpsConfig(FpsConfig.FpsMode.PERFORMANCE, 60f))

        delay(600000)
        //close scenario & activity
        scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    @Test
    fun mapLanguageChangesStreetNameTest(): Unit = runBlocking {
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        // create test scenario with activity & map fragment
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager
                .beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }

        try {
            val mapView = getMapView(mapFragment)
            disableOnlineMaps()
            mapDownload.installAndLoadMap("be")
            mapView.cameraModel.setPosition(GeoCoordinates(50.86309526480844, 4.29355710076467))
            mapView.cameraModel.setZoomLevel(20F)

            val streetCoordinates = GeoCoordinates(50.86309526480844, 4.29355710076467)
            val streetCoordinates2 = GeoCoordinates(50.86331495102468, 4.293697684351139)
            val positionManager = PositionManagerProvider.getInstance()

            suspend fun getStreetNameForLocale(locale: String): String? {
                println("🌍 Setting locale = $locale")
                mapView.setMapLanguage(Locale.forLanguageTag(locale))
                delay(1000)

                val matchResult = positionManager.match(listOf(streetCoordinates, streetCoordinates2))
                when (matchResult) {
                    is MatchResult.Success -> println("✅ Match success, found ${matchResult.submatchings.flatten().size} roads")
                    else -> println("❌ Match failed: $matchResult")
                }

                val roadIds = (matchResult as? MatchResult.Success)
                    ?.submatchings
                    ?.flatten()
                    .orEmpty()

                if (roadIds.isEmpty()) {
                    println("⚠️ roadIds are empty")
                    return null
                }

                val roadsResult = positionManager.getRoads(roadIds)
                when (roadsResult) {
                    is GetRoadsResult.Success -> {
                        println("✅ getRoads success, found ${roadsResult.roads.size} roads")
                        roadsResult.roads.forEachIndexed { i, r ->
                            println("  [$i] id=${r.id} street=${r.street}")
                        }
                        return roadsResult.roads.firstOrNull()?.street
                    }

                    else -> println("❌ getRoads failed: $roadsResult")
                }
                return null
            }

            val frenchName = getStreetNameForLocale("fr-BE")
            val dutchName = getStreetNameForLocale("nl-BE")

            println("🇫🇷 frenchName = $frenchName")
            println("🇳🇱 dutchName = $dutchName")

            assertTrue(dutchName == "Kerkstraat")
            assertTrue(frenchName == "Rue de l'Eglise")
        } finally {
            scenario.moveToState(Lifecycle.State.DESTROYED)
        }
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
            setMovementMode(MovementMode.Free)
            setRotationMode(RotationMode.Free)
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
