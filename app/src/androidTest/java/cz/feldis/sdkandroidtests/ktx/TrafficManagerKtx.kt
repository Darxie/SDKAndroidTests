package cz.feldis.sdkandroidtests.ktx

import com.sygic.sdk.navigation.traffic.TrafficManager
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class TrafficManagerKtx {
    suspend fun enableTrafficService(trafficManager: TrafficManager): Unit = suspendCoroutine { continuation ->
        trafficManager.enableTrafficService({ continuation.resume(Unit) })
    }

    suspend fun disableTrafficService(trafficManager: TrafficManager): Unit = suspendCoroutine { continuation ->
        trafficManager.disableTrafficService({ continuation.resume(Unit) })
    }
}