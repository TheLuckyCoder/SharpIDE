package net.theluckycoder.sharpide.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import java.util.regex.Pattern


class CodeEditText : AppCompatEditText {

    companion object {
        private val PATTERN_CLASSES = Pattern.compile(
                "^[\t ]*(Object|Function|Boolean|Symbol|Error|EvalError|InternalError|" +
                        "RangeError|ReferenceError|SyntaxError|TypeError|URIError|" +
                        "Number|Math|Date|String|RegExp|Map|Set|WeakMap|WeakSet|" +
                        "Array|ArrayBuffer|DataView|JSON|Promise|Generator|GeneratorFunction" +
                        "Reflect|Proxy|Intl)\\b",
                Pattern.MULTILINE)
        private val PATTERN_CUSTOM_CLASSES = Pattern.compile(
                "(\\w+[ .])")
        private val PATTERN_KEYWORDS = Pattern.compile(
                "\\b(break|case|catch|class|const|continue|debugger|default|delete|do|yield|" +
                        "else|export|extends|finally|for|function|if|import|in|instanceof|" +
                        "new|return|super|switch|this|throw|try|typeof|var|void|while|with|" +
                        "null|true|false)\\b")
        private val PATTERN_COMMENTS = Pattern.compile("/\\*(?:.|[\\n\\r])*?\\*/|//.*")
        private val PATTERN_SYMBOLS = Pattern.compile("[+\\-*&^!:/|?<>=;,.]")
        private val PATTERN_NUMBERS = Pattern.compile("\\b(\\d*[.]?\\d+)\\b")
    }

    private val mContext: Context
    @Transient private val paint = Paint()
    private var mLayout: Layout? = null
    private val updateHandler = Handler()
    private val onTextChangedListener: OnTextChangedListener? = null
    private val updateDelay = 500
    private var modified = true
    private var colorNumber = 0
    private var colorKeyword = 0
    private var colorClasses = 0
    private var colorComment = 0
    private var colorString = 0
    private val updateRunnable = Runnable {
        val e = text

        onTextChangedListener?.onTextChanged(
                e.toString())

        highlightWithoutChange(e)
    }
    private var mOnImeBack: BackPressedListener? = null

    constructor(context: Context) : super(context) {
        mContext = context
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        mContext = context
        init(context)
    }

    private fun clearSpans(e: Editable) {
        // remove foreground color spans
        run {
            val spans = e.getSpans(0, e.length, ForegroundColorSpan::class.java)

            var n = spans.size
            while (n-- > 0)
                e.removeSpan(spans[n])
        }

        // remove background color spans
        run {
            val spans = e.getSpans(0, e.length, BackgroundColorSpan::class.java)

            var n = spans.size
            while (n-- > 0)
                e.removeSpan(spans[n])
        }
    }

    fun setTextHighlighted(text: CharSequence?) {
        val newText = text ?: ""

        cancelUpdate()

        modified = false
        setText(highlight(SpannableStringBuilder(newText)))
        modified = true

        onTextChangedListener?.onTextChanged(newText.toString())
    }

