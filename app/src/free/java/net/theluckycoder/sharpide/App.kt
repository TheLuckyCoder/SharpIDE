package net.theluckycoder.sharpide

import android.app.Application
import android.support.v7.app.AppCompatDelegate
import android.util.Log
import com.crashlytics.android.Crashlytics
import com.crashlytics.android.core.CrashlyticsCore
import com.google.android.gms.ads.MobileAds
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import io.fabric.sdk.android.Fabric
import net.theluckycoder.sharpide.utils.UpdateChecker

@Suppress("unused")
class App : Application() {

    override fun onCreate() {
        super.onCreate()

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

        // Set up Crashlytics, disabled for debug builds
        val crashlyticsKit = Crashlytics.Builder()
            .core(CrashlyticsCore.Builder().disabled(BuildConfig.DEBUG).build())
            .build()

        MobileAds.initialize(this, "ca-app-pub-1279472163660969~2916940339")

        Fabric.with(this, crashlyticsKit)

        // Setup Update Checker
        val firebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

        // Set in-app defaults
        val remoteConfigDefaults = HashMap<String, Any>().apply {
            put(UpdateChecker.KEY_CURRENT_VERSION, BuildConfig.VERSION_CODE)
        }

        firebaseRemoteConfig.setDefaults(remoteConfigDefaults)
        firebaseRemoteConfig.fetch().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d("Firebase SharpIDE", "Remote config is fetched.")
                firebaseRemoteConfig.activateFetched()
            }
        }
    }
}
