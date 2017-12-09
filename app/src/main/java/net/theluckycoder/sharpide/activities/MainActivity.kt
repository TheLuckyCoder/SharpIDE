package net.theluckycoder.sharpide.activities

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.NavigationView
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.ads.MobileAds
import net.theluckycoder.materialchooser.Chooser
import net.theluckycoder.sharpide.R
import net.theluckycoder.sharpide.utils.Ads
import net.theluckycoder.sharpide.utils.Const
import net.theluckycoder.sharpide.utils.Const.PERMISSION_REQUEST_CODE
import net.theluckycoder.sharpide.utils.CustomTabWidthSpan
import net.theluckycoder.sharpide.utils.Preferences
import net.theluckycoder.sharpide.utils.UpdateChecker
import net.theluckycoder.sharpide.utils.bind
import net.theluckycoder.sharpide.utils.lazyFast
import net.theluckycoder.sharpide.utils.saveFileInternally
import net.theluckycoder.sharpide.utils.string
import net.theluckycoder.sharpide.utils.verifyStoragePermission
import net.theluckycoder.sharpide.view.CodeEditText
import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEvent
import net.yslibrary.android.keyboardvisibilityevent.util.UIUtil
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStreamReader
import java.util.regex.Pattern


@SuppressLint("InflateParams")
class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener, UpdateChecker.OnUpdateNeededListener {

    private companion object {
        private const val LOAD_FILE_REQUEST_CODE = 150
        private const val CHANGE_PATH_REQUEST_CODE = 151
    }

    private val codeEditText: CodeEditText by bind(R.id.edit_main)
    private val scrollView: ScrollView by bind(R.id.main_scroll_view)
    private val symbolScrollView: HorizontalScrollView by bind(R.id.symbolScrollView)

    private val mAds = Ads(this)
    private var mDialog: AlertDialog? = null
    private lateinit var mCurrentFile: File
    private var mFileContent = ""
    private var mCurrentBuffer = ""
    private val mLoaded = StringBuilder()
    private lateinit var mDefaultFileName: String
    private val mPreferences by lazyFast { Preferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme_NoActionBar)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Views
        val symbolLayout: LinearLayout = findViewById(R.id.layout_symbols)
        for (i in 0 until symbolLayout.childCount) {
            symbolLayout.getChildAt(i).setOnClickListener({ codeEditText.text.insert(codeEditText.selectionStart, (it as TextView).text.string) })
        }

