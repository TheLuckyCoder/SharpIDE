package net.theluckycoder.sharpide.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.transition.TransitionManager
import android.support.v4.view.ViewCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.AppCompatEditText
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.webkit.*
import android.widget.LinearLayout
import android.widget.TextView
import net.theluckycoder.sharpide.R
import net.theluckycoder.sharpide.utils.bind


class ConsoleActivity : AppCompatActivity() {

    private var mWindowsIsVisible = false
    private val windowLayout: LinearLayout by bind(R.id.window)
    private val messageTv: TextView by bind(R.id.text_console_message)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_console)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        windowLayout.visibility = View.GONE

        // WebView Setup
        val webView: WebView = findViewById(R.id.web_view)
        with(webView) {
            clearCache(true)
            clearHistory()
            clearMatches()
            val url = "file://" + filesDir.absolutePath + "/index.html"
            with(settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
            }
            webViewClient = WebViewClient()
            webChromeClient = MyChromeClient()
            loadUrl("about:blank")
            loadUrl(url)
        }
    }

    fun expand(view: View) {
        val containerView: ViewGroup = findViewById(R.id.container)
        TransitionManager.beginDelayedTransition(containerView)

        val fab = view as FloatingActionButton
        val rotation = if (mWindowsIsVisible) {
            mWindowsIsVisible = false
            windowLayout.visibility = View.GONE
            0f
        } else {
            mWindowsIsVisible = true
            windowLayout.visibility = View.VISIBLE
            180f
        }

        ViewCompat.animate(fab)
                .rotation(rotation)
                .withLayer()
                .setDuration(400)
                .setInterpolator(OvershootInterpolator())
                .start()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private inner class MyChromeClient : WebChromeClient() {
        override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
            AlertDialog.Builder(this@ConsoleActivity)
                    .setTitle("JavaScript Alert")
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                    .setCancelable(false)
                    .show()
            return true
        }

        override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String?, result: JsPromptResult): Boolean {
            AlertDialog.Builder(this@ConsoleActivity)
                    .setTitle("JavaScript")
                    .setMessage(message)
                    .setView(AppCompatEditText(this@ConsoleActivity).apply { setText(defaultValue) })
                    .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
                    .setCancelable(false)
                    .show()
            return true
        }

        override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
            AlertDialog.Builder(this@ConsoleActivity)
                    .setTitle("JavaScript")
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                    .setNegativeButton(android.R.string.no) { _, _ -> result.cancel() }
                    .setCancelable(false)
                    .show()
            return true
        }

        override fun onJsBeforeUnload(view: WebView, url: String, message: String, result: JsResult): Boolean {
            AlertDialog.Builder(this@ConsoleActivity)
                    .setTitle("Confirm Navigation")
                    .setMessage((message + "\nAre your sure you want to leave this page?"))
                    .setPositiveButton("Leave page") { _, _ -> result.confirm() }
                    .setNegativeButton("Stay on this page") { _, _ -> result.cancel() }
                    .setCancelable(false)
                    .show()
            return true
        }

        @SuppressLint("SetTextI18n")
        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            messageTv.text = "Line " + consoleMessage.lineNumber() + ": " + consoleMessage.message()
            return true
        }
    }
}
