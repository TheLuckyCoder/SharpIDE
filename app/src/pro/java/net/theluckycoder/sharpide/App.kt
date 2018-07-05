package net.theluckycoder.sharpide

import android.app.Application
import android.support.v7.app.AppCompatDelegate
import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import net.theluckycoder.sharpide.utils.UpdateChecker

@Suppress("unused")
class App : Application() {

    override fun onCreate() {
        super.onCreate()

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

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
