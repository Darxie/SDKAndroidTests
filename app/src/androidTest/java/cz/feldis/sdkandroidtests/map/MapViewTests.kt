package cz.feldis.sdkandroidtests.map

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.timeout
import com.nhaarman.mockitokotlin2.verify
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
import com.sygic.sdk.map.`object`.ViewObject
import com.sygic.sdk.map.`object`.data.ViewObjectData
import com.sygic.sdk.position.GeoCoordinates
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.SygicActivity
import cz.feldis.sdkandroidtests.TestMapFragment
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

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