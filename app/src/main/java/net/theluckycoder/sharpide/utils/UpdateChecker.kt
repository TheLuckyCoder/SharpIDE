package net.theluckycoder.sharpide.utils

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import net.theluckycoder.sharpide.BuildConfig


class UpdateChecker(private val onUpdateNeededListener: OnUpdateNeededListener) {

    companion object {
        const val KEY_UPDATE_REQUIRED = "force_update_required"
        const val KEY_CURRENT_VERSION = "force_update_current_version"
    }

    interface OnUpdateNeededListener {
        fun onUpdateNeeded()
    }

    fun check() {
        val remoteConfig = FirebaseRemoteConfig.getInstance()

        if (remoteConfig.getBoolean(KEY_UPDATE_REQUIRED)) {
            val currentVersion = remoteConfig.getLong(KEY_CURRENT_VERSION).toInt()

            if (currentVersion > BuildConfig.VERSION_CODE) onUpdateNeededListener.onUpdateNeeded()
        }
    }
}

