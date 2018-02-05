package net.theluckycoder.sharpide.activities

import android.content.Intent
import android.content.pm.PackageManager
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
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import net.theluckycoder.materialchooser.Chooser
import net.theluckycoder.sharpide.R
import net.theluckycoder.sharpide.utils.Ads
import net.theluckycoder.sharpide.utils.Const
import net.theluckycoder.sharpide.utils.Preferences
import net.theluckycoder.sharpide.utils.UpdateChecker
import net.theluckycoder.sharpide.utils.bind
import net.theluckycoder.sharpide.utils.inflate
import net.theluckycoder.sharpide.utils.ktReplace
import net.theluckycoder.sharpide.utils.lazyFast
import net.theluckycoder.sharpide.utils.saveInternalFile
import net.theluckycoder.sharpide.utils.string
import net.theluckycoder.sharpide.utils.verifyStoragePermission
import net.theluckycoder.sharpide.view.CodeEditText
import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEvent
import net.yslibrary.android.keyboardvisibilityevent.util.UIUtil
import org.jetbrains.anko.alert
import org.jetbrains.anko.appcompat.v7.Appcompat
import org.jetbrains.anko.browse
import org.jetbrains.anko.longToast
import org.jetbrains.anko.startActivity
import org.jetbrains.anko.toast
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.regex.Pattern

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private companion object {
        private const val LOAD_FILE_REQUEST = 10
        private const val CHANGE_PATH_REQUEST = 11
    }

    private val codeEditText: CodeEditText by bind(R.id.code_editor)
    private val symbolScrollView: HorizontalScrollView by bind(R.id.sv_symbols)

    private val mFirebaseAnalytics by lazyFast { FirebaseAnalytics.getInstance(this) }
    private val mPreferences by lazyFast { Preferences(this) }
    private val mAds = Ads(this)
    private var mSaveDialog: AlertDialog? = null
    private lateinit var mCurrentFile: File

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
            val lastFile = File(mPreferences.getLastFilePath())

            if (lastFile.isFile && lastFile.canRead()) {
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
            alert(Appcompat, R.string.updater_new_update_desc, R.string.updater_new_version) {
                positiveButton(R.string.updater_update) {
                    browse(Const.MARKET_LINK, true)
                }
                negativeButton(R.string.updater_no) {}
            }.show()
        }

        // Setup ads
        mAds.initAds().loadInterstitial()
    }

    override fun onBackPressed() {
        val drawer: DrawerLayout = findViewById(R.id.drawer_layout)

        when {
            drawer.isDrawerOpen(GravityCompat.START) -> drawer.closeDrawer(GravityCompat.START)
            mPreferences.confirmAppQuit() -> {
                alert(Appcompat, R.string.quit_confirm, R.string.app_name) {
                    positiveButton(android.R.string.yes) { finish() }
                    negativeButton(android.R.string.no) {}
                }.show()
            }
            else -> return super.onBackPressed()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_save -> {
                if (mCurrentFile.path != mPreferences.getNewFilesName()) {
                    saveFileAsync(false)
                    val params = Bundle().apply {
                        putLong("size", mCurrentFile.length())
                        putInt("lines", codeEditText.lineCount)
                    }
                    mFirebaseAnalytics.logEvent("file_save", params)
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
                if (!mCurrentFile.exists()) {
                    longToast("File is not saved")
                    return true
                }
                alert(Appcompat, getFileInfo(), getString(R.string.menu_file_info)) {
                    positiveButton(R.string.action_close) {}
                }.show()
            }
            R.id.menu_run -> {
                if (mCurrentFile.path != mPreferences.getNewFilesName()) {
                    saveFileAsync(true)
                    val params = Bundle().apply {
                        putLong("size", mCurrentFile.length())
                        putInt("lines", codeEditText.lineCount)
                    }
                    mFirebaseAnalytics.logEvent("file_run", params)
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
                saveAs(true, data.getStringExtra(Chooser.RESULT_PATH))
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
            R.id.menu_find -> {
                val dialogView = layoutInflater inflate R.layout.dialog_find
                alert(Appcompat) {
                    setTitle(R.string.menu_find)
                    customView = dialogView
                    positiveButton(R.string.action_apply) {
                        val etFind: EditText = dialogView.findViewById(R.id.et_find)
                        val cbIgnoreCase: CheckBox = dialogView.findViewById(R.id.cb_ignore_case)

                        if (etFind.text.isEmpty()) return@positiveButton

                        updateFabVisibility(etFind.text.string, cbIgnoreCase.isChecked)

                        mAds.showInterstitial()
                    }
                    negativeButton(android.R.string.cancel) {}
                }.show()
            }
            R.id.menu_go_to_line -> {
                val dialogView = layoutInflater inflate R.layout.dialog_goto_line
                alert(Appcompat) {
                    setTitle(R.string.menu_go_to_line)
                    customView = dialogView
                    positiveButton(R.string.action_apply) {
                        val etLine: EditText = dialogView.findViewById(R.id.et_line)

                        if (etLine.text.isEmpty()) return@positiveButton

                        codeEditText.goToLine(etLine.text.string.toInt())

                        mAds.showInterstitial()
                    }
                    negativeButton(android.R.string.cancel) {}
                }.show()
            }
            R.id.menu_replace_all -> {
                val dialogView = layoutInflater inflate R.layout.dialog_replace
                alert(Appcompat) {
                    setTitle(R.string.replace_all)
                    customView = dialogView
                    positiveButton(R.string.replace_all) {
                        val etFind: EditText = dialogView.findViewById(R.id.et_find)
                        val etReplace: EditText = dialogView.findViewById(R.id.et_replace)

                        if (etFind.text.isEmpty()) return@positiveButton

                        val newText = codeEditText.text.string.ktReplace(etFind.text.string, etReplace.text.string)
                        codeEditText.setText(newText)

                        mAds.showInterstitial()
                    }
                    negativeButton(android.R.string.cancel) {}
                }.show()
            }
            R.id.menu_edit_cut -> codeEditText.cut()
            R.id.menu_edit_copy -> codeEditText.copy()
            R.id.menu_edit_paste -> codeEditText.paste()
            R.id.menu_edit_select_line -> codeEditText.selectLine()
            R.id.menu_edit_select_all -> codeEditText.selectAll()
            R.id.menu_edit_delete_line -> codeEditText.deleteLine()
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

    private fun getFileInfo(): String = "Name: ${mCurrentFile.nameWithoutExtension}\n" +
        "Path: ${mCurrentFile.parent}\n" +
        "Last Modified: ${SimpleDateFormat.getDateTimeInstance().format(Date(mCurrentFile.lastModified()))}\n" +
        "Size: ${getFileSize()}\n" +
        "Lines Count: ${codeEditText.lineCount}"

    private fun saveAs(createNewFile: Boolean, folderPath: String? = null) {
        val dialogView = layoutInflater inflate R.layout.dialog_new_file
        AlertDialog.Builder(this).apply {
            setTitle(if (createNewFile) R.string.create_new_file else R.string.menu_save_file_as)
            setView(dialogView)

            mSaveDialog = create()
        }

        val etFileName: EditText = dialogView.findViewById(R.id.et_file_name)
        val tvSelectPath: TextView = dialogView.findViewById(R.id.tv_select_path)
        val btnCancel: Button = dialogView.findViewById(R.id.btn_cancel)
        val btnOk: Button = dialogView.findViewById(R.id.btn_ok)

        etFileName.setText(mPreferences.getNewFilesName())

        tvSelectPath.text = folderPath ?: Const.MAIN_FOLDER
        tvSelectPath.setOnClickListener {
            Chooser(this,
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
                toast(R.string.invalid_file_name)
                etFileName.error = getString(R.string.invalid_file_name)
                return@setOnClickListener
            }

            val file = File(tvSelectPath.text.string + etFileName.text.string)
            if (!file.exists() && createNewFile) {
                file.createNewFile()
                toast(R.string.new_file_created)
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

        if (mCurrentFile.exists()) mAds.showInterstitial()
    }

    private fun loadFileAsync() = async(UI) {
        val job = async(CommonPool) {
            var result = ""

            try {
                result = mCurrentFile.readText()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            result
        }

        loadDocument(job.await())
    }

    private fun saveFileAsync(startConsole: Boolean) = async(UI) {
        val fileContent = codeEditText.text.string

        val job = async(CommonPool) {
            try {
                mCurrentFile.writeText(fileContent)
            } catch (e: IOException) {
                e.printStackTrace()
            }

            if (startConsole) {
                saveInternalFile("main.js", fileContent)
                saveInternalFile("index.html", "<!DOCTYPE html>\n<html>\n<head>\n" +
                    "<script type=\"text/javascript\" src=\"main.js\"></script>\n</head>\n<body>\n</body>\n</html>\n")
            }
        }

        job.await()

        mAds.showInterstitial()

        toast(R.string.file_saved)
        if (startConsole) startActivity<ConsoleActivity>()
    }
}