    private fun init(context: Context) {
        setHorizontallyScrolling(true)

        filters = arrayOf(InputFilter { source, start, end, dest, dStart, dEnd ->
            if (modified &&
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

                        if (!modified) return

                        updateHandler.postDelayed(updateRunnable, updateDelay.toLong())
                    }
                })

        // Set Syntax Colors
        colorNumber = ContextCompat.getColor(context, R.color.syntax_number)
        colorKeyword = ContextCompat.getColor(context, R.color.syntax_keyword)
        colorClasses = ContextCompat.getColor(context, R.color.syntax_class)
        colorComment = ContextCompat.getColor(context, R.color.syntax_comment)
        colorString = ContextCompat.getColor(context, R.color.syntax_string)

        val bgPaint = Paint()
        bgPaint.style = Paint.Style.FILL
        bgPaint.color = Color.parseColor("#eeeeee")

        paint.style = Paint.Style.FILL
        paint.isAntiAlias = true
        paint.color = Color.parseColor("#bbbbbb")
        paint.textSize = getPixels(Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(context).getString("font_size", "16")))
        viewTreeObserver.addOnGlobalLayoutListener { mLayout = layout }
    }

    private fun cancelUpdate() {
        updateHandler.removeCallbacks(updateRunnable)
    }

    private fun highlightWithoutChange(e: Editable) {
        modified = false
        highlight(e)
        modified = true
    }

    private fun highlight(editable: Editable): Editable {
        try {
            // don't use e.clearSpans() because it will
            // remove too much
            clearSpans(editable)

            if (editable.isEmpty()) return editable

            run {
                val m = PATTERN_CLASSES.matcher(editable)
                while (m.find())
                    editable.setSpan(ForegroundColorSpan(colorClasses), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            run {
                val m = PATTERN_CUSTOM_CLASSES.matcher(editable)
                while (m.find())
                    editable.setSpan(ForegroundColorSpan(colorClasses), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            run {
                val m = PATTERN_KEYWORDS.matcher(editable)
                while (m.find())
                    editable.setSpan(ForegroundColorSpan(colorKeyword), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            run {
                val m = PATTERN_SYMBOLS.matcher(editable)
                while (m.find())
                    editable.setSpan(ForegroundColorSpan(colorKeyword), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            run {
                val m = Pattern.compile("\\$\\w+").matcher(editable)
                while (m.find())
                    editable.setSpan(ForegroundColorSpan(colorKeyword), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            run {
                val m = Pattern.compile("\"(.*?)\"|'(.*?)'").matcher(editable)
                while (m.find()) {
                    val spans = editable.getSpans(m.start(), m.end(), ForegroundColorSpan::class.java)
                    for (span in spans)
                        editable.removeSpan(span)
                    editable.setSpan(ForegroundColorSpan(colorString), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }

            run {
                val m = PATTERN_NUMBERS.matcher(editable)
                while (m.find())
                    editable.setSpan(ForegroundColorSpan(colorNumber), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            val m = PATTERN_COMMENTS.matcher(editable)
            while (m.find()) {
                val spans = editable.getSpans(m.start(), m.end(), ForegroundColorSpan::class.java)
                for (span in spans)
                    editable.removeSpan(span)
                editable.setSpan(ForegroundColorSpan(colorComment), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        } catch (e: IllegalStateException) {
            Log.e("IllegalStateException", e.message, e)
            // raised by Matcher.start()/.end() when
            // no successful match has been made what
            // shouldn't ever happen because of find()
        }

        return editable
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
        val padding = getPixels(digitCount * 10 + 10).toInt()
        setPadding(padding, 0, 0, 0)

        val scrollY = scrollY
        val firstLine = mLayout!!.getLineForVertical(scrollY)
        val lastLine = try {
            mLayout!!.getLineForVertical(scrollY + (height - extendedPaddingTop - extendedPaddingBottom))
        } catch (e: NullPointerException) {
            mLayout!!.getLineForVertical(scrollY + (height - paddingTop - paddingBottom))
        }

        //the y position starts at the baseline of the first line
        var positionY = baseline + (mLayout!!.getLineBaseline(firstLine) - mLayout!!.getLineBaseline(0))
        drawLineNumber(canvas, mLayout, positionY, firstLine)

        for (i in firstLine + 1..lastLine) {
            //get the next y position using the difference between the current and last baseline
            positionY += mLayout!!.getLineBaseline(i) - mLayout!!.getLineBaseline(i - 1)
            drawLineNumber(canvas, mLayout, positionY, i)
        }

        super.onDraw(canvas)
    }

    private fun drawLineNumber(canvas: Canvas, layout: Layout?, positionY: Int, line: Int) {
        val positionX = layout!!.getLineLeft(line).toInt()
        canvas.drawText((line + 1).toString(), positionX + getPixels(2), positionY.toFloat(), paint)
    }

    private fun getPixels(dp: Int): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), mContext.resources.displayMetrics)
    }

    interface OnTextChangedListener {
        fun onTextChanged(text: String)
    }

    /*** Keyboard checking  */
    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP)
            mOnImeBack?.onImeBack()
        return super.dispatchKeyEvent(event)
    }

    interface BackPressedListener {
        fun onImeBack()
    }
}
