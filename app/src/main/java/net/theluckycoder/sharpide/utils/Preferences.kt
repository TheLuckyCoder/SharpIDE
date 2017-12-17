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

    fun confirmAppQuit() = preferences.getBoolean("quit_confirm", true)

    fun useDarkTheme() = preferences.getBoolean("dark_theme", false)

    fun showHiddenFiles() = preferences.getBoolean("show_hidden_files", false)

    fun getNewFilesName() = preferences.getString("new_files_name", "Untitled") + ".js"

    fun getFontSize() = preferences.getInt("editor_font_size", 18)

    fun showLineNumbers() = preferences.getBoolean("show_line_numbers", true)

    fun showSymbolsBar() = preferences.getBoolean("show_symbols_bar", true)

    fun isSyntaxHighlightingEnabled() = preferences.getBoolean("enable_syntax_highlighting", true)

    fun highlightCurrentLine() = preferences.getBoolean("highlight_current_line", true)

    fun autoCloseBrackets() = preferences.getBoolean("auto_close_brackets", true)

    fun showSuggestions() = preferences.getBoolean("show_code_suggestions", false)
}
