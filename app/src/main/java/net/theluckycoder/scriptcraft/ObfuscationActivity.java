package net.theluckycoder.scriptcraft;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import net.theluckycoder.scriptcraft.utils.FileChooser;
import net.theluckycoder.scriptcraft.utils.Util;

import java.io.File;
import java.util.List;

public class ObfuscationActivity extends AppCompatActivity {

    private String modName, modPath, modContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_obfuscation);
        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        //Create Mod Maker folder
        Util.verifyStoragePermissions(this);
        new File(Util.mainFolder).mkdir();

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
        final File file = new File(Util.mainFolder + modName);
        try {
            modContent = Util.loadFile(modPath);
        } catch (Exception e) {
            Log.e("Error: ", e.getMessage(), e);
        }

        if (modContent == null) {
            Snackbar.make(view, R.string.error_empty_file, Snackbar.LENGTH_SHORT).show();
            return;
        }

        modContent = modContent.replaceAll("//.*?\n", "").replace("\r", "").replace("\n", "").replace("\t", "").replace(" ", "");

        Util.saveFile(file, modContent.split(System.getProperty("line.separator")));

        Snackbar.make(view, R.string.mod_obfuscated, Snackbar.LENGTH_LONG)
                .setAction(R.string.import_script, new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent importMod = new Intent(Intent.ACTION_VIEW);
                        importMod.setDataAndType(Uri.fromFile(file), "application/js"); // Open the created file with its default application

                        PackageManager pm = getPackageManager();
                        List<ResolveInfo> apps = pm.queryIntentActivities(importMod, PackageManager.MATCH_DEFAULT_ONLY);

                        if (apps.size() > 0)
                            startActivity(importMod);
                    }
                }).show();
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
