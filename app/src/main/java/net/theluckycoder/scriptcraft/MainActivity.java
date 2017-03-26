package net.theluckycoder.scriptcraft;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.Spannable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;
import com.nbsp.materialfilepicker.MaterialFilePicker;
import com.nbsp.materialfilepicker.ui.FilePickerActivity;

import net.theluckycoder.scriptcraft.component.CodeEditText;
import net.theluckycoder.scriptcraft.component.InteractiveScrollView;
import net.theluckycoder.scriptcraft.listener.FileChangeListener;
import net.theluckycoder.scriptcraft.listener.OnBottomReachedListener;
import net.theluckycoder.scriptcraft.listener.OnScrollListener;
import net.theluckycoder.scriptcraft.utils.CustomTabWidthSpan;
import net.theluckycoder.scriptcraft.utils.Util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.regex.Pattern;

import static android.preference.PreferenceManager.getDefaultSharedPreferences;

public final class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, TextWatcher {

    static { AppCompatDelegate.setCompatVectorFromResourcesEnabled(true); }

    private CodeEditText contentView;
    private InteractiveScrollView scrollView;
    private LinearLayout startLayout;
    private Toolbar toolbar;
    private HorizontalScrollView symbolScrollView;

    private InterstitialAd mInterstitialAd;
    private File currentFile;
    @SuppressWarnings("unused")
    private FileChangeListener fileChangeListener;
    private final int CHUNK = 20000;
    private String FILE_CONTENT, currentBuffer;
    private StringBuilder loaded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Uri data = getIntent().getData();
        if (data != null) {
            try {
                currentFile = new File(data.getPath());
                new DocumentLoader().execute();
            } catch (Exception e) {
                Log.e("Bad Data", e.getMessage(), e);
            }
        }

        // Setup Views
        contentView = (CodeEditText) findViewById(R.id.fileContent);
        startLayout = (LinearLayout) findViewById(R.id.startLayout);

