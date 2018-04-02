package net.theluckycoder.sharpide.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.transition.TransitionManager
import android.support.v4.content.ContextCompat
import android.support.v4.view.ViewCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.AppCompatEditText
import android.view.MenuItem
import android.view.View
import android.view.animation.OvershootInterpolator
import android.webkit.ConsoleMessage
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import net.theluckycoder.sharpide.R
import net.theluckycoder.sharpide.utils.Preferences
import net.theluckycoder.sharpide.utils.bind
import org.jetbrains.anko.alert
import org.jetbrains.anko.appcompat.v7.Appcompat

class ConsoleActivity : AppCompatActivity() {

    private val webView by bind<WebView>(R.id.web_view)
    private val windowLayout by bind<LinearLayout>(R.id.layout_window)
    private val messageTv by bind<TextView>(R.id.tv_console_message)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_console)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        windowLayout.visibility = View.GONE

        val fab = findViewById<FloatingActionButton>(R.id.fab_expand)
        fab.setOnClickListener {
            expand(fab)
        }
        if (Preferences(this).consoleOpenByDefault()) {
            expand(fab)
        }

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

    private fun expand(view: FloatingActionButton) {
        TransitionManager.beginDelayedTransition(findViewById(R.id.container))

        val rotation = if (windowLayout.visibility == View.VISIBLE) {
            windowLayout.visibility = View.GONE
            0f
        } else {
            windowLayout.visibility = View.VISIBLE
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
            alert(Appcompat, message, "JavaScript Alert") {
                isCancelable = false
                positiveButton(android.R.string.ok) { result.confirm() }
            }.show()
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
            alert(Appcompat, message, "JavaScript Prompt") {
                customView = editText
                isCancelable = false
                positiveButton(android.R.string.ok) { result.confirm(editText.text.toString()) }
                negativeButton(android.R.string.cancel) { result.cancel() }
            }.show()
            return true
        }

        override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
            alert(Appcompat, message, "JavaScript Confirm") {
                isCancelable = false
                positiveButton(android.R.string.ok) { result.confirm() }
                negativeButton(android.R.string.cancel) { result.cancel() }
            }.show()
            return true
        }

        override fun onJsBeforeUnload(view: WebView, url: String, message: String, result: JsResult): Boolean {
            alert(Appcompat, "$message\n\nAre you sure you want to navigate away from this page?",
                "Confirm Navigation") {
                isCancelable = false
                positiveButton("Leave this page") { result.confirm() }
                negativeButton("Stay on this page") { result.cancel() }
            }.show()
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
