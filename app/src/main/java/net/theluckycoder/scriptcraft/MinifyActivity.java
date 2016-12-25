package net.theluckycoder.scriptcraft;

import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import net.theluckycoder.scriptcraft.utils.FileChooser;
import net.theluckycoder.scriptcraft.utils.Util;

import java.io.File;

public class MinifyActivity extends AppCompatActivity {

    private String modName, modPath, modContent;

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
        /*AdView mAdView = (AdView) findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder()
                .addTestDevice("A5E8BADC6DE99FBFFF54FEB0BD30A4B1")
                .setRequestAgent("android_studio:ad_template")
                .build();
        mAdView.loadAd(adRequest);*/
    }

    public void selectMod(View view) {
        FileChooser filechooser = new FileChooser(this);
        filechooser.setFileListener(new FileChooser.FileSelectedListener() {
            @Override public void fileSelected(final File file) {
                modName = file.getName();
                modPath = file.getPath();
                findViewById(R.id.obfuscateBtn).setEnabled(true);
            }
        });
    }

    public void obfuscate(View view) {
        Util.makeFolder(Util.minifyFolder);
        final File file = new File(Util.mainFolder + modName);
        try {
            modContent = Util.loadFile(modPath);
        } catch (Exception e) {
            Log.e("Error: ", e.getMessage(), e);
            Toast.makeText(this, R.string.error_empty_file, Toast.LENGTH_SHORT).show();
        }

        if (modContent == null) {
            Snackbar.make(view, R.string.error_empty_file, Snackbar.LENGTH_SHORT).show();
            return;
        }

        modContent = modContent.replaceAll("/\\*.*\\*/", "").replaceAll("//.*(?=\\n)", "");
        modContent = modContent.replace("\r", "").replace("\n", "").replace("\t", "");

        File newFile = new File(Util.minifyFolder + file.getName());

        Util.saveFile(newFile, modContent.split(System.getProperty("line.separator")));

        Toast.makeText(this, R.string.file_minified, Toast.LENGTH_LONG).show();
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
}
