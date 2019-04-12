package net.theluckycoder.sharpide.activities

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.android.synthetic.main.partial_symbols.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.theluckycoder.materialchooser.Chooser
import net.theluckycoder.materialchooser.ChooserType
import net.theluckycoder.sharpide.R
import net.theluckycoder.sharpide.utils.Ads
import net.theluckycoder.sharpide.utils.AppPreferences
import net.theluckycoder.sharpide.utils.Const
import net.theluckycoder.sharpide.utils.UpdateChecker
import net.theluckycoder.sharpide.utils.extensions.alertDialog
import net.theluckycoder.sharpide.utils.extensions.browse
import net.theluckycoder.sharpide.utils.extensions.bundleOf
import net.theluckycoder.sharpide.utils.extensions.checkHasPermission
import net.theluckycoder.sharpide.utils.extensions.containsAny
import net.theluckycoder.sharpide.utils.extensions.inflate
import net.theluckycoder.sharpide.utils.extensions.longToast
import net.theluckycoder.sharpide.utils.extensions.replace
import net.theluckycoder.sharpide.utils.extensions.setTitleWithColor
import net.theluckycoder.sharpide.utils.extensions.startActivity
import net.theluckycoder.sharpide.utils.extensions.toast
import net.theluckycoder.sharpide.utils.extensions.verifyStoragePermission
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

    private val preferences = AppPreferences(this)
    private val ads = Ads(this)
    private var mSaveDialog: AlertDialog? = null
    private lateinit var mCurrentFile: File

    override fun onCreate(savedInstanceState: Bundle?) {
        // Set the Theme
        setTheme(R.style.AppTheme_NoActionBar)
        if (preferences.useDarkTheme) {
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
                val selection = code_editor.selectionStart
                if (selection != -1) {
                    code_editor.text.insert(selection, (view as TextView).text.toString())
                }
            }
        }

        // Set up Drawer Layout
        val toggle = object : ActionBarDrawerToggle(this, drawer_layout, toolbar, 0, 0) {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                super.onDrawerSlide(drawerView, slideOffset)

                UIUtil.hideKeyboard(this@MainActivity)
                code_editor.clearFocus()
            }
        }
        drawer_layout.addDrawerListener(toggle)
        toggle.syncState()

        // Setup Navigation View
        val navigationView = findViewById<NavigationView>(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener(this)
        val header = navigationView.getHeaderView(0)
        header.findViewById<View>(R.id.header_layout_main).setOnClickListener {
            startActivity<AboutActivity>()
            drawer_layout.closeDrawer(GravityCompat.START)
        }
        header.findViewById<View>(R.id.header_image_settings).setOnClickListener {
            startActivity<SettingsActivity>()
            drawer_layout.closeDrawer(GravityCompat.START)
        }

        // Load preferences
        mCurrentFile = File(preferences.newFilesName)
        supportActionBar?.subtitle = mCurrentFile.name

        if (!Build.MANUFACTURER.equals("samsung", true)) {
            code_editor.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        }
        code_editor.textSize = preferences.fontSize.toFloat()
        if (preferences.loadLastFile) {
            val lastFile = File(preferences.lastFilePath)

            if (lastFile.isFile && lastFile.canRead() && checkHasPermission()) {
                mCurrentFile = lastFile
                loadFileAsync()
            }
        }

        // Check for permission
        verifyStoragePermission()
        File(Const.MAIN_FOLDER).mkdirs()

        // Setup Keyboard Checker
        KeyboardVisibilityEvent.setEventListener(this) { isOpen ->
            if (preferences.showSymbolsBar) {
                sv_symbols.visibility = if (isOpen && code_editor.isFocused) View.VISIBLE else View.GONE
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
        ads.loadInterstitial()
    }

    override fun onResume() {
        super.onResume()
        if (preferences.isFullscreen) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }

    override fun onBackPressed() {
        when {
            drawer_layout.isDrawerOpen(GravityCompat.START) -> drawer_layout.closeDrawer(GravityCompat.START)
            preferences.confirmAppQuit -> {
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

        drawer_layout.closeDrawer(GravityCompat.START)
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
            R.id.menu_undo -> code_editor.undo()
            R.id.menu_redo -> code_editor.redo()
            R.id.menu_run -> {
                if (mCurrentFile.path != preferences.newFilesName) {
                    saveFileAsync(true)

                    val params = bundleOf(
                        "size" to mCurrentFile.length(),
                        "lines" to code_editor.lineCount)
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

                        ads.showInterstitial()
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

                        code_editor.goToLine(etLine.text.toString().toInt())

                        ads.showInterstitial()
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

                        val newText = code_editor.text.replace(etFind.text.toString(), etReplace.text.toString())
                        code_editor.setText(newText)

                        ads.showInterstitial()
                    }.setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            R.id.menu_edit_cut -> code_editor.cut()
            R.id.menu_edit_copy -> code_editor.copy()
            R.id.menu_edit_paste -> code_editor.paste()
            R.id.menu_edit_select_line -> code_editor.selectLine()
            R.id.menu_edit_select_all -> code_editor.selectAll()
            R.id.menu_edit_delete_line -> code_editor.deleteLine()
            R.id.menu_edit_duplicate_line -> code_editor.duplicateLine()
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
        "Lines Count: ${code_editor.lineCount}"

    private fun saveFile() {
        if (mCurrentFile.path != preferences.newFilesName) {
            saveFileAsync(false)

            val params = bundleOf(
                "size" to mCurrentFile.length(),
                "lines" to code_editor.lineCount)
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

        etFileName.setText(preferences.newFilesName)

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
            preferences.lastFilePath = mCurrentFile.absolutePath

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
            sv_symbols.visibility = View.GONE

            fabPrevious.setOnClickListener { code_editor.findPreviousText(searchText, ignoreCase) }
            fabNext.setOnClickListener { code_editor.findText(searchText, ignoreCase) }
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
            .setShowHiddenFiles(preferences.showHiddenFiles)
            .setStartPath(Const.MAIN_FOLDER)
            .setNightTheme(preferences.useDarkTheme)
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

        preferences.lastFilePath = mCurrentFile.absolutePath
        code_editor.scrollTo(0, 0)

        val chunkSize = 20000
        val loaded = StringBuilder()

        if (content.length > chunkSize) {
            loaded.append(content.substring(0, chunkSize))
            code_editor.setTextHighlighted(loaded)
        } else {
            loaded.append(content)
            code_editor.setTextHighlighted(loaded)
        }

        supportActionBar?.subtitle = mCurrentFile.name

        if (mCurrentFile.exists()) ads.showInterstitial()
    }

    private fun saveFileAsync(startConsole: Boolean) = GlobalScope.launch(Dispatchers.Main) {
        try {
            val fileContent = code_editor.text.toString()

            withContext(Dispatchers.Default) { mCurrentFile.writeText(fileContent) }
            ads.showInterstitial()
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
