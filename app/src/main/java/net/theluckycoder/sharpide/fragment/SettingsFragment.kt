package net.theluckycoder.sharpide.fragment

import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import net.theluckycoder.sharpide.R

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        findPreference<SwitchPreference>("dark_theme")?.setOnPreferenceClickListener {
            if (preferenceManager.sharedPreferences.getBoolean("dark_theme", false)) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }

            true
        }

        findPreference<SwitchPreference>("fullscreen")?.setOnPreferenceClickListener {
            tryToRestartActivity()
            true
        }
    }

    private fun tryToRestartActivity() {
        activity?.let {
            val intent = it.intent
            it.finish()
            startActivity(intent)
        }
    }
}
