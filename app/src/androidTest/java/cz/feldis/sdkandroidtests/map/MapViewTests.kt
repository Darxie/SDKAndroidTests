package cz.feldis.sdkandroidtests.map

import android.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.sygic.sdk.incidents.IncidentData
import com.sygic.sdk.incidents.IncidentId
import com.sygic.sdk.incidents.IncidentType
import com.sygic.sdk.incidents.IncidentsManager
import com.sygic.sdk.incidents.IncidentsManagerProvider
import com.sygic.sdk.incidents.IncidentsResultListener
import com.sygic.sdk.incidents.SpeedCamera
import com.sygic.sdk.map.Camera
import com.sygic.sdk.map.CameraState
import com.sygic.sdk.map.MapAnimation
import com.sygic.sdk.map.MapCenter
import com.sygic.sdk.map.MapCenterSettings
import com.sygic.sdk.map.MapView
import com.sygic.sdk.map.listeners.OnMapInitListener
import com.sygic.sdk.map.listeners.RequestObjectCallback
import com.sygic.sdk.map.`object`.MapIncident
import com.sygic.sdk.map.`object`.MapPolygon
import com.sygic.sdk.map.`object`.ProxyPlace
import com.sygic.sdk.map.`object`.ViewObject
import com.sygic.sdk.map.`object`.data.ViewObjectData
import com.sygic.sdk.places.PlacesManagerProvider
import com.sygic.sdk.places.data.PlaceCategoryGroupVisibility
import com.sygic.sdk.places.data.PlaceCategoryVisibility
import com.sygic.sdk.places.listeners.SetVisibleCategoriesListener
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.route.RouterProvider
import com.sygic.sdk.route.RoutingOptions
import com.sygic.sdk.route.listeners.EVRangeListener
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.SygicActivity
import cz.feldis.sdkandroidtests.TestMapFragment
import cz.feldis.sdkandroidtests.mapInstaller.MapDownloadHelper
import cz.feldis.sdkandroidtests.routing.RouteComputeHelper
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.*

@RunWith(MockitoJUnitRunner::class)
class MapViewTests : BaseTest() {

    private lateinit var incidentsManager: IncidentsManager

    override fun setUp() {
        super.setUp()
        incidentsManager = IncidentsManagerProvider.getInstance().get()
    }

    @Test
    fun showMapFragment(): Unit = runBlocking {
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        // create test scenario with activity & map fragment
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager
                .beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }

        //do crazy stuff with map
        //mapFragment.getMapAsync( ... )
        //mapFragment.mapDataModel.addMapObject(...)

        delay(3000L)

        //close scenario & activity
        scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    @Test
    fun setTilt(): Unit = runBlocking {
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        // create test scenario with activity & map fragment
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager
                .beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }

        val mapView = getMapView(mapFragment)
        delay(500)
        mapView.cameraModel.tilt = 45.06479F
        delay(500)
        mapView.cameraModel.tilt = 48.06479F
        delay(500)
        mapView.cameraModel.tilt = 59.06479F
        delay(500)
        mapView.cameraModel.tilt = 90.06479F
        delay(500)
        mapView.cameraModel.tilt = 0.06479F
        delay(500)
        mapView.cameraModel.tilt = -30.06479F
        delay(500)

        //close scenario & activity
        scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    @Test
    fun addAndVerifyCustomIncident(): Unit = runBlocking {
        val listener: IncidentsResultListener = mock()
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        // create test scenario with activity & map fragment
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager
                .beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }

        val mapView = getMapView(mapFragment)

        val importedSpeedCam = getMockSpeedCam()
        val importedIncidentData = IncidentData(importedSpeedCam, audioNotificationParams)
        incidentsManager.addIncidents(listOf(importedIncidentData), listener)
        verify(listener, timeout(TIMEOUT)).onSuccess()
        verify(listener, never()).onError(any())

        mapView.cameraModel.position = GeoCoordinates(48.10095535808773, 17.234824479529344)
        mapView.cameraModel.zoomLevel = 20F
        mapView.cameraModel.tilt = 0F
        delay(3000) // it takes around 1,5s until the speedcam is shown on map
        val callback: RequestObjectCallback = mock(verboseLogging = true)
        val captor = argumentCaptor<List<ViewObject<ViewObjectData>>>()

        val view = requireNotNull(mapView.view)

        val x: Float = view.width / 2F
        val y: Float = view.height / 2F
        val id = mapView.requestObjectsAtPoint(x, y, callback)

