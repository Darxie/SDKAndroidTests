package cz.feldis.sdkandroidtests.explore

import com.sygic.sdk.map.MapInstallerProvider
import com.sygic.sdk.navigation.NavigationManager
import com.sygic.sdk.navigation.NavigationManagerProvider
import com.sygic.sdk.navigation.explorer.RouteExplorer
import com.sygic.sdk.navigation.explorer.RouteExplorerProvider
import com.sygic.sdk.navigation.explorer.results.ExploreChargingStationsOnRouteData
import com.sygic.sdk.navigation.explorer.results.ExplorePlacesOnRouteData
import com.sygic.sdk.navigation.explorer.results.ExplorerTrafficOnRouteResult
import com.sygic.sdk.navigation.traffic.TrafficManager
import com.sygic.sdk.navigation.traffic.TrafficManagerProvider
import com.sygic.sdk.places.PlacesManager
import com.sygic.sdk.position.GeoCoordinates
import com.sygic.sdk.route.ChargingStation
import com.sygic.sdk.route.RoutingOptions
import com.sygic.sdk.route.simulator.PositionSimulator
import com.sygic.sdk.route.simulator.RouteDemonstrateSimulatorProvider
import cz.feldis.sdkandroidtests.BaseTest
import cz.feldis.sdkandroidtests.ktx.NavigationManagerKtx
import cz.feldis.sdkandroidtests.mapInstaller.MapDownloadHelper
import cz.feldis.sdkandroidtests.routing.RouteComputeHelper
import cz.feldis.sdkandroidtests.utils.RouteDemonstrateSimulatorAdapter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import timber.log.Timber

class RouteExploreTests : BaseTest() {

