package net.theluckycoder.scriptcraft;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.Spannable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;

import net.theluckycoder.scriptcraft.component.CodeEditText;
import net.theluckycoder.scriptcraft.component.InteractiveScrollView;
import net.theluckycoder.scriptcraft.listener.FileChangeListener;
import net.theluckycoder.scriptcraft.listener.OnBottomReachedListener;
import net.theluckycoder.scriptcraft.listener.OnScrollListener;
import net.theluckycoder.scriptcraft.utils.CustomTabWidthSpan;
import net.theluckycoder.scriptcraft.utils.FileChooser;
import net.theluckycoder.scriptcraft.utils.Util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, TextWatcher {

    private InterstitialAd mInterstitialAd;
    private CodeEditText contentView;
    private InteractiveScrollView scrollView;
    private Toolbar toolbar;

    private File file;
    private FileChangeListener fileChangeListener;

    private final int CHUNK = 20000;
    private String FILE_CONTENT;
    private String currentBuffer;
    private StringBuilder loaded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme_NoActionBar);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setSubtitle(R.string.no_file_open);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar, 0, 0);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        mInterstitialAd = new InterstitialAd(this);
        mInterstitialAd.setAdUnitId("ca-app-pub-1279472163660969/4393673534");

        mInterstitialAd.setAdListener(new AdListener() {
            @Override
            public void onAdClosed() {
                requestNewInterstitial();
            }
        });
        requestNewInterstitial();

        contentView = (CodeEditText) findViewById(R.id.fileContent);

        LinearLayout symbolLayout = (LinearLayout) findViewById(R.id.symbolLayout);
        View.OnClickListener symbolClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                contentView.getText().insert(contentView.getSelectionStart(), ((TextView) view).getText().toString());
            }
        };
        for (int i = 0; i < symbolLayout.getChildCount(); i++)
            symbolLayout.getChildAt(i).setOnClickListener(symbolClickListener);

        scrollView = (InteractiveScrollView) findViewById(R.id.scrollView);
        scrollView.setOnBottomReachedListener(null);
        scrollView.setOnScrollListener((OnScrollListener) fileChangeListener);

        //Load preferences
        contentView.setTextSize(Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(this).getString("font_size", "16")));

        final HorizontalScrollView symbolScrollView = (HorizontalScrollView) findViewById(R.id.symbolScrollView);
        contentView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (hasFocus && PreferenceManager.getDefaultSharedPreferences(MainActivity.this).getBoolean("show_symbols_bar", true))
                    symbolScrollView.setVisibility(View.VISIBLE);
            }
        });

        Util.verifyStoragePermissions(this);
        new File(Util.mainFolder).mkdir();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START))
            drawer.closeDrawer(GravityCompat.START);
        else {
            if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("quit_confirm", true)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.app_name);
                builder.setMessage(R.string.exit_confirmation);
                builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        finish();
                    }
                }).setNegativeButton(android.R.string.no, null);
                builder.show();
            } else
                finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_save) {
            saveFile();
        } else if (id == R.id.action_open) {
            final Context context = this;
            new File(Util.mainFolder).mkdir();
            FileChooser filechooser = new FileChooser(this);
            filechooser.setFileListener(new FileChooser.FileSelectedListener() {
                @Override public void fileSelected(final File newFile) {
                    if (newFile.length() >= 20971520) { //If file is bigger than 20 MB
                        Toast.makeText(context, "File is too big", Toast.LENGTH_LONG).show();
                        return;
                    }
                    file = newFile;
                    new DocumentLoader().execute();
                }
            });
        } else if (id == R.id.action_new) {
            final Context context = this;

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.create_new_file);

            View root = getLayoutInflater().inflate(R.layout.dialog_new_file, null);
            builder.setView(root);

            final EditText etFileName = (EditText) root.findViewById(R.id.file_name);

            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {

                    if (!Pattern.compile("[_a-zA-Z0-9 \\-\\.]+").matcher(etFileName.getText().toString()).matches()) {
                        Toast.makeText(context, R.string.invalid_file_name, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    File newFile = new File(Util.mainFolder + etFileName.getText().toString());
                    if (!newFile.exists()) {
                        Util.createFile(newFile);
                        Toast.makeText(context, R.string.new_file_created, Toast.LENGTH_SHORT).show();
                    }
                    file = newFile;
                    new DocumentLoader().execute();
                }
            });
            builder.show();
        } else if (id == R.id.action_file_info) {
            if (file == null) {
                Toast.makeText(this, R.string.no_file_open, Toast.LENGTH_SHORT).show();
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.file_info);
                builder.setMessage(getFileInfo());
                builder.setNeutralButton(R.string.action_close, null);
                builder.show();
            }
        } else if (id == R.id.action_import) {
            Intent importMod = new Intent(Intent.ACTION_VIEW);
            importMod.setDataAndType(Uri.fromFile(file), "application/js"); // Open the created file with its default application

            PackageManager pm = getPackageManager();
            List<ResolveInfo> apps = pm.queryIntentActivities(importMod, PackageManager.MATCH_DEFAULT_ONLY);

            if (apps.size() > 0) {
                try {
                    startActivity(importMod);
                } catch (Exception e) {
                    Log.e("Exception", e.getMessage(), e);
                    Toast.makeText(this, "Something went wrong", Toast.LENGTH_SHORT).show();
                }
            } else
                Toast.makeText(this, "There are no apps available", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.action_replace_all) {
            LayoutInflater inflater = this.getLayoutInflater();
            final View dialogView = inflater.inflate(R.layout.dialog_replace, null);
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.replace_all);
            builder.setView(dialogView);
            builder.setPositiveButton(R.string.replace_all, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    EditText findText = (EditText) dialogView.findViewById(R.id.findText);
                    EditText replaceText = (EditText) dialogView.findViewById(R.id.replaceText);
                    String newText = contentView.getText().toString().replace(findText.getText().toString(), replaceText.getText().toString());
                    contentView.setText(newText);
                    if (mInterstitialAd.isLoaded()) mInterstitialAd.show();
                }
            });
            builder.setNegativeButton(android.R.string.cancel, null);
            builder.show();
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_functions) {
            AlertDialog.Builder dialog = new AlertDialog.Builder(this);
            dialog.setTitle(R.string.modpe_functions);
            dialog.setNegativeButton(R.string.action_close, null);

            WebView mWebView = new WebView(this);
            mWebView.loadUrl("file:///android_asset/dump.txt");

            dialog.setView(mWebView);
            dialog.show();
        } else if (id == R.id.nav_textures) {
            AlertDialog.Builder dialog = new AlertDialog.Builder(this);
            dialog.setTitle(R.string.texture_names);

            WebView mWebView = new WebView(this);
            mWebView.loadUrl("http://zhuoweizhang.net/mcpetexturenames/");
            mWebView.getSettings().setJavaScriptEnabled(true);
            mWebView.getSettings().setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
            mWebView.getSettings().setAppCacheEnabled(true);

            dialog.setView(mWebView);
            dialog.setNegativeButton(R.string.action_close, null);
            dialog.show();
        } else if (id == R.id.nav_obfuscation) {
            startActivity(new Intent(MainActivity.this, ObfuscationActivity.class));
        } else if (id == R.id.nav_settings) {
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private String getFileSize() {
        String modifiedFileSize;
        double fileSize;
        if (file == null)
            return null;
        if (file.isFile()) {
            fileSize = (double) file.length(); //in Bytes

            if (fileSize < 1024) {
                modifiedFileSize = String.valueOf(fileSize).concat("B");
            } else if (fileSize > 1024 && fileSize < (1024 * 1024)) {
                modifiedFileSize = String.valueOf(Math.round((fileSize / 1024 * 100.0)) / 100.0).concat("KB");
            } else {
                modifiedFileSize = String.valueOf(Math.round((fileSize / (1024 * 1204) * 100.0)) / 100.0).concat("MB");
            }
        } else {
            modifiedFileSize = "Unknown";
        }

        return modifiedFileSize;
    }

    private String getFileInfo() {
        if (getFileSize() == null)
            return null;
        else
            return "Size : " + getFileSize() + "\n" + "Path : " + file.getPath() + "\n";
    }

    private boolean isChanged() {
        if (FILE_CONTENT == null) {
            return false;
        }

        if (FILE_CONTENT.length() >= CHUNK && FILE_CONTENT.substring(0, loaded.length()).equals(currentBuffer))
            return false;
        else if (FILE_CONTENT.equals(currentBuffer))
            return false;

        return true;
    }

    private void loadInChunks(InteractiveScrollView scrollView, final String bigString) {
        loaded.append(bigString.substring(0, CHUNK));
        contentView.setTextHighlighted(loaded);
        scrollView.setOnBottomReachedListener(new OnBottomReachedListener() {
            @Override
            public void onBottomReached() {
                if (loaded.length() >= bigString.length())
                    return;
                else if (loaded.length() + CHUNK > bigString.length()) {
                    String buffer = bigString.substring(loaded.length(), bigString.length());
                    loaded.append(buffer);
                } else {
                    String buffer = bigString.substring(loaded.length(), loaded.length() + CHUNK);
                    loaded.append(buffer);
                }

                contentView.setTextHighlighted(loaded);
            }
        });
    }

    private void loadDocument(final String fileContent) {
        scrollView.smoothScrollTo(0, 0);

        contentView.setFocusable(false);
        contentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                contentView.setFocusableInTouchMode(true);
            }
        });

        loaded = new StringBuilder();
        if (fileContent.length() > CHUNK)
            loadInChunks(scrollView, fileContent);
        else {
            loaded.append(fileContent);
            contentView.setTextHighlighted(loaded);
        }


        contentView.addTextChangedListener(this);
        currentBuffer = contentView.getText().toString();

        if (isFileChangeListenerAttached()) fileChangeListener.onFileOpen();

        toolbar.setSubtitle(file.getName());

        if (mInterstitialAd.isLoaded()) mInterstitialAd.show();
    }

    private void saveFile() {
        if (isChanged())
            new DocumentSaver().execute();
        else
            Toast.makeText(getApplicationContext(), "No change in file", Toast.LENGTH_SHORT).show();
    }

    private void onPostSave() {
        Toast.makeText(getApplicationContext(), R.string.file_saved, Toast.LENGTH_SHORT).show();

        if (isFileChangeListenerAttached()) fileChangeListener.onFileSave();

        if (mInterstitialAd.isLoaded()) mInterstitialAd.show();
    }

    private void applyTabWidth(Editable text, int start, int end) {
        //TODO: Better TABS
        final String INDEX_CHAR="m";
        final int TAB_NUMBER = 10;

        String str = text.toString();
        float tabWidth = CodeEditText.paint.measureText(INDEX_CHAR) * TAB_NUMBER;
        while (start < end)     {
            int index = str.indexOf("\t", start);
            if(index < 0)
                break;
            text.setSpan(new CustomTabWidthSpan(Float.valueOf(tabWidth).intValue()), index, index + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            start = index + 1;
        }
    }

    private boolean isFileChangeListenerAttached() {
        return fileChangeListener != null;
    }

    private int start = 0,end = 0;
    @Override
    public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence charSequence, int start, int count, int after) {
        this.start = start;
        this.end = start + count;
    }

    @Override
    public void afterTextChanged(Editable editable) {
        applyTabWidth(editable, start, end);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                currentBuffer = contentView.getText().toString();

                if (isFileChangeListenerAttached()) fileChangeListener.onFileChanged(isChanged());
            }
        }, 1000);
    }

    private class DocumentLoader extends AsyncTask<Void, Void, String> {

        ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog = new ProgressDialog(MainActivity.this);
            progressDialog.setTitle(R.string.loading_file);
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        @Override
        protected String doInBackground(Void... paths) {

            try {
                BufferedReader br = new BufferedReader(new FileReader(file.getAbsoluteFile()));
                try {
                    StringBuilder sb = new StringBuilder();
                    String line = br.readLine();

                    while (line != null) {
                        sb.append(line);
                        sb.append("\n");
                        line = br.readLine();
                    }
                    FILE_CONTENT = sb.toString();
                    return FILE_CONTENT;
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                } finally {
                    try {
                        br.close();
                    } catch (IOException e) {
                        Log.e("FileNotFoundException", e.getMessage(), e);
                    }
                }
            } catch (FileNotFoundException e) {
                Log.e("FileNotFoundException", e.getMessage(), e);
            }

            return "\n";
        }

        @Override
        protected void onPostExecute(String s) {
            loadDocument(s);
            progressDialog.dismiss();
        }
    }

    private class DocumentSaver extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            BufferedWriter output = null;
            String toSave = currentBuffer;
            try {
                output = new BufferedWriter(new FileWriter(file));
                if (FILE_CONTENT.length() > CHUNK)
                    toSave = currentBuffer + FILE_CONTENT.substring(loaded.length(), FILE_CONTENT.length());
                output.write(toSave);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (output != null) {
                    try {
                        output.close();
                        FILE_CONTENT = toSave;
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            onPostSave();
        }
    }

    private void requestNewInterstitial() {
        AdRequest adRequest = new AdRequest.Builder().build();

        mInterstitialAd.loadAd(adRequest);
    }
}
