package net.theluckycoder.sharpide.activities

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import android.view.WindowManager
import net.theluckycoder.sharpide.fragment.SettingsFragment
import net.theluckycoder.sharpide.utils.Preferences

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Set Fullscreen
        if (Preferences(this).isFullscreen()) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }

        supportFragmentManager.beginTransaction().replace(android.R.id.content, SettingsFragment()).commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            onBackPressed()
            true
        } else super.onOptionsItemSelected(item)
    }
}
