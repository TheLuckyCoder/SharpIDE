package net.theluckycoder.sharpide.utils.text

import android.content.Context
import android.text.Editable
import android.text.Spannable
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import androidx.core.content.ContextCompat
import net.theluckycoder.sharpide.R
import java.util.regex.Pattern

class SyntaxHighlighter(context: Context) {

    private val colorNumber = ContextCompat.getColor(context, R.color.syntax_number)
    private val colorKeyword = ContextCompat.getColor(context, R.color.syntax_keyword)
    private val colorClasses = ContextCompat.getColor(context, R.color.syntax_class)
    private val colorComment = ContextCompat.getColor(context, R.color.syntax_comment)
    private val colorString = ContextCompat.getColor(context, R.color.syntax_string)

    private val classesMatcher = emptyMatcher(PATTERN_CLASSES)
    private val customClassesMatcher = emptyMatcher(PATTERN_CUSTOM_CLASSES)
    private val keywordsMatcher = emptyMatcher(PATTERN_KEYWORDS)
    private val otherKeywordsMatcher = emptyMatcher(PATTERN_KEYWORDS_OTHER)
    private val symbolsMatcher = emptyMatcher(PATTERN_SYMBOLS)
    private val numbersMatcher = emptyMatcher(PATTERN_NUMBERS)
    private val quotesMatcher = emptyMatcher(PATTERN_QUOTES)
    private val commentsMatcher = emptyMatcher(PATTERN_COMMENTS)

    private fun emptyMatcher(pattern: Pattern) = pattern.matcher("")

    fun clearSpans(editable: Editable) {
        // remove foreground color spans
        val foregroundSpans = editable.getSpans(0, editable.length, ForegroundColorSpan::class.java)

        for (i in foregroundSpans.size - 1 downTo 0)
            editable.removeSpan(foregroundSpans[i])

        // remove background color spans
        val backgroundSpans = editable.getSpans(0, editable.length, BackgroundColorSpan::class.java)

        for (i in backgroundSpans.size - 1 downTo 0)
            editable.removeSpan(backgroundSpans[i])
    }

    fun highlight(editable: Editable) {
        if (editable.isEmpty()) return

        var m = classesMatcher.reset(editable)
        while (m.find())
            editable.setSpan(ForegroundColorSpan(colorClasses), m.start(), m.end(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        m = customClassesMatcher.reset(editable)
        while (m.find())
            editable.setSpan(ForegroundColorSpan(colorClasses), m.start(), m.end(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        m = keywordsMatcher.reset(editable)
        while (m.find())
            editable.setSpan(ForegroundColorSpan(colorKeyword), m.start(), m.end(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        m = otherKeywordsMatcher.reset(editable)
        while (m.find())
            editable.setSpan(ForegroundColorSpan(colorKeyword), m.start(), m.end(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        m = symbolsMatcher.reset(editable)
        while (m.find())
            editable.setSpan(ForegroundColorSpan(colorKeyword), m.start(), m.end(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        m = numbersMatcher.reset(editable)
        while (m.find())
            editable.setSpan(ForegroundColorSpan(colorNumber), m.start(), m.end(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        m = quotesMatcher.reset(editable)
        while (m.find()) {
            val spans = editable.getSpans(m.start(), m.end(), ForegroundColorSpan::class.java)

            for (span in spans)
                editable.removeSpan(span)

            editable.setSpan(ForegroundColorSpan(colorString), m.start(), m.end(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        m = commentsMatcher.reset(editable)
        while (m.find()) {
            val spans = editable.getSpans(m.start(), m.end(), ForegroundColorSpan::class.java)
            for (span in spans)
                editable.removeSpan(span)

            editable.setSpan(ForegroundColorSpan(colorComment), m.start(), m.end(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    companion object {
        private val PATTERN_CLASSES = Pattern.compile(
            "^[\t ]*(Object|Function|Boolean|Symbol|Error|EvalError|InternalError|RangeError|ReferenceError|" +
                "SyntaxError|TypeError|URIError|Number|Math|Date|String|RegExp|Map|Set|WeakMap|WeakSet|Array|" +
                "ArrayBuffer|DataView|JSON|Promise|Generator|GeneratorFunctionReflect|Proxy|Intl)\\b",
            Pattern.MULTILINE)
        private val PATTERN_CUSTOM_CLASSES = Pattern.compile("(\\w+[ .])")
        private val PATTERN_KEYWORDS = Pattern.compile(
            "\\b(break|case|catch|class|const|continue|default|delete|do|else|export|extends|false|finally|" +
                "for|function|if|import|in|instanceof|interface|let|new|null|static|super|switch|this|throw|true|try|" +
                "typeof|var|void|while|with)\\b")
        private val PATTERN_KEYWORDS_OTHER = Pattern.compile("\\$\\w+")
        private val PATTERN_SYMBOLS = Pattern.compile("[+\\-*&^!:/|?<>=;,.]")
        private val PATTERN_NUMBERS = Pattern.compile("\\b(\\d*[.]?\\d+)\\b")
        private val PATTERN_QUOTES = Pattern.compile("\"(.*?)\"|'(.*?)'")
        val PATTERN_COMMENTS: Pattern = Pattern.compile("/\\*(?:.|[\\n\\r])*?\\*/|//.*")
    }
}
