package net.theluckycoder.sharpide.activities

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.NavigationView
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.app.AppCompatDelegate
import android.support.v7.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import net.theluckycoder.materialchooser.Chooser
import net.theluckycoder.sharpide.R
import net.theluckycoder.sharpide.utils.Ads
import net.theluckycoder.sharpide.utils.Const
import net.theluckycoder.sharpide.utils.Const.PERMISSION_REQUEST_CODE
import net.theluckycoder.sharpide.utils.Preferences
import net.theluckycoder.sharpide.utils.UpdateChecker
import net.theluckycoder.sharpide.utils.bind
import net.theluckycoder.sharpide.utils.inflate
import net.theluckycoder.sharpide.utils.ktReplace
import net.theluckycoder.sharpide.utils.lazyFast
import net.theluckycoder.sharpide.utils.saveInternalFile
import net.theluckycoder.sharpide.utils.startActivity
import net.theluckycoder.sharpide.utils.string
import net.theluckycoder.sharpide.utils.verifyStoragePermission
import net.theluckycoder.sharpide.view.CodeEditText
import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEvent
import net.yslibrary.android.keyboardvisibilityevent.util.UIUtil
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStreamReader
import java.util.regex.Pattern

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private companion object {
        private const val LOAD_FILE_REQUEST = 10
        private const val CHANGE_PATH_REQUEST = 11
    }

    private val codeEditText: CodeEditText by bind(R.id.code_editor)
    private val symbolScrollView: HorizontalScrollView by bind(R.id.scroll_symbols)

    private val mAds = Ads(this)
    private var mSaveDialog: AlertDialog? = null
    private lateinit var mCurrentFile: File
    private val mPreferences by lazyFast { Preferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Set the Theme
        setTheme(R.style.AppTheme_NoActionBar)
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("dark_theme", false)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Set Toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Views
        val symbolLayout: LinearLayout = findViewById(R.id.layout_symbols)
        for (i in 0 until symbolLayout.childCount) {
            symbolLayout.getChildAt(i).setOnClickListener { view ->
                codeEditText.text.insert(codeEditText.selectionStart, (view as TextView).text.string)
            }
        }

        // Set up Drawer Layout
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

        // Setup Navigation View
        val navigationView: NavigationView = findViewById(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener(this)
        val header = navigationView.getHeaderView(0)
        header.findViewById<View>(R.id.header_layout_main).setOnClickListener {
            startActivity<AboutActivity>()
            drawer.closeDrawer(GravityCompat.START)
        }
        header.findViewById<View>(R.id.header_image_settings).setOnClickListener {
            startActivity<SettingsActivity>()
            drawer.closeDrawer(GravityCompat.START)
        }

        // Load preferences
        mCurrentFile = File(mPreferences.getNewFilesName())
        supportActionBar?.subtitle = mCurrentFile.name

        codeEditText.textSize = mPreferences.getFontSize().toFloat()
        if (mPreferences.getLoadLastFile()) {
            val lastFilePath = mPreferences.getLastFilePath()
            val lastFile = File(lastFilePath)

            if (lastFilePath != "" && lastFile.isFile && lastFile.canRead()) {
                mCurrentFile = lastFile
                loadFileAsync()
            }
        }

        // Check for permission
        verifyStoragePermission()
        File(Const.MAIN_FOLDER).mkdirs()

        // Setup keyboard checker
        KeyboardVisibilityEvent.setEventListener(this) { isOpen ->
            if (mPreferences.showSymbolsBar()) {
                symbolScrollView.visibility = if (isOpen && codeEditText.isFocused) View.VISIBLE else View.GONE
            }
        }

        UpdateChecker {
            AlertDialog.Builder(this)
                .setTitle(R.string.updater_new_version)
                .setMessage(R.string.updater_new_update_desc)
                .setPositiveButton(R.string.updater_update, { _, _ ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(Const.MARKET_LINK))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                }).setNegativeButton(R.string.updater_no, null)
                .show()
        }

        // Setup ads
        mAds.initAds().loadInterstitial()
    }

    override fun onBackPressed() {
        val drawer: DrawerLayout = findViewById(R.id.drawer_layout)

        when {
            drawer.isDrawerOpen(GravityCompat.START) -> drawer.closeDrawer(GravityCompat.START)
            mPreferences.confirmAppQuit() -> {
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
                if (mCurrentFile.path != mPreferences.getNewFilesName()) {
                    saveFileAsync(false)
                } else {
                    saveAs(false)
                }
            }
            R.id.menu_save_as -> saveAs(false)
            R.id.menu_open -> {
                Chooser(this,
                    requestCode = LOAD_FILE_REQUEST,
                    fileExtension = "js",
                    showHiddenFiles = mPreferences.showHiddenFiles(),
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
                if (mCurrentFile.path != mPreferences.getNewFilesName()) {
                    saveFileAsync(true)
                } else {
                    saveAs(false)
                }
            }
            R.id.menu_minify -> startActivity<MinifyActivity>()
        }

        findViewById<DrawerLayout>(R.id.drawer_layout).closeDrawer(GravityCompat.START)
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        data ?: return

        if (requestCode == LOAD_FILE_REQUEST && resultCode == RESULT_OK) {
            val newFile = File(data.getStringExtra(Chooser.RESULT_PATH))
            if (newFile.length() >= 10485760) { // if the file is bigger than 10 MB
                Toast.makeText(this, R.string.file_too_big, Toast.LENGTH_LONG).show()
                return
            }

            if (newFile.isFile && newFile.canRead()) {
                mCurrentFile = newFile
                loadFileAsync()
            }
        }

        if (requestCode == CHANGE_PATH_REQUEST && resultCode == RESULT_OK) {
            mSaveDialog?.let {
                it.dismiss()
                saveAs(true, data.getStringExtra(Chooser.RESULT_PATH))
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.size < 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "External Storage permission is required in order for the app to work",
                    Toast.LENGTH_SHORT).show()
                finish()
            } else {
                File(Const.MAIN_FOLDER).mkdir()
                loadFileAsync()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_find -> {
                val dialogView = layoutInflater inflate R.layout.dialog_find
                AlertDialog.Builder(this)
                    .setTitle(R.string.menu_find)
                    .setView(dialogView)
                    .setPositiveButton(R.string.action_apply) { _, _ ->
                        val etFind: EditText = dialogView.findViewById(R.id.et_find)
                        val cbIgnoreCase: CheckBox = dialogView.findViewById(R.id.cb_ignore_case)

                        if (etFind.text.isEmpty()) return@setPositiveButton

                        updateFabVisibility(etFind.text.string, cbIgnoreCase.isChecked)

                        mAds.showInterstitial()
                    }.setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            R.id.menu_go_to_line -> {
                val dialogView = layoutInflater inflate R.layout.dialog_goto_line
                AlertDialog.Builder(this)
                    .setTitle(R.string.menu_go_to_line)
                    .setView(dialogView)
                    .setPositiveButton(R.string.action_apply) { _, _ ->
                        val etLine: EditText = dialogView.findViewById(R.id.et_line)

                        if (etLine.text.isEmpty()) return@setPositiveButton

                        codeEditText.goToLine(etLine.text.string.toInt())

                        mAds.showInterstitial()
                    }.setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            R.id.menu_replace_all -> {
                val dialogView = layoutInflater inflate R.layout.dialog_replace
                AlertDialog.Builder(this)
                    .setTitle(R.string.replace_all)
                    .setView(dialogView)
                    .setPositiveButton(R.string.replace_all) { _, _ ->
                        val etFind: EditText = dialogView.findViewById(R.id.et_find)
                        val etReplace: EditText = dialogView.findViewById(R.id.et_replace)

                        if (etFind.text.isEmpty()) return@setPositiveButton

                        val newText = codeEditText.text.string.ktReplace(etFind.text.string, etReplace.text.string)
                        codeEditText.setText(newText)

                        mAds.showInterstitial()
                    }.setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            R.id.menu_edit_cut -> codeEditText.cut()
            R.id.menu_edit_copy -> codeEditText.copy()
            R.id.menu_edit_paste -> codeEditText.paste()
            R.id.menu_edit_select_line -> codeEditText.selectLine()
            R.id.menu_edit_select_all -> codeEditText.selectAll()
        }
        return super.onOptionsItemSelected(item)
    }

    /*** Private Functions  ***/
    private fun getFileSize(): String {
        val fileSize = mCurrentFile.length().toDouble()

        return when {
            fileSize < 1024 -> fileSize.string + "B"
            fileSize > 1024 && fileSize < 1024 * 1024 -> (Math.round(fileSize / 1024 * 100.0) / 100.0).string + "KB"
            else -> (Math.round(fileSize / (1024 * 1204) * 100.0) / 100.0).string + "MB"
        }
    }

    private fun getFileInfo() = "Size: ${getFileSize()}\nPath: ${mCurrentFile.path}\n"

    private fun saveAs(createNewFile: Boolean, folderPath: String? = null) {
        val dialogBuilder = AlertDialog.Builder(this)

        dialogBuilder.setTitle(if (createNewFile) R.string.create_new_file else R.string.menu_save_file_as)

        val dialogView = layoutInflater inflate R.layout.dialog_new_file
        dialogBuilder.setView(dialogView)
        mSaveDialog = dialogBuilder.create()

        val etFileName: EditText = dialogView.findViewById(R.id.et_file_name)
        val textSelectPath: TextView = dialogView.findViewById(R.id.tv_select_path)
        val btnCancel: Button = dialogView.findViewById(R.id.btn_cancel)
        val btnOk: Button = dialogView.findViewById(R.id.btn_ok)

        etFileName.setText(mPreferences.getNewFilesName())

        textSelectPath.text = folderPath ?: Const.MAIN_FOLDER
        textSelectPath.setOnClickListener {
            Chooser(this@MainActivity,
                requestCode = CHANGE_PATH_REQUEST,
                fileExtension = "js",
                showHiddenFiles = mPreferences.showHiddenFiles(),
                startPath = Const.MAIN_FOLDER,
                chooserType = Chooser.FOLDER_CHOOSER)
                .start()
        }

        btnCancel.setOnClickListener {
            mSaveDialog?.dismiss()
        }

        btnOk.setOnClickListener {
            if (!Pattern.compile("[_a-zA-Z0-9 \\-.]+").matcher(etFileName.text.string).matches()) {
                Toast.makeText(this@MainActivity, R.string.invalid_file_name, Toast.LENGTH_SHORT).show()
                etFileName.error = getString(R.string.invalid_file_name)
                return@setOnClickListener
            }

            val file = File(textSelectPath.text.string + etFileName.text.string)
            if (!file.exists() && createNewFile) {
                file.createNewFile()
                Toast.makeText(this@MainActivity, R.string.new_file_created, Toast.LENGTH_SHORT).show()
            }

            mCurrentFile = file
            mPreferences.setLastFilePath(mCurrentFile.absolutePath)

            if (createNewFile) {
                loadFileAsync()
            } else {
                saveFileAsync(false)
            }

            mSaveDialog?.dismiss()
        }

        mSaveDialog?.show()
    }

    private fun loadDocument(fileContent: String) {
        mPreferences.setLastFilePath(mCurrentFile.absolutePath)
        codeEditText.scrollTo(0, 0)

        val chunkSize = 20000
        val loaded = StringBuilder()

        if (fileContent.length > chunkSize) {
            loaded.append(fileContent.substring(0, chunkSize))
            codeEditText.setTextHighlighted(loaded)
        } else {
            loaded.append(fileContent)
            codeEditText.setTextHighlighted(loaded)
        }

        supportActionBar?.subtitle = mCurrentFile.name

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

    private fun loadFileAsync() = async(UI) {
        val job = async(CommonPool) {
            val result = try {
                val dis = DataInputStream(mCurrentFile.inputStream())
                val br = BufferedReader(InputStreamReader(dis))
                val builder = StringBuilder()

                try {
                    var line = br.readLine()

                    while (line != null) {
                        builder.append(line).append("\n")
                        line = br.readLine()
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                } finally {
                    try {
                        br.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
                builder.string
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
                ""
            }
            result
        }

        loadDocument(job.await())
    }

    private fun saveFileAsync(startConsole: Boolean) = async(UI) {
        val fileContent = codeEditText.text.string

        val job = async(CommonPool) {
            mCurrentFile.writeText(fileContent)

            if (startConsole) {
                this@MainActivity.saveInternalFile("main.js", fileContent)
                this@MainActivity.saveInternalFile("index.html", "<!DOCTYPE html>\n<html>\n<head>\n" +
                    "<script type=\"text/javascript\" src=\"main.js\"></script>\n</head>\n<body>\n</body>\n</html>\n")
            }
        }

        job.await()

        mAds.showInterstitial()

        Toast.makeText(this@MainActivity, R.string.file_saved, Toast.LENGTH_SHORT).show()
        if (startConsole) this@MainActivity.startActivity<ConsoleActivity>()
    }
}
