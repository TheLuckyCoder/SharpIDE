package net.theluckycoder.sharpide

import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import net.theluckycoder.filechooser.Chooser
import net.theluckycoder.sharpide.utils.Util
import java.io.File


class MinifyActivity : AppCompatActivity() {

    private val mUtil = Util()
    private var mFile: File? = null
    private lateinit var mFileContent: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_minify)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        //Create main folder
        mUtil.verifyStoragePermissions(this, 10)
        File(mUtil.mainFolder)

        //Init AdMob
        val mAdView = findViewById<AdView>(R.id.adView)
        val adRequest = AdRequest.Builder()
                .setRequestAgent("android_studio:ad_template")
                .build()
        mAdView.loadAd(adRequest)
    }

    fun selectFile(@Suppress("UNUSED_PARAMETER") view: View) {
        Chooser(this, 100)
                .setFileExtension("js")
                .showHiddenFiles(PreferenceManager.getDefaultSharedPreferences(this)
                        .getBoolean("show_hidden_files", false))
                .start()
    }

    fun obfuscate(view: View) {
        if (mFile == null) {
            Snackbar.make(view, R.string.error_empty_file, Snackbar.LENGTH_SHORT).show()
            return
        }

        File(mUtil.minifyFolder).mkdirs()
        mFileContent = mUtil.loadFile(mFile!!.absolutePath)

        if (mFileContent == "") {
            Snackbar.make(view, R.string.error_empty_file, Snackbar.LENGTH_SHORT).show()
            return
        }

        // uniform line endings, make them all line feed
        mFileContent = mFileContent.replace("\r\n", "\n").replace("\r", "\n")
        // strip leading & trailing whitespace
        mFileContent = mFileContent.replace(" \n", "\n").replace("\n ", "\n")
        // collapse consecutive line feeds into just 1
        mFileContent = mFileContent.replace("/\n+/".toRegex(), "\n")
        // remove comments
        mFileContent = mFileContent.replace("/\\*.*\\*/".toRegex(), "").replace("//.*(?=\\n)".toRegex(), "")
        mFileContent = mFileContent.replace(" + ", "+").replace(" - ", "-").replace(" = ", "=").replace("if ", "if").replace("( ", "(")
        // remove the new lines and tabs
        mFileContent = mFileContent.replace("\n", "").replace("\t", "")

        val newFile = File(mUtil.minifyFolder + mFile!!.name)
        mUtil.saveFile(newFile, mFileContent.split(System.getProperty("line.separator").toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
        Toast.makeText(this, R.string.file_minify_ready, Toast.LENGTH_LONG).show()
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
        if (data == null)
            return

        if (requestCode == 100 && resultCode == RESULT_OK) {
            mFile = File(data.getStringExtra(Chooser.RESULT_PATH))
            findViewById<View>(R.id.obfuscateBtn).isEnabled = true
        }
    }
}
