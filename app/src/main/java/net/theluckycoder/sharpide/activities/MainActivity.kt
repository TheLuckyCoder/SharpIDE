package net.theluckycoder.sharpide.activities

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager
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
import android.widget.*
import net.theluckycoder.materialchooser.Chooser
import net.theluckycoder.sharpide.R
import net.theluckycoder.sharpide.listener.OnBottomReachedListener
import net.theluckycoder.sharpide.utils.*
import net.theluckycoder.sharpide.utils.Constants.PERMISSION_REQUEST_CODE
import net.theluckycoder.sharpide.view.CodeEditText
import net.theluckycoder.sharpide.view.InteractiveScrollView
import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEvent
import java.io.*
import java.util.regex.Pattern


class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener, UpdateChecker.OnUpdateNeededListener {

    private companion object {
        private const val CHUNK = 20000
        private const val LOAD_FILE_REQUEST_CODE = 150
        private const val CHANGE_PATH_REQUEST_CODE = 151
    }

    private val codeEditText: CodeEditText by bind(R.id.edit_main)
    private val scrollView: InteractiveScrollView by bind(R.id.main_scroll_view)
    private val symbolScrollView: HorizontalScrollView by bind(R.id.symbolScrollView)

    private val mAds = Ads(this)
    private var mDialog: AlertDialog? = null
    private lateinit var mCurrentFile: File
    private var mFileContent = ""
    private var mCurrentBuffer = ""
    private val mLoaded = StringBuilder()
    private var mLastSavePath = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme_NoActionBar)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Views
        val symbolLayout: LinearLayout = findViewById(R.id.layout_symbols)
        for (i in 0 until symbolLayout.childCount)
            symbolLayout.getChildAt(i).setOnClickListener({ codeEditText.text.insert(codeEditText.selectionStart, (it as TextView).text.string) })

        scrollView.setOnBottomReachedListener(null)

        // Set up navigation drawer
        val drawer: DrawerLayout = findViewById(R.id.drawer_layout)
        val toggle = ActionBarDrawerToggle(this, drawer, toolbar, 0, 0)
        drawer.addDrawerListener(toggle)
        toggle.syncState()

        val navigationView: NavigationView = findViewById(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener(this)

        // Load preferences
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)

        mCurrentFile = File(Constants.mainFolder + preferences.getString("new_files_name", "New File") + ".js")
        supportActionBar?.subtitle = mCurrentFile.name

        codeEditText.textSize = Integer.parseInt(preferences.getString("font_size", "16")).toFloat()
        if (preferences.getBoolean("load_last_file", true)) {
            val lastFilePath = preferences.getString("last_file_path", "")
            val lastFile = File(lastFilePath)

            if (lastFilePath != "" && lastFile.isFile && lastFile.canRead()) {
                mCurrentFile = lastFile
                LoadTask().execute()
            }
        }

        mLastSavePath = preferences.getString("last_save_path", Constants.mainFolder)

        // Check for permission
        verifyStoragePermission()
        File(Constants.mainFolder).mkdirs()

        // Setup keyboard checker
        KeyboardVisibilityEvent.setEventListener(this) { isOpen ->
            if (PreferenceManager.getDefaultSharedPreferences(this@MainActivity).getBoolean("show_symbols_bar", true)) {
                symbolScrollView.visibility = if (isOpen) View.VISIBLE else View.GONE
            }
        }

        // Setup UpdateChecker
        UpdateChecker(this).check()

        // Setup ads
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
            PreferenceManager.getDefaultSharedPreferences(this).getBoolean("quit_confirm", true) -> AlertDialog.Builder(this)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.quit_confirm)
                    .setPositiveButton(android.R.string.yes) { _, _ -> finish() }
                    .setNegativeButton(android.R.string.no, null)
                    .show()
            else -> return super.onBackPressed()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_save -> SaveTask(false).execute()
            R.id.menu_save_as -> saveAs(false)
            R.id.menu_open -> openFileClick()
            R.id.menu_new -> saveAs(true)
            R.id.menu_file_info -> {
                AlertDialog.Builder(this)
                        .setTitle(R.string.menu_file_info)
                        .setMessage(getFileInfo())
                        .setNeutralButton(R.string.action_close, null)
                        .show()
            }
            R.id.menu_run -> SaveTask(true).execute()
            R.id.menu_find -> {
                val dialogView = layoutInflater.inflate(R.layout.dialog_find, null)
                AlertDialog.Builder(this)
                        .setTitle(R.string.menu_find)
                        .setView(dialogView)
                        .setPositiveButton(R.string.action_apply) { _, _ ->
                            val etFind: EditText = dialogView.findViewById(R.id.edit_find)
                            val cbIgnoreCase: CheckBox = dialogView.findViewById(R.id.cb_ignore_case)

                            updateFabVisibility(etFind.text.string, cbIgnoreCase.isChecked, true)

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
            mLastSavePath = data.getStringExtra(Chooser.RESULT_PATH)
            mDialog?.let {
                it.dismiss()
                saveAs(true)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.size < 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "External Storage permission is required in order for the app to work", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                File(Constants.mainFolder).mkdir()
                LoadTask().execute()
            }
        }
    }

    /*** Private Functions  ***/
    private fun getFileSize(): String {
        val fileSize: Double
        if (mCurrentFile.isFile) {
            fileSize = mCurrentFile.length().toDouble()

            return when {
                fileSize < 1024 -> fileSize.string + "B"
                fileSize > 1024 && fileSize < 1024 * 1024 -> (Math.round(fileSize / 1024 * 100.0) / 100.0).string + "KB"
                else -> (Math.round(fileSize / (1024 * 1204) * 100.0) / 100.0).string + "MB"
            }
        }
        return ""
    }

    private fun getFileInfo() = "Size: ${getFileSize()}\nPath: ${mCurrentFile.path}\n"

    private fun openFileClick() {
        Chooser(this, LOAD_FILE_REQUEST_CODE,
                fileExtension = "js",
                showHiddenFiles = PreferenceManager.getDefaultSharedPreferences(this)
                        .getBoolean("show_hidden_files", false),
                startPath = mLastSavePath)
                .start()
    }

    private fun saveAs(newFile: Boolean) {
        val dialogBuilder = AlertDialog.Builder(this)

        if (newFile) {
            dialogBuilder.setTitle(R.string.create_new_file)
        } else {
            dialogBuilder.setTitle(R.string.menu_save_file_as)
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_new_file, null)
        dialogBuilder.setView(dialogView)
        mDialog = dialogBuilder.create()

        val etFileName: EditText = dialogView.findViewById(R.id.file_name)
        val textSelectPath: TextView = dialogView.findViewById(R.id.text_select_path)
        val btnOk: Button = dialogView.findViewById(R.id.button_ok)

        etFileName.setText(PreferenceManager.getDefaultSharedPreferences(this).getString("new_files_name", getString(R.string.menu_new_file)) + ".js")

        textSelectPath.text = mLastSavePath
        textSelectPath.setOnClickListener {
            Chooser(this@MainActivity, CHANGE_PATH_REQUEST_CODE,
                    fileExtension = "js",
                    showHiddenFiles = PreferenceManager.getDefaultSharedPreferences(this)
                            .getBoolean("show_hidden_files", false),
                    startPath = Constants.mainFolder,
                    chooserType = Chooser.FOLDER_CHOOSER)
                    .start()
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
                file.writeText("")
                Toast.makeText(this@MainActivity, R.string.new_file_created, Toast.LENGTH_SHORT).show()
            }

            mCurrentFile = file

            if (!newFile) {
                SaveTask(false).execute()
                LoadTask().execute()
            }

        }

        mDialog!!.show()
    }

    private fun loadInChunks(scrollView: InteractiveScrollView, bigString: String) {
        mLoaded.append(bigString.substring(0, CHUNK))
        codeEditText.setTextHighlighted(mLoaded)
        scrollView.setOnBottomReachedListener(object : OnBottomReachedListener {
            override fun onBottomReached() {
                val length = mLoaded.length
                when {
                    length >= bigString.length -> return
                    length + CHUNK > bigString.length -> {
                        val buffer = bigString.substring(length, bigString.length)
                        mLoaded.append(buffer)
                    }
                    else -> {
                        val buffer = bigString.substring(length, length + CHUNK)
                        mLoaded.append(buffer)
                    }
                }

                codeEditText.setTextHighlighted(mLoaded)
            }
        })
    }

    private fun loadDocument(fileContent: String) {
        scrollView.smoothScrollTo(0, 0)

        mLoaded.delete(0, mLoaded.length)
        if (fileContent.length > CHUNK) {
            loadInChunks(scrollView, fileContent)
        } else {
            mLoaded.append(fileContent)
            codeEditText.setTextHighlighted(mLoaded)
        }

        codeEditText.addTextChangedListener(object : TextWatcher {
            private var start = 0
            private var end = 0

            override fun beforeTextChanged(p1: CharSequence, p2: Int, p3: Int, p4: Int) = Unit

            override fun onTextChanged(p1: CharSequence, start: Int, before: Int, count: Int) {
                this.start = start
                this.end = start + count
            }

            override fun afterTextChanged(p1: Editable) {
                Handler().postDelayed({
                    applyTabWidth(p1, start, end)

                    mCurrentBuffer = codeEditText.text.string
                }, 500)
            }
        })
        mCurrentBuffer = codeEditText.text.string

        supportActionBar?.subtitle = mCurrentFile.name
        scrollView.visibility = View.VISIBLE

        mAds.showInterstitial()
    }

    private fun updateFabVisibility(searchText: String, ignoreCase: Boolean, visible: Boolean) {
        val fabPrevious: FloatingActionButton = findViewById(R.id.fab_previous)
        val fabNext: FloatingActionButton = findViewById(R.id.fab_next)
        val fabClose: FloatingActionButton = findViewById(R.id.fab_close)

        if (visible) {
            fabPrevious.show()
            fabNext.show()
            fabClose.show()
            symbolScrollView.visibility = View.GONE
        } else {
            fabPrevious.hide(object : FloatingActionButton.OnVisibilityChangedListener() {
                override fun onHidden(fab: FloatingActionButton?) {
                    super.onHidden(fab)
                    fab?.visibility = View.GONE
                }
            })
            fabNext.hide(object : FloatingActionButton.OnVisibilityChangedListener() {
                override fun onHidden(fab: FloatingActionButton?) {
                    super.onHidden(fab)
                    fab?.visibility = View.GONE
                }
            })
            fabClose.hide(object : FloatingActionButton.OnVisibilityChangedListener() {
                override fun onHidden(fab: FloatingActionButton?) {
                    super.onHidden(fab)
                    fab?.visibility = View.GONE
                }
            })
            return
        }


        fabPrevious.setOnClickListener { codeEditText.findPreviousText(searchText, ignoreCase) }
        fabNext.setOnClickListener { codeEditText.findText(searchText, ignoreCase) }
        fabClose.setOnClickListener { updateFabVisibility("", false, false) }
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
                val fis = FileInputStream(mCurrentFile)
                val dis = DataInputStream(fis)
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
            val editor = PreferenceManager.getDefaultSharedPreferences(this@MainActivity).edit()
            editor.putString("last_file_path", mCurrentFile.absolutePath)
            editor.apply()
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
