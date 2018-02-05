package net.theluckycoder.sharpide.activities

import android.annotation.SuppressLint
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
import org.jetbrains.anko.browse
import org.jetbrains.anko.email

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
            text = String.format(getString(R.string.version), BuildConfig.VERSION_NAME)
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

    @SuppressLint("PrivateResource")
    override fun onClick(view: View) {
        when (view.id) {
            R.id.card_about_2_shop -> browse(Const.MARKET_LINK)
            R.id.card_about_2_email -> email("mail@theluckycoder.net", "About: SharpIDE")
            R.id.card_about_2_website -> browse("http://theluckycoder.net/")
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
