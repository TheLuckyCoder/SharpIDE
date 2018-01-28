package net.theluckycoder.sharpide.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.ShareCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.MenuItem
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationUtils
import android.widget.TextView
import de.psdev.licensesdialog.LicensesDialog
import de.psdev.licensesdialog.licenses.ApacheSoftwareLicense20
import de.psdev.licensesdialog.model.Notice
import de.psdev.licensesdialog.model.Notices
import net.theluckycoder.sharpide.BuildConfig
import net.theluckycoder.sharpide.R
import net.theluckycoder.sharpide.utils.Const

class AboutActivity : AppCompatActivity(), View.OnClickListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        // Set Toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar_about)
        setSupportActionBar(toolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val animation = AnimationUtils.loadAnimation(this, R.anim.anim_about_card_show)
        findViewById<View>(R.id.scroll_about).startAnimation(animation)

        findViewById<View>(R.id.card_about_2_shop).setOnClickListener(this)
        findViewById<View>(R.id.card_about_2_email).setOnClickListener(this)
        findViewById<View>(R.id.card_about_2_website).setOnClickListener(this)
        findViewById<View>(R.id.card_about_2_open_source).setOnClickListener(this)
        findViewById<View>(R.id.fab_about_share).setOnClickListener(this)

        val alphaAnimation = AlphaAnimation(0.0f, 1.0f).apply {
            duration = 300
            startOffset = 600
        }

        findViewById<TextView>(R.id.tv_about_version).apply {
            append(" ${BuildConfig.VERSION_NAME}")
            startAnimation(alphaAnimation)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onClick(view: View) {
        val intent = Intent()

        when (view.id) {
            R.id.card_about_2_shop -> {
                intent.apply {
                    data = Uri.parse(Const.MARKET_LINK)
                    action = Intent.ACTION_VIEW
                }.run {
                    if (intent.resolveActivity(packageManager) != null) {
                        startActivity(this)
                    }
                }
            }
            R.id.card_about_2_email -> {
                intent.apply {
                    action = Intent.ACTION_SENDTO
                    data = Uri.parse("mailto:mail@theluckycoder.net")
                    putExtra(Intent.EXTRA_SUBJECT, "About: SharpIDE")
                }.run {
                    if (intent.resolveActivity(packageManager) != null) {
                        startActivity(this)
                    }
                }
            }
            R.id.card_about_2_website -> {
                intent.apply {
                    data = Uri.parse("http://theluckycoder.net/")
                    action = Intent.ACTION_VIEW
                }.run {
                    if (intent.resolveActivity(packageManager) != null) {
                        startActivity(this)
                    }
                }
            }
            R.id.card_about_2_open_source -> {
                val notices = Notices().apply {
                    addNotice(Notice("Android Support Libraries",
                        "https://developer.android.com/topic/libraries/support-library/index.html",
                        "Copyright 2005-2011 The Android Open Source Project", ApacheSoftwareLicense20()))
                    addNotice(Notice("KeyboardVisibilityEvent", "http://yslibrary.net",
                        "Copyright 2015-2017 Shimizu Yasuhiro (yshrsmz)", ApacheSoftwareLicense20()))
                }

                LicensesDialog.Builder(this)
                    .setNotices(notices)
                    .setIncludeOwnLicense(true)
                    .build()
                    .showAppCompat()
            }
            R.id.fab_about_share -> {
                ShareCompat.IntentBuilder.from(this)
                    .setChooserTitle(R.string.abc_shareactionprovider_share_with)
                    .setType("text/plain")
                    .setText(Const.MARKET_LINK)
                    .startChooser()
            }
        }
    }
}
