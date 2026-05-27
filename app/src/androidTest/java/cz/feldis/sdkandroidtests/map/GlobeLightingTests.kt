package cz.feldis.sdkandroidtests.map

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.sygic.sdk.map.MapView
import com.sygic.sdk.position.GeoCoordinates
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.SygicActivity
import cz.feldis.sdkandroidtests.TestMapFragment
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * Acceptance tests for [MapView.setGlobeLightMode] introduced in SDK commit dda02f37
 * (PR 3531 / PR 3498).
 *
 * Covers:
 *  - listener and suspend variants
 *  - all three [MapView.GlobeLightMode] kinds (Automatic, Custom, Camera)
 *  - executor parameter dispatch
 *  - mode switching and concurrent setter calls (no crashes / no stuck callbacks)
 *
 * Motivation: GPS navi product asked to render the entire globe fully lit so the navigation
 * camera animation does not pan into a dark hemisphere. The new public API exposes
 * [MapView.GlobeLightMode.Camera] for this case plus [MapView.GlobeLightMode.Custom] for
 * arbitrary direction vectors.
 */
@RunWith(MockitoJUnitRunner::class)
class GlobeLightingTests : BaseTest() {

    @Test
    fun setGlobeLightModeAutomaticListenerReturnsSuccess(): Unit = runBlocking {
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }
        val mapView = getMapView(mapFragment)
        delay(1000)

        val listener: MapView.SetGlobeLightModeListener = mock(verboseLogging = true)
        mapView.setGlobeLightMode(MapView.GlobeLightMode.Automatic, listener)

        verify(listener, timeout(5_000L)).onSuccess()
        verify(listener, never()).onError()

        scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    @Test
    fun setGlobeLightModeCustomSuspendReturnsSuccess(): Unit = runBlocking {
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }
        val mapView = getMapView(mapFragment)
        delay(1000)

        val result = mapView.setGlobeLightMode(
            MapView.GlobeLightMode.Custom(dirX = 1f, dirY = 0f, dirZ = 0f)
        )
        assertEquals(MapView.SetGlobeLightModeResult.Success, result)

        scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    /**
     * Reproduces the GPS-navi use case: zoom out to the globe and switch to
     * [MapView.GlobeLightMode.Camera] so the visible hemisphere is fully lit regardless
     * of time/date. We render for a few seconds to catch any rendering crash that the
     * new lighting path could introduce.
     */
    @Test
    fun setGlobeLightModeCameraDoesNotCrashAndRendersGlobe(): Unit = runBlocking {
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }
        val mapView = getMapView(mapFragment)
        mapView.cameraModel.setPosition(GeoCoordinates(0.0, 0.0))
        mapView.cameraModel.setZoomLevel(2F)
        mapView.cameraModel.setTilt(0F)
        delay(1500)

        val result = mapView.setGlobeLightMode(MapView.GlobeLightMode.Camera)
        assertEquals(MapView.SetGlobeLightModeResult.Success, result)

        // Slow rotation around the globe so the camera direction (== light direction)
        // changes and the lighting must follow without artefacts.
        repeat(4) { i ->
            mapView.cameraModel.setRotation(i * 90F)
            delay(500)
        }

        scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    @Test
    fun setGlobeLightModeSwitchModesRepeatedly(): Unit = runBlocking {
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }
        val mapView = getMapView(mapFragment)
        mapView.cameraModel.setZoomLevel(3F)
        delay(1500)

        val modes = listOf(
            MapView.GlobeLightMode.Automatic,
            MapView.GlobeLightMode.Custom(1f, 0f, 0f),
            MapView.GlobeLightMode.Custom(0f, 1f, 0f),
            MapView.GlobeLightMode.Custom(0f, 0f, 1f),
            MapView.GlobeLightMode.Custom(-1f, -1f, -1f),
            MapView.GlobeLightMode.Camera,
            MapView.GlobeLightMode.Automatic
        )

        modes.forEach { mode ->
            val result = mapView.setGlobeLightMode(mode)
            assertEquals("Setting mode $mode should succeed",
                MapView.SetGlobeLightModeResult.Success, result)
            delay(200)
        }

        scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    /**
     * A zero-length direction vector is degenerate. The native side normalises the input,
     * so we either get [MapView.SetGlobeLightModeResult.Success] (zero handled gracefully)
     * or [MapView.SetGlobeLightModeResult.Error] — both are acceptable; the contract is
     * that the call must complete and must not crash the renderer.
     */
    @Test
    fun setGlobeLightModeCustomWithZeroVectorHandledGracefully(): Unit = runBlocking {
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }
        val mapView = getMapView(mapFragment)
        delay(1000)

        val result = mapView.setGlobeLightMode(MapView.GlobeLightMode.Custom(0f, 0f, 0f))
        assertTrue(
            "Expected Success or Error, got $result",
            result is MapView.SetGlobeLightModeResult.Success ||
                result is MapView.SetGlobeLightModeResult.Error
        )

        // Map must still render & accept further commands.
        val recovery = mapView.setGlobeLightMode(MapView.GlobeLightMode.Automatic)
        assertEquals(MapView.SetGlobeLightModeResult.Success, recovery)

        scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    /**
     * The [java.util.concurrent.Executor] overload must dispatch the listener callback on
     * the supplied executor (Android Auto integrations rely on this to marshal results back
     * to a specific worker thread).
     */
    @Test
    fun setGlobeLightModeListenerInvokedOnProvidedExecutor(): Unit = runBlocking {
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }
        val mapView = getMapView(mapFragment)
        delay(1000)

