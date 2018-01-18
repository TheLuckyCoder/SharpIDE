package net.theluckycoder.sharpide.activities

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.Toast
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import net.theluckycoder.materialchooser.Chooser
import net.theluckycoder.sharpide.R
import net.theluckycoder.sharpide.utils.Ads
import net.theluckycoder.sharpide.utils.Const
import net.theluckycoder.sharpide.utils.Preferences
import net.theluckycoder.sharpide.utils.bind
import net.theluckycoder.sharpide.utils.ktReplace
import net.theluckycoder.sharpide.utils.lazyFast
import net.theluckycoder.sharpide.utils.verifyStoragePermission
import java.io.File

class MinifyActivity : AppCompatActivity() {

    private val obfuscateBtn by bind<Button>(R.id.btn_obfuscate)
    private val mPreferences by lazyFast { Preferences(this) }
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

        obfuscateBtn.setOnClickListener { obfuscateFile() }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState ?: return
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
        data ?: return

        if (requestCode == 10 && resultCode == RESULT_OK) {
            mFilePath = data.getStringExtra(Chooser.RESULT_PATH)
            obfuscateBtn.isEnabled = true
        }
    }

    fun selectFile(@Suppress("UNUSED_PARAMETER") view: View) {
        Chooser(this, 10,
                fileExtension = "js",
                showHiddenFiles = mPreferences.showHiddenFiles())
                .start()
    }

    private fun obfuscateFile() = async(UI) {
        val file = File(mFilePath)
        val fileContent = file.readText()

        if (fileContent.isBlank()) {
            Toast.makeText(this@MinifyActivity, R.string.error_empty_file, Toast.LENGTH_SHORT).show()
            return@async
        }

        val job = async(UI) {
            // uniform line endings, make them all line feed
            fileContent.ktReplace("\r\n", "\n").ktReplace("\r", "\n")
                // strip leading & trailing whitespace
                .ktReplace(" \n", "\n").ktReplace("\n ", "\n")
                // collapse consecutive line feeds into just 1
                .replace("/\n+/".toRegex(), "\n")
                // remove comments
                .replace("/\\*.*\\*/".toRegex(), "").replace("//.*(?=\\n)".toRegex(), "")
                .ktReplace(" + ", "+").ktReplace(" - ", "-").ktReplace(" = ", "=")
                .ktReplace("if ", "if").ktReplace("for ", "for").ktReplace("while ", "while")
                .ktReplace("( ", "(")
                // remove the new lines and tabs
                .ktReplace("\n", "").ktReplace("\t", "")
        }

        val newFile = File(Const.MINIFY_FOLDER + file.name)
        newFile.writeText(job.await())

        Toast.makeText(this@MinifyActivity, R.string.file_minify_ready, Toast.LENGTH_LONG).show()
    }
}
