package cz.feldis.sdkandroidtests

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import timber.log.Timber
import timber.log.Timber.DebugTree

class SygicActivity : AppCompatActivity() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.plant(DebugTree())
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContentView(R.layout.sygic_sdk_activity_map)
    }
}
