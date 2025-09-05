package cz.feldis.sdkandroidtests.utils

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.sygic.sdk.map.Camera
import com.sygic.sdk.map.CameraState
import com.sygic.sdk.map.MapAnimation
import com.sygic.sdk.map.MapCenter
import com.sygic.sdk.map.MapCenterSettings
import com.sygic.sdk.map.MapRectangle
import com.sygic.sdk.map.MapView
import com.sygic.sdk.map.listeners.OnMapInitListener
import com.sygic.sdk.map.`object`.MapRoute
import com.sygic.sdk.map.`object`.MapRoute.RouteType
import com.sygic.sdk.position.GeoBoundingBox
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.route.Route
import cz.feldis.sdkandroidtests.SygicActivity
import cz.feldis.sdkandroidtests.TestMapFragment
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify

object GeoUtils {

    fun isPointInBoundingBox(point: GeoCoordinates, boundingBox: GeoBoundingBox): Boolean {
        val withinLatitude = point.latitude <= boundingBox.topLeft.latitude &&
                point.latitude >= boundingBox.bottomRight.latitude
        val withinLongitude = point.longitude >= boundingBox.topLeft.longitude &&
                point.longitude <= boundingBox.bottomRight.longitude
        return withinLatitude && withinLongitude
    }

    fun showRouteOnMap(route: Route): Unit = runBlocking {
        // Create a map fragment with the initial camera state.
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        // Launch the activity and add the map fragment.
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity { activity ->
            activity.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }

        val mapView = getMapView(mapFragment)

        // Set the camera position to the midpoint of the polyline.
        mapView.cameraModel.tilt = 0F
        mapView.cameraModel.mapRectangle = MapRectangle(
            GeoBoundingBox(
                route.boundingBox.topLeft,
                route.boundingBox.bottomRight
            ), 100, 10, 100, 10
        )

        val mapRoute = MapRoute.from(route)
            .setType(RouteType.Primary)
            .build()
        // Add the polyline to the map.
        mapView.mapDataModel.addMapObject(mapRoute)
        delay(60_000L)
        scenario.moveToState(Lifecycle.State.DESTROYED)
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
}