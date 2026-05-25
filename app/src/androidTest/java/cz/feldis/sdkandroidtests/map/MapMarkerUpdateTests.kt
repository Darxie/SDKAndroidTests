package cz.feldis.sdkandroidtests.map

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.sygic.sdk.map.listeners.RequestObjectCallback
import com.sygic.sdk.map.`object`.MapMarker
import com.sygic.sdk.map.`object`.StyledText
import com.sygic.sdk.map.`object`.ViewObject
import com.sygic.sdk.map.`object`.data.ViewObjectData
import com.sygic.sdk.position.GeoCoordinates
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.SygicActivity
import cz.feldis.sdkandroidtests.TestMapFragment
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotSame
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify

/**
 * Acceptance tests for in-place [com.sygic.sdk.map.`object`.MapMarker] update introduced in
 * SDK commit 3c09190 (feature/DNAENG-1605).
 *
 * Before the change, callers had to remove a marker and add a new one to reflect updated
 * data, which caused a visible flicker in Android Auto — for a single frame the marker was
 * absent because remove and add happened in separate render frames.
 *
 * The new contract:
 *  - [com.sygic.sdk.map.data.MapDataModel.updateMapObject] performs an in-place native slot
 *    overwrite, preserving the marker's native id and never leaving the slot empty.
 *  - [com.sygic.sdk.map.`object`.data.MarkerData] became a Kotlin data class with `var`
 *    fields, so callers can mutate `label`, `anchorPosition`, `bitmapFactory`, etc. before
 *    invoking `updateMapObject`.
 *
 * These tests exercise the integration end-to-end (real renderer + fragment lifecycle).
 * The in-SDK [com.sygic.sdk.map.MapObjectsTest] covers the data-model contract in
 * isolation; the tests here add the end-to-end / render-thread / no-flicker dimension.
 */
@RunWith(MockitoJUnitRunner::class)
class MapMarkerUpdateTests : BaseTest() {

    private val markerCoord = GeoCoordinates(48.10095535808773, 17.234824479529344)

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

        val marker = MapMarker.at(markerCoord)
            .withLabel(StyledText("Original"))
            .setMinZoomLevel(5F)
            .setMaxZoomLevel(21F)
            .build()
        assertTrue(mapView.mapDataModel.addMapObject(marker))
        delay(1500)

        val originalId = marker.id
        assertTrue("Native id must be assigned after addMapObject, got $originalId", originalId != 0)

        // Mutate in place via the now-mutable MarkerData fields.
        marker.data.label = StyledText("Modified")
        marker.data.minZoomLevel = 10F
        marker.data.maxZoomLevel = 20F
        marker.data.collisions = true
        marker.data.labelCollisions = true

        assertTrue(mapView.mapDataModel.updateMapObject(marker))
        delay(1500)

        assertEquals("Native id must be preserved across updateMapObject",
            originalId, marker.id)
        assertEquals(StyledText("Modified"), marker.data.label)

        scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    /**
     * Negative control for [updateMapObjectPreservesNativeId]: the legacy
     * remove + add workflow re-issues a fresh native id. If this regresses (i.e. the
     * native id stops changing), the comparison in the positive test loses meaning.
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

        val marker = MapMarker.at(markerCoord).withLabel(StyledText("A")).build()
        assertTrue(mapView.mapDataModel.addMapObject(marker))
        delay(1500)
        val firstId = marker.id
        assertTrue(firstId != 0)

        assertTrue(mapView.mapDataModel.removeMapObject(marker))
        delay(500)
        marker.data.label = StyledText("B")
        assertTrue(mapView.mapDataModel.addMapObject(marker))
        delay(1500)

        assertNotSame(
            "Legacy remove+add must re-issue a new native id; got $firstId twice",
            firstId, marker.id
        )

        scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    /**
     * After [updateMapObject] the marker must remain hit-testable at its position and the
     * Java-side mutated data must round-trip through the native layer back to the caller.
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
        mapView.cameraModel.setZoomLevel(19F)
        mapView.cameraModel.setTilt(0F)

        val marker = MapMarker.at(markerCoord).withLabel(StyledText("before")).build()
        assertTrue(mapView.mapDataModel.addMapObject(marker))
        delay(2000)
        val originalId = marker.id
        assertTrue(originalId != 0)

        marker.data.label = StyledText("after")
        marker.data.collisions = true
        assertTrue(mapView.mapDataModel.updateMapObject(marker))
        delay(1500)

        val callback: RequestObjectCallback = mock(verboseLogging = true)
        val captor = argumentCaptor<List<ViewObject<ViewObjectData>>>()
        val view = requireNotNull(mapView.getView())
        val x = view.width / 2F
        val y = view.height / 2F
        val requestId = mapView.requestObjectsAtPoint(x, y, callback)

        verify(callback, timeout(5_000L)).onRequestResult(captor.capture(), eq(x), eq(y), eq(requestId))

        val hit = captor.firstValue.filterIsInstance<MapMarker>().firstOrNull { it.id == originalId }
        assertTrue("Updated marker (id=$originalId) must be hit-testable at its position", hit != null)
        assertEquals(StyledText("after"), hit?.data?.label)

        scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    /**
     * Calls [updateMapObject] without a prior [addMapObject]. The documented contract is
     * "behaves like [addMapObject] when not yet in the model" — marker count goes to 1 and
     * a native id is issued.
     */
    @Test
    fun updateMapObjectBeforeAddBehavesLikeAdd(): Unit = runBlocking {
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }
        val mapView = getMapView(mapFragment)
        delay(1000)