        verify(callback, timeout(5_000L)).onRequestResult(captor.capture(), eq(x), eq(y), eq(id))

        val firstValue = captor.firstValue[0]
        if (firstValue is MapIncident) {
            val resultSpeedCam = firstValue.data.incident as? SpeedCamera
            assertNotNull(resultSpeedCam)  // Ensure that the cast to SpeedCamera is successful
            assertEquals(IncidentType.RadarMobileSpeed, resultSpeedCam?.category)
            scenario.moveToState(Lifecycle.State.DESTROYED)
        } else {
            scenario.moveToState(Lifecycle.State.DESTROYED)
            fail("Expected a MapIncident, but got ${firstValue::class.simpleName}")
        }

        //close scenario & activity
        scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    @Test
    fun addAndVerifyCustomIncidentCustomCategory(): Unit = runBlocking {
        val listener: IncidentsResultListener = mock()
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        // create test scenario with activity & map fragment
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager
                .beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }

        val mapView = getMapView(mapFragment)

        val importedSpeedCam = getMockSpeedCamCustomCategory()
        val importedIncidentData = IncidentData(importedSpeedCam, audioNotificationParams)
        incidentsManager.addIncidents(listOf(importedIncidentData), listener)
        verify(listener, timeout(TIMEOUT)).onSuccess()
        verify(listener, never()).onError(any())

        mapView.cameraModel.position = GeoCoordinates(48.10095535808773, 17.234824479529344)
        mapView.cameraModel.zoomLevel = 20F
        mapView.cameraModel.tilt = 0F
        delay(3000) // it takes around 1,5s until the speedcam is shown on map
        val callback: RequestObjectCallback = mock(verboseLogging = true)
        val captor = argumentCaptor<List<ViewObject<ViewObjectData>>>()

        val view = requireNotNull(mapView.view)

        val x: Float = view.width / 2F
        val y: Float = view.height / 2F
        val id = mapView.requestObjectsAtPoint(x, y, callback)

        verify(callback, timeout(5_000L)).onRequestResult(captor.capture(), eq(x), eq(y), eq(id))

        val firstValue = captor.firstValue[0]
        if (firstValue is MapIncident) {
            val resultSpeedCam = firstValue.data.incident as? SpeedCamera
            assertNotNull(resultSpeedCam)  // Ensure that the cast to SpeedCamera is successful
            assertEquals(resultSpeedCam?.category, "custom_incident_category")
            scenario.moveToState(Lifecycle.State.DESTROYED)
        } else {
            scenario.moveToState(Lifecycle.State.DESTROYED)
            fail("Expected a MapIncident, but got ${firstValue::class.simpleName}")
        }

        //close scenario & activity
        scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    /**
     * Calculates remaining EV range polygon and visualizes it, then removes it.
     * Zooms out and in with animation.
     * This test should pass without a crash.
     */
    @Test
    fun testSpiderRangeVisualization(): Unit = runBlocking {
        disableOnlineMaps()
        MapDownloadHelper().installAndLoadMap("sk")
        val listener: EVRangeListener = mock(verboseLogging = true)
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        // create test scenario with activity & map fragment
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager
                .beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }
        val mapView = getMapView(mapFragment)
        mapView.cameraModel.position = GeoCoordinates(48.10095535808773, 17.234824479529344)
        mapView.cameraModel.zoomLevel = 15F
        mapView.cameraModel.tilt = 0F

        RouterProvider.getInstance().get().computeEVRange(
            GeoCoordinates(48.10095535808773, 17.234824479529344),
            listOf(3.0),
            RoutingOptions().apply {
                vehicleProfile = RouteComputeHelper().createDefaultElectricVehicleProfile(5F, 3F)
            },
            listener
        )
        mapView.cameraModel.setZoomLevel(
            12F,
            MapAnimation(1500L, MapAnimation.InterpolationCurve.Accelerate)
        )
        delay(2000)
        val captor = argumentCaptor<List<List<GeoCoordinates>>>()
        verify(listener, timeout(60_000L)).onEVRangeComputed(captor.capture())
        val isochrones = captor.firstValue[0]

        val polygon =
            MapPolygon.of(GeoCoordinates(48.10095535808773, 17.234824479529344), isochrones)
                .setBorderColor(Color.BLUE)
                .setCenterColor(Color.TRANSPARENT)
                .setCenterRadius(0.95f)
                .build()

