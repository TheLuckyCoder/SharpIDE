package net.theluckycoder.sharpide

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.preference.Preference
import android.preference.PreferenceFragment

import de.psdev.licensesdialog.LicensesDialog
import de.psdev.licensesdialog.licenses.ApacheSoftwareLicense20
import de.psdev.licensesdialog.model.Notice
import de.psdev.licensesdialog.model.Notices

class SettingsFragment : PreferenceFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.preferences)

        findPreference("author").onPreferenceClickListener = Preference.OnPreferenceClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://theluckycoder.net")))
            true
        }

        findPreference("licenses").onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val notices = Notices()
            notices.addNotice(Notice("KeyboardVisibilityEvent", "http://yslibrary.net", "Copyright 2015-2017 Shimizu Yasuhiro (yshrsmz)", ApacheSoftwareLicense20()))

            LicensesDialog.Builder(activity)
                    .setNotices(notices)
                    .setIncludeOwnLicense(true)
                    .build()
                    .show()
            true
        }
    }
}
