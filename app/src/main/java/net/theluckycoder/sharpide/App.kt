package net.theluckycoder.sharpide

import android.app.Application
import android.support.v7.app.AppCompatDelegate
import android.util.Log
import com.crashlytics.android.Crashlytics
import com.crashlytics.android.core.CrashlyticsCore
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import io.fabric.sdk.android.Fabric
import net.theluckycoder.sharpide.utils.UpdateChecker


class App : Application() {

    override fun onCreate() {
        super.onCreate()

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

        // Set up Crashlytics, disabled for debug builds
        val crashlyticsKit = Crashlytics.Builder()
                .core(CrashlyticsCore.Builder().disabled(BuildConfig.DEBUG).build())
                .build()

        Fabric.with(this, crashlyticsKit)

        // Setup Update Checker
        val firebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

        // Set in-app defaults
        val remoteConfigDefaults = HashMap<String, Any>().apply {
            put(UpdateChecker.KEY_UPDATE_REQUIRED, false)
            put(UpdateChecker.KEY_CURRENT_VERSION, BuildConfig.VERSION_CODE)
        }

        firebaseRemoteConfig.setDefaults(remoteConfigDefaults)
        firebaseRemoteConfig.fetch(60).addOnCompleteListener { task ->  // Fetch every 60 minutes
            if (task.isSuccessful) {
                Log.d("Firebase SharpIDE", "Remote config is fetched.")
                firebaseRemoteConfig.activateFetched()
            }
        }
    }

}
