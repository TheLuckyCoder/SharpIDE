package net.theluckycoder.sharpide.fragment

import android.os.Bundle
import android.support.v7.app.AppCompatDelegate
import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompat
import net.theluckycoder.sharpide.R

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferencesFix(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences)

        findPreference("dark_theme").setOnPreferenceClickListener {
            if (preferenceManager.sharedPreferences.getBoolean("dark_theme", false)) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }

            activity?.let {
                val intent = it.intent
                it.finish()
                startActivity(intent)
            }
            true
        }
    }
}
