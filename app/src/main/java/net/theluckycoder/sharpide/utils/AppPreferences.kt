package net.theluckycoder.sharpide.utils

import android.content.Context
import android.preference.PreferenceManager
import androidx.annotation.StringRes
import net.theluckycoder.sharpide.R
import net.theluckycoder.sharpide.utils.extensions.lazyFast

class AppPreferences(private val context: Context) {

    private val preferences by lazyFast { PreferenceManager.getDefaultSharedPreferences(context) }

    private fun string(@StringRes resId: Int) = context.getString(resId)

    val loadLastFile get() = preferences.getBoolean(string(R.string.pref_load_last_file_key), true)

    var lastFilePath: String
        get() = preferences.getString(string(R.string.pref_last_file_path), Const.MAIN_FOLDER).orEmpty()
        set(path) { preferences.edit().putString(string(R.string.pref_last_file_path), path).apply() }

    val confirmAppQuit get() = preferences.getBoolean(string(R.string.pref_quit_confirm_key), true)

    val useDarkTheme get() = preferences.getBoolean(string(R.string.pref_dark_theme_key), false)

    val showHiddenFiles get() = preferences.getBoolean(string(R.string.pref_hidden_files_key), false)

    val newFilesName: String
        get() {
            var result = preferences.getString(string(R.string.pref_new_files_name_key), "Untitled").orEmpty()
            if (!result.endsWith(".js")) result += ".js"
            return result
        }

    val fontSize get() = preferences.getInt(string(R.string.pref_font_size_key), 17)

    val showLineNumbers get() = preferences.getBoolean(string(R.string.pref_line_numbers_key), true)

    val showSymbolsBar get() = preferences.getBoolean(string(R.string.pref_symbols_bar_key), true)

    val highlightSyntax get() = preferences.getBoolean(string(R.string.pref_syntax_highlighting_key), true)

    val highlightCurrentLine get() = preferences.getBoolean(string(R.string.pref_syntax_highlighting_key), true)

    val autoCloseBrackets get() = preferences.getBoolean(string(R.string.pref_auto_close_brackets_key), true)

    val autoCloseQuotes get() = preferences.getBoolean(string(R.string.pref_auto_close_quotes_key), true)

    val showSuggestions get() = preferences.getBoolean(string(R.string.pref_code_completion_key), true)

    val isConsoleOpenByDefault get() = preferences.getBoolean(string(R.string.pref_console_open_by_default_key), false)

    val isFullscreen get() = preferences.getBoolean(string(R.string.pref_fullscreen_key), false)

    // fun autoIndent() = preferences.getBoolean(string(R.string.pref_auto_indent_key), true)
}
