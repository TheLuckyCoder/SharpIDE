package net.theluckycoder.scriptcraft;

import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.nbsp.materialfilepicker.MaterialFilePicker;
import com.nbsp.materialfilepicker.ui.FilePickerActivity;

import net.theluckycoder.scriptcraft.utils.Util;

import java.io.File;
import java.util.regex.Pattern;

public final class MinifyActivity extends AppCompatActivity {

    private String fileName, filePath, fileContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_minify);
        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        //Create main folder
        Util.verifyStoragePermissions(this);
        Util.makeFolder(Util.mainFolder);

        //Init AdMob
        AdView mAdView = (AdView) findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder()
                .setRequestAgent("android_studio:ad_template")
                .build();
        mAdView.loadAd(adRequest);
    }

    public void selectMod(View view) {
        new MaterialFilePicker()
                .withActivity(this)
                .withRequestCode(1)
                .withFilter(Pattern.compile(".*\\.js$")) // Filtering files and directories by file name using regexp
                .withHiddenFiles(PreferenceManager.getDefaultSharedPreferences(this).getBoolean("show_hidden_files", false))
                .start();
    }

    public void obfuscate(View view) {
        Util.makeFolder(Util.minifyFolder);
        final File file = new File(Util.mainFolder + fileName);
        try {
            fileContent = Util.loadFile(filePath);
        } catch (Exception e) {
            Log.e("Error: ", e.getMessage(), e);
            Toast.makeText(this, R.string.error_empty_file, Toast.LENGTH_SHORT).show();
        }

        if (fileContent == null) {
            Snackbar.make(view, R.string.error_empty_file, Snackbar.LENGTH_SHORT).show();
            return;
        }

        // uniform line endings, make them all line feed
        fileContent = fileContent.replace("\r\n", "\n").replace("\r", "\n");
        // strip leading & trailing whitespace
        fileContent = fileContent.replace(" \n", "\n").replace("\n ", "\n");
        // collapse consecutive line feeds into just 1
        fileContent = fileContent.replaceAll("/\n+/", "\n");
        // remove comments
        fileContent = fileContent.replaceAll("/\\*.*\\*/", "").replaceAll("//.*(?=\\n)", "");
        fileContent = fileContent.replace(" + ", "+").replace(" - ", "-").replace(" = ", "=").replace("if ", "if").replace("( ", "(");
        // remove the new lines and tabs
        fileContent = fileContent.replace("\n", "").replace("\t", "");

        File newFile = new File(Util.minifyFolder + file.getName());
        Util.saveFile(newFile, fileContent.split(System.getProperty("line.separator")));
        Toast.makeText(this, R.string.file_minify_ready, Toast.LENGTH_LONG).show();
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1 && resultCode == RESULT_OK) {
            String filePath = data.getStringExtra(FilePickerActivity.RESULT_FILE_PATH);
            File file = new File(filePath);
            fileName = file.getName();
            this.filePath = file.getPath();
            findViewById(R.id.obfuscateBtn).setEnabled(true);
        }
    }
}
