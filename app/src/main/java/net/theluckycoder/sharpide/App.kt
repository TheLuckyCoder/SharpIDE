package net.theluckycoder.sharpide

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.gms.ads.MobileAds
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import net.theluckycoder.sharpide.utils.AppPreferences
import net.theluckycoder.sharpide.utils.UpdateChecker

@Suppress("unused")
class App : Application() {

    override fun onCreate() {
        super.onCreate()

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

        if (AppPreferences(this).useDarkTheme) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        MobileAds.initialize(this) {
        }


        // Setup Update Checker
        val firebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

        // Set in-app defaults
        val remoteConfigDefaults = HashMap<String, Any>().apply {
            put(UpdateChecker.KEY_CURRENT_VERSION, BuildConfig.VERSION_CODE)
        }

        firebaseRemoteConfig.setDefaultsAsync(remoteConfigDefaults)
        firebaseRemoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful)
                Log.d("Firebase SharpIDE", "Remote config is fetched.")
        }
    }
}
