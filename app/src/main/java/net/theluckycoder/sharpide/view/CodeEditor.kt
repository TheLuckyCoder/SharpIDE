package net.theluckycoder.sharpide.view

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.text.Editable
import android.text.InputFilter
import android.text.Layout
import android.text.Selection
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.method.ScrollingMovementMethod
import android.util.AttributeSet
import android.view.KeyEvent
import android.widget.ArrayAdapter
import androidx.appcompat.widget.AppCompatMultiAutoCompleteTextView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.theluckycoder.sharpide.R
import net.theluckycoder.sharpide.utils.AppPreferences
import net.theluckycoder.sharpide.utils.EditorSettings
import net.theluckycoder.sharpide.utils.extensions.dpToPx
import net.theluckycoder.sharpide.utils.extensions.spToPx
import net.theluckycoder.sharpide.utils.extensions.toast
import net.theluckycoder.sharpide.utils.text.SyntaxHighlighter
import net.theluckycoder.sharpide.utils.text.TextChange
import net.theluckycoder.sharpide.utils.text.UndoStack
import kotlin.math.max
import kotlin.math.min

class CodeEditor : AppCompatMultiAutoCompleteTextView {

    private companion object {
        private val COMPLETION_KEYWORDS = arrayOf("break", "case:", "catch (e) {\n}", "super", "class", "const",
            "continue", "default:", "delete", "do", "yield", "else if () {\n}", "else {\n}", "extends", "let",
            "finally {\n}", "for", "function", "if () {\n}", "in", "instanceof", "new", "return", "switch () {\n}",
            "this", "throw", "try {\n}", "typeof", "var", "while", "with", "null", "true", "false")

        private const val SYNTAX_HIGHLIGHTING_DELAY = 300L
    }

    private val mTextPaint = Paint()
    private val mPaintHighlight = Paint()
    private val mLineBounds = Rect()
    private lateinit var mLayout: Layout
    private var mModified = true
    private var mUpdateJob: Job? = null
    private var mIsDoingUndoRedo = false
    private var mUpdateLastChange: TextChange? = null
    private val mSyntaxHighlighter = SyntaxHighlighter(context)
    private val mRedoStack = UndoStack()
    private val mUndoStack = UndoStack()

    var editorSettings = EditorSettings.fromPreferences(AppPreferences(context))
        set(value) {
            field = value

            if (field != value) {
                updateSettings()
                invalidate()
            }
        }