        final LinearLayout symbolLayout = (LinearLayout) findViewById(R.id.symbolLayout);
        View.OnClickListener symbolClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                contentView.getText().insert(contentView.getSelectionStart(), ((TextView) view).getText().toString());
            }
        };
        for (int i = 0; i < symbolLayout.getChildCount(); i++)
            symbolLayout.getChildAt(i).setOnClickListener(symbolClickListener);

        scrollView = (InteractiveScrollView) findViewById(R.id.mainScrollView);
        scrollView.setOnBottomReachedListener(null);
        scrollView.setOnScrollListener((OnScrollListener) fileChangeListener);
        symbolScrollView = (HorizontalScrollView) findViewById(R.id.symbolScrollView);

        //Set up navigation drawer
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar, 0, 0);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        // Load preferences
        contentView.setTextSize(Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(this).getString("font_size", "16")));
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("load_last_file", true)) {
            String lastFilePath = getDefaultSharedPreferences(this).getString("last_file_path", "");
            File lastFile = new File(lastFilePath);
            if (!lastFilePath.equals("") && lastFile.isFile()) {
                currentFile = lastFile;
                new DocumentLoader().execute();
            }
        }

        Util.verifyStoragePermissions(this);
        Util.makeFolder(Util.mainFolder);

        // Setup keyboard checker
        /*contentView.setBackPressedListener(new CodeEditText.BackPressedListener() {
            @Override
            public void onImeBack() {
                symbolLayout.setVisibility(View.GONE);
            }
        });*/

        // Setup ads
        mInterstitialAd = new InterstitialAd(this);
        mInterstitialAd.setAdUnitId("ca-app-pub-1279472163660969/4393673534");
        mInterstitialAd.setAdListener(new AdListener() {
            @Override
            public void onAdClosed() {
                requestNewInterstitial();
            }
        });
        requestNewInterstitial();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            if (getDefaultSharedPreferences(this).getBoolean("quit_confirm", true)) {
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
        }, 500);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_save) {
            if (currentFile == null)
                Toast.makeText(this, R.string.no_file_open, Toast.LENGTH_SHORT).show();
            else
                saveFile();
        } else if (id == R.id.menu_open) {
            openFileClick(item.getActionView());
        } else if (id == R.id.menu_new) {
            newFile();
        } else if (id == R.id.menu_file_info) {
            if (currentFile == null) {
                Toast.makeText(this, R.string.no_file_open, Toast.LENGTH_SHORT).show();
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.file_info);
                builder.setMessage(getFileInfo());
                builder.setNeutralButton(R.string.action_close, null);
                builder.show();
            }
        } else if (id == R.id.menu_run) {
            if (currentFile == null) {
                Toast.makeText(this, R.string.no_file_open, Toast.LENGTH_SHORT).show();
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage("Running the code is still in beta. It might give inconsistent result. Do you want to continue?");
                builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        new DocumentSaver().execute();
                        Util.makeFolder(Util.hiddenFolder);
                        Intent intent = new Intent(MainActivity.this, ConsoleActivity.class).putExtra("filePath", currentFile.getAbsolutePath());
                        startActivity(intent);
                    }
                });
                builder.setNegativeButton(android.R.string.no, null);
                builder.show();
            }
        } else if (id == R.id.menu_replace_all) {
            if (currentFile == null) {
                Toast.makeText(this, R.string.no_file_open, Toast.LENGTH_SHORT).show();
            } else {
                LayoutInflater inflater = this.getLayoutInflater();
                @SuppressLint("InflateParams")
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
        } else if (id == R.id.menu_minify) {
            startActivity(new Intent(MainActivity.this, MinifyActivity.class));
        } else if (id == R.id.menu_settings) {
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
        } else if (id == R.id.menu_rate) {
            Uri uri = Uri.parse("market://details?id=" + getPackageName());
            Intent goToMarket = new Intent(Intent.ACTION_VIEW, uri);
            goToMarket.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            try {
                startActivity(goToMarket);
            } catch (ActivityNotFoundException e) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=" + getPackageName())));
            }
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private void newFile() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.create_new_file);

        @SuppressLint("InflateParams")
        View root = getLayoutInflater().inflate(R.layout.dialog_new_file, null);
        builder.setView(root);

        final EditText etFileName = (EditText) root.findViewById(R.id.file_name);

        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {

                if (!Pattern.compile("[_a-zA-Z0-9 \\-.]+").matcher(etFileName.getText().toString()).matches()) {
                    Toast.makeText(MainActivity.this, R.string.invalid_file_name, Toast.LENGTH_SHORT).show();
                    return;
                }

                File newFile = new File(Util.mainFolder + etFileName.getText().toString());
                if (!newFile.exists()) {
                    Util.createFile(newFile);
                    Toast.makeText(MainActivity.this, R.string.new_file_created, Toast.LENGTH_SHORT).show();
                }
                currentFile = newFile;
                new DocumentLoader().execute();
            }
        });
        builder.show();
    }

    public void openFileClick(View view) {
        Util.makeFolder(Util.mainFolder);
        new MaterialFilePicker()
                .withActivity(this)
                .withRequestCode(555)
                .withFilter(Pattern.compile(".*\\.js$")) // Filtering files and directories by currentFile name using regexp
                .withHiddenFiles(getDefaultSharedPreferences(this).getBoolean("show_hidden_files", false))
                .start();
    }

    /*** Private Functions ***/
    private void requestNewInterstitial() {
        AdRequest adRequest = new AdRequest.Builder()
                .build();

        mInterstitialAd.loadAd(adRequest);
    }

    private String getFileSize() {
        double fileSize;
        if (currentFile.isFile()) {
            fileSize = (double) currentFile.length(); //in Bytes

            if (fileSize < 1024)
                return String.valueOf(fileSize).concat("B");
            else if (fileSize > 1024 && fileSize < (1024 * 1024))
                return String.valueOf(Math.round((fileSize / 1024 * 100.0)) / 100.0).concat("KB");
            else
                return String.valueOf(Math.round((fileSize / (1024 * 1204) * 100.0)) / 100.0).concat("MB");
        }
        return null;
    }

    private String getFileInfo() {
        return "Size : " + getFileSize() + "\n" + "Path : " + currentFile.getPath() + "\n";
    }

    private boolean isChanged() {
        if (FILE_CONTENT == null)
            return false;

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

        final boolean showSymbolBar = getDefaultSharedPreferences(MainActivity.this).getBoolean("show_symbols_bar", true);

        contentView.setFocusable(false);
        contentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                contentView.setFocusableInTouchMode(true);
                if (showSymbolBar)
                    symbolScrollView.setVisibility(View.VISIBLE);
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

        toolbar.setSubtitle(currentFile.getName());
        scrollView.setVisibility(View.VISIBLE);
        startLayout.setVisibility(View.GONE);

        if (mInterstitialAd.isLoaded()) mInterstitialAd.show();
    }

    private void saveFile() {
        if (isChanged())
            new DocumentSaver().execute();
        else
            Toast.makeText(getApplicationContext(), R.string.no_change_in_file, Toast.LENGTH_SHORT).show();
    }

    private void applyTabWidth(Editable text, int start, int end) {
        //TODO: Better TABS
        String str = text.toString();
        while (start < end)     {
            int index = str.indexOf("\t", start);
            if(index < 0)
                break;
            text.setSpan(new CustomTabWidthSpan(), index, index + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            start = index + 1;
        }
    }

    private boolean isFileChangeListenerAttached() {
        return fileChangeListener != null;
    }


    /*** Async Classes ***/
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
                BufferedReader br = new BufferedReader(new FileReader(currentFile.getAbsoluteFile()));
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

            return "";
        }

        @Override
        protected void onPostExecute(String string) {
            loadDocument(string);
            SharedPreferences.Editor editor = getDefaultSharedPreferences(MainActivity.this).edit();
            editor.putString("last_file_path", currentFile.getAbsolutePath());
            editor.apply();
            progressDialog.dismiss();
        }
    }

    private class DocumentSaver extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            BufferedWriter output = null;
            String toSave = currentBuffer;
            try {
                output = new BufferedWriter(new FileWriter(currentFile));
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
            Toast.makeText(getApplicationContext(), R.string.file_saved, Toast.LENGTH_SHORT).show();

            if (isFileChangeListenerAttached()) fileChangeListener.onFileSave();

            if (mInterstitialAd.isLoaded()) mInterstitialAd.show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 555 && resultCode == RESULT_OK) {
            String filePath = data.getStringExtra(FilePickerActivity.RESULT_FILE_PATH);
            final File newFile = new File(filePath);
            if (newFile.length() >= 10485760) { // if the file is bigger than 10 MB
                Toast.makeText(this, R.string.file_too_big, Toast.LENGTH_LONG).show();
                return;
            }
            if (!isChanged())
                currentFile = newFile;
            else {
                AlertDialog.Builder confirmDialog = new AlertDialog.Builder(MainActivity.this);
                confirmDialog.setTitle(R.string.file_modified);
                confirmDialog.setMessage(R.string.discard_changes_confirm);
                confirmDialog.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        currentFile = newFile;
                    }
                });
                confirmDialog.setNegativeButton(android.R.string.no, null);
                confirmDialog.show();
            }
            new DocumentLoader().execute();
        }
    }

}
