package net.theluckycoder.sharpide.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ShareCompat
import de.psdev.licensesdialog.LicensesDialog
import de.psdev.licensesdialog.licenses.ApacheSoftwareLicense20
import de.psdev.licensesdialog.model.Notice
import de.psdev.licensesdialog.model.Notices
import net.theluckycoder.sharpide.BuildConfig
import net.theluckycoder.sharpide.R
import net.theluckycoder.sharpide.databinding.ActivityAboutBinding
import net.theluckycoder.sharpide.utils.AppPreferences
import net.theluckycoder.sharpide.utils.Const
import net.theluckycoder.sharpide.utils.extensions.browse
import net.theluckycoder.sharpide.utils.extensions.email

class AboutActivity : AppCompatActivity(), View.OnClickListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        val binding = ActivityAboutBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        // Set Toolbar
        setSupportActionBar(binding.toolbarAbout)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Set Fullscreen
        if (AppPreferences(this).isFullscreen) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }

        val animation = AnimationUtils.loadAnimation(this, R.anim.anim_about_card_show)
        binding.svAbout.startAnimation(animation)

        binding.fabAboutShare.setOnClickListener(this)

        binding.cardAbout2.also {
            it.tvAboutShop.setOnClickListener(this)
            it.tvAboutEmail.setOnClickListener(this)
            it.tvAboutWebsite.setOnClickListener(this)
            it.tvAboutPrivacyPolicy.setOnClickListener(this)
            it.tvAboutOpenSource.setOnClickListener(this)
        }

        val alphaAnimation = AlphaAnimation(0.0f, 1.0f).apply {
            duration = 300
            startOffset = 600
        }

        binding.cardAbout1.tvAboutVersion.apply {
            text = String.format(getString(R.string.version), BuildConfig.VERSION_NAME)
            startAnimation(alphaAnimation)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            onBackPressed()
            true
        } else super.onOptionsItemSelected(item)
    }

    @SuppressLint("PrivateResource")
    override fun onClick(view: View) {
        when (view.id) {
            R.id.tv_about_shop -> browse(Const.MARKET_LINK)
            R.id.tv_about_email -> email("mail@theluckycoder.net", "About: SharpIDE")
            R.id.tv_about_website -> browse("http://theluckycoder.net/", true)
            R.id.tv_about_privacy_policy -> browse("http://theluckycoder.net/privacy-policy/sharpide.html", true)
            R.id.tv_about_open_source -> {
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
                    .show()
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
