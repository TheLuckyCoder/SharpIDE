package net.theluckycoder.sharpide.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.preference.PreferenceManager
import android.support.v4.content.ContextCompat
import android.support.v7.widget.AppCompatEditText
import android.text.*
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import net.theluckycoder.sharpide.R
import net.theluckycoder.sharpide.utils.Prefs
import java.util.regex.Pattern


class CodeEditText : AppCompatEditText {

    private companion object {
        private val PATTERN_CLASSES = Pattern.compile(
                "^[\t ]*(Object|Function|Boolean|Symbol|Error|EvalError|InternalError|" +
                        "RangeError|ReferenceError|SyntaxError|TypeError|URIError|" +
                        "Number|Math|Date|String|RegExp|Map|Set|WeakMap|WeakSet|" +
                        "Array|ArrayBuffer|DataView|JSON|Promise|Generator|GeneratorFunction" +
                        "Reflect|Proxy|Intl)\\b",
                Pattern.MULTILINE)
        private val PATTERN_CUSTOM_CLASSES = Pattern.compile("(\\w+[ .])")
        private val PATTERN_KEYWORDS = Pattern.compile(
                "\\b(break|case|catch|class|const|continue|debugger|default|delete|do|yield|" +
                        "else|export|extends|finally|for|function|if|import|in|instanceof|" +
                        "new|return|super|switch|this|throw|try|typeof|var|void|while|with|" +
                        "null|true|false)\\b")
        private val PATTERN_COMMENTS = Pattern.compile("/\\*(?:.|[\\n\\r])*?\\*/|//.*")
        private val PATTERN_SYMBOLS = Pattern.compile("[+\\-*&^!:/|?<>=;,.]")
        private val PATTERN_NUMBERS = Pattern.compile("\\b(\\d*[.]?\\d+)\\b")
    }

