package cz.feldis.sdkandroidtests.ktx

import com.sygic.sdk.position.PositionManager
import com.sygic.sdk.position.PositionManagerProvider
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class PositionManagerKtx {
    suspend fun stopPositionUpdating(): Unit = suspendCoroutine { continuation ->
        PositionManagerProvider.getInstance().get().stopPositionUpdating({ continuation.resume(Unit) })
    }
}