        mapView.mapDataModel.addMapObject(polygon)
        delay(2000)
        mapView.mapDataModel.removeMapObject(polygon)
        mapView.cameraModel.setZoomLevel(
            15F,
            MapAnimation(1500L, MapAnimation.InterpolationCurve.Accelerate)
        )
        delay(2000)

        scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    // https://git.sygic.com/projects/NAVI/repos/sdk/pull-requests/8699/overview
    // the next set of polygon tests should not crash upon adding to map
    @Test
    fun testPolygonClockwise(): Unit = runBlocking {
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        // create test scenario with activity & map fragment
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager
                .beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }
        val mapView = getMapView(mapFragment)
        mapView.cameraModel.position = GeoCoordinates(48.10095535808773, 17.234824479529344)
        mapView.cameraModel.zoomLevel = 16F
        mapView.cameraModel.tilt = 0F

        val geoCoordinates = listOf(
            GeoCoordinates(48.102000, 17.234824),  // North
            GeoCoordinates(48.101500, 17.236000),  // Northeast
            GeoCoordinates(48.100955, 17.236800),  // East
            GeoCoordinates(48.100000, 17.236000),  // Southeast
            GeoCoordinates(48.099500, 17.234824),  // South
            GeoCoordinates(48.100000, 17.233600),  // Southwest
            GeoCoordinates(48.100955, 17.232800),  // West
            GeoCoordinates(48.101500, 17.233600)   // Northwest
        )

        val polygon =
            MapPolygon.of(GeoCoordinates(48.10095535808773, 17.234824479529344), geoCoordinates)
                .setBorderColor(Color.BLUE)
                .setCenterColor(Color.TRANSPARENT)
                .setCenterRadius(0.95f)
                .build()

        mapView.mapDataModel.addMapObject(polygon)
        delay(2000)
        scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    @Test
    fun testPolygonCounterClockwise(): Unit = runBlocking {
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        // create test scenario with activity & map fragment
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager
                .beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }
        val mapView = getMapView(mapFragment)
        mapView.cameraModel.position = GeoCoordinates(48.10095535808773, 17.234824479529344)
        mapView.cameraModel.zoomLevel = 16F
        mapView.cameraModel.tilt = 0F

        val geoCoordinates = listOf(
            GeoCoordinates(48.102000, 17.234824),  // North
            GeoCoordinates(48.101500, 17.233600),  // Northwest
            GeoCoordinates(48.100955, 17.232800),  // West
            GeoCoordinates(48.100000, 17.233600),  // Southwest
            GeoCoordinates(48.099500, 17.234824),  // South
            GeoCoordinates(48.100000, 17.236000),  // Southeast
            GeoCoordinates(48.100955, 17.236800),  // East
            GeoCoordinates(48.101500, 17.236000)   // Northeast
        )

        val polygon =
            MapPolygon.of(GeoCoordinates(48.10095535808773, 17.234824479529344), geoCoordinates)
                .setBorderColor(Color.BLUE)
                .setCenterColor(Color.TRANSPARENT)
                .setCenterRadius(0.95f)
                .build()

        mapView.mapDataModel.addMapObject(polygon)
        delay(2000)
        scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    @Test
    fun testPolygonStarShape(): Unit = runBlocking {
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        // create test scenario with activity & map fragment
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager
                .beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }
        val mapView = getMapView(mapFragment)
        mapView.cameraModel.position = GeoCoordinates(48.10095535808773, 17.234824479529344)
        mapView.cameraModel.zoomLevel = 16F
        mapView.cameraModel.tilt = 0F

        val starShape = listOf(
            GeoCoordinates(48.102000, 17.234824),  // Top
            GeoCoordinates(48.101300, 17.235500), // Upper-right inner
            GeoCoordinates(48.100955, 17.236800), // Right
            GeoCoordinates(48.100300, 17.235500), // Lower-right inner
            GeoCoordinates(48.099500, 17.234824), // Bottom
            GeoCoordinates(48.100300, 17.234100), // Lower-left inner
            GeoCoordinates(48.100955, 17.232800), // Left
            GeoCoordinates(48.101300, 17.234100)  // Upper-left inner
        )

        val polygon =
            MapPolygon.of(GeoCoordinates(48.10095535808773, 17.234824479529344), starShape)
                .setBorderColor(Color.BLUE)
                .setCenterColor(Color.TRANSPARENT)
                .setCenterRadius(0.95f)
                .build()

