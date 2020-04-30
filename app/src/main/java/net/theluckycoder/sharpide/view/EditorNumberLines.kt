package net.theluckycoder.sharpide.view

import android.annotation.SuppressLint
import android.content.Context
import android.text.method.ScrollingMovementMethod
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.GravityCompat
import net.theluckycoder.sharpide.utils.extensions.toDp

class EditorNumberLines(context: Context) : AppCompatTextView(context) {

    companion object {
        private const val LEFT_PADDING = 4
    }

    init {
        // reset attributes
        includeFontPadding = false
        setPadding(context.toDp(LEFT_PADDING), 0, 0, 0)

        // set vertical scroll
        isVerticalScrollBarEnabled = true
        movementMethod = ScrollingMovementMethod()

        // default number line to "1"
        text = "1\n"

        // Moving content to TOP and RIGHT
        gravity = GravityCompat.END
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun performClick() = false
}
