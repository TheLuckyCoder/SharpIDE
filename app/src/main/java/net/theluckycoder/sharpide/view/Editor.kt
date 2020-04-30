package net.theluckycoder.sharpide.view

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import net.theluckycoder.sharpide.R
import net.theluckycoder.sharpide.utils.EditorSettings
import net.theluckycoder.sharpide.utils.extensions.toDp

class Editor(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {

    private val numLinesView = EditorNumberLines(context)
    private val dividerView = View(context)
    val editText = CodeEditor(context)

    var text: String
        get() = editText.text.toString()
        set(value) {
            editText.setText(value)
        }
    var lineCount = 0
        private set

    init {
        orientation = HORIZONTAL

        // Add the views
        addView(numLinesView)
        addView(dividerView)
        addView(editText)

        dividerView.setBackgroundColor(ContextCompat.getColor(context, R.color.line_highlight))

        // Deliver touch events on numLines to editText
        numLinesView.setOnTouchListener { _, event ->
            editText.onTouchEvent(event)
        }

        editText.changeLineCountListener = { newLineCount ->
            lineCount = newLineCount

            val lines = StringBuilder(newLineCount * 2)
            for (i in 1..newLineCount) lines.append(i).append('\n')
            numLinesView.text = lines.toString()
        }

        // Observe changes in editText scroll
        editText.changeVerticalScrollListener = { scrollY ->
            numLinesView.scrollY = scrollY
        }
    }

    fun setEditorSettings(settings: EditorSettings) {
        editText.editorSettings = settings
        setTextSize(settings.fontSize.toFloat())

        numLinesView.isVisible = settings.showLineNumbers
        dividerView.isVisible = settings.showLineNumbers
    }

    /**
     * Set text size to edit text and number lines
     * @param size size of font as float
     */
    fun setTextSize(size: Float) {
        numLinesView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size)
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size)
    }

    override fun setLayoutParams(params: ViewGroup.LayoutParams?) {
        super.setLayoutParams(params)
        params ?: return

        numLinesView.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, params.height)

        dividerView.layoutParams = LayoutParams(context.toDp(1), params.height)

        editText.layoutParams = LayoutParams(0, params.height).apply {
            weight = 1f
        }
    }
}