        mapView.mapDataModel.addMapObject(polygon)
        delay(2000)
        scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    @Test
    fun testPolygonSpiralShape(): Unit = runBlocking {
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        // create test scenario with activity & map fragment
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager
                .beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }
        val mapView = getMapView(mapFragment)
        mapView.cameraModel.position = GeoCoordinates(48.10095535808773, 17.234824479529344)
        mapView.cameraModel.zoomLevel = 16F
        mapView.cameraModel.tilt = 0F

        val spiralShape = listOf(
            GeoCoordinates(48.102000, 17.234824), // Outer spiral
            GeoCoordinates(48.101700, 17.235300),
            GeoCoordinates(48.101200, 17.235600),
            GeoCoordinates(48.100600, 17.235700),
            GeoCoordinates(48.100100, 17.235400),
            GeoCoordinates(48.099800, 17.234900),
            GeoCoordinates(48.100000, 17.234300),
            GeoCoordinates(48.100500, 17.234000),
            GeoCoordinates(48.101100, 17.234100),
            GeoCoordinates(48.101600, 17.234400)  // Inner spiral
        )

        val polygon =
            MapPolygon.of(GeoCoordinates(48.10095535808773, 17.234824479529344), spiralShape)
                .setBorderColor(Color.BLUE)
                .setCenterColor(Color.TRANSPARENT)
                .setCenterRadius(0.95f)
                .build()

        mapView.mapDataModel.addMapObject(polygon)
        delay(2000)
        scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    @Test
    fun testPolygonIrregularShape(): Unit = runBlocking {
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        // create test scenario with activity & map fragment
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager
                .beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }
        val mapView = getMapView(mapFragment)
        mapView.cameraModel.position = GeoCoordinates(48.10095535808773, 17.234824479529344)
        mapView.cameraModel.zoomLevel = 16F
        mapView.cameraModel.tilt = 0F

        val irregularShape = listOf(
            GeoCoordinates(48.102000, 17.235824), // North-northeast
            GeoCoordinates(48.101200, 17.237000), // East-northeast
            GeoCoordinates(48.099800, 17.236300), // Southeast
            GeoCoordinates(48.099400, 17.234800), // South
            GeoCoordinates(48.099800, 17.233300), // Southwest
            GeoCoordinates(48.101000, 17.232800), // West
            GeoCoordinates(48.101700, 17.233600)  // Northwest
        )

        val polygon =
            MapPolygon.of(GeoCoordinates(48.10095535808773, 17.234824479529344), irregularShape)
                .setBorderColor(Color.BLUE)
                .setCenterColor(Color.TRANSPARENT)
                .setCenterRadius(0.95f)
                .build()

        mapView.mapDataModel.addMapObject(polygon)
        delay(2000)
        scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    /**
     * https://jira.sygic.com/browse/SDC-13484
     *
     * In this test, we create an activity, then hide petrol stations
     * from the map through PlacesManager,destroy the activity, create a new activity
     * and we expect that the petrol stations are still hidden.
     * There was a problem where the skin would be reset to default upon creating a mapView.
     */
    @Test
    fun checkVisibilityOfPOIsAfterDestroyingActivity(): Unit = runBlocking {
        // Create a map fragment with the initial camera state.
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        // Launch the activity and add the map fragment.
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity { activity ->
            activity.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }

        val mapView = getMapView(mapFragment)
        // Set the camera position.
        mapView.cameraModel.position = GeoCoordinates(48.1293, 17.1943)
        mapView.cameraModel.zoomLevel = 20F
        mapView.cameraModel.tilt = 0F

        val listener: SetVisibleCategoriesListener = mock(verboseLogging = true)

        PlacesManagerProvider.getInstance().get().setVisibleCategories(
            listOf(
                PlaceCategoryGroupVisibility(
                    "SYPetrolStation",
                    listOf(PlaceCategoryVisibility("SYPetrolStation", false))
                )
            ), listener
        )
        verify(listener, timeout(3_000L)).onSuccess()
        verify(listener, never()).onError(any())

        delay(1000)
        scenario.moveToState(Lifecycle.State.DESTROYED)
        delay(1000)

        val mapFragment2 = TestMapFragment.newInstance(getInitialCameraState())
        val scenario2 = ActivityScenario.launch(SygicActivity::class.java).onActivity { activity ->
            activity.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, mapFragment2)
                .commitNow()
        }
        val mapView2 = getMapView(mapFragment2)

