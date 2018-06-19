package net.theluckycoder.sharpide.utils

import android.content.Context
import android.preference.PreferenceManager
import android.support.annotation.StringRes
import net.theluckycoder.sharpide.R
import net.theluckycoder.sharpide.utils.extensions.lazyFast

class Preferences(private val context: Context) {

    private val preferences by lazyFast { PreferenceManager.getDefaultSharedPreferences(context) }

    private fun string(@StringRes resId: Int) = context.getString(resId)

    fun getLoadLastFile() = preferences.getBoolean(string(R.string.pref_load_last_file_key), true)

    fun getLastFilePath(): String = preferences.getString(string(R.string.pref_last_file_path), Const.MAIN_FOLDER)

    fun putLastFilePath(path: String) {
        preferences.edit().putString(string(R.string.pref_last_file_path), path).apply()
    }

    fun confirmAppQuit() = preferences.getBoolean(string(R.string.pref_quit_confirm_key), true)

    fun useDarkTheme() = preferences.getBoolean(string(R.string.pref_dark_theme_key), false)

    fun showHiddenFiles() = preferences.getBoolean(string(R.string.pref_hidden_files_key), false)

    fun getNewFilesName(): String {
        var result = preferences.getString(string(R.string.pref_new_files_name_key), "Untitled")
        if (!result.endsWith(".js")) result += ".js"
        return result
    }

    fun getFontSize() = preferences.getInt(string(R.string.pref_font_size_key), 17)

    fun showLineNumbers() = preferences.getBoolean(string(R.string.pref_line_numbers_key), true)

    fun showSymbolsBar() = preferences.getBoolean(string(R.string.pref_symbols_bar_key), true)

    fun isSyntaxHighlightingEnabled() = preferences.getBoolean(string(R.string.pref_syntax_highlighting_key), true)

    fun highlightCurrentLine() = preferences.getBoolean(string(R.string.pref_syntax_highlighting_key), true)

    fun autoCloseBrackets() = preferences.getBoolean(string(R.string.pref_auto_close_brackets_key), true)

    fun autoCloseQuotes() = preferences.getBoolean(string(R.string.pref_auto_close_quotes_key), true)

    fun showSuggestions() = preferences.getBoolean(string(R.string.pref_code_completion_key), false)

    fun isConsoleOpenByDefault() = preferences.getBoolean(string(R.string.pref_console_open_by_default_key), false)

    fun isFullscreen() = preferences.getBoolean(string(R.string.pref_fullscreen_key), false)

    fun autoIndent() = preferences.getBoolean(string(R.string.pref_auto_indent_key), true)
}
