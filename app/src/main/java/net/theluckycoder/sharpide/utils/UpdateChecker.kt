package net.theluckycoder.sharpide.utils

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import net.theluckycoder.sharpide.BuildConfig

class UpdateChecker(onUpdateNeeded: () -> Unit) {

    companion object {
        const val KEY_CURRENT_VERSION = "current_version"
    }

    init {
        val remoteConfig = FirebaseRemoteConfig.getInstance()

        val currentVersion = remoteConfig.getLong(KEY_CURRENT_VERSION).toInt()

        if (currentVersion > BuildConfig.VERSION_CODE) onUpdateNeeded()
    }
}
