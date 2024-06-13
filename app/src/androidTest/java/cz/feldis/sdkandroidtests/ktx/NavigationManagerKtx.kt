package cz.feldis.sdkandroidtests.ktx

import com.sygic.sdk.navigation.NavigationManager
import com.sygic.sdk.route.Route
import com.sygic.sdk.route.simulator.NmeaLogSimulator
import com.sygic.sdk.route.simulator.RouteDemonstrateSimulator
import cz.feldis.sdkandroidtests.utils.Simulator
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class NavigationManagerKtx {
    suspend fun stopNavigation(navigation: NavigationManager): Unit = suspendCoroutine { continuation ->
        navigation.stopNavigation { continuation.resume(Unit) }
    }

    suspend fun setRouteForNavigation(route: Route, navigation: NavigationManager): Unit = suspendCoroutine { continuation ->
        navigation.setRouteForNavigation(route) {
            continuation.resume(Unit)
        }
    }

    suspend fun setSpeedMultiplier(simulator: Simulator, speedMultiplier: Float): Unit = suspendCoroutine { continuation ->
        simulator.setSpeedMultiplier(speedMultiplier) {
            continuation.resume(Unit)
        }
    }

    suspend fun stopSimulator(simulator: Simulator): Unit = suspendCoroutine { continuation ->
        simulator.stop {
            continuation.resume(Unit)
        }
    }

    suspend fun startSimulator(simulator: Simulator): Unit = suspendCoroutine { continuation ->
        simulator.start {
            continuation.resume(Unit)
        }
    }
}