        // Set the camera position.
        mapView2.cameraModel.position = GeoCoordinates(48.1293, 17.1943)
        mapView2.cameraModel.zoomLevel = 20F
        mapView2.cameraModel.tilt = 0F

        delay(3000)

        // Prepare the callback for object requests.
        val callback: RequestObjectCallback = mock(verboseLogging = true)
        val captor = argumentCaptor<List<ViewObject<ViewObjectData>>>()

        // Determine the center coordinates of the MapView.
        val view = requireNotNull(mapView2.view)
        val x = view.width / 2F
        val y = view.height / 2F

        // Request objects at the center point.
        val requestId = mapView2.requestObjectsAtPoint(x, y, callback)

        // Verify that the callback returns objects within 5 seconds and capture them.
        verify(callback, timeout(5_000L))
            .onRequestResult(captor.capture(), eq(x), eq(y), eq(requestId))

        val viewObjects = captor.firstValue

        // Clean up
        scenario2.moveToState(Lifecycle.State.DESTROYED)

        // Assert that none of the returned objects is a ProxyPlace.
        assertFalse(
            "No ProxyPlace should be present in the returned viewObjects list",
            viewObjects.any { it is ProxyPlace }
        )
    }

    /**
     * https://jira.sygic.com/browse/SDC-13484
     *
     * In this test, we hide petrol stations from the map using PlacesManager,
     * create a new activity and we expect that the petrol stations are hidden.
     * There was a problem where the skin would be reset to default upon creating a mapView.
     */
    @Test
    fun checkVisibilityOfPOIsBeforeFirstScenario(): Unit = runBlocking {
        val listener: SetVisibleCategoriesListener = mock(verboseLogging = true)

        PlacesManagerProvider.getInstance().get().setVisibleCategories(
            listOf(
                PlaceCategoryGroupVisibility(
                    "SYPetrolStation",
                    listOf(PlaceCategoryVisibility("SYPetrolStation", false))
                )
            ), listener
        )
        verify(listener, timeout(3_000L)).onSuccess()
        verify(listener, never()).onError(any())

        // Create a map fragment with the initial camera state.
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        // Launch the activity and add the map fragment.
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity { activity ->
            activity.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }

        val mapView = getMapView(mapFragment)

        // Set the camera position.
        mapView.cameraModel.position = GeoCoordinates(48.1293, 17.1943)
        mapView.cameraModel.zoomLevel = 20F
        mapView.cameraModel.tilt = 0F

        delay(1000)

        // Prepare the callback for object requests.
        val callback: RequestObjectCallback = mock(verboseLogging = true)
        val captor = argumentCaptor<List<ViewObject<ViewObjectData>>>()

        // Determine the center coordinates of the MapView.
        val view = requireNotNull(mapView.view)
        val x = view.width / 2F
        val y = view.height / 2F

        // Request objects at the center point.
        val requestId = mapView.requestObjectsAtPoint(x, y, callback)

        // Verify that the callback returns objects within 5 seconds and capture them.
        verify(callback, timeout(5_000L))
            .onRequestResult(captor.capture(), eq(x), eq(y), eq(requestId))

        val viewObjects = captor.firstValue

        // Clean up
        scenario.moveToState(Lifecycle.State.DESTROYED)

        // Assert that none of the returned objects is a ProxyPlace.
        assertFalse(
            "No ProxyPlace should be present in the returned viewObjects list",
            viewObjects.any { it is ProxyPlace }
        )
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

    companion object {
        private val audioNotificationParams = IncidentsManager.AudioNotificationParameters(5, 10)
        private const val TIMEOUT = 3000L
        private const val VALID_TO_TIMESTAMP = 1904969474L // 14.5.2030
        private fun getMockSpeedCam(): SpeedCamera {
            return SpeedCamera(
                IncidentId("26a69832-7f72-42ba-8f1d-394811376579"),
                GeoCoordinates(48.10095535808773, 17.234824479529344),
                IncidentType.RadarMobileSpeed,
                VALID_TO_TIMESTAMP,
                359F,
                true,
                80
            )
        }

        private fun getMockSpeedCamCustomCategory(): SpeedCamera {
            return SpeedCamera(
                IncidentId("26a69832-7f72-42ba-8f1d-a94811376579"),
                GeoCoordinates(48.10095535808773, 17.234824479529344),
                "custom_incident_category",
                VALID_TO_TIMESTAMP,
                0F,
                false,
                120
            )
        }
    }
}