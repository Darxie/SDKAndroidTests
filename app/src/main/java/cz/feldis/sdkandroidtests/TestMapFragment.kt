package cz.feldis.sdkandroidtests

import com.sygic.sdk.low.gl.GlSurfaceListenerFactory
import com.sygic.sdk.map.*
import com.sygic.sdk.map.data.SimpleMapDataModel
import com.sygic.sdk.position.GeoCoordinates

class TestMapFragment : MapFragment() {

    private val testDataModel = SimpleMapDataModel()

    companion object {
        fun newInstance(): TestMapFragment {
            return newInstance(
                CameraState.Builder()
                    .setPosition(GeoCoordinates(48.15132, 17.07665))
                    .setMapCenterSettings(
                        MapCenterSettings(
                            MapCenter(0.5f, 0.5f),
                            MapCenter(0.5f, 0.5f),
                            MapAnimation.NONE,
                            MapAnimation.NONE
                        )
                    )
                    .setMapPadding(0.0f, 0.0f, 0.0f, 0.0f)
                    .setRotation(0f)
                    .setZoomLevel(0f)
                    .setMovementMode(Camera.MovementMode.Free)
                    .setRotationMode(Camera.RotationMode.Free)
                    .setTilt(0f)
                    .build()
            )
        }

        fun newInstance(cameraState: CameraState?): TestMapFragment {
            cameraState ?: return newInstance()
            return newInstance(
                TestMapFragment::class.java,
                GlSurfaceListenerFactory.Type.SURFACE_VIEW,
                cameraState,
                listOf("day")
            )
        }
    }

    override fun getMapDataModel(): MapView.MapDataModel {
        return testDataModel
    }
}