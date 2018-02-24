package net.theluckycoder.sharpide.view

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.support.v4.content.ContextCompat
import android.support.v7.widget.AppCompatMultiAutoCompleteTextView
import android.text.Editable
import android.text.InputFilter
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.method.ScrollingMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.KeyEvent
import android.widget.ArrayAdapter
import net.theluckycoder.sharpide.CompletionTokenizer
import net.theluckycoder.sharpide.R
import net.theluckycoder.sharpide.utils.Preferences
import net.theluckycoder.sharpide.utils.lazyFast
import java.util.regex.Pattern

class CodeEditor : AppCompatMultiAutoCompleteTextView {

    private companion object {
        private val COMPLETION_KEYWORDS = arrayOf("break", "case", "catch {\n}", "super", "class", "const", "continue",
            "default", "delete", "do", "yield", "else", "export", "extends", "finally", "for", "function", "if {\n}",
            "import", "in", "instanceof", "new", "return", "switch", "this", "throw", "try {\n}", "typeof", "var",
            "void", "while", "with", "null", "true", "false")

        private val PATTERN_CLASSES = Pattern.compile(
            "^[\t ]*(Object|Function|Boolean|Symbol|Error|EvalError|InternalError|" +
            "RangeError|ReferenceError|SyntaxError|TypeError|URIError|" +
            "Number|Math|Date|String|RegExp|Map|Set|WeakMap|WeakSet|" +
            "Array|ArrayBuffer|DataView|JSON|Promise|Generator|GeneratorFunction" +
            "Reflect|Proxy|Intl)\\b", Pattern.MULTILINE)
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

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context)
    }

    @Transient private val mPaint = Paint()
    private val mPaintHighlight = Paint()
    private val mLineBounds = Rect()
    private lateinit var mLayout: Layout
    private val mUpdateHandler = Handler()
    private var mModified = true
    private val mUpdateRunnable = Runnable { highlightWithoutChange(text) }
    private val mPreferences by lazyFast { Preferences(context) }

    private var mColorNumber = 0
    private var mColorKeyword = 0
    private var mColorClasses = 0
    private var mColorComment = 0
    private var mColorString = 0

    /*private var mIsDoingUndoRedo = false
    private var mUpdateLastChange: TextChange? = null
    private var mRedoStack = UndoStack()
    private val mUndoStack = UndoStack()*/

    override fun onDraw(canvas: Canvas) {
        if (mPreferences.highlightCurrentLine()) {
            try {
                getLineBounds(getLine(), mLineBounds)
            } catch (e: Exception) {}

            val color = if (!mPreferences.useDarkTheme()) {
                ContextCompat.getColor(context, R.color.line_highlight)
            } else {
                ContextCompat.getColor(context, R.color.line_highlight_dark)
            }
            mPaintHighlight.color = color

            canvas.drawRect(mLineBounds, mPaintHighlight)
        }

        val normalPadding = getPixels(6).toInt()

        if (mPreferences.showLineNumbers()) {
            val padding = getPixels(digitCount * 10 + 14).toInt()
            setPadding(padding, normalPadding, normalPadding, normalPadding)

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
            setPadding(normalPadding, normalPadding, normalPadding, normalPadding)
        }

        super.onDraw(canvas)
    }

    private fun init(context: Context) {
        // Enable Scrolling
        isVerticalScrollBarEnabled = true
        movementMethod = ScrollingMovementMethod()
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

        addTextChangedListener(object : TextWatcher {
            private var count = 0
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                this.count = count
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit

            override fun afterTextChanged(e: Editable) {
                if (mPreferences.autoCloseBrackets()) autoCloseBrackets(e, count)
                if (mPreferences.autoCloseQuotes()) autoCloseQuotes(e, count)
                cancelUpdate()

                if (!mModified) return

                mUpdateHandler.postDelayed(mUpdateRunnable, 250)
            }
        })

        // Set Syntax Colors
        mColorNumber = ContextCompat.getColor(context, R.color.syntax_number)
        mColorKeyword = ContextCompat.getColor(context, R.color.syntax_keyword)
        mColorClasses = ContextCompat.getColor(context, R.color.syntax_class)
        mColorComment = ContextCompat.getColor(context, R.color.syntax_comment)
        mColorString = ContextCompat.getColor(context, R.color.syntax_string)

        mPaint.style = Paint.Style.FILL
        mPaint.isAntiAlias = true
        mPaint.color = Color.parseColor("#bbbbbb")
        mPaint.textSize = getPixels(mPreferences.getFontSize())

        viewTreeObserver.addOnGlobalLayoutListener { mLayout = layout }

        // Set Adapter
        val adapter = ArrayAdapter<String>(context, android.R.layout.simple_dropdown_item_1line, COMPLETION_KEYWORDS)
        setAdapter(adapter)
        setTokenizer(CompletionTokenizer())
    }

    override fun showDropDown() {
        if (mPreferences.showSuggestions()) super.showDropDown()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isCtrlPressed) {
            return when (keyCode) {
                KeyEvent.KEYCODE_X -> {
                    cut()
                    true
                }
                KeyEvent.KEYCODE_C -> {
                    copy()
                    true
                }
                KeyEvent.KEYCODE_V -> {
                    paste()
                    true
                }
                KeyEvent.KEYCODE_A -> {
                    selectAll()
                    true
                }
                KeyEvent.KEYCODE_DEL -> {
                    deleteLine()
                    true
                }
                KeyEvent.KEYCODE_D -> {
                    duplicateLine()
                    true
                }
                //KeyEvent.KEYCODE_S -> saveFile()
                else -> super.onKeyDown(keyCode, event)
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // ------------------------------------------------------------------

    private fun clearSpans(e: Editable) {
        // remove foreground color spans
        run {
            val spans = e.getSpans(0, e.length, ForegroundColorSpan::class.java)

            var i = spans.size
            while (i-- > 0) e.removeSpan(spans[i])
        }

        // remove background color spans
        run {
            val spans = e.getSpans(0, e.length, BackgroundColorSpan::class.java)

            var i = spans.size
            while (i-- > 0) e.removeSpan(spans[i])
        }
    }

    private fun cancelUpdate() {
        mUpdateHandler.removeCallbacks(mUpdateRunnable)
    }

    private fun autoCloseBrackets(e: Editable, count: Int) {
        val selectedStr = SpannableStringBuilder(text)
        val startSelection = selectionStart
        val endSelection = selectionEnd

        if (count > 0 && selectedStr.isNotEmpty() && startSelection > 0 && startSelection == endSelection) {
            val c = selectedStr[startSelection - 1]
            var nextC = 'x'
            var prevC = 'x'

            if (selectedStr.length > startSelection) {
                nextC = selectedStr[startSelection]
            }
            if (startSelection > 1) {
                prevC = selectedStr[startSelection - 2]
            }
            if (!(c != '(' || nextC == ')' || prevC == '(')) {
                e.insert(startSelection, ")")
                setSelection(startSelection)
            } else if (!(c != '{' || nextC == '}' || prevC == '{')) {
                e.insert(startSelection, "}")
                setSelection(startSelection)
            } else if (!(c != '[' || nextC == ']' || prevC == '[')) {
                e.insert(startSelection, "]")
                setSelection(startSelection)
            }
        }
    }

    private fun autoCloseQuotes(e: Editable, count: Int) {
        val selectedStr = SpannableStringBuilder(text)
        val startSelection = selectionStart
        val endSelection = selectionEnd

        if (count > 0 && selectedStr.isNotEmpty() && startSelection > 0 && startSelection == endSelection) {
            val c = selectedStr[startSelection - 1]
            var nextC = 'x'
            var prevC = 'x'

            if (selectedStr.length > startSelection) {
                nextC = selectedStr[startSelection]
            }
            if (startSelection > 1) {
                prevC = selectedStr[startSelection - 2]
            }
            if (!(c != '\'' || nextC == '\'' || prevC == '\'')) {
                e.insert(startSelection, "\'")
                setSelection(startSelection)
            } else if (!(c != '\"' || nextC == '\"' || prevC == '\"')) {
                e.insert(startSelection, "\"")
                setSelection(startSelection)
            }
        }
    }

    private fun highlightWithoutChange(e: Editable) {
        mModified = false
        highlight(e)
        mModified = true
    }

    private fun highlight(editable: Editable): Editable {
        if (!mPreferences.isSyntaxHighlightingEnabled() || editable.isEmpty()) {
            return editable
        }

        // don't use e.clearSpans() because it will remove too much
        clearSpans(editable)

        if (editable.isEmpty()) return editable

        var m = PATTERN_CLASSES.matcher(editable)
        while (m.find()) {
            editable.setSpan(ForegroundColorSpan(mColorClasses), m.start(), m.end(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        m = PATTERN_CUSTOM_CLASSES.matcher(editable)
        while (m.find()) {
            editable.setSpan(ForegroundColorSpan(mColorClasses), m.start(), m.end(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        m = PATTERN_KEYWORDS.matcher(editable)
        while (m.find()) {
            editable.setSpan(ForegroundColorSpan(mColorKeyword), m.start(), m.end(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        m = PATTERN_SYMBOLS.matcher(editable)
        while (m.find()) {
            editable.setSpan(ForegroundColorSpan(mColorKeyword), m.start(), m.end(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        m = Pattern.compile("\\$\\w+").matcher(editable)
        while (m.find()) {
            editable.setSpan(ForegroundColorSpan(mColorKeyword), m.start(), m.end(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        m = Pattern.compile("\"(.*?)\"|'(.*?)'").matcher(editable)
        while (m.find()) {
            val spans = editable.getSpans(m.start(), m.end(), ForegroundColorSpan::class.java)
            for (span in spans) {
                editable.removeSpan(span)
            }
            editable.setSpan(ForegroundColorSpan(mColorString), m.start(), m.end(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        m = PATTERN_NUMBERS.matcher(editable)
        while (m.find()) {
            editable.setSpan(ForegroundColorSpan(mColorNumber), m.start(), m.end(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        m = PATTERN_COMMENTS.matcher(editable)
        while (m.find()) {
            val spans = editable.getSpans(m.start(), m.end(), ForegroundColorSpan::class.java)
            for (span in spans) editable.removeSpan(span)

            editable.setSpan(ForegroundColorSpan(mColorComment), m.start(), m.end(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        return editable
    }

    private fun getLine(): Int {
        return if (selectionStart == -1 || layout == null) {
            0
        } else {
            layout.getLineForOffset(selectionStart)
        }
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

    private fun drawLineNumber(canvas: Canvas, layout: Layout, positionY: Int, line: Int) {
        val positionX = layout.getLineLeft(line).toInt()
        canvas.drawText((line + 1).toString(), positionX + getPixels(2), positionY.toFloat(), mPaint)
    }

    private fun getPixels(dp: Int): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics)

    private fun getSelectedText(): CharSequence {
        return if (selectionEnd > selectionStart) {
            text.subSequence(selectionStart, selectionEnd)
        } else {
            text.subSequence(selectionEnd, selectionStart)
        }
    }

    // --------------------------------------------------------------------------------

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

    fun cut() {
        copy()

        if (selectionEnd > selectionStart) {
            text.replace(selectionStart, selectionEnd, "")
        } else {
            text.replace(selectionEnd, selectionStart, "")
        }
    }

    fun copy() {
        val clipboard: ClipboardManager? = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        val clipData = ClipData.newPlainText("text", getSelectedText())
        clipboard?.primaryClip = clipData
    }

    fun paste() {
        val clipboard: ClipboardManager? = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboard == null || !clipboard.hasPrimaryClip()) return

        val item = clipboard.primaryClip.getItemAt(0)
        val clipboardText = item.text.toString()

        if (clipboardText.isBlank() || selectionStart < 0 || selectionEnd < 0) return

        if (selectionEnd > selectionStart) {
            text.replace(selectionStart, selectionEnd, clipboardText)
        } else {
            text.replace(selectionEnd, selectionStart, clipboardText)
        }
    }

    fun selectLine() {
        if (selectionEnd != -1) {
            val line = layout.getLineForOffset(selectionEnd)
            setSelection(layout.getLineStart(line), layout.getLineEnd(line))
        }
    }

    fun deleteLine() {
        val at = selectionEnd
        if (at == -1) return

        val line = layout.getLineForOffset(at)
        var startAt = layout.getLineStart(line)
        val endAt = layout.getLineEnd(line)
        val len = text.length
        if (startAt > 1 && endAt > 1 && len == endAt) {
            startAt--
        }
        editableText.delete(startAt, endAt)
    }

    fun duplicateLine() {
        var start = selectionStart
        var end = selectionEnd
        if (start < 0 || end < 0) return
        start = Math.min(start, end)
        end = Math.max(start, end)

        if (end > start) end--
        while (end < text.length && text[end] != '\n') end++
        while (start > 0 && text[start - 1] != '\n') start--

        editableText.insert(end, "\n" + text.subSequence(start, end).toString())
    }

    /*private fun updateUndoRedoOnTextChanged(s: CharSequence, start: Int, count: Int) {
        val updateLastChange = mUpdateLastChange
        updateLastChange ?: return
        if(count < UndoStack.MAX_SIZE) {
            updateLastChange.newText = s.subSequence(start, start + count).toString()
            if(start == updateLastChange.start &&
                ((updateLastChange.oldText.isNotEmpty()
                    || updateLastChange.newText.isNotEmpty())
                    && updateLastChange.oldText != updateLastChange.newText)) {
                mUndoStack.push(updateLastChange)
                mRedoStack.removeAll()
            }
        } else {
            mUndoStack.removeAll()
            mRedoStack.removeAll()
        }
        mUpdateLastChange = null
    }

    private fun updateUndoRedoBeforeTextChanged(s: CharSequence, start: Int, count: Int) {
        if (count < UndoStack.MAX_SIZE) {
            mUpdateLastChange = TextChange().apply {
                this.oldText = s.subSequence(start, start + count).toString()
                this.start = start
            }
            return
        }
        mUndoStack.removeAll()
        mRedoStack.removeAll()
        mUpdateLastChange = null;
    }*/
}
