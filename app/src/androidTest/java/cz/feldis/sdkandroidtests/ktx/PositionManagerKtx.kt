package cz.feldis.sdkandroidtests.ktx

import com.sygic.sdk.position.PositionManagerProvider
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class PositionManagerKtx {
    suspend fun stopPositionUpdating(): Unit = suspendCoroutine { continuation ->
        runBlocking { PositionManagerProvider.getInstance() } .stopPositionUpdating({ continuation.resume(Unit) })
    }
}