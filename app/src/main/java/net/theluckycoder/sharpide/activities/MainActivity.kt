package net.theluckycoder.sharpide.activities

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewStub
import android.view.WindowManager
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.theluckycoder.materialchooser.Chooser
import net.theluckycoder.materialchooser.ChooserType
import net.theluckycoder.sharpide.EditorFile
import net.theluckycoder.sharpide.R
import net.theluckycoder.sharpide.adapter.SymbolsAdapter
import net.theluckycoder.sharpide.databinding.ActivityMainBinding
import net.theluckycoder.sharpide.databinding.DialogFindBinding
import net.theluckycoder.sharpide.databinding.DialogGotoLineBinding
import net.theluckycoder.sharpide.databinding.DialogNewFileBinding
import net.theluckycoder.sharpide.databinding.DialogReplaceBinding
import net.theluckycoder.sharpide.utils.Ads
import net.theluckycoder.sharpide.utils.AppPreferences
import net.theluckycoder.sharpide.utils.Const
import net.theluckycoder.sharpide.utils.EditorSettings
import net.theluckycoder.sharpide.utils.UpdateChecker
import net.theluckycoder.sharpide.utils.extensions.alertDialog
import net.theluckycoder.sharpide.utils.extensions.browse
import net.theluckycoder.sharpide.utils.extensions.bundleOf
import net.theluckycoder.sharpide.utils.extensions.checkHasPermission
import net.theluckycoder.sharpide.utils.extensions.containsAny
import net.theluckycoder.sharpide.utils.extensions.lazyFast
import net.theluckycoder.sharpide.utils.extensions.longToast
import net.theluckycoder.sharpide.utils.extensions.setTitleWithColor
import net.theluckycoder.sharpide.utils.extensions.setUseNightMode
import net.theluckycoder.sharpide.utils.extensions.startActivity
import net.theluckycoder.sharpide.utils.extensions.startActivityForResult
import net.theluckycoder.sharpide.utils.extensions.toast
import net.theluckycoder.sharpide.utils.extensions.verifyStoragePermission
import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEvent
import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEventListener
import net.yslibrary.android.keyboardvisibilityevent.util.UIUtil
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private val editor by lazyFast { binding.content.editor }

    private val preferences = AppPreferences(this)
    private val ads = Ads(this)
    private var saveDialog: AlertDialog? = null
    private lateinit var editorFile: EditorFile

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityMainBinding.inflate(layoutInflater)
        // Set the Theme
        setTheme(R.style.AppTheme_NoActionBar)
        setUseNightMode(preferences.useDarkTheme)

        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        /// Views
        // Set up Drawer Layout
        val toggle = object : ActionBarDrawerToggle(this, binding.drawerLayout, binding.toolbar, 0, 0) {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                super.onDrawerSlide(drawerView, slideOffset)

                UIUtil.hideKeyboard(this@MainActivity)
                editor.editText.clearFocus()
            }
        }
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Symbols
        val symbolsAdapter = SymbolsAdapter {
            val selection = editor.editText.selectionStart

            if (selection != -1)
                editor.editText.text.insert(selection, it)
        }
        val symbolsLinearManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.content.rvSymbols.apply {
            adapter = symbolsAdapter
            layoutManager = symbolsLinearManager
            setHasFixedSize(true)
        }

        // Setup Navigation View
        binding.navView.setNavigationItemSelectedListener(this)
        val header = binding.navView.getHeaderView(0)
        header.findViewById<View>(R.id.header_layout_main).setOnClickListener {
            startActivity<AboutActivity>()
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }
        header.findViewById<View>(R.id.header_image_settings).setOnClickListener {
            startActivityForResult<SettingsActivity>(SETTINGS_REQUEST_CODE)
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }

        // Check for permission
        verifyStoragePermission()
        File(Const.MAIN_FOLDER).mkdirs()

        val loadedFile = savedInstanceState?.getParcelable<EditorFile>(STATE_EDITOR_FILE)

        // Load preferences
        if (loadedFile == null) {
            editorFile = EditorFile(File(preferences.newFilesName))

            if (preferences.loadLastFile && checkHasPermission()) {
                val lastFile = File(preferences.lastFilePath)

                if (lastFile.isFile && lastFile.canRead()) {
                    editorFile = EditorFile(lastFile)
                    loadCurrentFile()
                }
            }
        } else {
            editorFile = loadedFile
        }
        supportActionBar?.subtitle = editorFile.name
        editor.setEditorSettings(EditorSettings.fromPreferences(preferences))

        // Setup Keyboard Checker
        KeyboardVisibilityEvent.setEventListener(this, this, object : KeyboardVisibilityEventListener {
            override fun onVisibilityChanged(isOpen: Boolean) {
                if (preferences.showSymbolsBar)
                    binding.content.rvSymbols.isVisible = isOpen && editor.editText.isFocused
            }
        })

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

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable(STATE_EDITOR_FILE, editorFile)
        super.onSaveInstanceState(outState)
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
            binding.drawerLayout.isDrawerOpen(GravityCompat.START) ->
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            preferences.confirmAppQuit -> {
                alertDialog(R.style.AppTheme_Dialog)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.quit_confirm)
                    .setPositiveButton(android.R.string.yes) { _, _ ->
                        finish()
                    }.setNegativeButton(android.R.string.no, null)
                    .show()
                return
            }
        }

        return super.onBackPressed()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_save -> saveFile()
            R.id.menu_save_as -> saveFileAs(false)
            R.id.menu_open -> openChooser(LOAD_FILE_REQUEST, Chooser.FILE_CHOOSER)
            R.id.menu_new -> saveFileAs(true)
            R.id.menu_file_info -> {
                if (!editorFile.isSavedToDisk()) {
                    longToast(R.string.error_file_not_saved)
                    return true
                }

                alertDialog(R.style.AppTheme_Dialog)
                    .setTitle(R.string.menu_file_info)
                    .setMessage(editorFile.computeFileInfo(editor.lineCount))
                    .setPositiveButton(R.string.action_close, null)
                    .show()
            }
            R.id.menu_minify -> startActivity<MinifyActivity>()
            R.id.menu_about -> startActivity<AboutActivity>()
        }

        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == LOAD_FILE_REQUEST && resultCode == RESULT_OK) {
            data ?: return
            val newFile = File(data.getStringExtra(Chooser.RESULT_PATH)!!)
            if (newFile.length() >= 5242880) { // if the file is bigger than 5 MB
                longToast(R.string.file_too_big)
                return
            }

            if (newFile.isFile && newFile.canRead()) {
                editorFile = EditorFile(newFile)
                loadCurrentFile()
            }
        }

        if (requestCode == CHANGE_PATH_REQUEST && resultCode == RESULT_OK) {
            data ?: return
            saveDialog?.let {
                it.dismiss()
                saveFileAs(true, data.getStringExtra(Chooser.RESULT_PATH))
            }
        }

        if (requestCode == SETTINGS_REQUEST_CODE) {
            setUseNightMode(preferences.useDarkTheme)

            editor.setEditorSettings(EditorSettings.fromPreferences(preferences))
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == Const.PERMISSION_REQUEST_CODE) {
            if (grantResults.size < 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                longToast(R.string.permission_required)
                finish()
            } else {
                File(Const.MAIN_FOLDER).mkdir()
                // TODO: Should we really?
                //loadCurrentFile()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val et = editor.editText
        when (item.itemId) {
            R.id.menu_undo -> et.undo()
            R.id.menu_redo -> et.redo()
            R.id.menu_run -> {
                if (editorFile.isSavedToDisk()) {
                    saveFileForConsole()

                    val params = bundleOf(
                        "size" to editorFile.file.length(),
                        "lines" to editor.lineCount
                    )
                    FirebaseAnalytics.getInstance(this).logEvent("file_run", params)
                } else {
                    saveFileAs(false)
                }
            }
            R.id.menu_go_to_line -> {
                val dialogBinding = DialogGotoLineBinding.inflate(layoutInflater, null, false)

                alertDialog(R.style.AppTheme_Dialog)
                    .setTitle(R.string.menu_go_to_line)
                    .setView(dialogBinding.root)
                    .setPositiveButton(R.string.action_apply) { _, _ ->
                        val lineText = dialogBinding.etLine.text?.toString()
                        val line = lineText?.toIntOrNull()

                        if (line != null) {
                            et.goToLine(line)

                            ads.showInterstitial()
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            R.id.menu_find -> {
                val dialogBinding = DialogFindBinding.inflate(layoutInflater, null, false)

                alertDialog(R.style.AppTheme_Dialog)
                    .setTitle(R.string.menu_find)
                    .setView(dialogBinding.root)
                    .setPositiveButton(R.string.action_apply) { _, _ ->
                        val searchText = dialogBinding.etFind.text?.toString()

                        if (!searchText.isNullOrBlank()) {
                            updateSearchFabVisibility(searchText, dialogBinding.cbIgnoreCase.isChecked)

                            ads.showInterstitial()
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            R.id.menu_replace_all -> {
                val dialogBinding = DialogReplaceBinding.inflate(layoutInflater, null, false)

                alertDialog(R.style.AppTheme_Dialog)
                    .setTitle(R.string.replace_all)
                    .setTitle(R.string.replace_all)
                    .setView(dialogBinding.root)
                    .setPositiveButton(R.string.replace_all) { _, _ ->
                        val searchText = dialogBinding.etFind.text?.toString()

                        if (!searchText.isNullOrBlank()) {
                            val replaceText = dialogBinding.etReplace.text?.toString().orEmpty()
                            val newText = editor.text.replace(searchText, replaceText)
                            editor.text = newText

                            ads.showInterstitial()
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            R.id.menu_edit_cut -> et.cut()
            R.id.menu_edit_copy -> et.copy()
            R.id.menu_edit_paste -> et.paste()
            R.id.menu_edit_select_line -> et.selectLine()
            R.id.menu_edit_select_all -> et.selectAll()
            R.id.menu_edit_delete_line -> et.deleteLine()
            R.id.menu_edit_delete_selected -> et.deleteSelected()
            R.id.menu_edit_duplicate_line -> et.duplicateLine()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val et = editor.editText

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
                KeyEvent.KEYCODE_X -> {
                    et.cut()
                    true
                }
                KeyEvent.KEYCODE_C -> {
                    et.copy()
                    true
                }
                KeyEvent.KEYCODE_V -> {
                    et.paste()
                    true
                }
                KeyEvent.KEYCODE_A -> {
                    et.selectAll()
                    true
                }
                KeyEvent.KEYCODE_D -> {
                    et.duplicateLine()
                    true
                }
                KeyEvent.KEYCODE_Z -> {
                    if (event.isShiftPressed)
                        et.redo()
                    else
                        et.undo()
                    true
                }
                else -> super.onKeyDown(keyCode, event)
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // region Private Functions
    private fun saveFile() {
        if (editorFile.isSavedToDisk()) {
            GlobalScope.launch(Dispatchers.Main.immediate) {
                try {
                    editorFile.saveFileAsync(editor.text)
                    supportActionBar?.subtitle = editorFile.name
                    toast(R.string.file_saved)
                } catch (e: IOException) {
                    e.printStackTrace()
                    toast(R.string.error)
                }
            }

            val params = bundleOf(
                "size" to editorFile.file.length(),
                "lines" to editor.lineCount
            )
            FirebaseAnalytics.getInstance(this).logEvent("file_save", params)
        } else {
            saveFileAs(false)
        }
    }

    private fun saveFileAs(createNewFile: Boolean, folderPath: String? = null) {
        val dialogBinding = DialogNewFileBinding.inflate(layoutInflater, null, false)
        val title = if (createNewFile) R.string.create_new_file else R.string.menu_save_file_as

        saveDialog = alertDialog(R.style.AppTheme_Dialog)
            .setTitle(title)
            .setView(dialogBinding.root)
            .show()

        dialogBinding.etFileName.setText(preferences.newFilesName)

        dialogBinding.tvSelectPath.text = folderPath ?: Const.MAIN_FOLDER
        dialogBinding.tvSelectPath.setOnClickListener {
            openChooser(CHANGE_PATH_REQUEST, Chooser.FOLDER_CHOOSER)
        }

        dialogBinding.btnCancel.setOnClickListener {
            saveDialog?.dismiss()
        }

        dialogBinding.btnOk.setOnClickListener {
            val fileName = dialogBinding.etFileName.text?.toString().orEmpty()
            if (fileName.containsAny(INVALID_FILE_NAME_SYMBOLS)) {
                toast(R.string.invalid_file_name)
                dialogBinding.etFileName.error = getString(R.string.invalid_file_name)
                return@setOnClickListener
            }

            val file = File(dialogBinding.tvSelectPath.text.toString(), fileName)
            if (!file.exists() && createNewFile) {
                try {
                    file.createNewFile()
                    toast(R.string.new_file_created)
                } catch (e: IOException) {
                    e.printStackTrace()
                    toast(R.string.error)
                }
            }

            editorFile = EditorFile(file)
            preferences.lastFilePath = file.absolutePath

            if (createNewFile) {
                loadCurrentFile()
            } else {
                GlobalScope.launch(Dispatchers.Main.immediate) {
                    try {
                        editorFile.saveFileAsync(editor.text)
                        toast(R.string.file_saved)
                        supportActionBar?.subtitle = editorFile.name
                    } catch (e: IOException) {
                        e.printStackTrace()
                        toast(R.string.error)
                    }
                }
            }

            saveDialog?.dismiss()
        }

        saveDialog?.setOnDismissListener {
            saveDialog = null
        }

        saveDialog?.show()
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
            binding.content.rvSymbols.visibility = View.GONE

            fabPrevious.setOnClickListener { editor.editText.findPreviousText(searchText, ignoreCase) }
            fabNext.setOnClickListener { editor.editText.findText(searchText, ignoreCase) }
            fabClose.setOnClickListener { updateSearchFabVisibility(null, false) }
        } else {
            val listener = object : FloatingActionButton.OnVisibilityChangedListener() {
                override fun onHidden(fab: FloatingActionButton) {
                    (fab.parent as View).visibility = View.GONE
                }
            }

            fabPrevious.hide(listener)
            fabNext.hide(listener)
            fabClose.hide(listener)
        }
    }

    private fun openChooser(requestCode: Int, @ChooserType chooserType: Int) {
        val extension = if (chooserType == Chooser.FILE_CHOOSER) "js" else ""

        Chooser(this, requestCode)
            .setFileExtensions(extension)
            .setShowHiddenFiles(preferences.showHiddenFiles)
            .setStartPath(Const.MAIN_FOLDER)
            .setChooserType(chooserType)
            .start()
    }

    private fun loadCurrentFile() {
        GlobalScope.launch(Dispatchers.Main.immediate) {
            val content = EditorFile.loadFileAsync(editorFile)
            if (content == null) {
                toast(R.string.error_load_file)
                return@launch
            }

            preferences.lastFilePath = editorFile.file.absolutePath
            editor.editText.scrollTo(0, 0)

            val chunkSize = 20000
            val loaded = StringBuilder()

            if (content.length > chunkSize) {
                loaded.append(content.substring(0, chunkSize))
                editor.text = loaded.toString()
            } else {
                loaded.append(content)
                editor.text = loaded.toString()
            }

            supportActionBar?.subtitle = editorFile.name
        }
    }

    private fun saveFileForConsole() {
        GlobalScope.launch(Dispatchers.Main.immediate) {
            if (editorFile.saveFileForConsoleAsync(editor.text, applicationContext)) {
                startActivity<ConsoleActivity>()
                toast(R.string.file_saved)
            } else {
                toast(R.string.error_file_not_saved)
            }
        }
    }

    // endregion Private Functions

    private companion object {
        private const val LOAD_FILE_REQUEST = 10
        private const val CHANGE_PATH_REQUEST = 11
        private const val SETTINGS_REQUEST_CODE = 20

        private const val STATE_EDITOR_FILE = "state_editor_file"

        val INVALID_FILE_NAME_SYMBOLS = "|\\?*<\":>+[]/'".toCharArray()
    }
}
