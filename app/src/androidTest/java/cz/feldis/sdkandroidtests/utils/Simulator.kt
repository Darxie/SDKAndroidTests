package cz.feldis.sdkandroidtests.utils

import com.sygic.sdk.route.simulator.NmeaLogSimulator
import com.sygic.sdk.route.simulator.PositionSimulator
import com.sygic.sdk.route.simulator.RouteDemonstrateSimulator

interface Simulator {
    fun start(callback: () -> Unit)
    fun stop(callback: () -> Unit)
    fun setSpeedMultiplier(speedMultiplier: Float, callback: () -> Unit)
}

class NmeaLogSimulatorAdapter(private val simulator: NmeaLogSimulator) : Simulator {
    override fun start(callback: () -> Unit) {
        simulator.start { callback() }
    }

    override fun stop(callback: () -> Unit) {
        simulator.stop { callback() }
    }

    override fun setSpeedMultiplier(speedMultiplier: Float, callback: () -> Unit) {
        simulator.setSpeedMultiplier(speedMultiplier) {
            callback()
        }
    }
}

class RouteDemonstrateSimulatorAdapter(private val simulator: RouteDemonstrateSimulator) : Simulator {
    override fun start(callback: () -> Unit) {
        simulator.start { callback() }
    }

    override fun stop(callback: () -> Unit) {
        simulator.stop { callback() }
    }

    override fun setSpeedMultiplier(speedMultiplier: Float, callback: () -> Unit) {
        simulator.setSpeedMultiplier(speedMultiplier) {
            callback()
        }
    }
}