    @Transient private val mPaint = Paint()
    private val mPaintHighlight = Paint()
    private val mLineBounds = Rect()
    private lateinit var mLayout: Layout
    private val mUpdateHandler = Handler()
    private val mUpdateDelay = 250
    private var mModified = true
    private val mUpdateRunnable = Runnable { highlightWithoutChange(text) }
    private var mOnImeBack: BackPressedListener? = null
    private var mColorNumber = 0
    private var mColorKeyword = 0
    private var mColorClasses = 0
    private var mColorComment = 0
    private var mColorString = 0
    private val mPreferences get() = PreferenceManager.getDefaultSharedPreferences(context)

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(context)
    }

    private fun clearSpans(e: Editable) {
        // remove foreground color spans
        run {
            val spans = e.getSpans(0, e.length, ForegroundColorSpan::class.java)

            var n = spans.size
            while (n-- > 0) e.removeSpan(spans[n])
        }

        // remove background color spans
        run {
            val spans = e.getSpans(0, e.length, BackgroundColorSpan::class.java)

            var n = spans.size
            while (n-- > 0) e.removeSpan(spans[n])
        }
    }

    fun setTextHighlighted(text: CharSequence) {
        cancelUpdate()

        mModified = false
        setText(highlight(SpannableStringBuilder(text)))
        mModified = true
    }

    fun findText(searchText: String, ignoreCase: Boolean) {
        var needle = searchText

        if (needle.isEmpty()) return

        var startSelection = selectionEnd
        var haystack = text.toString()

        if (ignoreCase) {
            needle = needle.toLowerCase()
            haystack = haystack.toLowerCase()
        }

        var foundPosition = haystack.substring(startSelection).indexOf(needle)

        if (foundPosition == -1) {
            foundPosition = haystack.indexOf(needle)
            startSelection = 0
        }
        if (foundPosition != -1) {
            val newSelection = foundPosition + startSelection
            setSelection(newSelection, needle.length + newSelection)
        }
    }

    fun findPreviousText(searchText: String, ignoreCase: Boolean) {
        var needle = searchText

        if (needle.isEmpty()) return

        val endSelection = selectionStart
        var haystack = text.toString()
        if (ignoreCase) {
            needle = needle.toLowerCase()
            haystack = haystack.toLowerCase()
        }

        var foundPosition = haystack.substring(0, endSelection).lastIndexOf(needle)
        if (foundPosition == -1) foundPosition = haystack.lastIndexOf(needle)
        if (foundPosition != -1) setSelection(foundPosition, needle.length + foundPosition)
    }

    fun goToLine(toLine: Int) {
        var line = toLine - 1

        when {
            line < 0 -> line = 0
            line > lineCount - 1 -> line = lineCount - 1
        }

        setSelection(layout.getLineStart(line))
    }

    /*fun toBegin() = setLine(0)

    fun toEnd() = setLine(lineCount - 1)*/

    private fun init(context: Context) {
        setHorizontallyScrolling(true)

        filters = arrayOf(InputFilter { source, start, end, dest, dStart, dEnd ->
            if (mModified &&
                    end - start == 1 &&
                    start < source.length &&
                    dStart < dest.length) {
                val c = source[start]

                if (c == '\n') return@InputFilter autoIndent(source, dest, dStart, dEnd)
            }

            source
        })

        addTextChangedListener(
                object : TextWatcher {

                    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit

                    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit

                    override fun afterTextChanged(e: Editable) {
                        cancelUpdate()

                        if (!mModified) return

                        mUpdateHandler.postDelayed(mUpdateRunnable, mUpdateDelay.toLong())
                    }
                })

        // Set Syntax Colors
        mColorNumber = ContextCompat.getColor(context, R.color.syntax_number)
        mColorKeyword = ContextCompat.getColor(context, R.color.syntax_keyword)
        mColorClasses = ContextCompat.getColor(context, R.color.syntax_class)
        mColorComment = ContextCompat.getColor(context, R.color.syntax_comment)
        mColorString = ContextCompat.getColor(context, R.color.syntax_string)

        val bgPaint = Paint()
        bgPaint.style = Paint.Style.FILL
        bgPaint.color = Color.parseColor("#eeeeee")

        mPaint.style = Paint.Style.FILL
        mPaint.isAntiAlias = true
        mPaint.color = Color.parseColor("#bbbbbb")
        mPaint.textSize = getPixels(Integer.parseInt(mPreferences.getString(Prefs.FONT_SIZE, "16")))
        viewTreeObserver.addOnGlobalLayoutListener { mLayout = layout }
    }

    private fun cancelUpdate() {
        mUpdateHandler.removeCallbacks(mUpdateRunnable)
    }

    private fun highlightWithoutChange(e: Editable) {
        mModified = false
        highlight(e)
        mModified = true
    }

    private fun highlight(editable: Editable): Editable {
        if (!mPreferences.getBoolean(Prefs.SYNTAX_HIGHLIGHTING, true)) return editable

        try {
            // don't use e.clearSpans() because it will
            // remove too much
            clearSpans(editable)

            if (editable.isEmpty()) return editable

            run {
                val m = PATTERN_CLASSES.matcher(editable)
                while (m.find())
                    editable.setSpan(ForegroundColorSpan(mColorClasses), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            run {
                val m = PATTERN_CUSTOM_CLASSES.matcher(editable)
                while (m.find())
                    editable.setSpan(ForegroundColorSpan(mColorClasses), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            run {
                val m = PATTERN_KEYWORDS.matcher(editable)
                while (m.find())
                    editable.setSpan(ForegroundColorSpan(mColorKeyword), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            run {
                val m = PATTERN_SYMBOLS.matcher(editable)
                while (m.find())
                    editable.setSpan(ForegroundColorSpan(mColorKeyword), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            run {
                val m = Pattern.compile("\\$\\w+").matcher(editable)
                while (m.find())
                    editable.setSpan(ForegroundColorSpan(mColorKeyword), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            run {
                val m = Pattern.compile("\"(.*?)\"|'(.*?)'").matcher(editable)
                while (m.find()) {
                    val spans = editable.getSpans(m.start(), m.end(), ForegroundColorSpan::class.java)
                    for (span in spans)
                        editable.removeSpan(span)
                    editable.setSpan(ForegroundColorSpan(mColorString), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }

            run {
                val m = PATTERN_NUMBERS.matcher(editable)
                while (m.find())
                    editable.setSpan(ForegroundColorSpan(mColorNumber), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            val m = PATTERN_COMMENTS.matcher(editable)
            while (m.find()) {
                val spans = editable.getSpans(m.start(), m.end(), ForegroundColorSpan::class.java)
                for (span in spans)
                    editable.removeSpan(span)
                editable.setSpan(ForegroundColorSpan(mColorComment), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        } catch (e: IllegalStateException) {
            Log.e("IllegalStateException", e.message, e)
            // raised by Matcher.start()/.end() when
            // no successful match has been made what
            // shouldn't ever happen because of find()
        }

        return editable
    }

    private val line: Int
        get() = if (selectionStart == -1 || layout == null) {
            -1
        } else {
            layout.getLineForOffset(selectionStart)
        }

    private fun autoIndent(source: CharSequence, dest: Spanned, dStart: Int, dEnd: Int): CharSequence {
        var indent = ""
        var iStart = dStart - 1

        // find start of this line
        var dataBefore = false
        var pt = 0

        while (iStart > -1) {
            val c = dest[iStart]

            if (c == '\n') break

            if (c != ' ' && c != '\t') {
                if (!dataBefore) {
                    // indent always after those characters
                    if (c == '{' || c == '+' || c == '-' || c == '*' || c == '/' || c == '%' || c == '^' || c == '=')
                        pt--

                    dataBefore = true
                }

                // parenthesis counter
                if (c == '(')
                    --pt
                else if (c == ')')
                    ++pt
            }
            --iStart
        }

        // copy indent of this line into the next
        if (iStart > -1) {
            val charAtCursor = dest[dStart]
            var iEnd = ++iStart

            while (iEnd < dEnd) {
                val c = dest[iEnd]

                // auto expand comments
                if (charAtCursor != '\n' &&
                        c == '/' &&
                        iEnd + 1 < dEnd &&
                        dest[iEnd] == c) {
                    iEnd += 2
                    break
                }

                if (c != ' ' && c != '\t')
                    break
                ++iEnd
            }

            indent += dest.subSequence(iStart, iEnd)
        }

        // add new indent
        if (pt < 0) indent += "\t"

        // append white space of previous line and new indent
        return source.toString() + indent
    }

    private val digitCount: Int
        get() {
            var count = 0
            var lineCount = lineCount

            while (lineCount > 0) {
                count++
                lineCount /= 10
            }

            return count
        }

    override fun onDraw(canvas: Canvas) {
        if(mPreferences.getBoolean(Prefs.HIGHLIGHT_CURRENT_LINE, true)) {
            getLineBounds(line, mLineBounds)
            mPaintHighlight.color = Color.parseColor("#ffffff")
            canvas.drawRect(mLineBounds, mPaintHighlight)
        }

        if (mPreferences.getBoolean(Prefs.SHOW_LINE_NUMBERS, true)) {
            val padding = getPixels(digitCount * 10 + 10).toInt()
            setPadding(padding, 0, 0, 0)

            val firstLine = mLayout.getLineForVertical(scrollY)
            val lastLine = try {
                mLayout.getLineForVertical(scrollY + (height - extendedPaddingTop - extendedPaddingBottom))
            } catch (e: NullPointerException) {
                mLayout.getLineForVertical(scrollY + (height - paddingTop - paddingBottom))
            }

            // The y position starts at the baseline of the first line
            var positionY = baseline + (mLayout.getLineBaseline(firstLine) - mLayout.getLineBaseline(0))
            drawLineNumber(canvas, mLayout, positionY, firstLine)

            for (i in firstLine + 1..lastLine) {
                // Get the next y position using the difference between the current and last baseline
                positionY += mLayout.getLineBaseline(i) - mLayout.getLineBaseline(i - 1)
                drawLineNumber(canvas, mLayout, positionY, i)
            }
        } else {
            setPadding(0, 0, 0, 0)
        }

        super.onDraw(canvas)
    }

    private fun drawLineNumber(canvas: Canvas, layout: Layout?, positionY: Int, line: Int) {
        val positionX = layout!!.getLineLeft(line).toInt()
        canvas.drawText((line + 1).toString(), positionX + getPixels(2), positionY.toFloat(), mPaint)
    }

    private fun getPixels(dp: Int): Float =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics)

    /*** Keyboard checking ***/
    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP)
            mOnImeBack?.onImeBack()
        return super.dispatchKeyEvent(event)
    }

    interface BackPressedListener {
        fun onImeBack()
    }
}