        val executor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "globe-light-test-executor")
        }
        try {
            val callbackThread = AtomicReference<String?>(null)
            val listener: MapView.SetGlobeLightModeListener = mock(verboseLogging = true)
            // Spy on the listener via a wrapping listener that records the calling thread.
            val recordingListener = object : MapView.SetGlobeLightModeListener {
                override fun onSuccess() {
                    callbackThread.set(Thread.currentThread().name)
                    listener.onSuccess()
                }
                override fun onError() {
                    callbackThread.set(Thread.currentThread().name)
                    listener.onError()
                }
            }

            mapView.setGlobeLightMode(
                MapView.GlobeLightMode.Custom(1f, 1f, 1f),
                recordingListener,
                executor
            )

            verify(listener, timeout(5_000L)).onSuccess()
            assertEquals("globe-light-test-executor", callbackThread.get())
        } finally {
            executor.shutdownNow()
            scenario.moveToState(Lifecycle.State.DESTROYED)
        }
    }

    /**
     * Fire several setters back-to-back. The contract we care about is "no stuck callbacks,
     * no crashes" — each command must produce a Success result and the engine must end up
     * in the last-set mode (verified implicitly by the renderer continuing to update without
     * artefacts during the trailing render delay).
     */
    @Test
    fun setGlobeLightModeConcurrentCallsAllSucceed(): Unit = runBlocking {
        val mapFragment = TestMapFragment.newInstance(getInitialCameraState())
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, mapFragment)
                .commitNow()
        }
        val mapView = getMapView(mapFragment)
        mapView.cameraModel.setZoomLevel(3F)
        delay(1500)

        val listeners = List(5) { mock<MapView.SetGlobeLightModeListener>(verboseLogging = true) }
        val modes = listOf(
            MapView.GlobeLightMode.Automatic,
            MapView.GlobeLightMode.Custom(1f, 0f, 0f),
            MapView.GlobeLightMode.Custom(0f, 1f, 0f),
            MapView.GlobeLightMode.Camera,
            MapView.GlobeLightMode.Custom(0.5f, 0.5f, 0.5f)
        )

        modes.forEachIndexed { idx, mode ->
            mapView.setGlobeLightMode(mode, listeners[idx])
        }

        listeners.forEach { l ->
            verify(l, timeout(5_000L)).onSuccess()
            verify(l, never()).onError()
        }

        // Map must still be live afterwards — render a final frame.
        delay(1000)
        scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    /**
     * Basic lifecycle: set a mode on one MapView, tear the activity down completely, then
     * spin up a fresh MapView and apply a different mode. The new MapView must accept
     * setGlobeLightMode normally — no state from the previous instance must linger and
     * trigger a crash.
     */
    @Test
    fun setGlobeLightModeSurvivesMapViewRecreation(): Unit = runBlocking {
        val firstFragment = TestMapFragment.newInstance(getInitialCameraState())
        val firstScenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, firstFragment)
                .commitNow()
        }
        val firstMapView = getMapView(firstFragment)
        delay(1000)

        val firstResult = firstMapView.setGlobeLightMode(MapView.GlobeLightMode.Custom(1f, 1f, 1f))
        assertEquals(MapView.SetGlobeLightModeResult.Success, firstResult)
        delay(500)
        firstScenario.moveToState(Lifecycle.State.DESTROYED)
        delay(1000)

        val secondFragment = TestMapFragment.newInstance(getInitialCameraState())
        val secondScenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, secondFragment)
                .commitNow()
        }
        val secondMapView = getMapView(secondFragment)
        delay(1000)

        val secondResult = secondMapView.setGlobeLightMode(MapView.GlobeLightMode.Camera)
        assertEquals(MapView.SetGlobeLightModeResult.Success, secondResult)

        secondScenario.moveToState(Lifecycle.State.DESTROYED)
    }

    /**
     * Simulates a user repeatedly switching between Sygic and another foreground app:
     * 5 rounds of (create MapView → set globe light mode → destroy). Each round picks a
     * different mode. The whole sequence must complete without a crash and every setter
     * must report Success.
     *
     * Catches regressions in per-MapView teardown of native globe-lighting state — e.g.
     * if a destroyed MapView left a dangling native listener that the next setGlobeLightMode
     * would touch, this would explode somewhere in the middle of the loop.
     */
    @Test
    fun setGlobeLightModeRepeatedAppSwitchingDoesNotCrash(): Unit = runBlocking {
        val modes = listOf(
            MapView.GlobeLightMode.Automatic,
            MapView.GlobeLightMode.Custom(1f, 0f, 0f),
            MapView.GlobeLightMode.Camera,
            MapView.GlobeLightMode.Custom(-1f, -1f, -1f),
            MapView.GlobeLightMode.Automatic
        )

        modes.forEachIndexed { round, mode ->
            val fragment = TestMapFragment.newInstance(getInitialCameraState())
            val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
                it.supportFragmentManager.beginTransaction()
                    .add(android.R.id.content, fragment)
                    .commitNow()
            }
            val mapView = getMapView(fragment)
            delay(800)

            val listener: MapView.SetGlobeLightModeListener = mock(verboseLogging = true)
            mapView.setGlobeLightMode(mode, listener)
            verify(listener, timeout(5_000L)).onSuccess()
            verify(listener, never()).onError()
            delay(500)

            scenario.moveToState(Lifecycle.State.DESTROYED)
            delay(800)
        }
    }

    /**
     * Race scenario: fire setGlobeLightMode and immediately destroy the activity, before
     * the asynchronous callback has any chance to arrive. The engine must release the
     * pending callback cleanly — no native callback into a freed MapView pointer, no
     * unhandled exception bubbling up to the test thread.
     */
    @Test
    fun setGlobeLightModeFollowedByImmediateDestroyDoesNotCrash(): Unit = runBlocking {
        val fragment = TestMapFragment.newInstance(getInitialCameraState())
        val scenario = ActivityScenario.launch(SygicActivity::class.java).onActivity {
            it.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, fragment)
                .commitNow()
        }
        val mapView = getMapView(fragment)
        delay(1000)

        val listener: MapView.SetGlobeLightModeListener = mock(verboseLogging = true)
        mapView.setGlobeLightMode(MapView.GlobeLightMode.Custom(0.5f, 0.5f, 0.5f), listener)
        // Do NOT wait for onSuccess — destroy while the callback may still be in flight.
        scenario.moveToState(Lifecycle.State.DESTROYED)
        delay(1500)
        // Reaching this point without an exception or crash is the assertion. The
        // listener may have received either onSuccess (if the callback raced ahead of
        // destroy) or nothing at all — both outcomes are acceptable; what is not
        // acceptable is a native crash or a test process termination.
    }
}
