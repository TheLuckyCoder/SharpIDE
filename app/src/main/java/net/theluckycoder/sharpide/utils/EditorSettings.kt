package net.theluckycoder.sharpide.utils

data class EditorSettings(
    val useDarkTheme: Boolean,
    val fontSize: Int,
    val showLineNumbers: Boolean,
    val highlightSyntax: Boolean,
    val highlightCurrentLine: Boolean,
    val closeBrackets: Boolean,
    val closeQuotes: Boolean,
    val autoIndent: Boolean,
    val showSuggestions: Boolean
) {

    companion object {
        fun fromPreferences(prefs: AppPreferences): EditorSettings = EditorSettings(
            useDarkTheme = prefs.useDarkTheme,
            fontSize = prefs.fontSize,
            showLineNumbers = prefs.showLineNumbers,
            highlightSyntax = prefs.highlightSyntax,
            highlightCurrentLine = prefs.highlightCurrentLine,
            closeBrackets = prefs.autoCloseBrackets,
            closeQuotes = prefs.autoCloseQuotes,
            autoIndent = prefs.autoIndent,
            showSuggestions = prefs.showSuggestions
        )
    }
}