        // Set up navigation drawer
        val drawer: DrawerLayout = findViewById(R.id.drawer_layout)
        val toggle = object : ActionBarDrawerToggle(this, drawer, toolbar, 0, 0) {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                super.onDrawerSlide(drawerView, slideOffset)
                UIUtil.hideKeyboard(this@MainActivity)
                codeEditText.clearFocus()
            }
        }
        drawer.addDrawerListener(toggle)
        toggle.syncState()

        val navigationView: NavigationView = findViewById(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener(this)

        // Load preferences
        mDefaultFileName = mPreferences.getNewFilesName()
        mCurrentFile = File(mDefaultFileName)
        supportActionBar?.subtitle = mCurrentFile.name

        codeEditText.textSize = mPreferences.getFontSize().toFloat()
        if (mPreferences.getLoadLastFile()) {
            val lastFilePath = mPreferences.getLastFilePath()
            val lastFile = File(lastFilePath)

            if (lastFilePath != "" && lastFile.isFile && lastFile.canRead()) {
                mCurrentFile = lastFile
                LoadTask().execute()
            }
        }

        // Check for permission
        verifyStoragePermission()
        File(Const.MAIN_FOLDER).mkdirs()

        // Setup keyboard checker
        KeyboardVisibilityEvent.setEventListener(this) { isOpen ->
            if (mPreferences.getShowSymbolsBar()) {
                symbolScrollView.visibility = if (isOpen) View.VISIBLE else View.GONE
            }
        }

        // Setup UpdateChecker
        UpdateChecker(this).check()

        // Setup ads
        MobileAds.initialize(this, "ca-app-pub-1279472163660969~2916940339")
        mAds.loadInterstitial()
    }

    override fun onUpdateNeeded() {
        AlertDialog.Builder(this)
                .setTitle(R.string.updater_new_version)
                .setMessage(R.string.updater_new_update_desc)
                .setPositiveButton(R.string.updater_update, { _, _ ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=net.theluckycoder.scriptcraft"))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                }).setNegativeButton(getString(R.string.updater_no), null)
                .show()
    }

    override fun onBackPressed() {
        val drawer: DrawerLayout = findViewById(R.id.drawer_layout)

        when {
            drawer.isDrawerOpen(GravityCompat.START) -> drawer.closeDrawer(GravityCompat.START)
            mPreferences.getConfirmQuit() -> {
                AlertDialog.Builder(this)
                        .setTitle(R.string.app_name)
                        .setMessage(R.string.quit_confirm)
                        .setPositiveButton(android.R.string.yes) { _, _ -> finish() }
                        .setNegativeButton(android.R.string.no, null)
                        .show()
            }
            else -> return super.onBackPressed()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_save -> {
                if (mCurrentFile.path != mDefaultFileName) {
                    SaveTask(false).execute()
                } else {
                    saveAs(false)
                }
            }
            R.id.menu_save_as -> saveAs(false)
            R.id.menu_open -> {
                Chooser(this,
                        requestCode = LOAD_FILE_REQUEST_CODE,
                        fileExtension = "js",
                        showHiddenFiles = mPreferences.getShowHiddenFiles(),
                        startPath = Const.MAIN_FOLDER)
                        .start()
            }
            R.id.menu_new -> saveAs(true)
            R.id.menu_file_info -> {
                AlertDialog.Builder(this)
                        .setTitle(R.string.menu_file_info)
                        .setMessage(getFileInfo())
                        .setNeutralButton(R.string.action_close, null)
                        .show()
            }
            R.id.menu_run -> {
                if (mCurrentFile.path != mDefaultFileName) {
                    SaveTask(true).execute()
                } else {
                    saveAs(false)
                }
            }
            R.id.menu_find -> {
                val dialogView = layoutInflater.inflate(R.layout.dialog_find, null)
                AlertDialog.Builder(this)
                        .setTitle(R.string.menu_find)
                        .setView(dialogView)
                        .setPositiveButton(R.string.action_apply) { _, _ ->
                            val etFind: EditText = dialogView.findViewById(R.id.edit_find)
                            val cbIgnoreCase: CheckBox = dialogView.findViewById(R.id.cb_ignore_case)

                            if (etFind.text.isEmpty()) return@setPositiveButton

                            updateFabVisibility(etFind.text.string, cbIgnoreCase.isChecked)

                            mAds.showInterstitial()
                        }.setNegativeButton(android.R.string.cancel, null)
                        .show()
            }
            R.id.menu_go_to_line -> {
                val dialogView = layoutInflater.inflate(R.layout.dialog_goto_line, null)
                AlertDialog.Builder(this)
                        .setTitle(R.string.menu_go_to_line)
                        .setView(dialogView)
                        .setPositiveButton(R.string.action_apply) { _, _ ->
                            val etLine: EditText = dialogView.findViewById(R.id.edit_line)

                            codeEditText.goToLine(etLine.text.string.toInt())

                            mAds.showInterstitial()
                        }.setNegativeButton(android.R.string.cancel, null)
                        .show()
            }
            R.id.menu_replace_all -> {
                val dialogView = layoutInflater.inflate(R.layout.dialog_replace, null)
                AlertDialog.Builder(this)
                        .setTitle(R.string.replace_all)
                        .setView(dialogView)
                        .setPositiveButton(R.string.replace_all) { _, _ ->
                            val etFind: EditText = dialogView.findViewById(R.id.edit_find)
                            val etReplace: EditText = dialogView.findViewById(R.id.edit_replace)

                            val newText = codeEditText.text.string.replace(etFind.text.string, etReplace.text.string)
                            codeEditText.setText(newText)

                            mAds.showInterstitial()
                        }.setNegativeButton(android.R.string.cancel, null)
                        .show()
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

        findViewById<DrawerLayout>(R.id.drawer_layout).closeDrawer(GravityCompat.START)
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (data == null) return

        if (requestCode == LOAD_FILE_REQUEST_CODE && resultCode == RESULT_OK) {
            val newFile = File(data.getStringExtra(Chooser.RESULT_PATH))
            if (newFile.length() >= 10485760) { // if the file is bigger than 10 MB
                Toast.makeText(this, R.string.file_too_big, Toast.LENGTH_LONG).show()
                return
            }

            mCurrentFile = newFile

            LoadTask().execute()
        }

        if (requestCode == CHANGE_PATH_REQUEST_CODE && resultCode == RESULT_OK) {
            mDialog?.let {
                it.dismiss()
                saveAs(true, data.getStringExtra(Chooser.RESULT_PATH))
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.size < 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "External Storage permission is required in order for the app to work", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                File(Const.MAIN_FOLDER).mkdir()
                LoadTask().execute()
            }
        }
    }

    /*** Private Functions  ***/
    private fun getFileSize(): String {
        if (!mCurrentFile.isFile) return ""

        val fileSize = mCurrentFile.length().toDouble()

        return when {
            fileSize < 1024 -> fileSize.string + "B"
            fileSize > 1024 && fileSize < 1024 * 1024 -> {
                (Math.round(fileSize / 1024 * 100.0) / 100.0).string + "KB"
            }
            else -> (Math.round(fileSize / (1024 * 1204) * 100.0) / 100.0).string + "MB"
        }
    }

    private fun getFileInfo() = "Size: ${getFileSize()}\nPath: ${mCurrentFile.path}\n"

    private fun saveAs(newFile: Boolean, folderPath: String? = null) {
        val dialogBuilder = AlertDialog.Builder(this)

        dialogBuilder.setTitle(if (newFile) R.string.create_new_file else R.string.menu_save_file_as)

        val dialogView = layoutInflater.inflate(R.layout.dialog_new_file, null)
        dialogBuilder.setView(dialogView)
        mDialog = dialogBuilder.create()

        val etFileName: EditText = dialogView.findViewById(R.id.file_name)
        val textSelectPath: TextView = dialogView.findViewById(R.id.text_select_path)
        val btnCancel: Button = dialogView.findViewById(R.id.button_cancel)
        val btnOk: Button = dialogView.findViewById(R.id.button_ok)

        etFileName.setText(mPreferences.getNewFilesName())

        textSelectPath.text = folderPath ?: Const.MAIN_FOLDER
        textSelectPath.setOnClickListener {
            Chooser(this@MainActivity,
                    requestCode = CHANGE_PATH_REQUEST_CODE,
                    fileExtension = "js",
                    showHiddenFiles = mPreferences.getShowHiddenFiles(),
                    startPath = Const.MAIN_FOLDER,
                    chooserType = Chooser.FOLDER_CHOOSER)
                    .start()
        }

        btnCancel.setOnClickListener {
            mDialog!!.dismiss()
        }

        btnOk.setOnClickListener {
            if (!Pattern.compile("[_a-zA-Z0-9 \\-.]+").matcher(etFileName.text.string).matches()) {
                Toast.makeText(this@MainActivity, R.string.invalid_file_name, Toast.LENGTH_SHORT).show()
                etFileName.error = getString(R.string.invalid_file_name)
                return@setOnClickListener
            }
            etFileName.error = null

            val file = File(textSelectPath.text.string + etFileName.text.string)
            if (!file.exists() && newFile) {
                file.createNewFile()
                Toast.makeText(this@MainActivity, R.string.new_file_created, Toast.LENGTH_SHORT).show()
            }

            mCurrentFile = file

            if (!newFile) {
                SaveTask(false).execute()
                LoadTask().execute()
            }

            mDialog!!.dismiss()
        }

        mDialog!!.show()
    }

    private fun loadDocument(fileContent: String) {
        val chunkSize = 20000

        scrollView.smoothScrollTo(0, 0)

        mLoaded.delete(0, mLoaded.length)
        if (fileContent.length > chunkSize) {
            mLoaded.append(fileContent.substring(0, chunkSize))
            codeEditText.setTextHighlighted(mLoaded)
        } else {
            mLoaded.append(fileContent)
            codeEditText.setTextHighlighted(mLoaded)
        }

        codeEditText.addTextChangedListener(object : TextWatcher {
            private var start = 0
            private var end = 0

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                this.start = start
                this.end = start + count
            }

            override fun afterTextChanged(s: Editable) {
                Handler().postDelayed({
                    applyTabWidth(s, start, end)

                    mCurrentBuffer = codeEditText.text.string
                }, 500)
            }
        })
        mCurrentBuffer = codeEditText.text.string

        supportActionBar?.subtitle = mCurrentFile.name
        scrollView.visibility = View.VISIBLE

        mAds.showInterstitial()
    }

    private fun updateFabVisibility(searchText: String?, ignoreCase: Boolean) {
        val fabPrevious: FloatingActionButton = findViewById(R.id.fab_previous)
        val fabNext: FloatingActionButton = findViewById(R.id.fab_next)
        val fabClose: FloatingActionButton = findViewById(R.id.fab_close)

        if (searchText != null) {
            fabPrevious.show()
            fabNext.show()
            fabClose.show()
            symbolScrollView.visibility = View.GONE

            fabPrevious.setOnClickListener { codeEditText.findPreviousText(searchText, ignoreCase) }
            fabNext.setOnClickListener { codeEditText.findText(searchText, ignoreCase) }
            fabClose.setOnClickListener { updateFabVisibility(null, false) }
        } else {
            fabPrevious.hide(object : FloatingActionButton.OnVisibilityChangedListener() {
                override fun onHidden(fab: FloatingActionButton) {
                    super.onHidden(fab)
                    fab.visibility = View.GONE
                }
            })
            fabNext.hide(object : FloatingActionButton.OnVisibilityChangedListener() {
                override fun onHidden(fab: FloatingActionButton) {
                    super.onHidden(fab)
                    fab.visibility = View.GONE
                }
            })
            fabClose.hide(object : FloatingActionButton.OnVisibilityChangedListener() {
                override fun onHidden(fab: FloatingActionButton) {
                    super.onHidden(fab)
                    fab.visibility = View.GONE
                }
            })
        }
    }

    private fun applyTabWidth(text: Editable, start: Int, end: Int) {
        var newStart = start

        while (newStart < end) {
            val index = text.string.indexOf("\t", newStart)
            if (index < 0)
                break
            text.setSpan(CustomTabWidthSpan(), index, index + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            newStart = index + 1
        }
    }

    /*** Async Classes  ***/
    @SuppressLint("StaticFieldLeak")
    private inner class LoadTask internal constructor() : AsyncTask<Void, Void, String>() {

        override fun doInBackground(vararg paths: Void?): String {
            try {
                val dis = DataInputStream(FileInputStream(mCurrentFile))
                val br = BufferedReader(InputStreamReader(dis))
                try {
                    val sb = StringBuilder()
                    var line = br.readLine()

                    while (line != null) {
                        sb.append(line).append("\n")
                        line = br.readLine()
                    }
                    mFileContent = sb.string
                    return mFileContent
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
            mPreferences.setLastFilePath(mCurrentFile.absolutePath)
        }
    }

    @SuppressLint("StaticFieldLeak")
    private inner class SaveTask internal constructor(private val startConsole: Boolean) : AsyncTask<Void, Void, Void>() {

        override fun doInBackground(vararg voids: Void?): Void? {
            mFileContent = codeEditText.text.string
            mCurrentFile.writeText(mFileContent)

            if (startConsole)
                try {
                    this@MainActivity.saveFileInternally("main.js", mFileContent)
                    this@MainActivity.saveFileInternally("index.html", "<!DOCTYPE html>\n<html>\n<head>\n<script type=\"text/javascript\" src=\"main.js\"></script>\n</head>\n<body>\n</body>\n</html>")
                } catch (e: RuntimeException) {
                    e.printStackTrace()
                }

            return null
        }

        override fun onPostExecute(v: Void?) {
            Toast.makeText(this@MainActivity, R.string.file_saved, Toast.LENGTH_SHORT).show()

            mAds.showInterstitial()

            if (startConsole) startActivity(Intent(this@MainActivity, ConsoleActivity::class.java))
        }
    }
}