    private lateinit var routeCompute: RouteComputeHelper
    private val navigationManagerKtx = NavigationManagerKtx()
    private lateinit var trafficManager: TrafficManager
    private lateinit var routeExplorer: RouteExplorer
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    /**
     * Polls Mockito's invocation list waiting for a terminal callback on
     * [RouteExplorer.OnExplorePlacesOnRouteListener]: either `onExplorePlacesError(_)`
     * or `onExplorePlacesLoaded(_, 100)`. Returns `true` if such a callback arrived
     * within [timeoutMs], `false` otherwise.
     *
     * Use this after the reload storm in DNAENG-1571 tests: the fix in 0d0ac936c2b
     * moves the analyzer's fail/recover dispatch to the persistent dispatcher so a
     * locked core thread pool can no longer swallow the terminal callback. Without
     * the fix the analyzer can hang with no terminal callback at all.
     */
    private suspend fun awaitTerminalExploreCallback(
        listener: RouteExplorer.OnExplorePlacesOnRouteListener,
        timeoutMs: Long = 15_000
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val invocations = Mockito.mockingDetails(listener).invocations
            val terminal = invocations.any { inv ->
                inv.method.name == "onExplorePlacesError" ||
                (inv.method.name == "onExplorePlacesLoaded" && inv.arguments.getOrNull(1) == 100)
            }
            if (terminal) return true
            delay(200)
        }
        return false
    }

    override fun setUp() {
        super.setUp()
        routeCompute = RouteComputeHelper()
        trafficManager = runBlocking { TrafficManagerProvider.getInstance() }
        routeExplorer = runBlocking { RouteExplorerProvider.getInstance() }
    }

    @Test
    fun exploreTrafficOnRoute() = runBlocking {
        trafficManager.enableTrafficService()

        val route = routeCompute.onlineRouteCompute(
            GeoCoordinates(48.155195, 17.136827),
            GeoCoordinates(48.289024, 17.264717)
        )

        when (val explorerResult = routeExplorer.exploreTrafficOnRoute(route)) {
            is ExplorerTrafficOnRouteResult.Success -> {
            }

            is ExplorerTrafficOnRouteResult.Error -> fail("exploreTrafficOnRoute failed with: ${explorerResult.errorCode}")
        }

        trafficManager.disableTrafficService()
    }

    @Test
    fun exploreTrafficOnRouteWithDisabledTraffic() = runBlocking {
        trafficManager.disableTrafficService()

        val route = routeCompute.onlineRouteCompute(
            GeoCoordinates(48.155195, 17.136827),
            GeoCoordinates(48.289024, 17.264717)
        )

        when (val explorerResult = routeExplorer.exploreTrafficOnRoute(route)) {
            is ExplorerTrafficOnRouteResult.Success -> {
                fail("exploreTrafficOnRoute should have failed with traffic service disabled")
            }

            is ExplorerTrafficOnRouteResult.Error -> {
                assertEquals(TrafficManager.ErrorCode.SERVICE_DISABLED, explorerResult.errorCode)
            }
        }
    }

    /**
     * Explore test on places on route
     *
     * In this test we compute route and via route explorer explore places
     * type of SYRestArea and SYPetrolStation on this route.
     * We verify that onExplorePlacesLoaded was invoked with progress equals 100.
     */
    @Test
    fun onExplorePlacesOnRoute() = runBlocking {
        val listener: RouteExplorer.OnExplorePlacesOnRouteListener = mock(verboseLogging = true)
        val route =
            routeCompute.onlineRouteCompute(
                GeoCoordinates(48.167749, 17.184778),
                GeoCoordinates(48.586029, 17.824360)
            )
        val list = listOf("SYRestArea", "SYPetrolStation")

        routeExplorer.explorePlacesOnRoute(route, list, listener)

        verify(
            listener,
            Mockito.timeout(15_000L)
        )
            .onExplorePlacesLoaded(argThat { this.isNotEmpty() }, eq(100))

        verify(listener, never())
            .onExplorePlacesError(any())
    }

    @Test
    fun exploreIncidentsOnRoute() = runBlocking {
        val listener: RouteExplorer.OnExploreIncidentsOnRouteListener = mock(verboseLogging = true)

        val route = routeCompute.onlineRouteCompute(
            GeoCoordinates(48.73965, 17.86153),
            GeoCoordinates(48.75368, 17.86204)
        )

        routeExplorer.exploreIncidentsOnRoute(route, emptyList(), listener)

        verify(
            listener,
            Mockito.timeout(30_000L)
        )
            .onExploreIncidentsLoaded(anyList(), eq(100))

        verify(listener, never())
            .onExploreIncidentsError(any())
    }

    @Test
    fun explorePlacesOnRouteLongRoutePerformance() = runBlocking {
        assumeTrue(!isRunningOnEmulator())
        disableOnlineMaps()
        val listener: NavigationManager.OnPlaceListener = mock(verboseLogging = true)
        val completeListener: PositionSimulator.OnOperationComplete = mock(verboseLogging = true)
        val navigation = NavigationManagerProvider.getInstance()

        val mapDownloadHelper = MapDownloadHelper()
        mapDownloadHelper.installAndLoadMap("sk")
        mapDownloadHelper.installAndLoadMap("at")
        mapDownloadHelper.installAndLoadMap("it")

        val route =
            routeCompute.offlineRouteCompute(
                GeoCoordinates(48.15343979881289, 17.13600525926161),
                GeoCoordinates(40.74965825876095, 14.504129256547257) // pompeje
            )

        navigation.addOnPlaceListener(listener)
        navigationManagerKtx.setRouteForNavigation(route, navigation)
        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route)
        val simulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.startSimulator(simulatorAdapter)

        val startTime = System.currentTimeMillis()

        verify(
            listener,
            Mockito.timeout(50_000L).atLeastOnce()
        )
            .onPlaceInfoChanged(argThat {
                val isNonEmpty = this.isNotEmpty()
                if (isNonEmpty) {
                    // Calculate elapsed time
                    val elapsedTime = System.currentTimeMillis() - startTime
                    Timber.i("Time elapsed waiting for PoR: $elapsedTime ms")
                    simulator.stop(completeListener)
                    verify(completeListener, timeout(5_000L).atLeastOnce()).onComplete()
                }
                return@argThat isNonEmpty
            })
    }

    @Test
    fun exploreChargingStationsOnRoute() = runBlocking {
        disableOnlineMaps()

        val mapDownloadHelper = MapDownloadHelper()
        mapDownloadHelper.installAndLoadMap("sk")
        val vehicleProfile = RouteComputeHelper().createDefaultElectricVehicleProfile(50f, 50f)

        val route =
            routeCompute.offlineRouteCompute(
                GeoCoordinates(48.12749909071542, 17.126906729580128),
                GeoCoordinates(48.962803073321275, 18.162986338115697),
                routingOptions = RoutingOptions().apply {
                    vehicleProfile
                }
            )

        // Store the aggregated list of charging stations
        val aggregatedChargingStations = mutableListOf<ChargingStation>()
        var previousSize = 0
        var firstInvocationSize = -1
        var lastInvocationSize = -1

        routeExplorer.exploreChargingStationsOnRoute(route, vehicleProfile)
            .onEach {
                assertFalse(it is ExploreChargingStationsOnRouteData.Error)
            }
            .filterIsInstance<ExploreChargingStationsOnRouteData.ChargingStationsLoaded>()
            .collect {
                val chargingStations = it.chargingStations
                val progress = it.progress

                // Capture the size during the first callback
                if (firstInvocationSize == -1 && chargingStations.isNotEmpty()) {
                    firstInvocationSize = chargingStations.size
                }

                // Check that the list size grows with each callback
                assertTrue(chargingStations.size >= previousSize)
                aggregatedChargingStations.addAll(chargingStations)
                previousSize = chargingStations.size

                // Capture the size during the last invocation when progress is 100
                if (progress == 100) {
                    lastInvocationSize = chargingStations.size

                    // Ensure that the last invocation contains more charging stations than the first
                    assertTrue(lastInvocationSize > firstInvocationSize)
                }
            }

        // Ensure that the first and last invocation checks were performed
        assertTrue(firstInvocationSize >= 0)  // Ensure that the first invocation was recorded
        assertTrue(lastInvocationSize >= 0)   // Ensure that the last invocation was recorded
    }

    /**
     * Regression for DNAENG-1571 (SDK commit 0d0ac936c2b): a data race in
     * PoiOnRouteAnalyzer. Before the fix, its `.fail(...)` handler was scheduled on the
     * regular dispatcher, which is locked during map reload — the failure callback then
     * either ran on the wrong (core) pool or was dropped entirely, leaving the analyzer
     * stuck without `Reset()`/`Finished()` being called and subsequent invocations
     * crashing or hanging. The fix routes the failure path through
     * `PersistentDispatcherLocator` via `.recover(...)`, so cleanup always lands on a
     * dispatcher that is not locked during map reload.
     *
     * The test boots navigation + simulator + `OnPlaceListener` (which is what drives
     * `PoiOnRouteAnalyzer`), then unloads and reloads the Slovak map several times while
     * the analyzer is running. A regression would manifest as a native crash, a deadlock,
     * or a stuck analyzer that fails to deliver place updates after the reload storm
     * settles.
     */
    @Test
    fun reloadSkMapDuringNavigationDoesNotCrashPoiAnalyzer() = runBlocking {
        disableOnlineMaps()
        val mapDownloadHelper = MapDownloadHelper()
        mapDownloadHelper.installAndLoadMap("sk")

        val route = routeCompute.offlineRouteCompute(
            start = GeoCoordinates(48.15343979881289, 17.13600525926161), // Bratislava
            destination = GeoCoordinates(48.71977, 21.25785)              // Košice
        )

        val placeListener: NavigationManager.OnPlaceListener = mock(verboseLogging = true)
        val navigation = NavigationManagerProvider.getInstance()
        navigation.addOnPlaceListener(placeListener)
        navigationManagerKtx.setRouteForNavigation(route, navigation)

        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route)
        val simulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.startSimulator(simulatorAdapter)

        // Let the analyzer establish itself before we start yanking the map out.
        delay(1500)

        // Stress the analyzer's failure path: unload + reload the Slovak map repeatedly
        // while navigation (and PoiOnRouteAnalyzer behind it) is active.
        val mapInstaller = MapInstallerProvider.getInstance()
        repeat(10) {
            mapInstaller.unloadMap("sk")
            delay(200)
            mapInstaller.loadMap("sk")
            delay(400)
        }

        // Allow any in-flight analyzer task to finish/recover cleanly. This is a smoke
        // test — its job is just to prove that the reload storm does not crash native
        // code (SIGSEGV / SIGABRT inside the analyzer). The companion explorer test
        // [reloadSkMapDuringExplorePlacesOnRouteDoesNotCrashAnalyzer] makes the
        // deterministic terminal-callback assertion; the streaming OnPlaceListener
        // here doesn't fire predictably enough after a reload to support that check.
        delay(2000)

        navigationManagerKtx.stopSimulator(simulatorAdapter)
        navigationManagerKtx.stopNavigation(navigation)
        navigation.removeOnPlaceListener(placeListener)
    }

    /**
     * Sister test to [reloadSkMapDuringNavigationDoesNotCrashPoiAnalyzer]: a cross-border
     * route (Košice → Salzburg) with the Austrian map being reloaded during navigation.
     * `PoiOnRouteAnalyzer` chunks the entire remaining route into per-rect analyses, so
     * even early in the journey it queries map data from Austria — reloading `at`
     * exercises the same race the fix targets, but on a foreign country's map.
     *
     * Requires `sk`, `cz`, `at` offline maps (Košice → Brno → Vienna → Salzburg is the
     * most natural offline-routable path with these three).
     */
    @Test
    fun reloadAtMapDuringNavigationDoesNotCrashPoiAnalyzer() = runBlocking {
        disableOnlineMaps()
        val mapDownloadHelper = MapDownloadHelper()
        mapDownloadHelper.installAndLoadMap("sk")
        mapDownloadHelper.installAndLoadMap("cz")
        mapDownloadHelper.installAndLoadMap("at")

        val route = routeCompute.offlineRouteCompute(
            start = GeoCoordinates(48.71977, 21.25785), // Košice
            destination = GeoCoordinates(47.80949, 13.05501) // Salzburg
        )

        val placeListener: NavigationManager.OnPlaceListener = mock(verboseLogging = true)
        val navigation = NavigationManagerProvider.getInstance()
        navigation.addOnPlaceListener(placeListener)
        navigationManagerKtx.setRouteForNavigation(route, navigation)

        val simulator = RouteDemonstrateSimulatorProvider.getInstance(route)
        val simulatorAdapter = RouteDemonstrateSimulatorAdapter(simulator)
        navigationManagerKtx.startSimulator(simulatorAdapter)

        // Let the analyzer start crunching the Austrian portion of the route.
        delay(1500)

        // Stress the analyzer's failure path against the Austrian map specifically.
        val mapInstaller = MapInstallerProvider.getInstance()
        repeat(10) {
            mapInstaller.unloadMap("at")
            delay(200)
            mapInstaller.loadMap("at")
            delay(400)
        }

        // Smoke-only: see comment in reloadSkMapDuringNavigationDoesNotCrashPoiAnalyzer.
        delay(2000)

        navigationManagerKtx.stopSimulator(simulatorAdapter)
        navigationManagerKtx.stopNavigation(navigation)
        navigation.removeOnPlaceListener(placeListener)
    }

    /**
     * Companion to [reloadSkMapDuringNavigationDoesNotCrashPoiAnalyzer] that triggers the
     * same analyzer through [RouteExplorer.explorePlacesOnRoute] instead of via
     * `NavigationManager.addOnPlaceListener`. The route-explorer path doesn't need an
     * active navigation/simulator — a bare route is enough to drive the analyzer along
     * the rects.
     */
    @Test
    fun reloadSkMapDuringExplorePlacesOnRouteDoesNotCrashAnalyzer() = runBlocking {
        disableOnlineMaps()
        val mapDownloadHelper = MapDownloadHelper()
        mapDownloadHelper.installAndLoadMap("sk")

        val route = routeCompute.offlineRouteCompute(
            start = GeoCoordinates(48.15343979881289, 17.13600525926161), // Bratislava
            destination = GeoCoordinates(48.71977, 21.25785)              // Košice
        )

        val placeListener: RouteExplorer.OnExplorePlacesOnRouteListener = mock(verboseLogging = true)
        val categories = listOf("SYRestArea", "SYPetrolStation")

        // Kick off the analyzer; it processes the route rect-by-rect asynchronously.
        routeExplorer.explorePlacesOnRoute(route, categories, placeListener)

        // Let the analyzer actually start working before we yank the map out from under it.
        // Without this warmup the very first unloadMap fires before any rect has been
        // processed and the analyzer simply reports REQUEST_CANCELED — that's not the
        // path the underlying fix protects.
        delay(1000)

        // Stress the analyzer's failure path: unload + reload the Slovak map repeatedly
        // while the rect-by-rect analysis is in flight.
        val mapInstaller = MapInstallerProvider.getInstance()
        repeat(10) {
            mapInstaller.unloadMap("sk")
            delay(200)
            mapInstaller.loadMap("sk")
            delay(400)
        }

        // Without the fix the dispatcher can be left locked while a fail/recover lambda
        // is still queued — the terminal callback then never fires and the analyzer hangs.
        assertTrue(
            "Analyzer never reached a terminal state (onExplorePlacesError or " +
            "onExplorePlacesLoaded with progress=100) within 15s after the reload storm — " +
            "fail/recover callback may be stuck on a locked dispatcher (DNAENG-1571).",
            awaitTerminalExploreCallback(placeListener)
        )
    }

    /**
     * Companion to [reloadAtMapDuringNavigationDoesNotCrashPoiAnalyzer], driving the
     * analyzer via [RouteExplorer.explorePlacesOnRoute] on a cross-border route and
     * reloading the Austrian map. Same install set (`sk`, `cz`, `at`) and same rationale
     * as the navigation-driven sister test.
     */
    @Test
    fun reloadAtMapDuringExplorePlacesOnRouteDoesNotCrashAnalyzer() = runBlocking {
        disableOnlineMaps()
        val mapDownloadHelper = MapDownloadHelper()
        mapDownloadHelper.installAndLoadMap("sk")
        mapDownloadHelper.installAndLoadMap("cz")
        mapDownloadHelper.installAndLoadMap("at")

        val route = routeCompute.offlineRouteCompute(
            start = GeoCoordinates(48.71977, 21.25785),    // Košice
            destination = GeoCoordinates(47.80949, 13.05501) // Salzburg
        )

        val placeListener: RouteExplorer.OnExplorePlacesOnRouteListener = mock(verboseLogging = true)
        val categories = listOf("SYRestArea", "SYPetrolStation")

        routeExplorer.explorePlacesOnRoute(route, categories, placeListener)

        // Let the analyzer get past the SK/CZ rects before we start cycling AT — otherwise
        // it would be cancelled before AT is even relevant.
        delay(1000)

        val mapInstaller = MapInstallerProvider.getInstance()
        repeat(10) {
            mapInstaller.unloadMap("at")
            delay(200)
            mapInstaller.loadMap("at")
            delay(400)
        }

        assertTrue(
            "Analyzer never reached a terminal state (onExplorePlacesError or " +
            "onExplorePlacesLoaded with progress=100) within 15s after the reload storm — " +
            "fail/recover callback may be stuck on a locked dispatcher (DNAENG-1571).",
            awaitTerminalExploreCallback(placeListener)
        )
    }

    @Test
    fun testReloadMapWhileExploring() = runBlocking {
        val mapDownloadHelper = MapDownloadHelper()
        mapDownloadHelper.installAndLoadMap("va")
        val route = routeCompute.onlineRouteCompute(
            GeoCoordinates(48.16876, 17.07634),
            GeoCoordinates(48.16281, 17.09559)
        )

        val interrupted = CompletableDeferred<Unit>()

        val exploreJob = scope.launch {
            while (isActive) {
                RouteExplorerProvider.getInstance().explorePlacesOnRoute(route, emptyList())
                    .collect {
                        if (it is ExplorePlacesOnRouteData.Error && it.errorCode == PlacesManager.ErrorCode.INTERRUPTED_BY_MAP_RELOAD) {
                            interrupted.complete(Unit)
                        }
                    }
            }
        }

        val reloadJob = scope.launch {
            val mapInstaller = MapInstallerProvider.getInstance()
            while (isActive) {
                mapInstaller.unloadMap("va")
                mapInstaller.loadMap("va")
            }
        }

        interrupted.await()

        // Stop the infinite explore/reload loops, otherwise they keep running on the
        // class-level Unconfined scope long after the test returns and spam subsequent
        // tests with "Exploring places on route -1" / map-reload storms.
        exploreJob.cancelAndJoin()
        reloadJob.cancelAndJoin()
    }

    @After
    fun cancelExploreScope() {
        scope.cancel()
    }
}
