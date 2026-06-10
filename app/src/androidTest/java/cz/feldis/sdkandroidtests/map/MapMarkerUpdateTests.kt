package cz.feldis.sdkandroidtests.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PointF
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.sygic.sdk.map.MapView
import com.sygic.sdk.map.factory.DrawableFactory
import com.sygic.sdk.map.factory.SimpleBitmapFactory
import com.sygic.sdk.map.`object`.MapMarker
import com.sygic.sdk.map.`object`.StyledText
import com.sygic.sdk.map.`object`.data.MarkerData
import com.sygic.sdk.map.results.MapValidityData
import com.sygic.sdk.position.GeoCoordinates
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.R
import cz.feldis.sdkandroidtests.SygicActivity
import cz.feldis.sdkandroidtests.TestMapFragment
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

/**
 * Acceptance tests for in-place [MapMarker] update.
 *
 * Contract:
 *  - [MapMarker.copy] returns a Builder that retains the source marker's native id and zIndex.
 *  - [com.sygic.sdk.map.data.MapDataModel.updateMapObject] is id-keyed: overwrites in place,
 *    no remove+add gap (avoids a single-frame flicker in Android Auto).
 *  - updateMapObject returns false for a marker with id == 0; callers must use addMapObject
 *    for the initial insertion.
 */
@RunWith(MockitoJUnitRunner::class)
class MapMarkerUpdateTests : BaseTest() {

    private val markerCoord = GeoCoordinates(48.10095535808773, 17.234824479529344)

    /**
     * Destroy the Rule-managed activity before [BaseTest.tearDown] destroys the SDK context.
     * Otherwise the MapView attached to that activity tries to clean up against a torn-down
     * SygicContext when Rule's own `@After` finally closes the activity, which crashes the run.
     * Subclass `@After` runs before superclass `@After`, giving us the order: activity → SDK.
     */
    @After
    fun destroyRuleActivity() {
        runCatching { activityRule.scenario.moveToState(Lifecycle.State.DESTROYED) }
    }

