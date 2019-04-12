package net.theluckycoder.sharpide.activities

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_minify.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.theluckycoder.materialchooser.Chooser
import net.theluckycoder.sharpide.R
import net.theluckycoder.sharpide.utils.Ads
import net.theluckycoder.sharpide.utils.AppPreferences
import net.theluckycoder.sharpide.utils.Const
import net.theluckycoder.sharpide.utils.extensions.longToast
import net.theluckycoder.sharpide.utils.extensions.replace
import net.theluckycoder.sharpide.utils.extensions.toast
import net.theluckycoder.sharpide.utils.extensions.verifyStoragePermission
import java.io.File

class MinifyActivity : AppCompatActivity() {

    private val preferences = AppPreferences(this)
    private var filePath = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_minify)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Set Fullscreen
        if (AppPreferences(this).isFullscreen) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }

        verifyStoragePermission()

        // Create main folder
        File(Const.MINIFY_FOLDER).mkdirs()

        // Init AdMob
        Ads(this).loadBanner()

        btn_obfuscate.setOnClickListener { obfuscateFile() }

        savedInstanceState?.let {
            filePath = it.getString("filePath", "")
            if (filePath.isNotEmpty()) btn_obfuscate.isEnabled = true
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("filePath", filePath)
        super.onSaveInstanceState(outState)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            onBackPressed()
            true
        } else super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        data ?: return

        if (requestCode == 10 && resultCode == RESULT_OK) {
            filePath = data.getStringExtra(Chooser.RESULT_PATH)
            btn_obfuscate.isEnabled = true
        }
    }

    fun selectFile(@Suppress("UNUSED_PARAMETER") view: View) {
        Chooser(this, 10,
            fileExtension = "js",
            showHiddenFiles = preferences.showHiddenFiles,
            startPath = Const.MAIN_FOLDER,
            useNightTheme = preferences.useDarkTheme)
            .start()
    }

    private fun obfuscateFile() = GlobalScope.launch(Dispatchers.Main) {
        try {
            withContext(Dispatchers.Default) {
                val file = File(filePath)

                val fileContent = try {
                    file.readText()
                } catch (e: Exception) {
                    e.printStackTrace()
                    ""
                }

                if (fileContent.isBlank()) {
                    toast(R.string.error_empty_file)
                    return@withContext
                }

                // uniform line endings, make them all line feed
                fileContent.replace("\r\n", "\n").replace("\r", "\n")
                    // strip leading & trailing whitespace
                    .replace(" \n", "\n").replace("\n ", "\n")
                    // collapse consecutive line feeds into just 1
                    .replace("/\n+/".toRegex(), "\n")
                    // remove comments
                    .replace("/\\*(?:.|[\\n])*?\\*/|//.*".toRegex(), "")
                    // remove other spaces
                    .replace(" + ", "+").replace(" - ", "-").replace(" = ", "=")
                    .replace("if ", "if").replace("for ", "for").replace("while ", "while")
                    .replace("( ", "(")
                    // remove the new lines and tabs
                    .replace("\n", "").replace("\t", "")

                val newFile = File(Const.MINIFY_FOLDER + file.name)
                newFile.writeText(fileContent)
            }

            longToast(R.string.file_minify_ready)
        } catch (e: Exception) {
            toast(R.string.error)
        }
    }
}
