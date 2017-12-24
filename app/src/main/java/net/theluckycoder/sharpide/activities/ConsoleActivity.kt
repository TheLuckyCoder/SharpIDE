package net.theluckycoder.sharpide.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.transition.TransitionManager
import android.support.v4.content.ContextCompat
import android.support.v4.view.ViewCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.AppCompatEditText
import android.view.MenuItem
import android.view.View
import android.view.animation.OvershootInterpolator
import android.webkit.*
import android.widget.LinearLayout
import android.widget.TextView
import net.theluckycoder.sharpide.R
import net.theluckycoder.sharpide.utils.bind


class ConsoleActivity : AppCompatActivity() {

    private val webView: WebView by bind(R.id.web_view)
    private val windowLayout: LinearLayout by bind(R.id.window)
    private val messageTv: TextView by bind(R.id.text_console_message)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_console)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        windowLayout.visibility = View.GONE

        // WebView Setup
        with(webView) {
            loadUrl("about:blank")
            val url = "file://" + filesDir.absolutePath + "/index.html"
            settings.javaScriptEnabled = true
            setBackgroundColor(ContextCompat.getColor(this@ConsoleActivity, R.color.main_background))
            webViewClient = WebViewClient()
            webChromeClient = MyChromeClient()
            loadUrl(url)
        }

        savedInstanceState?.let {
            webView.restoreState(it.getBundle("webViewState"))
        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        val state = Bundle()
        webView.saveState(state)
        outState?.putBundle("webViewState", state)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        with(webView) {
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

    fun expand(view: View) {
        TransitionManager.beginDelayedTransition(findViewById(R.id.container))

        val rotation = if (windowLayout.visibility == View.VISIBLE) {
            windowLayout.visibility = View.GONE
            0f
        } else {
            windowLayout.visibility = View.VISIBLE
            180f
        }

        ViewCompat.animate(view as FloatingActionButton)
                .rotation(rotation)
                .withLayer()
                .setDuration(400)
                .setInterpolator(OvershootInterpolator())
                .start()
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
                    .setTitle("JavaScript Prompt")
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
                    .setTitle("JavaScript Confirm")
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
                    .setMessage(message + "\nAre your sure you want to leave this page?")
                    .setPositiveButton("Leave page") { _, _ -> result.confirm() }
                    .setNegativeButton("Stay on this page") { _, _ -> result.cancel() }
                    .setCancelable(false)
                    .show()
            return true
        }

        @SuppressLint("SetTextI18n")
        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            var text = messageTv.text.toString()
            if (text == getString(R.string.no_errors)) {
                text = ""
            } else {
                text += "\n"
            }

            messageTv.text = text + "Line " + consoleMessage.lineNumber() + ": " + consoleMessage.message()
            return true
        }
    }
}