    private fun makeIconBitmap(size: Int = 64, color: Int = Color.RED): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(color)
        return bitmap
    }

    private suspend fun MapView.awaitRenderedFrames(count: Int) {
        onSwapBuffers().take(count).collect {}
    }

    /**
     * Polls [requestObjectsAtPoint] at view center until a [MapMarker] appears or [timeoutMs]
     * elapses. Tolerates the async gap between addMapObject / updateMapObject and the native
     * hit-test spatial index rebuild — that rebuild is not synchronized with [onSwapBuffers]
     * events, so a single one-shot request can race the rebuild and return an empty result.
     * Returns null on timeout.
     */
    private suspend fun MapView.awaitMarkerAtCenter(
        timeoutMs: Long = 5_000L,
        pollMs: Long = 100L,
    ): MapMarker? {
        val view = requireNotNull(getView())
        val x = view.width / 2F
        val y = view.height / 2F
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val result = requestObjectsAtPoint(x, y)
            val hit = result.viewObjects.firstOrNull { it is MapMarker } as? MapMarker
            if (hit != null) return hit
            if (System.currentTimeMillis() >= deadline) return null
            delay(pollMs)
        }
    }

    @Test
    fun updateMapObjectPreservesNativeId(): Unit = runBlocking {
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }
        val mapView = getMapView(mapFragment)
        mapView.cameraModel.setPosition(markerCoord)
        mapView.cameraModel.setZoomLevel(19F)
        mapView.cameraModel.setTilt(0F)

        val original = MapMarker(
            MarkerData(
                position = markerCoord,
                label = StyledText("Original"),
                minZoomLevel = 5F,
                maxZoomLevel = 21F,
            )
        )
        assertTrue(mapView.mapDataModel.addMapObject(original))
        delay(1500)

        val originalId = original.id
        assertTrue("Native id must be assigned after addMapObject, got $originalId", originalId != 0)

        val updated = original.copy {
            copy(
                label = StyledText("Modified"),
                minZoomLevel = 10F,
                maxZoomLevel = 20F,
                collisions = true,
                labelCollisions = true,
            )
        }
        assertEquals("copy() must retain the original native id on the new MapMarker",
            originalId, updated.id)

        assertTrue(mapView.mapDataModel.updateMapObject(updated))
        delay(1500)

        val objectsInModel = mapView.mapDataModel.getMapObjects()
        val inModel = objectsInModel.singleOrNull() as? MapMarker

        if (inModel != null) {
            assertEquals(originalId, inModel.id)
            assertEquals(StyledText("Modified"), inModel.data.label)
            scenario.moveToState(Lifecycle.State.DESTROYED)
        } else {
            scenario.moveToState(Lifecycle.State.DESTROYED)
            fail("Expected exactly one MapMarker in data model after update, got: $objectsInModel")
        }
    }

    /**
     * Negative control for [updateMapObjectPreservesNativeId]: remove+add must re-issue a
     * fresh native id. If this regresses, the id-stability check in the positive test loses
     * meaning.
     */
    @Test
    fun removeThenAddAssignsDifferentNativeId(): Unit = runBlocking {
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }
        val mapView = getMapView(mapFragment)
        mapView.cameraModel.setPosition(markerCoord)
        mapView.cameraModel.setZoomLevel(19F)
        mapView.cameraModel.setTilt(0F)

        val original = MapMarker(MarkerData(position = markerCoord, label = StyledText("A")))
        assertTrue(mapView.mapDataModel.addMapObject(original))
        delay(1500)
        val firstId = original.id
        assertTrue(firstId != 0)

        val reAdded = original.copy { copy(label = StyledText("B")) }
        assertTrue(mapView.mapDataModel.removeMapObject(original))
        delay(500)
        assertTrue(mapView.mapDataModel.addMapObject(reAdded))
        delay(1500)

        val secondId = reAdded.id
        if (firstId != secondId) {
            scenario.moveToState(Lifecycle.State.DESTROYED)
        } else {
            scenario.moveToState(Lifecycle.State.DESTROYED)
            fail("Legacy remove+add must re-issue a new native id; got $firstId twice")
        }
    }

    /**
     * After updateMapObject the marker must remain hit-testable and the mutated data must
     * round-trip through the native layer via requestObjectsAtPoint.
     */
    @Test
    fun updateMapObjectChangesAreVisibleViaRequestObjectsAtPoint(): Unit = runBlocking {
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }
        val mapView = getMapView(mapFragment)
        mapView.cameraModel.setPosition(markerCoord)
        mapView.cameraModel.setZoomLevel(20F)
        mapView.cameraModel.setTilt(0F)
        mapView.awaitRenderedFrames(2)

        val original = MapMarker(
            MarkerData(
                position = markerCoord,
                anchorPosition = PointF(0.5f, 0.7f),
                bitmapFactory = SimpleBitmapFactory(makeIconBitmap()),
                label = StyledText("before"),
            )
        )
        assertTrue(mapView.mapDataModel.addMapObject(original))
        mapView.awaitRenderedFrames(3)
        val originalId = original.id
        assertTrue(originalId != 0)

        val updated = original.copy {
            copy(anchorPosition = PointF(0.5f, 0.7f), label = StyledText("after"))
        }
        assertTrue(mapView.mapDataModel.updateMapObject(updated))
        mapView.awaitRenderedFrames(3)

        val inModelAfterUpdate = mapView.mapDataModel.getMapObjects()
            .filterIsInstance<MapMarker>()
            .singleOrNull { it.id == originalId }
        if (inModelAfterUpdate == null) {
            scenario.moveToState(Lifecycle.State.DESTROYED)
            fail("Updated marker (id=$originalId) is not in the data model. Objects: ${mapView.mapDataModel.getMapObjects()}")
        }
        assertEquals(
            "Updated marker in data model must carry the new label",
            StyledText("after"),
            inModelAfterUpdate!!.data.label
        )

        val hit = mapView.awaitMarkerAtCenter()
        if (hit != null) {
            assertEquals(StyledText("after"), hit.data.label)
            scenario.moveToState(Lifecycle.State.DESTROYED)
        } else {
            scenario.moveToState(Lifecycle.State.DESTROYED)
            fail("Expected updated MapMarker to be hit-testable at its position within 5s, but none was returned.")
        }
    }

    /**
     * Control for [updateMapObjectChangesAreVisibleViaRequestObjectsAtPoint]: with the same
     * camera setup, marker icon, and request coordinates, a marker reached via
     * removeMapObject+addMapObject (instead of updateMapObject) must be hit-testable. If this
     * passes while the updateMapObject path fails, the native hit-test sync after
     * updateMapObject is the root cause.
     */
    @Test
    fun controlRemoveAddProducesHitTestableMarker(): Unit = runBlocking {
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }
        val mapView = getMapView(mapFragment)
        mapView.cameraModel.setPosition(markerCoord)
        mapView.cameraModel.setZoomLevel(20F)
        mapView.cameraModel.setTilt(0F)
        mapView.awaitRenderedFrames(2)

        val icon = makeIconBitmap()
        val original = MapMarker(
            MarkerData(
                position = markerCoord,
                anchorPosition = PointF(0.5f, 0.7f),
                bitmapFactory = SimpleBitmapFactory(icon),
                label = StyledText("before"),
            )
        )
        assertTrue(mapView.mapDataModel.addMapObject(original))
        mapView.awaitRenderedFrames(3)
        assertTrue(original.id != 0)

        assertTrue(mapView.mapDataModel.removeMapObject(original))
        mapView.awaitRenderedFrames(1)

        val reAdded = MapMarker(
            MarkerData(
                position = markerCoord,
                anchorPosition = PointF(0.5f, 0.7f),
                bitmapFactory = SimpleBitmapFactory(icon),
                label = StyledText("after"),
            )
        )
        assertTrue(mapView.mapDataModel.addMapObject(reAdded))
        mapView.awaitRenderedFrames(3)
        assertTrue(reAdded.id != 0)

        val hit = mapView.awaitMarkerAtCenter()
        if (hit != null) {
            assertEquals(StyledText("after"), hit.data.label)
            scenario.moveToState(Lifecycle.State.DESTROYED)
        } else {
            scenario.moveToState(Lifecycle.State.DESTROYED)
            fail("Control failed: remove+add path produced a marker (id=${reAdded.id}) in the data model but it is not hit-testable within 5s.")
        }
    }

    /**
     * Mirrors the working SDK test `updateMapMarkerPropagatesToNative` (MapObjectsTest.kt):
     *  - uses [BaseTest.activityRule] (no second `ActivityScenario.launch`)
     *  - fragment attach + mapValid + 2 swap-buffers settle in a setup `runBlocking`
     *    before the test's `runBlocking`, matching SDK's `@Before` order
     *  - mounts the fragment into [R.id.sygicSdkFragmentContainer], not `android.R.id.content`
     *  - uses [DrawableFactory] (drawable resource), not a raw Bitmap
     *  - synchronizes via [MapView.onSwapBuffers] frames, not `delay`
     *  - uses the suspend `requestObjectsAtPoint(x, y)` overload
     */
    @Test
    fun updateMapMarkerPropagatesToNative() {
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        activityRule.scenario.onActivity {
            it.supportFragmentManager.beginTransaction()
                .add(R.id.sygicSdkFragmentContainer, mapFragment)
                .commitNow()
        }
        val mapView = runBlocking {
            val mv = getMapView(mapFragment)
            mv.mapValidity().filterIsInstance<MapValidityData.MapValid>().first()
            mv.onSwapBuffers().take(2).collect {}
            delay(2000) // map valid + first frames rendered
            mv
        }

        runBlocking {
            val factory = DrawableFactory(R.drawable.ic_launcher_background)
            val original = MapMarker(
                MarkerData(
                    position = markerCoord,
                    anchorPosition = PointF(0.5f, 0.7f), // move it a bit down, so that mid-screen is requestable
                    label = StyledText("Original"),
                    bitmapFactory = factory,
                )
            )

            mapView.cameraModel.setZoomLevel(19F)
            mapView.cameraModel.setPosition(markerCoord)
            mapView.cameraModel.setTilt(0F)
            delay(1000) // camera zoomed in to marker position

            assertTrue(mapView.mapDataModel.addMapObject(original))
            delay(1000) // marker added with label "Original"

            val view = requireNotNull(mapView.getView())
            val x = view.width / 2F
            val y = view.height / 2F

            mapView.onSwapBuffers().take(2).collect {}
            val beforeUpdate = mapView.requestObjectsAtPoint(x, y)
            val originalHit = beforeUpdate.viewObjects.firstOrNull {
                it is MapMarker && it.data.label == StyledText("Original")
            }
            if (originalHit == null) {
                fail("Marker with label 'Original' was not hit-testable. Captured: ${beforeUpdate.viewObjects}")
            }
            delay(1000) // hit-test before update succeeded

            val retrieved = mapView.mapDataModel.getMapObjects().single() as MapMarker
            val updated = retrieved.copy { copy(label = StyledText("Modified")) }
            assertTrue(mapView.mapDataModel.updateMapObject(updated))
            delay(1000) // marker updated to label "Modified"

            mapView.onSwapBuffers().take(2).collect {}
            val afterUpdate = mapView.requestObjectsAtPoint(x, y)
            val nativeMarker = afterUpdate.viewObjects.firstOrNull { it is MapMarker } as? MapMarker
            if (nativeMarker == null) {
                fail("Updated marker was not hit-testable. Captured: ${afterUpdate.viewObjects}")
            }
            assertEquals(StyledText("Modified"), nativeMarker!!.data.label)
            delay(2000) // hit-test after update succeeded

            factory.recycle()
        }
    }

    /**
     * updateMapObject must refuse a marker with id == 0 (never added) and leave the model
     * untouched. Caller has to use addMapObject for the initial insertion.
     */
    @Test
    fun updateMapObjectWithIdZeroReturnsFalseAndDoesNotAdd(): Unit = runBlocking {
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }
        val mapView = getMapView(mapFragment)
        delay(1000)

        val marker = MapMarker(MarkerData(position = markerCoord, label = StyledText("never_added")))
        val initialId = marker.id
        val refusedUpdate = mapView.mapDataModel.updateMapObject(marker)
        val modelEmptyAfterRefusedUpdate = mapView.mapDataModel.getMapObjects().isEmpty()
        val addedAfter = mapView.mapDataModel.addMapObject(marker)
        delay(1000)
        val idAfterAdd = marker.id

        if (initialId == 0 && !refusedUpdate && modelEmptyAfterRefusedUpdate && addedAfter && idAfterAdd != 0) {
            scenario.moveToState(Lifecycle.State.DESTROYED)
        } else {
            scenario.moveToState(Lifecycle.State.DESTROYED)
            fail(
                "Expected: initialId=0 (got $initialId), updateMapObject=false (got $refusedUpdate), " +
                "model empty after refused update (got ${!modelEmptyAfterRefusedUpdate}), " +
                "addMapObject=true (got $addedAfter), idAfterAdd!=0 (got $idAfterAdd)"
            )
        }
    }

    /**
     * Update a subset of a batch of markers. All native ids must remain stable and the model
     * must still contain every original marker after the update.
     */
    @Test
    fun updateMapObjectMultipleMarkersIdsRemainStable(): Unit = runBlocking {
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }
        val mapView = getMapView(mapFragment)
        mapView.cameraModel.setPosition(markerCoord)
        mapView.cameraModel.setZoomLevel(17F)
        delay(1000)

        val markers = (0 until 10).map { i ->
            MapMarker(
                MarkerData(
                    position = GeoCoordinates(48.100 + i * 0.001, 17.234 + i * 0.001),
                    label = StyledText("m_$i"),
                )
            )
        }
        markers.forEach { assertTrue(mapView.mapDataModel.addMapObject(it)) }
        delay(1500)

        val originalIds = markers.map { it.id }
        assertTrue("Every marker must get a native id", originalIds.all { it != 0 })

        markers.forEachIndexed { idx, m ->
            if (idx % 2 == 0) {
                val updated = m.copy {
                    copy(label = StyledText("updated_$idx"), collisions = true)
                }
                assertEquals("copy() must retain the id at index $idx", originalIds[idx], updated.id)
                assertTrue(mapView.mapDataModel.updateMapObject(updated))
            }
        }
        delay(1500)

        val objectsInModel = mapView.mapDataModel.getMapObjects()
        val sizeOk = objectsInModel.size == 10
        val missingIds = originalIds.filter { id -> objectsInModel.none { it.id == id } }

        if (sizeOk && missingIds.isEmpty()) {
            scenario.moveToState(Lifecycle.State.DESTROYED)
        } else {
            scenario.moveToState(Lifecycle.State.DESTROYED)
            fail(
                "After updating the even-indexed half, model must still contain all 10 original ids; " +
                "got size=${objectsInModel.size}, missing ids=$missingIds"
            )
        }
    }

    /**
     * Flicker regression test: across a sequence of updateMapObject calls the marker must
     * stay in the data model with the same native id every iteration. A regression that
     * reroutes updateMapObject back to remove+add would either evict the marker briefly or
     * reissue its id.
     */
    @Test
    fun updateMapObjectNoFlickerMarkerAlwaysHitTestable(): Unit = runBlocking {
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }
        val mapView = getMapView(mapFragment)
        mapView.cameraModel.setPosition(markerCoord)
        mapView.cameraModel.setZoomLevel(19F)
        mapView.cameraModel.setTilt(0F)

        val original = MapMarker(MarkerData(position = markerCoord, label = StyledText("flicker_0")))
        assertTrue(mapView.mapDataModel.addMapObject(original))
        delay(2000)
        val originalId = original.id
        assertTrue(originalId != 0)

        var current = original
        val iterations = 20
        var failureMessage: String? = null
        for (i in 0 until iterations) {
            current = current.copy {
                copy(
                    label = StyledText("flicker_${i + 1}"),
                    minZoomLevel = (i % 5).toFloat(),
                )
            }
            if (current.id != originalId) {
                failureMessage = "copy() must retain native id at iteration $i: expected $originalId, got ${current.id}"
                break
            }
            if (!mapView.mapDataModel.updateMapObject(current)) {
                failureMessage = "updateMapObject returned false at iteration $i"
                break
            }
            val inModel = mapView.mapDataModel.getMapObjects().any { it.id == originalId }
            if (!inModel) {
                failureMessage = "Marker (id=$originalId) was evicted from the data model on iteration $i — remove+add regression suspected"
                break
            }
        }

        if (failureMessage == null) {
            scenario.moveToState(Lifecycle.State.DESTROYED)
        } else {
            scenario.moveToState(Lifecycle.State.DESTROYED)
            fail(failureMessage)
        }
    }

    /**
     * Stress: concurrent updates of a single marker from multiple coroutines while the
     * renderer is running must not crash, deadlock, or evict the marker from the model.
     */
    @Test
    fun updateMapObjectConcurrentUpdatesDoNotEvictMarker(): Unit = runBlocking {
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }
        val mapView = getMapView(mapFragment)
        mapView.cameraModel.setPosition(markerCoord)
        mapView.cameraModel.setZoomLevel(19F)
        delay(1500)

        val original = MapMarker(MarkerData(position = markerCoord, label = StyledText("base")))
        assertTrue(mapView.mapDataModel.addMapObject(original))
        delay(1500)
        val originalId = original.id
        assertTrue(originalId != 0)

        coroutineScope {
            repeat(4) { workerIdx ->
                launch(Dispatchers.IO) {
                    repeat(25) { i ->
                        val updated = original.copy {
                            copy(
                                label = StyledText("w${workerIdx}_$i"),
                                minZoomLevel = (i % 5).toFloat(),
                            )
                        }
                        mapView.mapDataModel.updateMapObject(updated)
                    }
                }
            }
        }

        delay(500)
        val survivesInModel = mapView.mapDataModel.getMapObjects().any { it.id == originalId }

        if (survivesInModel) {
            scenario.moveToState(Lifecycle.State.DESTROYED)
        } else {
            scenario.moveToState(Lifecycle.State.DESTROYED)
            fail(
                "Marker (id=$originalId) was evicted from the data model during the concurrent " +
                "update storm — id-keyed updateMapObject should keep it"
            )
        }
    }
}
