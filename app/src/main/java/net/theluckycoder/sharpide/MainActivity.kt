package net.theluckycoder.sharpide

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ProgressDialog
import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager
import android.support.design.widget.NavigationView
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.app.AppCompatDelegate
import android.support.v7.widget.Toolbar
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.widget.*
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.InterstitialAd
import net.theluckycoder.filechooser.Chooser
import net.theluckycoder.sharpide.component.CodeEditText
import net.theluckycoder.sharpide.component.InteractiveScrollView
import net.theluckycoder.sharpide.listener.FileChangeListener
import net.theluckycoder.sharpide.listener.OnBottomReachedListener
import net.theluckycoder.sharpide.listener.OnScrollListener
import net.theluckycoder.sharpide.utils.CustomTabWidthSpan
import net.theluckycoder.sharpide.utils.Util
import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEvent
import java.io.*
import java.util.regex.Pattern


class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private val mUtil = Util()
    private var mDialog: AlertDialog? = null
    private lateinit var codeEditText: CodeEditText
    private lateinit var scrollView: InteractiveScrollView
    private lateinit var startLayout: LinearLayout
    private lateinit var toolbar: Toolbar
    private lateinit var symbolScrollView: HorizontalScrollView

    private lateinit var mInterstitialAd: InterstitialAd
    private var mCurrentFile: File? = null
    private val fileChangeListener: FileChangeListener? = null
    private val CHUNK = 20000
    private var mFileContent: String? = null
    private var mCurrentBuffer: String? = null
    private var mLoaded: StringBuilder? = null
    private lateinit var mLastSavePath: String

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme_NoActionBar)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Setup Views
        codeEditText = findViewById(R.id.fileContent)
        startLayout = findViewById(R.id.startLayout)

        val symbolLayout: LinearLayout = findViewById(R.id.symbolLayout)
        for (i in 0 until symbolLayout.childCount)
            symbolLayout.getChildAt(i).setOnClickListener({ view -> codeEditText.text.insert(codeEditText.selectionStart, (view as TextView).text.toString()) })

        scrollView = findViewById(R.id.mainScrollView)
        scrollView.setOnBottomReachedListener(null)
        scrollView.setOnScrollListener(fileChangeListener as OnScrollListener?)
        symbolScrollView = findViewById(R.id.symbolScrollView)

        //Set up navigation drawer
        val drawer: DrawerLayout = findViewById(R.id.drawer_layout)
        val toggle = ActionBarDrawerToggle(this, drawer, toolbar, 0, 0)
        drawer.addDrawerListener(toggle)
        toggle.syncState()

        val navigationView: NavigationView = findViewById(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener(this)

        // Load preferences
        codeEditText.textSize = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(this).getString("font_size", "16")).toFloat()
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("load_last_file", true)) {
            val lastFilePath = PreferenceManager.getDefaultSharedPreferences(this).getString("last_file_path", "")
            val lastFile = File(lastFilePath)
            if (lastFilePath != "" && lastFile.isFile) {
                mCurrentFile = lastFile
                LoadTask().execute()
            }
        }

        mLastSavePath = PreferenceManager.getDefaultSharedPreferences(this).getString("last_save_path", mUtil.mainFolder)

        //Check for permission
        mUtil.verifyStoragePermissions(this, 10)
        File(mUtil.mainFolder).mkdirs()

        // Setup keyboard checker
        KeyboardVisibilityEvent.setEventListener(this) { isOpen ->
            if (PreferenceManager.getDefaultSharedPreferences(this@MainActivity).getBoolean("show_symbols_bar", true)) {
                if (isOpen)
                    symbolScrollView.visibility = View.VISIBLE
                else
                    symbolScrollView.visibility = View.GONE
            }
        }

        // Setup ads
        mInterstitialAd = InterstitialAd(this)
        mInterstitialAd.adUnitId = "ca-app-pub-1279472163660969/4393673534"
        mInterstitialAd.adListener = object : AdListener() {
            override fun onAdClosed() {
                requestNewInterstitial()
            }
        }
        requestNewInterstitial()
    }

    override fun onBackPressed() {
        val drawer: DrawerLayout = findViewById(R.id.drawer_layout)
        if (drawer.isDrawerOpen(GravityCompat.START))
            drawer.closeDrawer(GravityCompat.START)
        else {
            if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("quit_confirm", true)) {
                val builder = AlertDialog.Builder(this)
                builder.setTitle(R.string.app_name)
                builder.setMessage(R.string.exit_confirmation)
                builder.setPositiveButton(android.R.string.yes) { _, _ -> finish() }.setNegativeButton(android.R.string.no, null)
                builder.show()
            } else
                finish()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        val id = item.itemId

        when (id) {
            R.id.menu_save -> if (mCurrentFile == null)
                Toast.makeText(this, R.string.no_file_open, Toast.LENGTH_SHORT).show()
            else
                saveFile()
            R.id.menu_open -> openFileClick(null)
            R.id.menu_new -> newFile()
            R.id.menu_file_info -> if (mCurrentFile == null) {
                Toast.makeText(this, R.string.no_file_open, Toast.LENGTH_SHORT).show()
            } else {
                val builder = AlertDialog.Builder(this)
                builder.setTitle(R.string.file_info)
                builder.setMessage(getFileInfo())
                builder.setNeutralButton(R.string.action_close, null)
                builder.show()
            }
            R.id.menu_run -> if (mCurrentFile == null) {
                Toast.makeText(this, R.string.no_file_open, Toast.LENGTH_SHORT).show()
            } else {
                SaveTask(true).execute()
            }
            R.id.menu_replace_all -> if (mCurrentFile == null) {
                Toast.makeText(this, R.string.no_file_open, Toast.LENGTH_SHORT).show()
            } else {
                val inflater = this.layoutInflater
                @SuppressLint("InflateParams")
                val dialogView = inflater.inflate(R.layout.dialog_replace, null)
                val builder = AlertDialog.Builder(this)
                builder.setTitle(R.string.replace_all)
                builder.setView(dialogView)
                builder.setPositiveButton(R.string.replace_all) { _, _ ->
                    val findText: EditText = dialogView.findViewById(R.id.findText)
                    val replaceText: EditText = dialogView.findViewById(R.id.replaceText)
                    val newText = codeEditText.text.toString().replace(findText.text.toString(), replaceText.text.toString())
                    codeEditText.setText(newText)
                    if (mInterstitialAd.isLoaded) mInterstitialAd.show()
                }

                builder.setNegativeButton(android.R.string.cancel, null)
                builder.show()
            }
            R.id.menu_minify -> startActivity(Intent(this, MinifyActivity::class.java))
            R.id.menu_settings -> startActivity(Intent(this, SettingsActivity::class.java))
            R.id.menu_rate -> {
                val uri = Uri.parse("market://details?id=" + packageName)
                val goToMarket = Intent(Intent.ACTION_VIEW, uri)
                goToMarket.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                try {
                    startActivity(goToMarket)
                } catch (e: ActivityNotFoundException) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=net.theluckycoder.scriptcraft")))
                }

            }
        }

        val drawer: DrawerLayout = findViewById(R.id.drawer_layout)
        drawer.closeDrawer(GravityCompat.START)
        return true
    }

    private fun newFile() {
        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setTitle(R.string.create_new_file)

        @SuppressLint("InflateParams")
        val dialogView = layoutInflater.inflate(R.layout.dialog_new_file, null)
        dialogBuilder.setView(dialogView)

        val etFileName: EditText = dialogView.findViewById(R.id.file_name)
        etFileName.setText(PreferenceManager.getDefaultSharedPreferences(this)
                .getString("new_files_name", getString(R.string.new_file))!! + ".js")

        val textSelectPath: TextView = dialogView.findViewById(R.id.text_select_path)
        textSelectPath.text = mLastSavePath
        textSelectPath.setOnClickListener {
            Chooser(this@MainActivity, 200)
                    .setChooserType(Chooser.ChooserType.FOLDER_CHOOSER)
                    .setStartPath(mUtil.mainFolder)
                    .showHiddenFiles(PreferenceManager.getDefaultSharedPreferences(this@MainActivity)
                            .getBoolean("show_hidden_files", false))
                    .start()
        }

        dialogBuilder.setPositiveButton(android.R.string.ok, DialogInterface.OnClickListener { _, _ ->
            if (!Pattern.compile("[_a-zA-Z0-9 \\-.]+").matcher(etFileName.text.toString()).matches()) {
                Toast.makeText(this@MainActivity, R.string.invalid_file_name, Toast.LENGTH_SHORT).show()
                return@OnClickListener
            }

            val newFile = File(textSelectPath.text.toString() + etFileName.text.toString())
            if (!newFile.exists()) {
                mUtil.createFile(newFile)
                Toast.makeText(this@MainActivity, R.string.new_file_created, Toast.LENGTH_SHORT).show()
            }
            mCurrentFile = newFile
            LoadTask().execute()
        })
        mDialog = dialogBuilder.create()
        mDialog!!.show()
    }

    fun openFileClick(@Suppress("UNUSED_PARAMETER") view: View?) {
        Chooser(this, 100)
                .setFileExtension("js")
                .setStartPath(mLastSavePath)
                .showHiddenFiles(PreferenceManager.getDefaultSharedPreferences(this)
                        .getBoolean("show_hidden_files", false))
                .start()
    }

    /*** Private Functions  */
    private fun requestNewInterstitial() {
        val adRequest = AdRequest.Builder()
                .build()

        mInterstitialAd.loadAd(adRequest)
    }

    private fun getFileSize(): String {
        val fileSize: Double
        if (mCurrentFile!!.isFile) {
            fileSize = mCurrentFile!!.length().toDouble()

            return if (fileSize < 1024)
                fileSize.toString() + "B"
            else if (fileSize > 1024 && fileSize < 1024 * 1024)
                (Math.round(fileSize / 1024 * 100.0) / 100.0).toString() + "KB"
            else
                (Math.round(fileSize / (1024 * 1204) * 100.0) / 100.0).toString() + "MB"
        }
        return ""
    }

    private fun getFileInfo(): String = "Size : " + getFileSize() + "\n" + "Path : " + mCurrentFile!!.path + "\n"

    private val isChanged: Boolean
        get() {
            if (mFileContent == null)
                return false
            else if (mFileContent!!.length >= CHUNK && mFileContent!!.substring(0, mLoaded!!.length) == mCurrentBuffer)
                return false
            else if (mFileContent == mCurrentBuffer)
                return false

            return true
        }

    private fun loadInChunks(scrollView: InteractiveScrollView, bigString: String) {
        mLoaded!!.append(bigString.substring(0, CHUNK))
        codeEditText.setTextHighlighted(mLoaded)
        scrollView.setOnBottomReachedListener(object : OnBottomReachedListener {
            override fun onBottomReached() {
                when {
                    mLoaded!!.length >= bigString.length -> return
                    mLoaded!!.length + CHUNK > bigString.length -> {
                        val buffer = bigString.substring(mLoaded!!.length, bigString.length)
                        mLoaded!!.append(buffer)
                    }
                    else -> {
                        val buffer = bigString.substring(mLoaded!!.length, mLoaded!!.length + CHUNK)
                        mLoaded!!.append(buffer)
                    }
                }

                codeEditText.setTextHighlighted(mLoaded)
            }
        })
    }

    private fun loadDocument(fileContent: String) {
        scrollView.smoothScrollTo(0, 0)

        codeEditText.isFocusable = false
        codeEditText.setOnClickListener { codeEditText.isFocusableInTouchMode = true }

        mLoaded = StringBuilder()
        if (fileContent.length > CHUNK)
            loadInChunks(scrollView, fileContent)
        else {
            mLoaded!!.append(fileContent)
            codeEditText.setTextHighlighted(mLoaded)
        }

        codeEditText.addTextChangedListener(object : TextWatcher {
            internal var start = 0
            internal var end = 0
            override fun beforeTextChanged(p1: CharSequence, p2: Int, p3: Int, p4: Int) {}

            override fun onTextChanged(p1: CharSequence, start: Int, before: Int, count: Int) {
                this.start = start
                this.end = start + count
            }

            override fun afterTextChanged(p1: Editable) {
                Handler().postDelayed({
                    applyTabWidth(p1, start, end)

                    mCurrentBuffer = codeEditText.text.toString()

                    fileChangeListener?.onFileChanged(isChanged)
                }, 500)
            }
        })
        mCurrentBuffer = codeEditText.text.toString()

        fileChangeListener?.onFileOpen()

        toolbar.subtitle = mCurrentFile!!.name
        scrollView.visibility = View.VISIBLE
        startLayout.visibility = View.GONE

        if (mInterstitialAd.isLoaded) mInterstitialAd.show()
    }

    private fun saveFile() {
        if (isChanged)
            SaveTask(false).execute()
        else
            Toast.makeText(this, R.string.no_change_in_file, Toast.LENGTH_SHORT).show()
    }

    private fun applyTabWidth(text: Editable, start: Int, end: Int) {
        var newStart = start
        val str = text.toString()
        while (newStart < end) {
            val index = str.indexOf("\t", newStart)
            if (index < 0)
                break
            text.setSpan(CustomTabWidthSpan(), index, index + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            newStart = index + 1
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (data == null) return

        if (requestCode == 100 && resultCode == Activity.RESULT_OK) {
            val filePath = data.getStringExtra(Chooser.RESULT_PATH)
            val newFile = File(filePath)
            if (newFile.length() >= 10485760) { // if the file is bigger than 10 MB
                Toast.makeText(this, R.string.file_too_big, Toast.LENGTH_LONG).show()
                return
            }
            if (!isChanged)
                mCurrentFile = newFile
            else {
                val confirmDialog = AlertDialog.Builder(this@MainActivity)
                confirmDialog.setTitle(R.string.file_modified)
                confirmDialog.setMessage(R.string.discard_changes_confirm)
                confirmDialog.setPositiveButton(android.R.string.yes) { _, _ -> mCurrentFile = newFile }
                confirmDialog.setNegativeButton(android.R.string.no, null)
                confirmDialog.show()
            }
            LoadTask().execute()
        }
        if (requestCode == 200 && resultCode == Activity.RESULT_OK) {
            mLastSavePath = data.getStringExtra(Chooser.RESULT_PATH)
            if (mDialog != null) {
                mDialog!!.dismiss()
                newFile()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            10 -> if (grantResults.size < 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "External Storage permission is required in order for the app to work", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                File(mUtil.mainFolder).mkdir()
                if (mCurrentFile != null)
                    LoadTask().execute()
            }
        }
    }

    /*** Async Classes  ***/
    private inner class LoadTask : AsyncTask<Void, Void, String>() {

        private lateinit var progressDialog: ProgressDialog

        override fun onPreExecute() {
            super.onPreExecute()
            progressDialog = ProgressDialog(this@MainActivity)
            progressDialog.setTitle(R.string.loading_file)
            progressDialog.setCancelable(false)
            progressDialog.show()
        }

        override fun doInBackground(vararg paths: Void?): String {
            try {
                val br = BufferedReader(FileReader(mCurrentFile!!.absoluteFile))
                try {
                    val sb = StringBuilder()
                    var line: String? = br.readLine()

                    while (line != null) {
                        sb.append(line)
                        sb.append("\n")
                        line = br.readLine()
                    }
                    mFileContent = sb.toString()
                    return mFileContent as String
                } catch (e: IOException) {
                    e.printStackTrace()
                } finally {
                    try {
                        br.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }

                }
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            }

            return ""
        }

        override fun onPostExecute(string: String) {
            loadDocument(string)
            val editor = PreferenceManager.getDefaultSharedPreferences(this@MainActivity).edit()
            editor.putString("last_file_path", mCurrentFile!!.absolutePath)
            editor.apply()
            progressDialog.dismiss()
        }
    }

    private inner class SaveTask internal constructor(private val startConsole: Boolean) : AsyncTask<Void, Void, Void>() {

        override fun doInBackground(vararg voids: Void): Void? {
            var output: BufferedWriter? = null
            var toSave = mCurrentBuffer
            try {
                output = BufferedWriter(FileWriter(mCurrentFile!!))
                if (mFileContent!!.length > CHUNK)
                    toSave = mCurrentBuffer!! + mFileContent!!.substring(mLoaded!!.length, mFileContent!!.length)
                output.write(toSave!!)
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                if (output != null) {
                    try {
                        output.close()
                        mFileContent = toSave
                    } catch (ioe: IOException) {
                        ioe.printStackTrace()
                    }

                }
            }

            if (startConsole)
                try {
                    mUtil.saveFileInternally(this@MainActivity, "main.js", mFileContent!!)
                    mUtil.saveFileInternally(this@MainActivity, "index.html", "<!DOCTYPE html>\n<html>\n<head>\n<script type=\"text/javascript\" src=\"main.js\"></script>\n</head>\n<body>\n</body>\n</html>")
                } catch (e: IOException) {
                    e.printStackTrace()
                } catch (e: RuntimeException) {
                    e.printStackTrace()
                }

            return null
        }

        override fun onPostExecute(v: Void?) {
            Toast.makeText(this@MainActivity, R.string.file_saved, Toast.LENGTH_SHORT).show()

            fileChangeListener?.onFileSave()

            if (mInterstitialAd.isLoaded) mInterstitialAd.show()

            if (startConsole)
                startActivity(Intent(this@MainActivity, ConsoleActivity::class.java))
        }
    }

    companion object {
        init { AppCompatDelegate.setCompatVectorFromResourcesEnabled(true) }
    }

}
