package net.theluckycoder.sharpide.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.webkit.ConsoleMessage
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.transition.TransitionManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import net.theluckycoder.sharpide.R
import net.theluckycoder.sharpide.databinding.ActivityConsoleBinding
import net.theluckycoder.sharpide.utils.AppPreferences
import net.theluckycoder.sharpide.utils.extensions.alertDialog
import net.theluckycoder.sharpide.utils.extensions.setTitleWithColor

class ConsoleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConsoleBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConsoleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.layoutWindow.visibility = View.GONE

        val fab = findViewById<FloatingActionButton>(R.id.fab_expand)
        fab.setOnClickListener {
            expand(fab)
        }

        val preferences = AppPreferences(this)
        if (preferences.isConsoleOpenByDefault) {
            expand(fab)
        }

        // Set Fullscreen
        if (preferences.isFullscreen) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }

        // WebView Setup
        with(binding.webView) {
            loadUrl("about:blank")
            settings.javaScriptEnabled = true
            setBackgroundColor(ContextCompat.getColor(context, R.color.main_background))
            webViewClient = WebViewClient()
            webChromeClient = MyChromeClient()
            loadUrl("file://" + filesDir.absolutePath + "/index.html")
        }

        savedInstanceState?.let {
            binding.webView.restoreState(it.getBundle("web_viewState"))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val state = Bundle()
        binding.webView.saveState(state)
        outState.putBundle("web_viewState", state)

        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        with(binding.webView) {
            clearCache(true)
            clearHistory()
            clearMatches()
        }
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun expand(view: FloatingActionButton) {
        TransitionManager.beginDelayedTransition(findViewById(R.id.container))

        val layoutWindow = binding.layoutWindow
        val rotation = if (layoutWindow.visibility == View.VISIBLE) {
            layoutWindow.visibility = View.GONE
            0f
        } else {
            layoutWindow.visibility = View.VISIBLE
            180f
        }

        ViewCompat.animate(view)
            .rotation(rotation)
            .withLayer()
            .setDuration(400)
            .setInterpolator(OvershootInterpolator())
            .start()
    }

    private inner class MyChromeClient : WebChromeClient() {

        override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
            alertDialog(R.style.AppTheme_Dialog)
                .setTitleWithColor("JavaScript Alert", R.color.textColorPrimary)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(android.R.string.yes) { _, _ ->
                    result.confirm()
                }
                .show()
            return true
        }

        override fun onJsPrompt(
            view: WebView,
            url: String,
            message: String,
            defaultValue: String?,
            result: JsPromptResult
        ): Boolean {
            val editText = AppCompatEditText(this@ConsoleActivity).apply {
                setText(defaultValue)
            }
            alertDialog(R.style.AppTheme_Dialog)
                .setTitleWithColor("JavaScript Prompt", R.color.textColorPrimary)
                .setView(editText)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(android.R.string.yes) { _, _ ->
                    result.confirm(editText.text.toString())
                }
                .setNegativeButton(android.R.string.no) { _, _ ->
                    result.cancel()
                }
                .show()
            return true
        }

        override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
            alertDialog(R.style.AppTheme_Dialog)
                .setTitleWithColor("JavaScript Confirm", R.color.textColorPrimary)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(android.R.string.yes) { _, _ ->
                    result.confirm()
                }
                .setNegativeButton(android.R.string.no) { _, _ ->
                    result.cancel()
                }
                .show()
            return true
        }

        override fun onJsBeforeUnload(view: WebView, url: String, message: String, result: JsResult): Boolean {
            alertDialog(R.style.AppTheme_Dialog)
                .setTitleWithColor("Confirm Navigation", R.color.textColorPrimary)
                .setMessage("$message\n\nAre you sure you want to navigate away from this page?")
                .setCancelable(false)
                .setPositiveButton("Leave this page") { _, _ ->
                    result.confirm()
                }
                .setNegativeButton("Stay on this page") { _, _ ->
                    result.cancel()
                }
                .show()
            return true
        }

        @SuppressLint("SetTextI18n")
        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            var text = binding.tvConsoleMessage.text.toString()
            if (text == getString(R.string.no_errors)) {
                text = ""
            } else {
                text += "\n"
            }

            binding.tvConsoleMessage.text = text + "Line " + consoleMessage.lineNumber() + ": " + consoleMessage.message()
            return true
        }
    }
}
