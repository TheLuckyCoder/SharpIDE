package net.theluckycoder.sharpide.utils

import android.content.Context
import android.preference.PreferenceManager

class Preferences(context: Context) {

    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)

    fun getLoadLastFile() = preferences.getBoolean("load_last_file", true)

    fun getLastFilePath(): String = preferences.getString("last_file_path", Const.MAIN_FOLDER)

    fun setLastFilePath(path: String) {
        preferences.edit().putString("last_file_path", path).apply()
    }

    fun getConfirmQuit() = preferences.getBoolean("quit_confirm", true)

    fun getShowHiddenFiles() = preferences.getBoolean("show_hidden_files", false)

    fun getNewFilesName() = preferences.getString("new_files_name", "Untitled") + ".js"

    fun getFontSize() = preferences.getString("font_size", "16").toInt()

    fun getShowLineNumbers() = preferences.getBoolean("show_line_numbers", true)

    fun getShowSymbolsBar() = preferences.getBoolean("show_symbols_bar", true)

    fun getSyntaxHighlighting() = preferences.getBoolean("enable_syntax_highlighting", true)

    fun getHighlightCurrentLine() = preferences.getBoolean("highlight_current_line", true)

    fun getAutoCloseBrackets() = preferences.getBoolean("auto_close_brackets", true)
}