    // region Constructor

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context)
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

                if (c == '\n')
                    return@InputFilter autoIndent(source, dest, dStart, dEnd)
            }

            source
        })

        addTextChangedListener(object : TextWatcher {
            private var count = 0
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                updateUndoRedoBeforeTextChanged(s, start,  count)
            }

            override fun afterTextChanged(e: Editable) {
                if (editorSettings.closeBrackets) autoCloseBrackets(e, count)
                if (editorSettings.closeQuotes) autoCloseQuotes(e, count)
                mUpdateJob?.cancel()

                if (!mModified) return

                if (editorSettings.highlightSyntax)
                    mUpdateJob = highlightWithoutChange()
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                this.count = count
                updateUndoRedoOnTextChanged(s, start, count)
            }
        })

        with(mTextPaint) {
            style = Paint.Style.FILL
            isAntiAlias = true
            color = Color.parseColor("#bbbbbb")
        }

        viewTreeObserver.addOnGlobalLayoutListener {
            layout?.let { mLayout = it }
        }

        updateSettings()

        // Set Adapter
        val adapter = ArrayAdapter(context, R.layout.item_suggestion, COMPLETION_KEYWORDS)
        setAdapter(adapter)
        dropDownVerticalOffset = 20
        setTokenizer(CompletionTokenizer())
    }

    // endregion Constructor

    override fun enoughToFilter(): Boolean {
        if (editorSettings.highlightSyntax)
            return super.enoughToFilter()

        return false
    }

    override fun onDraw(canvas: Canvas) {
        if (editorSettings.highlightCurrentLine) {
            try {
                getLineBounds(getLine(), mLineBounds)
                mPaintHighlight.color = ContextCompat.getColor(context, R.color.line_highlight)
                canvas.drawRect(mLineBounds, mPaintHighlight)
            } catch (e: Exception) {
            }
        }

        val normalPadding = context.dpToPx(6).toInt()

        if (editorSettings.showLineNumbers) {
            val leftPadding = context.dpToPx(digitCount * 10 + 14).toInt()
            setPadding(leftPadding, normalPadding, normalPadding, normalPadding)

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

    override fun showDropDown() {
        super.showDropDown()
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
                KeyEvent.KEYCODE_Z -> {
                    undo()
                    true
                }
                KeyEvent.KEYCODE_Y -> {
                    redo()
                    true
                }
                else -> super.onKeyDown(keyCode, event)
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // region Private Functions

    private fun updateSettings() {
        setDropDownBackgroundResource(R.color.main_background)

        val fontSizeFloat = editorSettings.fontSize.toFloat()
        textSize = fontSizeFloat
        mTextPaint.textSize = context.spToPx(fontSizeFloat)

        mSyntaxHighlighter.clearSpans(text)
        mUpdateJob = highlightWithoutChange()
    }

    private fun autoCloseBrackets(e: Editable, count: Int) {
        val selectedStr = SpannableStringBuilder(e)
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

    private fun highlightWithoutChange() = GlobalScope.launch(Dispatchers.Main.immediate) {
        delay(SYNTAX_HIGHLIGHTING_DELAY)

        mModified = false

        try {
            withContext(Dispatchers.Default) {
                highlight(text)
            }
        } catch (e: Throwable) {
            // Some devices will throw a CalledFromWrongThreadException
            e.printStackTrace()
            highlight(text)
        }

        mModified = true
    }

    private fun highlight(editable: Editable) {
        if (!editorSettings.highlightSyntax || editable.isEmpty()) return

        // don't use e.clearSpans() because it will remove too much
        mSyntaxHighlighter.clearSpans(editable)
        mSyntaxHighlighter.highlight(editable)
    }

    private fun getLine(): Int = if (selectionStart == -1) {
        0
    } else {
        mLayout.getLineForOffset(selectionStart)
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

                if (c != ' ' && c != '\t') break
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
        canvas.drawText((line + 1).toString(), positionX + context.dpToPx(2), positionY.toFloat(), mTextPaint)
    }

    private fun getSelectedText(): CharSequence? {
        val start = selectionStart
        val end = selectionEnd

        return if (start == -1 || end == -1) {
            null
        } else if (end > start) {
            text.subSequence(start, end)
        } else {
            text.subSequence(end, start)
        }
    }

    private fun updateUndoRedoBeforeTextChanged(s: CharSequence, start: Int, count: Int) {
        if (!mIsDoingUndoRedo) {
            mUpdateLastChange = if (count < UndoStack.MAX_SIZE) {
                TextChange(oldText = s.subSequence(start, start + count).toString(), start = start)
            } else {
                mUndoStack.removeAll()
                mRedoStack.removeAll()
                null
            }
        }
    }

    private fun updateUndoRedoOnTextChanged(s: CharSequence, start: Int, count: Int) {
        val lastChange = mUpdateLastChange
        if (lastChange == null || mIsDoingUndoRedo) return

        if (count < UndoStack.MAX_SIZE) {
            lastChange.newText = s.subSequence(start, start + count).toString()

            if(start == lastChange.start &&
                ((lastChange.oldText.isNotEmpty()
                    || lastChange.newText.isNotEmpty())
                    && lastChange.oldText != lastChange.newText)) {

                mUndoStack.push(lastChange)
                mRedoStack.removeAll()
            }
        } else {
            mUndoStack.removeAll()
            mRedoStack.removeAll()
        }

        mUpdateLastChange = null
    }


    // endregion Private Functions

    // region Public Functions

    fun undo() {
        val textChange = mUndoStack.pop()

        when {
            textChange == null -> context.toast("Nothing to Undo")
            textChange.start >= 0 -> {
                mIsDoingUndoRedo = true

                if (textChange.start > text.length) {
                    textChange.start = text.length
                }

                var end = textChange.start + textChange.newText.length
                if (end < 0) end = 0
                if (end > text.length) end = text.length

                text.replace(textChange.start, end, textChange.oldText)
                Selection.setSelection(text, textChange.start + textChange.oldText.length)
                mRedoStack.push(textChange)
                mIsDoingUndoRedo = false
            }
            else -> mRedoStack.clear()
        }
    }

    fun redo() {
        val textChange = mRedoStack.pop()

        when {
            textChange == null -> context.toast("Nothing to Redo")
            textChange.start >= 0 -> {
                mIsDoingUndoRedo = true
                text.replace(textChange.start, textChange.start + textChange.oldText.length, textChange.newText)
                Selection.setSelection(text, textChange.start + textChange.newText.length)
                mUndoStack.push(textChange)
                mIsDoingUndoRedo = false
            }
            else -> mRedoStack.clear()
        }
    }

    fun setTextHighlighted(text: CharSequence) = GlobalScope.launch(Dispatchers.Main.immediate) {
        mUpdateJob?.cancel()

        mModified = false
        val highlightedText = SpannableStringBuilder(text)
        launch(Dispatchers.Default) { highlight(highlightedText) }
        setText(highlightedText)
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
        val endSelection = selectionStart
        var needle = searchText
        var haystack = text.toString()

        if (endSelection == -1 || needle.isEmpty() || haystack.isEmpty()) return

        if (ignoreCase) {
            needle = needle.toLowerCase()
            haystack = haystack.toLowerCase()
        }

        var foundPosition = haystack.substring(0, endSelection).lastIndexOf(needle)
        if (foundPosition == -1) {
            foundPosition = haystack.lastIndexOf(needle)
        }
        if (foundPosition != -1) {
            setSelection(foundPosition, needle.length + foundPosition)
        }
    }

    fun goToLine(toLine: Int) {
        val line = when {
            toLine - 1 < 0 -> 0
            toLine > lineCount -> lineCount - 1
            else -> toLine - 1
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
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?

        getSelectedText()?.let {
            val clipData = ClipData.newPlainText("text", it)
            clipboard?.primaryClip = clipData
        }
    }

    fun paste() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
        if (clipboard == null || !clipboard.hasPrimaryClip()) return

        val item = clipboard.primaryClip?.getItemAt(0) ?: return
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

        if (startAt > 1 && endAt > 1 && len == endAt)
            startAt--

        editableText.delete(startAt, endAt)
    }

    fun duplicateLine() {
        val selectedStart = selectionStart
        val selectedEnd = selectionEnd
        if (selectedStart < 0 || selectedEnd < 0) return

        var start = min(selectedStart, selectedEnd)
        var end = max(selectedStart, selectedEnd)

        if (end > start) end--
        while (end < text.length && text[end] != '\n') end++
        while (start > 0 && text[start - 1] != '\n') start--

        editableText.insert(end, "\n" + text.subSequence(start, end).toString())
    }

    // endregion Public Functions
}