        val marker = MapMarker.at(markerCoord).withLabel(StyledText("fresh")).build()
        assertEquals(0, marker.id)

        assertTrue(mapView.mapDataModel.updateMapObject(marker))
        delay(1500)

        assertTrue("Native id must be issued after first updateMapObject, got ${marker.id}",
            marker.id != 0)
        assertTrue(mapView.mapDataModel.getMapObjects().contains(marker))

        scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    /**
     * Add a batch of markers, mutate a subset, push the subset via [updateMapObject], and
     * verify that every marker's native id stayed put — including the un-updated half. This
     * catches a regression in the rebucket logic that [MapDataModel.updateMapObject] uses
     * to deal with mutated data-class hashCodes shifting buckets on Android API 26.
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
            MapMarker.at(GeoCoordinates(48.100 + i * 0.001, 17.234 + i * 0.001))
                .withLabel(StyledText("m_$i"))
                .build()
        }
        markers.forEach { assertTrue(mapView.mapDataModel.addMapObject(it)) }
        delay(1500)

        val originalIds = markers.map { it.id }
        assertTrue("Every marker must get a native id", originalIds.all { it != 0 })

        // Mutate the even-indexed half in place and push via updateMapObject.
        markers.filterIndexed { idx, _ -> idx % 2 == 0 }.forEachIndexed { i, m ->
            m.data.label = StyledText("updated_$i")
            m.data.collisions = true
            assertTrue(mapView.mapDataModel.updateMapObject(m))
        }
        delay(1500)

        markers.forEachIndexed { idx, m ->
            assertEquals(
                "Marker $idx native id must remain stable across update batch",
                originalIds[idx], m.id
            )
        }
        // Sanity: the model still contains exactly the same 10 markers.
        assertEquals(10, mapView.mapDataModel.getMapObjects().size)

        scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    /**
     * The flicker regression test. With the new in-place update, the marker's native slot
     * is overwritten atomically — the marker is never absent between successive updates,
     * so a hit test at the marker's coordinates must return it on every iteration.
     *
     * With the legacy remove+add workflow the marker would be missing in any iteration
     * that happened to poll between the remove and the add (and the native id would change
     * across the loop), so this test is sensitive to a regression that reroutes
     * [updateMapObject] back to remove+add semantics.
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

        val marker = MapMarker.at(markerCoord).withLabel(StyledText("flicker_0")).build()
        assertTrue(mapView.mapDataModel.addMapObject(marker))
        delay(2000)
        val originalId = marker.id
        assertTrue(originalId != 0)

        val view = requireNotNull(mapView.getView())
        val x = view.width / 2F
        val y = view.height / 2F

        val iterations = 20
        repeat(iterations) { i ->
            marker.data.label = StyledText("flicker_${i + 1}")
            marker.data.collisions = (i % 2 == 0)
            assertTrue(mapView.mapDataModel.updateMapObject(marker))

            val callback: RequestObjectCallback = mock(verboseLogging = true)
            val captor = argumentCaptor<List<ViewObject<ViewObjectData>>>()
            val requestId = mapView.requestObjectsAtPoint(x, y, callback)
            verify(callback, timeout(2_000L))
                .onRequestResult(captor.capture(), eq(x), eq(y), eq(requestId))

            val hit = captor.firstValue.filterIsInstance<MapMarker>()
                .firstOrNull { it.id == originalId }
            assertTrue(
                "Marker disappeared on iteration $i — flicker regression suspected",
                hit != null
            )
            assertEquals(
                "Native id must stay stable across the whole update cycle (iteration $i)",
                originalId, hit?.id
            )
        }

        scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    /**
     * Stress: bombard a single marker with concurrent updates from multiple coroutines
     * while the renderer is running. The data model uses synchronized blocks plus a
     * rebucket fallback so this must not crash, deadlock, or evict the marker.
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

        val marker = MapMarker.at(markerCoord).withLabel(StyledText("base")).build()
        assertTrue(mapView.mapDataModel.addMapObject(marker))
        delay(1500)
        val originalId = marker.id
        assertTrue(originalId != 0)

        runBlocking(Dispatchers.IO) {
            val jobs = (0 until 4).map { workerIdx ->
                launch {
                    repeat(25) { i ->
                        marker.data.label = StyledText("w${workerIdx}_$i")
                        marker.data.minZoomLevel = (i % 5).toFloat()
                        mapView.mapDataModel.updateMapObject(marker)
                    }
                }
            }
            jobs.forEach { it.join() }
        }

        delay(500)
        assertEquals(
            "Native id must survive concurrent updates",
            originalId, marker.id
        )
        assertTrue(
            "Marker must remain in the data model after concurrent updates",
            mapView.mapDataModel.getMapObjects().any { it.id == originalId }
        )

        val callback: RequestObjectCallback = mock(verboseLogging = true)
        val captor = argumentCaptor<List<ViewObject<ViewObjectData>>>()
        val view = requireNotNull(mapView.getView())
        val x = view.width / 2F
        val y = view.height / 2F
        val requestId = mapView.requestObjectsAtPoint(x, y, callback)
        verify(callback, timeout(3_000L)).onRequestResult(captor.capture(), any(), any(), eq(requestId))
        assertTrue(
            "Marker must still be hit-testable after the update storm",
            captor.firstValue.filterIsInstance<MapMarker>().any { it.id == originalId }
        )

        scenario.moveToState(Lifecycle.State.DESTROYED)
    }
}
