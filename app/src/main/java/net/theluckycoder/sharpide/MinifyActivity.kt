package net.theluckycoder.sharpide

import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import net.theluckycoder.filechooser.Chooser
import net.theluckycoder.filechooser.FileChooser
import net.theluckycoder.sharpide.utils.Util
import java.io.File

class MinifyActivity : AppCompatActivity() {

    private val util = Util()
    private var fileName: String? = null
    private var filePath: String? = null
    private var fileContent: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_minify)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        //Create main folder
        util.verifyStoragePermissions(this, 10)
        File(util.mainFolder)

        //Init AdMob
        val mAdView = findViewById<AdView>(R.id.adView)
        val adRequest = AdRequest.Builder()
                .setRequestAgent("android_studio:ad_template")
                .build()
        mAdView.loadAd(adRequest)
    }

    fun selectFile(@Suppress("UNUSED_PARAMETER") view: View) {
        FileChooser(this, 100)
                .setFileExtension("js")
                .showHiddenFiles(PreferenceManager.getDefaultSharedPreferences(this).getBoolean("show_hidden_files", false))
                .start()
    }

    fun obfuscate(view: View) {
        File(util.minifyFolder).mkdirs()
        val file = File(util.mainFolder + fileName!!)
        try {
            fileContent = util.loadFile(filePath!!)
        } catch (e: Exception) {
            Log.e("Error: ", e.message, e)
            Toast.makeText(this, R.string.error_empty_file, Toast.LENGTH_SHORT).show()
        }

        if (fileContent == null) {
            Snackbar.make(view, R.string.error_empty_file, Snackbar.LENGTH_SHORT).show()
            return
        }

        // uniform line endings, make them all line feed
        fileContent = fileContent!!.replace("\r\n", "\n").replace("\r", "\n")
        // strip leading & trailing whitespace
        fileContent = fileContent!!.replace(" \n", "\n").replace("\n ", "\n")
        // collapse consecutive line feeds into just 1
        fileContent = fileContent!!.replace("/\n+/".toRegex(), "\n")
        // remove comments
        fileContent = fileContent!!.replace("/\\*.*\\*/".toRegex(), "").replace("//.*(?=\\n)".toRegex(), "")
        fileContent = fileContent!!.replace(" + ", "+").replace(" - ", "-").replace(" = ", "=").replace("if ", "if").replace("( ", "(")
        // remove the new lines and tabs
        fileContent = fileContent!!.replace("\n", "").replace("\t", "")

        val newFile = File(util.minifyFolder + file.name)
        util.saveFile(newFile, fileContent!!.split(System.getProperty("line.separator").toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
        Toast.makeText(this, R.string.file_minify_ready, Toast.LENGTH_LONG).show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home)
            onBackPressed()
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (data == null)
            return

        if (requestCode == 100 && resultCode == RESULT_OK) {
            val filePath = data.getStringExtra(Chooser.RESULT_FILE_PATH)
            val file = File(filePath)
            fileName = file.name
            this.filePath = file.path
            findViewById<View>(R.id.obfuscateBtn).isEnabled = true
        }
    }
}
