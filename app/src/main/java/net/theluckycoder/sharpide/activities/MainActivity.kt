package net.theluckycoder.sharpide.activities

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.NavigationView
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.app.AppCompatDelegate
import android.support.v7.widget.Toolbar
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewStub
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.android.Main
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import net.theluckycoder.materialchooser.Chooser
import net.theluckycoder.materialchooser.ChooserType
import net.theluckycoder.sharpide.R
import net.theluckycoder.sharpide.utils.Ads
import net.theluckycoder.sharpide.utils.Const
import net.theluckycoder.sharpide.utils.AppPreferences
import net.theluckycoder.sharpide.utils.UpdateChecker
import net.theluckycoder.sharpide.utils.extensions.alertDialog
import net.theluckycoder.sharpide.utils.extensions.bind
import net.theluckycoder.sharpide.utils.extensions.browse
import net.theluckycoder.sharpide.utils.extensions.bundleOf
import net.theluckycoder.sharpide.utils.extensions.containsAny
import net.theluckycoder.sharpide.utils.extensions.inflate
import net.theluckycoder.sharpide.utils.extensions.ktReplace
import net.theluckycoder.sharpide.utils.extensions.longToast
import net.theluckycoder.sharpide.utils.extensions.setTitleWithColor
import net.theluckycoder.sharpide.utils.extensions.startActivity
import net.theluckycoder.sharpide.utils.extensions.toast
import net.theluckycoder.sharpide.utils.extensions.verifyStoragePermission
import net.theluckycoder.sharpide.view.CodeEditor
import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEvent
import net.yslibrary.android.keyboardvisibilityevent.util.UIUtil
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private companion object {
        private const val LOAD_FILE_REQUEST = 10
        private const val CHANGE_PATH_REQUEST = 11
    }

    private val mDrawerLayout by bind<DrawerLayout>(R.id.drawer_layout)
    private val mCodeEditor by bind<CodeEditor>(R.id.code_editor)
    private val mSymbolScrollView by bind<HorizontalScrollView>(R.id.sv_symbols)

    private val mPreferences = AppPreferences(this)
    private val mAds = Ads(this)
    private var mSaveDialog: AlertDialog? = null
    private lateinit var mCurrentFile: File

    override fun onCreate(savedInstanceState: Bundle?) {
        // Set the Theme
        setTheme(R.style.AppTheme_NoActionBar)
        if (mPreferences.useDarkTheme) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Set Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Views
        val symbolLayout = findViewById<LinearLayout>(R.id.layout_symbols)
        for (i in 0 until symbolLayout.childCount) {
            symbolLayout.getChildAt(i).setOnClickListener { view ->
                val selection = mCodeEditor.selectionStart
                if (selection != -1) {
                    mCodeEditor.text.insert(selection, (view as TextView).text.toString())
                }
            }
        }

        // Set up Drawer Layout
        val toggle = object : ActionBarDrawerToggle(this, mDrawerLayout, toolbar, 0, 0) {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                super.onDrawerSlide(drawerView, slideOffset)

                UIUtil.hideKeyboard(this@MainActivity)
                mCodeEditor.clearFocus()
            }
        }
        mDrawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Setup Navigation View
        val navigationView = findViewById<NavigationView>(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener(this)
        val header = navigationView.getHeaderView(0)
        header.findViewById<View>(R.id.header_layout_main).setOnClickListener {
            startActivity<AboutActivity>()
            mDrawerLayout.closeDrawer(GravityCompat.START)
        }
        header.findViewById<View>(R.id.header_image_settings).setOnClickListener {
            startActivity<SettingsActivity>()
            mDrawerLayout.closeDrawer(GravityCompat.START)
        }

        // Load preferences
        mCurrentFile = File(mPreferences.newFilesName)
        supportActionBar?.subtitle = mCurrentFile.name

        if (!Build.MANUFACTURER.equals("samsung", true)) {
            mCodeEditor.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        }
        mCodeEditor.textSize = mPreferences.fontSize.toFloat()
        if (mPreferences.loadLastFile) {
            val lastFile = File(mPreferences.lastFilePath)

            if (lastFile.isFile && lastFile.canRead()) {
                mCurrentFile = lastFile
                loadFileAsync()
            }
        }

        // Check for permission
        verifyStoragePermission()
        File(Const.MAIN_FOLDER).mkdirs()

        // Setup Keyboard Checker
        KeyboardVisibilityEvent.setEventListener(this) { isOpen ->
            if (mPreferences.showSymbolsBar) {
                mSymbolScrollView.visibility = if (isOpen && mCodeEditor.isFocused) View.VISIBLE else View.GONE
            }
        }

        // Setup Update Checker
        UpdateChecker {
            alertDialog(R.style.AppTheme_Dialog)
                .setTitleWithColor(R.string.updater_new_update, R.color.textColorPrimary)
                .setMessage(R.string.updater_new_update_desc)
                .setPositiveButton(R.string.updater_update) { _, _ ->
                    browse(Const.MARKET_LINK, true)
                }.setNegativeButton(R.string.updater_no, null)
                .show()
        }

        // Setup ads
        mAds.loadInterstitial()
    }

    override fun onResume() {
        super.onResume()
        if (mPreferences.isFullscreen) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }

    override fun onBackPressed() {
        when {
            mDrawerLayout.isDrawerOpen(GravityCompat.START) -> mDrawerLayout.closeDrawer(GravityCompat.START)
            mPreferences.confirmAppQuit -> {
                alertDialog(R.style.AppTheme_Dialog)
                    .setTitleWithColor(R.string.app_name, R.color.textColorPrimary)
                    .setMessage(R.string.quit_confirm)
                    .setPositiveButton(android.R.string.yes) { _, _ ->
                        finish()
                    }.setNegativeButton(android.R.string.no, null)
                    .show()
            }
            else -> return super.onBackPressed()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_save -> saveFile()
            R.id.menu_save_as -> saveFileAs(false)
            R.id.menu_open -> openChooser(LOAD_FILE_REQUEST, Chooser.FILE_CHOOSER)
            R.id.menu_new -> saveFileAs(true)
            R.id.menu_file_info -> {
                if (!mCurrentFile.exists()) {
                    longToast(R.string.error_file_not_saved)
                    return true
                }

                alertDialog(R.style.AppTheme_Dialog)
                    .setTitleWithColor(R.string.menu_file_info, R.color.textColorPrimary)
                    .setMessage(getFileInfo())
                    .setPositiveButton(R.string.action_close, null)
                    .show()
            }
            R.id.menu_minify -> startActivity<MinifyActivity>()
        }

        mDrawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        data ?: return

        if (requestCode == LOAD_FILE_REQUEST && resultCode == RESULT_OK) {
            val newFile = File(data.getStringExtra(Chooser.RESULT_PATH))
            if (newFile.length() >= 5242880) { // if the file is bigger than 5 MB
                longToast(R.string.file_too_big)
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
                saveFileAs(true, data.getStringExtra(Chooser.RESULT_PATH))
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == Const.PERMISSION_REQUEST_CODE) {
            if (grantResults.size < 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                longToast(R.string.permission_required)
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
            R.id.menu_undo -> mCodeEditor.undo()
            R.id.menu_redo -> mCodeEditor.redo()
            R.id.menu_run -> {
                if (mCurrentFile.path != mPreferences.newFilesName) {
                    saveFileAsync(true)

                    val params = bundleOf(
                        "size" to mCurrentFile.length(),
                        "lines" to mCodeEditor.lineCount)
                    FirebaseAnalytics.getInstance(this).logEvent("file_run", params)
                } else {
                    saveFileAs(false)
                }
            }
            R.id.menu_find -> {
                val dialogView = inflate(R.layout.dialog_find)

                alertDialog(R.style.AppTheme_Dialog)
                    .setTitleWithColor(R.string.menu_find, R.color.textColorPrimary)
                    .setView(dialogView)
                    .setPositiveButton(R.string.action_apply) { _, _ ->
                        val etFind: EditText = dialogView.findViewById(R.id.et_find)
                        val cbIgnoreCase: CheckBox = dialogView.findViewById(R.id.cb_ignore_case)

                        if (etFind.text.isEmpty()) return@setPositiveButton

                        updateSearchFabVisibility(etFind.text.toString(), cbIgnoreCase.isChecked)

                        mAds.showInterstitial()
                    }.setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            R.id.menu_go_to_line -> {
                val dialogView = inflate(R.layout.dialog_goto_line)

                alertDialog(R.style.AppTheme_Dialog)
                    .setTitle(R.string.menu_go_to_line)
                    .setView(dialogView)
                    .setPositiveButton(R.string.action_apply) { _, _ ->
                        val etLine: EditText = dialogView.findViewById(R.id.et_line)

                        if (etLine.text.isEmpty()) return@setPositiveButton

                        mCodeEditor.goToLine(etLine.text.toString().toInt())

                        mAds.showInterstitial()
                    }.setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            R.id.menu_replace_all -> {
                val dialogView = inflate(R.layout.dialog_replace)

                alertDialog(R.style.AppTheme_Dialog)
                    .setTitleWithColor(R.string.replace_all, R.color.textColorPrimary)
                    .setTitle(R.string.replace_all)
                    .setView(dialogView)
                    .setPositiveButton(R.string.replace_all) { _, _ ->
                        val etFind: EditText = dialogView.findViewById(R.id.et_find)
                        val etReplace: EditText = dialogView.findViewById(R.id.et_replace)

                        if (etFind.text.isEmpty()) return@setPositiveButton

                        val newText = mCodeEditor.text.ktReplace(etFind.text.toString(), etReplace.text.toString())
                        mCodeEditor.setText(newText)

                        mAds.showInterstitial()
                    }.setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            R.id.menu_edit_cut -> mCodeEditor.cut()
            R.id.menu_edit_copy -> mCodeEditor.copy()
            R.id.menu_edit_paste -> mCodeEditor.paste()
            R.id.menu_edit_select_line -> mCodeEditor.selectLine()
            R.id.menu_edit_select_all -> mCodeEditor.selectAll()
            R.id.menu_edit_delete_line -> mCodeEditor.deleteLine()
            R.id.menu_edit_duplicate_line -> mCodeEditor.duplicateLine()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isCtrlPressed) {
            return when (keyCode) {
                KeyEvent.KEYCODE_S -> {
                    if (!event.isShiftPressed) {
                        saveFile()
                    } else {
                        saveFileAs(false)
                    }
                    true
                }
                KeyEvent.KEYCODE_O -> {
                    openChooser(LOAD_FILE_REQUEST, Chooser.FILE_CHOOSER)
                    true
                }
                else -> super.onKeyDown(keyCode, event)
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // region Private Functions
    private fun getFileSize(): String {
        val fileSize = mCurrentFile.length().toDouble()

        return when {
            fileSize < 1024 -> "${fileSize}B"
            fileSize > 1024 && fileSize < 1024 * 1024 -> "${(Math.round(fileSize / 1024 * 100.0) / 100.0)}KB"
            else -> "${(Math.round(fileSize / (1024 * 1204) * 100.0) / 100.0)}MB"
        }
    }

    private fun getFileInfo(): String = "Name: ${mCurrentFile.nameWithoutExtension}\n" +
        "Path: ${mCurrentFile.parent}\n" +
        "Last Modified: ${SimpleDateFormat.getDateTimeInstance().format(Date(mCurrentFile.lastModified()))}\n" +
        "Size: ${getFileSize()}\n" +
        "Lines Count: ${mCodeEditor.lineCount}"

    private fun saveFile() {
        if (mCurrentFile.path != mPreferences.newFilesName) {
            saveFileAsync(false)

            val params = bundleOf(
                "size" to mCurrentFile.length(),
                "lines" to mCodeEditor.lineCount)
            FirebaseAnalytics.getInstance(this).logEvent("file_save", params)
        } else {
            saveFileAs(false)
        }
    }

    private fun saveFileAs(createNewFile: Boolean, folderPath: String? = null) {
        val dialogView = inflate(R.layout.dialog_new_file)
        val title = if (createNewFile) R.string.create_new_file else R.string.menu_save_file_as

        mSaveDialog = alertDialog(R.style.AppTheme_Dialog)
            .setTitleWithColor(title, R.color.textColorPrimary)
            .setView(dialogView)
            .show()

        val etFileName: EditText = dialogView.findViewById(R.id.et_file_name)
        val tvSelectPath: TextView = dialogView.findViewById(R.id.tv_select_path)
        val btnCancel: Button = dialogView.findViewById(R.id.btn_cancel)
        val btnOk: Button = dialogView.findViewById(R.id.btn_ok)

        etFileName.setText(mPreferences.newFilesName)

        tvSelectPath.text = folderPath ?: Const.MAIN_FOLDER
        tvSelectPath.setOnClickListener {
            openChooser(CHANGE_PATH_REQUEST, Chooser.FOLDER_CHOOSER)
        }

        btnCancel.setOnClickListener {
            mSaveDialog?.dismiss()
        }

        btnOk.setOnClickListener {
            if (etFileName.text.containsAny("|\\?*<\":>+[]/'".toCharArray())) {
                toast(R.string.invalid_file_name)
                etFileName.error = getString(R.string.invalid_file_name)
                return@setOnClickListener
            }

            val file = File(tvSelectPath.text.toString() + etFileName.text.toString())
            if (!file.exists() && createNewFile) {
                try {
                    file.createNewFile()
                    toast(R.string.new_file_created)
                } catch (e: IOException) {
                    e.printStackTrace()
                    toast(R.string.error)
                }
            }

            mCurrentFile = file
            mPreferences.lastFilePath = mCurrentFile.absolutePath

            if (createNewFile) {
                loadFileAsync()
            } else {
                saveFileAsync(false)
                supportActionBar?.subtitle = mCurrentFile.name
            }

            mSaveDialog?.dismiss()
        }

        mSaveDialog?.show()
    }

    private fun updateSearchFabVisibility(searchText: String?, ignoreCase: Boolean) {
        findViewById<ViewStub>(R.id.stub_fabs)?.inflate()

        val fabPrevious: FloatingActionButton = findViewById(R.id.fab_previous)
        val fabNext: FloatingActionButton = findViewById(R.id.fab_next)
        val fabClose: FloatingActionButton = findViewById(R.id.fab_close)

        if (searchText != null) {
            fabPrevious.show()
            fabNext.show()
            fabClose.show()
            mSymbolScrollView.visibility = View.GONE

            fabPrevious.setOnClickListener { mCodeEditor.findPreviousText(searchText, ignoreCase) }
            fabNext.setOnClickListener { mCodeEditor.findText(searchText, ignoreCase) }
            fabClose.setOnClickListener { updateSearchFabVisibility(null, false) }
        } else {
            fabPrevious.hide(object : FloatingActionButton.OnVisibilityChangedListener() {
                override fun onHidden(fab: FloatingActionButton) {
                    (fab.parent as View).visibility = View.GONE
                }
            })
            fabNext.hide(object : FloatingActionButton.OnVisibilityChangedListener() {
                override fun onHidden(fab: FloatingActionButton) {
                    (fab.parent as View).visibility = View.GONE
                }
            })
            fabClose.hide(object : FloatingActionButton.OnVisibilityChangedListener() {
                override fun onHidden(fab: FloatingActionButton) {
                    (fab.parent as View).visibility = View.GONE
                }
            })
        }
    }

    private fun openChooser(requestCode: Int, @ChooserType chooserType: Int) {
        val extension = if (chooserType == Chooser.FILE_CHOOSER) "js" else ""

        Chooser(this, requestCode)
            .setFileExtension(extension)
            .setShowHiddenFiles(mPreferences.showHiddenFiles)
            .setStartPath(Const.MAIN_FOLDER)
            .setNightTheme(mPreferences.useDarkTheme)
            .setChooserType(chooserType)
            .start()
    }

    private fun loadFileAsync() = GlobalScope.launch(Dispatchers.Main) {
        val content = try {
            withContext(Dispatchers.Default) { mCurrentFile.readText() }
        } catch (e: Exception) {
            e.printStackTrace()
            toast(R.string.error)
            return@launch
        }

        mPreferences.lastFilePath = mCurrentFile.absolutePath
        mCodeEditor.scrollTo(0, 0)

        val chunkSize = 20000
        val loaded = StringBuilder()

        if (content.length > chunkSize) {
            loaded.append(content.substring(0, chunkSize))
            mCodeEditor.setTextHighlighted(loaded)
        } else {
            loaded.append(content)
            mCodeEditor.setTextHighlighted(loaded)
        }

        supportActionBar?.subtitle = mCurrentFile.name

        if (mCurrentFile.exists()) mAds.showInterstitial()
    }

    private fun saveFileAsync(startConsole: Boolean) = GlobalScope.launch(Dispatchers.Main) {
        try {
            val fileContent = mCodeEditor.text.toString()

            withContext(Dispatchers.Default) { mCurrentFile.writeText(fileContent) }
            mAds.showInterstitial()
            toast(R.string.file_saved)

            if (startConsole) {
                saveConsoleFiles(fileContent).await()
                startActivity<ConsoleActivity>()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            toast(R.string.error)
        }
    }

    @Throws(IOException::class)
    private fun saveConsoleFiles(fileContent: String) = GlobalScope.async {
        openFileOutput("main.js", Context.MODE_PRIVATE).use {
            it.write(fileContent.toByteArray())
        }

        if (!fileList().contains("index.html")) {
            openFileOutput("index.html", Context.MODE_PRIVATE).use {
                val content = "<!DOCTYPE html><html><head>" +
                    "<script type=\"text/javascript\" src=\"main.js\"></script>" +
                    "</head><body></body></html>"
                it.write(content.toByteArray())
            }
        }
    }

    // endregion Private Functions
}
