package net.theluckycoder.sharpide

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

class ConsoleActivity : AppCompatActivity() {

    private var mWindowsIsVisible = false
    private lateinit var windowLayout: LinearLayout
    private lateinit var messageTv: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_console)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        messageTv = findViewById(R.id.consoleTextView)
        windowLayout = findViewById(R.id.window)
        windowLayout.visibility = View.GONE

        //WebView Setup
        val webView = findViewById<WebView>(R.id.webView)
        webView.clearCache(true)
        webView.clearHistory()
        webView.clearMatches()
        val url = "file://" + filesDir.absolutePath + "/index.html"
        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = MyChromeClient()
        webView.loadUrl("about:blank")
        webView.loadUrl(url)
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
            val dialog = AlertDialog.Builder(this@ConsoleActivity)
            dialog.setTitle("JavaScript Alert")
            dialog.setMessage(message)
            dialog.setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
            dialog.setCancelable(false)
            dialog.show()
            return true
        }

        override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String, result: JsPromptResult): Boolean {
            val editText = AppCompatEditText(this@ConsoleActivity)
            editText.setText(defaultValue)
            val dialog = AlertDialog.Builder(this@ConsoleActivity)
            dialog.setTitle("JavaScript")
            dialog.setMessage(message)
            dialog.setView(editText)
            dialog.setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
            dialog.setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
            dialog.setCancelable(false)
            dialog.show()
            return true
        }

        override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
            val dialog = AlertDialog.Builder(this@ConsoleActivity)
            dialog.setTitle("JavaScript")
            dialog.setMessage(message)
            dialog.setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
            dialog.setNegativeButton(android.R.string.no) { _, _ -> result.cancel() }
            dialog.setCancelable(false)
            dialog.show()
            return true
        }

        override fun onJsBeforeUnload(view: WebView, url: String, message: String, result: JsResult): Boolean {
            val dialog = AlertDialog.Builder(this@ConsoleActivity)
            dialog.setTitle("Confirm Navigation")
            dialog.setMessage((message + "\nAre your sure you want to leave this page?"))
            dialog.setPositiveButton("Leave page") { _, _ -> result.confirm() }
            dialog.setNegativeButton("Stay on this page") { _, _ -> result.cancel() }
            dialog.setCancelable(false)
            dialog.show()
            return true
        }

        @SuppressLint("SetTextI18n")
        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            messageTv.text = "Line " + consoleMessage.lineNumber() + ": " + consoleMessage.message()
            return true
        }
    }
}
