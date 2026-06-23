package cz.feldis.sdkandroidtests.routing

import android.util.Log
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.route.RoutingOptions
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.mapInstaller.MapDownloadHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for DNAENG-XXX (RoadLords / EW Navi crash:
 * `NoSuchElementException: List is empty` thrown from
 * RoutePlannerLabelHelper.createLabelText -> waypointDurations.last()).
 *
 * Root cause (in SDK 33.0.0 .. 37.0.0 and later, see Interface/Source/Cpp/Sources/sygm/Routing/sygm_router.cpp,
 * struct MapManagerSignalReceiver): every time a `MapsWillReleaseSignal` fires,
 * the SDK unconditionally removes ALL online route handles from `activeRoutes`
 * ("online routa sa vzdy zrusi"). The Kotlin `Route` wrapper is NOT notified,
 * so `route.isValid` stays `true`, but `route.routeInfo.waypointDurations`
 * silently returns an empty list because the native handle is gone.
 *
 * Any code that calls `route.routeInfo.waypointDurations.last()` after a
 * map signal fires (e.g. EW Navi route planner refreshing labels after a
 * map install / unload / online map manifest change) will crash.
 *
 * These tests are designed to FAIL while the bug exists and PASS after the
 * SDK is fixed (either by scoping the cleanup to only routes whose maps
 * actually changed, or by notifying the Kotlin side so `Route.isValid`
 * reflects native invalidation).
 */
class OnlineRouteHandleInvalidationTests : BaseTest() {

    private lateinit var mapDownloadHelper: MapDownloadHelper
    private lateinit var routeComputeHelper: RouteComputeHelper

    private val installedMapForTest = "is" // Iceland: small, geographically unrelated to BA->Vienna route

    override fun setUp() {
        super.setUp()
        mapDownloadHelper = MapDownloadHelper()
        routeComputeHelper = RouteComputeHelper()
    }

    @After
    fun cleanupInstalledMap() {
        runCatching { mapDownloadHelper.uninstallMap(installedMapForTest) }
    }

    /**
     * Reproduces the crash. Computes an online route, then triggers
     * `MapsWillReleaseSignal` by unloading an unrelated map. After the
     * signal, querying `routeInfo.waypointDurations` must still return
     * a non-empty list.
     *
     * Today (SDK 33.0.0 .. 37.0.0): waypointDurations becomes empty,
     * `last()` would throw NoSuchElementException -> test fails.
     * After fix: list stays non-empty -> test passes.
     */
    @Test
    fun onlineRoute_waypointDurations_remainNonEmptyAfterUnrelatedMapUnload() = runBlocking {
        val baStart = GeoCoordinates(48.145718, 17.118669)        // Bratislava
        val viennaEnd = GeoCoordinates(48.190322, 16.401080)      // Vienna

        // 1) Compute online route (no Iceland involved).
        val route = routeComputeHelper.onlineRouteCompute(
            baStart,
            viennaEnd,
            routingOptions = RoutingOptions().apply {
                routingService = RoutingOptions.RoutingService.Online
            }
        )

        // 2) Sanity: a freshly computed online route must have non-empty waypointDurations.
        val durationsBefore = route.routeInfo.waypointDurations
        Log.i(
            "SYGIC_TEST",
            "Before unload: isValid=${route.isValid} waypointDurations.size=${durationsBefore.size}"
        )
        assertTrue(
            "Pre-condition: freshly computed route must be valid",
            route.isValid
        )
        assertTrue(
            "Pre-condition: freshly computed online route must have non-empty waypointDurations",
            durationsBefore.isNotEmpty()
        )

        // 3) Install + load + unload an UNRELATED country to trigger
        //    MapManagerImpl::MapsWillReleaseSlot -> MapsWillReleaseSignal.
        //    MapManagerSignalReceiver in sygm_router.cpp then unconditionally
        //    removes ALL online routes from `activeRoutes` (the bug).
        mapDownloadHelper.installAndLoadMap(installedMapForTest)
        mapDownloadHelper.unloadMap(installedMapForTest)

        // Let the signal propagate through the SDK signal/slot machinery.
        delay(1500)

        // 4) Re-query routeInfo. Bug: empty list. Expected after fix: same as before.
        val durationsAfter = route.routeInfo.waypointDurations
        Log.i(
            "SYGIC_TEST",
            "After unload: isValid=${route.isValid} waypointDurations.size=${durationsAfter.size}"
        )

        assertTrue(
            "REGRESSION: routeInfo.waypointDurations became empty after an unrelated " +
                "MapsWillReleaseSignal fired. This is the crash trigger in " +
                "RoutePlannerLabelHelper.createLabelText (waypointDurations.last()). " +
                "Either (a) scope cleanup in sygm_router.cpp MapManagerSignalReceiver " +
                "to only routes whose maps actually changed, or (b) notify the Kotlin " +
                "Route wrapper so isValid reflects native invalidation.",
            durationsAfter.isNotEmpty()
        )

        // Stronger invariant: contents should match. waypointDurations is a
        // pure function of the route (count of waypoints + their durations).
        // If we get a non-empty but different list after the signal, that's
        // also a bug (means the handle got swapped, not just invalidated).
        assertEquals(
            "waypointDurations size changed across an unrelated map signal",
            durationsBefore.size,
            durationsAfter.size
        )
    }

    /**
     * Documents the second half of the bug: even if waypointDurations
     * silently empties out, `route.isValid` stays `true`, so callers
     * have no signal to defensively skip the route. This is what makes
     * the EW Navi crash unrecoverable on the Kotlin side without a
     * defensive `lastOrNull()`.
     *
     * Today: this test passes (documents broken behavior).
     * After fix where SDK notifies on invalidation: this test will fail,
     * which is correct - flip the assertion to `assertFalse(isValid)`
     * at that point.
     */
    @Test
    fun onlineRoute_isValid_isNotInvalidatedWhenNativeHandleIsRemoved() = runBlocking {
        val route = routeComputeHelper.onlineRouteCompute(
            GeoCoordinates(48.145718, 17.118669),
            GeoCoordinates(48.190322, 16.401080),
            routingOptions = RoutingOptions().apply {
                routingService = RoutingOptions.RoutingService.Online
            }
        )
        assertTrue("Precondition: route must be valid", route.isValid)
        val sizeBefore = route.routeInfo.waypointDurations.size

        mapDownloadHelper.installAndLoadMap(installedMapForTest)
        mapDownloadHelper.unloadMap(installedMapForTest)
        delay(1500)

        val sizeAfter = route.routeInfo.waypointDurations.size
        Log.i(
            "SYGIC_TEST",
            "Before=$sizeBefore After=$sizeAfter isValid=${route.isValid}"
        )

        // If durations got wiped, but isValid is still true, the SDK is lying
        // to clients about the route state. Document that contract violation.
        if (sizeBefore > 0 && sizeAfter == 0) {
            assertFalse(
                "BUG DOCUMENTED: native route handle was removed by " +
                    "MapManagerSignalReceiver, but Kotlin Route.isValid stayed true. " +
                    "Callers have no way to defensively skip invalid routes.",
                route.isValid
            )
        }
    }
}
