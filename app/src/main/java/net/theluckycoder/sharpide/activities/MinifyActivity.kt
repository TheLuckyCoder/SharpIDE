package net.theluckycoder.sharpide.activities

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.Button
import kotlinx.coroutines.experimental.DefaultDispatcher
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import net.theluckycoder.materialchooser.Chooser
import net.theluckycoder.sharpide.R
import net.theluckycoder.sharpide.utils.Ads
import net.theluckycoder.sharpide.utils.Const
import net.theluckycoder.sharpide.utils.Preferences
import net.theluckycoder.sharpide.utils.extensions.bind
import net.theluckycoder.sharpide.utils.extensions.ktReplace
import net.theluckycoder.sharpide.utils.extensions.longToast
import net.theluckycoder.sharpide.utils.extensions.toast
import net.theluckycoder.sharpide.utils.extensions.verifyStoragePermission
import java.io.File

class MinifyActivity : AppCompatActivity() {

    private val obfuscateBtn by bind<Button>(R.id.btn_obfuscate)
    private val mPreferences = Preferences(this)
    private var mFilePath = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_minify)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Set Fullscreen
        if (Preferences(this).isFullscreen) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }

        verifyStoragePermission()

        // Create main folder
        File(Const.MINIFY_FOLDER).mkdirs()

        // Init AdMob
        Ads(this).loadBanner()

        obfuscateBtn.setOnClickListener { obfuscateFile() }

        savedInstanceState?.let {
            mFilePath = it.getString("filePath", "")
            if (mFilePath.isNotEmpty()) obfuscateBtn.isEnabled = true
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("filePath", mFilePath)
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
            mFilePath = data.getStringExtra(Chooser.RESULT_PATH)
            obfuscateBtn.isEnabled = true
        }
    }

    fun selectFile(@Suppress("UNUSED_PARAMETER") view: View) {
        Chooser(this, 10,
            fileExtension = "js",
            showHiddenFiles = mPreferences.showHiddenFiles,
            startPath = Const.MAIN_FOLDER,
            useNightTheme = mPreferences.useDarkTheme)
            .start()
    }

    private fun obfuscateFile() = launch(UI) {
        try {
            async(DefaultDispatcher) {
                val file = File(mFilePath)

                val fileContent = try {
                    file.readText()
                } catch (e: Exception) {
                    e.printStackTrace()
                    ""
                }

                if (fileContent.isBlank()) {
                    toast(R.string.error_empty_file)
                    return@async
                }

                // uniform line endings, make them all line feed
                fileContent.ktReplace("\r\n", "\n").ktReplace("\r", "\n")
                    // strip leading & trailing whitespace
                    .ktReplace(" \n", "\n").ktReplace("\n ", "\n")
                    // collapse consecutive line feeds into just 1
                    .replace("/\n+/".toRegex(), "\n")
                    // remove comments
                    .replace("/\\*(?:.|[\\n])*?\\*/|//.*".toRegex(), "")
                    // remove other spaces
                    .ktReplace(" + ", "+").ktReplace(" - ", "-").ktReplace(" = ", "=")
                    .ktReplace("if ", "if").ktReplace("for ", "for").ktReplace("while ", "while")
                    .ktReplace("( ", "(")
                    // remove the new lines and tabs
                    .ktReplace("\n", "").ktReplace("\t", "")

                val newFile = File(Const.MINIFY_FOLDER + file.name)
                newFile.writeText(fileContent)
            }.await()
        } catch (e: Exception) {
            toast(R.string.error)
        }

        longToast(R.string.file_minify_ready)
    }
}
