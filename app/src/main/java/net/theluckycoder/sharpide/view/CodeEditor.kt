package net.theluckycoder.sharpide.view

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.Selection
import android.text.Spanned
import android.text.TextWatcher
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import androidx.appcompat.widget.AppCompatMultiAutoCompleteTextView
import androidx.core.content.ContextCompat
import net.theluckycoder.sharpide.R
import net.theluckycoder.sharpide.utils.AppPreferences
import net.theluckycoder.sharpide.utils.EditorSettings
import net.theluckycoder.sharpide.utils.extensions.lazyFast
import net.theluckycoder.sharpide.utils.extensions.toDp
import net.theluckycoder.sharpide.utils.extensions.toast
import net.theluckycoder.sharpide.utils.text.SyntaxHighlighter
import net.theluckycoder.sharpide.utils.text.TextChange
import net.theluckycoder.sharpide.utils.text.UndoStack
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class CodeEditor(context: Context) : AppCompatMultiAutoCompleteTextView(context) {

    private val paintLineHighlight = Paint()
    private val lineBounds = Rect()
    private val syntaxHighlighter by lazyFast { SyntaxHighlighter(context) }

    private var numberLines = 0
    private var isDoingUndoRedo = false
    private var updateLastChange: TextChange? = null
    private val redoStack = UndoStack()
    private val undoStack = UndoStack()
    private var updateSyntaxHighlighting = true

    var editorSettings = EditorSettings.fromPreferences(AppPreferences(context))
        set(value) {
            if (field != value) {
                field = value
                updateSettings()
                postInvalidate()
            }
        }

    var changeLineCountListener: ((Int) -> Unit)? = null
    var changeVerticalScrollListener: ((Int) -> Unit)? = null

    init {
        // Remove default padding
        includeFontPadding = false
        setPadding(context.toDp(2), 0, 0, 0)

        // Moving content to TOP
        gravity = Gravity.TOP

        // Allow horizontally scrolling
        setHorizontallyScrolling(true)
        movementMethod = ScrollingMovementMethod()

        // Make text selectable
        setTextIsSelectable(true)

        // Hide keyboard suggestions
        inputType = inputType or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_MULTI_LINE

        if (!Build.MANUFACTURER.equals("SAMSUNG", true))
            imeOptions = imeOptions or EditorInfo.IME_FLAG_NO_EXTRACT_UI

        // "Remove" bottom line
        setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))

        filters = arrayOf(InputFilter { source, start, end, dest, dStart, dEnd ->
            if (editorSettings.autoIndent &&
                end - start == 1 &&
                start < source.length &&
                dStart < dest.length
            ) {
                val c = source[start]

                if (c == '\n')
                    return@InputFilter autoIndent(source, dest, dStart, dEnd)
            }

            source
        })

        addTextChangedListener(object : TextWatcher {
            private var count = 0

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                updateUndoRedoBeforeTextChanged(s, start, count)
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                this.count = count
                updateUndoRedoOnTextChanged(s, start, count)
            }

            override fun afterTextChanged(e: Editable?) {
                e ?: return
                if (count == 1) {
                    if (editorSettings.closeBrackets) autoCloseBrackets(e)
                    if (editorSettings.closeQuotes) autoCloseQuotes(e)
                }

                applySyntaxHighlight()
            }
        })

        // Set Adapter
        val adapter = ArrayAdapter(context, R.layout.item_suggestion, COMPLETION_KEYWORDS)
        setAdapter(adapter)
        dropDownVerticalOffset = 20
        setTokenizer(CompletionTokenizer())

        // Apply Loaded Settings
        updateSettings()
    }

    // region Public Functions

    fun setHighlightedText(editable: Editable) {
        updateSyntaxHighlighting = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            setText(editable, false)
        } else {
            text = editable
        }
        updateSyntaxHighlighting = true
    }

    fun findText(searchText: String, ignoreCase: Boolean) {
        var needle = searchText

        if (needle.isEmpty()) return

        var startSelection = selectionEnd
        var haystack = text.toString()

        if (ignoreCase) {
            needle = needle.toLowerCase(Locale.ROOT)
            haystack = haystack.toLowerCase(Locale.ROOT)
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
            needle = needle.toLowerCase(Locale.ROOT)
            haystack = haystack.toLowerCase(Locale.ROOT)
        }

        var foundPosition = haystack.substring(0, endSelection).lastIndexOf(needle)
        if (foundPosition == -1)
            foundPosition = haystack.lastIndexOf(needle)

        if (foundPosition != -1)
            setSelection(foundPosition, needle.length + foundPosition)
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
            clipboard?.setPrimaryClip(clipData)
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

    fun deleteSelected() {
        val startSelection = selectionStart
        val endSelection = selectionEnd
        if (startSelection == -1 || endSelection == -1) return

        editableText.delete(startSelection, endSelection)
        setSelection(selectionStart)
    }

    fun deleteLine() {
        val at = selectionEnd
        if (at == -1) return

        val line = layout.getLineForOffset(at)
        var startAt = layout.getLineStart(line)
        val endAt = layout.getLineEnd(line)
        val length = text.length

        if (startAt > 1 && endAt > 1 && length == endAt)
            startAt--

        editableText.delete(startAt, endAt)
        setSelection(selectionStart)
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

    fun undo() {
        val textChange = undoStack.pop()

        when {
            textChange == null -> context.toast("Nothing to Undo")
            textChange.start >= 0 -> {
                isDoingUndoRedo = true

                if (textChange.start > text.length) {
                    textChange.start = text.length
                }

                var end = textChange.start + textChange.newText.length
                if (end < 0) end = 0
                if (end > text.length) end = text.length

                text.replace(textChange.start, end, textChange.oldText)
                Selection.setSelection(text, textChange.start + textChange.oldText.length)
                redoStack.push(textChange)
                isDoingUndoRedo = false
            }
            else -> redoStack.clear()
        }
    }

    fun redo() {
        val textChange = redoStack.pop()

        when {
            textChange == null -> context.toast("Nothing to Redo")
            textChange.start >= 0 -> {
                isDoingUndoRedo = true
                text.replace(textChange.start, textChange.start + textChange.oldText.length, textChange.newText)
                Selection.setSelection(text, textChange.start + textChange.newText.length)
                undoStack.push(textChange)
                isDoingUndoRedo = false
            }
            else -> redoStack.clear()
        }
    }

    // endregion Public Functions

    // region Override Functions

    override fun getText(): Editable = super.getText()!!

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)

        // Only when the line count differs
        if (numberLines != lineCount) {
            numberLines = lineCount

            // notify to listener if exists
            changeLineCountListener?.invoke(lineCount)
        }
    }

    override fun onDraw(canvas: Canvas?) {
        if (canvas != null) {
            if (editorSettings.highlightCurrentLine) {
                try {
                    val currentLine = if (selectionStart == -1) {
                        -1
                    } else {
                        layout?.getLineForOffset(selectionStart) ?: -1
                    }
                    if (currentLine >= 0) {
                        getLineBounds(currentLine, lineBounds)
                        canvas.drawRect(lineBounds, paintLineHighlight)
                    }
                } catch (e: Exception) {
                }
            }
        }

        super.onDraw(canvas)
    }

    override fun onScrollChanged(horiz: Int, vert: Int, oldHoriz: Int, oldVert: Int) {
        super.onScrollChanged(horiz, vert, oldHoriz, oldVert)

        // Only when vertical scroll differs
        if (vert != oldVert) changeVerticalScrollListener?.invoke(scrollY)
    }

    // endregion Override Functions

    // region Private Functions

    private fun applySyntaxHighlight() {
        if (updateSyntaxHighlighting) {
            syntaxHighlighter.clearSpans(text)

            syntaxHighlighter.highlight(text)
        }
    }

    private fun updateUndoRedoBeforeTextChanged(s: CharSequence, start: Int, count: Int) {
        if (!isDoingUndoRedo) {
            updateLastChange = if (count < UndoStack.MAX_SIZE) {
                TextChange(oldText = s.subSequence(start, start + count).toString(), start = start)
            } else {
                undoStack.clear()
                redoStack.clear()
                null
            }
        }
    }

    private fun updateUndoRedoOnTextChanged(s: CharSequence, start: Int, count: Int) {
        val lastChange = updateLastChange
        if (lastChange == null || isDoingUndoRedo) return

        if (count < UndoStack.MAX_SIZE) {
            lastChange.newText = s.subSequence(start, start + count).toString()

            if (start == lastChange.start &&
                ((lastChange.oldText.isNotEmpty()
                    || lastChange.newText.isNotEmpty())
                    && lastChange.oldText != lastChange.newText)
            ) {
                undoStack.push(lastChange)
                redoStack.clear()
            }
        } else {
            undoStack.clear()
            redoStack.clear()
        }

        updateLastChange = null
    }

    private fun getSelectedText(): CharSequence? {
        val start = selectionStart
        val end = selectionEnd

        return when {
            start == -1 || end == -1 -> null
            end > start -> text.subSequence(start, end)
            else -> text.subSequence(end, start)
        }
    }

    private fun autoCloseBrackets(editable: Editable) {
        val start = selectionStart
        val end = selectionEnd

        if (editable.isNotEmpty() && start > 0 && start == end) {
            val c = editable[start - 1]
            val nextC = if (editable.length > start) editable[start] else ' '
            val prevC = if (start > 1) editable[start - 2] else ' '

            val resultChar = when {
                c == '(' && nextC != ')' && prevC != '(' -> ')'
                c == '{' && nextC != '}' && prevC != '{' -> '}'
                c == '[' && nextC != ']' && prevC != '[' -> ']'
                else -> ' '
            }

            if (resultChar != ' ') {
                editable.filters
                editable.insert(start, resultChar.toString())
                setSelection(start)
            }
        }
    }

    private fun autoCloseQuotes(editable: Editable) {
        val start = selectionStart
        val end = selectionEnd

        if (editable.isNotEmpty() && start > 0 && start == end) {
            val c = editable[start - 1]
            val nextC = if (editable.length > start) editable[start] else ' '
            val prevC = if (start > 1) editable[start - 2] else ' '

            val resultChar = when {
                c == '\'' && nextC != '\'' && prevC != '\'' -> '\''
                c == '\"' && nextC != '\"' && prevC != '\"' -> '\"'
                else -> ' '
            }

            if (resultChar != ' ') {
                editable.insert(start, resultChar.toString())
                setSelection(start)
            }
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
                    dest[iEnd] == c
                ) {
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
        if (pt < 0) indent += '\t'

        // append white space of previous line and new indent
        return source.toString() + indent
    }

    private fun updateSettings() {
        setDropDownBackgroundResource(R.color.main_background)

        paintLineHighlight.color = ContextCompat.getColor(context, R.color.line_highlight)

        textSize = editorSettings.fontSize.toFloat()

        applySyntaxHighlight()
    }

    // endregion Private Functions

    companion object {
        private val COMPLETION_KEYWORDS = arrayOf("break", "case:", "catch (e) {\n}", "super", "class", "const",
            "continue", "default:", "delete", "do {\n} while()", "yield", "else if () {\n}", "else {\n}", "extends",
            "let", "finally {\n}", "for", "function", "if () {\n}", "in", "instanceof", "new", "return",
            "switch () {\n}", "this", "throw", "try {\n}", "typeof", "var", "while", "with", "null", "true", "false")
    }
}
