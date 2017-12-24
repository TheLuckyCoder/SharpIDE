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
import net.theluckycoder.sharpide.utils.Ads
import net.theluckycoder.sharpide.utils.Const
import net.theluckycoder.sharpide.utils.ktReplace
import net.theluckycoder.sharpide.utils.verifyStoragePermission
import java.io.File


class MinifyActivity : AppCompatActivity() {

    private var mFilePath = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_minify)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        verifyStoragePermission()

        //Create main folder
        File(Const.MINIFY_FOLDER).mkdirs()

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

        if (requestCode == Const.PERMISSION_REQUEST_CODE && resultCode == RESULT_OK) {
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
        var fileContent = file.readText()

        if (fileContent.isBlank()) {
            Snackbar.make(view, R.string.error_empty_file, Snackbar.LENGTH_SHORT).show()
            return
        }

        // uniform line endings, make them all line feed
        fileContent = fileContent.ktReplace("\r\n", "\n").ktReplace("\r", "\n")
        // strip leading & trailing whitespace
        .ktReplace(" \n", "\n").ktReplace("\n ", "\n")
        // collapse consecutive line feeds into just 1
        .replace("/\n+/".toRegex(), "\n")
        // remove comments
        .replace("/\\*.*\\*/".toRegex(), "").replace("//.*(?=\\n)".toRegex(), "")
        .ktReplace(" + ", "+").ktReplace(" - ", "-").ktReplace(" = ", "=")
        .ktReplace("if ", "if").ktReplace("for ", "for").ktReplace("( ", "(")
        // remove the new lines and tabs
        .ktReplace("\n", "").ktReplace("\t", "")

        val newFile = File(Const.MINIFY_FOLDER + file.name)
        newFile.writeText(fileContent)

        Toast.makeText(this, R.string.file_minify_ready, Toast.LENGTH_LONG).show()
    }

}
