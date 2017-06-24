package net.theluckycoder.scriptcraft;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.transition.TransitionManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.widget.AppCompatEditText;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.theluckycoder.scriptcraft.utils.Util;

public final class ConsoleActivity extends AppCompatActivity {

    static { AppCompatDelegate.setCompatVectorFromResourcesEnabled(true); }

    private boolean windowVisible = false;
    private LinearLayout windowLayout;
    private TextView messageTv;
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_console);

        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        try {
            Util.saveFileInternally(this, "index.html", "<!DOCTYPE html>\n<html>\n<head>\n<script type=\"text/javascript\" src=\"main.js\"></script>\n</head>\n<body>\n</body>\n</html>");
        } catch (Exception e) {
            e.printStackTrace();
        }

        messageTv = findViewById(R.id.consoleTextView);
        windowLayout = findViewById(R.id.window);
        windowLayout.setVisibility(View.GONE);
        webView = findViewById(R.id.webView);
        webView.loadUrl("about:blank");
        final String url = "file://" + getFilesDir().getAbsolutePath() + "/index.html";
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new MyChromeClient());
        webView.loadUrl(url);
    }

    public void expand(View view) {
        ViewGroup containerView = findViewById(R.id.container);
        TransitionManager.beginDelayedTransition(containerView);

        FloatingActionButton fab = (FloatingActionButton) view;
        if (windowVisible) {
            windowVisible = false;
            windowLayout.setVisibility(View.GONE);
            fab.setImageResource(R.drawable.ic_expand);
        } else {
            windowVisible = true;
            windowLayout.setVisibility(View.VISIBLE);
            fab.setImageResource(R.drawable.ic_expand_hide);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return super.onOptionsItemSelected(item);
    }

    private final class MyChromeClient extends WebChromeClient {
        @Override
        public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
            AlertDialog.Builder dialog = new AlertDialog.Builder(ConsoleActivity.this);
            dialog.setTitle("JavaScript Alert");
            dialog.setMessage(message);
            dialog.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    result.confirm();
                }
            });
            dialog.setCancelable(false);
            dialog.show();
            return true;
        }

        @Override
        public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, final JsPromptResult result) {
            final AppCompatEditText editText = new AppCompatEditText(ConsoleActivity.this);
            editText.setText(defaultValue);
            AlertDialog.Builder dialog = new AlertDialog.Builder(ConsoleActivity.this);
            dialog.setTitle("JavaScript");
            dialog.setMessage(message);
            dialog.setView(editText);
            dialog.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    result.confirm();
                }
            });
            dialog.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    result.cancel();
                }
            });
            dialog.setCancelable(false);
            dialog.show();
            return true;
        }

        @Override
        public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
            AlertDialog.Builder dialog = new AlertDialog.Builder(ConsoleActivity.this);
            dialog.setTitle("JavaScript");
            dialog.setMessage(message);
            dialog.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    result.confirm();
                }
            });
            dialog.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    result.cancel();
                }
            });
            dialog.setCancelable(false);
            dialog.show();
            return true;
        }

        @Override
        public boolean onJsBeforeUnload(WebView view, String url, String message, final JsResult result) {
            AlertDialog.Builder dialog = new AlertDialog.Builder(ConsoleActivity.this);
            dialog.setTitle("Confirm Navigation");
            dialog.setMessage(String.valueOf(message + "\nAre your sure you want to leave this page?"));
            dialog.setPositiveButton("Leave page", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    result.confirm();
                }
            });
            dialog.setNegativeButton("Stay on this page", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    result.cancel();
                }
            });
            dialog.setCancelable(false);
            dialog.show();
            return true;
        }

        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            messageTv.setText("Line " + consoleMessage.lineNumber() + ": " + consoleMessage.message());
            return true;
        }
    }
}
