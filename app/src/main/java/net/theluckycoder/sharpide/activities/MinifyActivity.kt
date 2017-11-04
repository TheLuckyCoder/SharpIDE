package net.theluckycoder.sharpide.activities

import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.Toast
import net.theluckycoder.materialchooser.Chooser
import net.theluckycoder.sharpide.R
import net.theluckycoder.sharpide.utils.*
import java.io.File


class MinifyActivity : AppCompatActivity() {

    private var mFilePath = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_minify)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        verifyStoragePermission()

        //Create main folder
        File(Constants.minifyFolder).mkdirs()

        //Init AdMob
        Ads(this).loadBanner()

        savedInstanceState?.let { mFilePath = it.getString("filePath", "") }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("filePath", mFilePath)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (data == null) return

        if (requestCode == Constants.PERMISSION_REQUEST_CODE && resultCode == RESULT_OK) {
            mFilePath = data.getStringExtra(Chooser.RESULT_PATH)
            findViewById<Button>(R.id.obfuscateBtn).isEnabled = true
        }
    }

    fun selectFile(@Suppress("UNUSED_PARAMETER") view: View) {
        Chooser(this, 100,
                fileExtension = "js",
                showHiddenFiles = PreferenceManager.getDefaultSharedPreferences(this)
                        .getBoolean("show_hidden_files", false))
                .start()
    }

    fun obfuscate(view: View) {
        val file = File(mFilePath)
        var fileContent = file.loadFile()

        if (fileContent == "") {
            Snackbar.make(view, R.string.error_empty_file, Snackbar.LENGTH_SHORT).show()
            return
        }

        // uniform line endings, make them all line feed
        fileContent = fileContent.replace("\r\n", "\n").replace("\r", "\n")
        // strip leading & trailing whitespace
        .replace(" \n", "\n").replace("\n ", "\n")
        // collapse consecutive line feeds into just 1
        .replace("/\n+/".toRegex(), "\n")
        // remove comments
        .replace("/\\*.*\\*/".toRegex(), "").replace("//.*(?=\\n)".toRegex(), "")
        .replace(" + ", "+").replace(" - ", "-").replace(" = ", "=").replace("if ", "if").replace("( ", "(")
        // remove the new lines and tabs
        .replace("\n", "").replace("\t", "")

        val newFile = File(Constants.minifyFolder + file.name)
        newFile.save(fileContent.split(System.getProperty("line.separator").toRegex()).toTypedArray())

        Toast.makeText(this, R.string.file_minify_ready, Toast.LENGTH_LONG).show()
    }
}
