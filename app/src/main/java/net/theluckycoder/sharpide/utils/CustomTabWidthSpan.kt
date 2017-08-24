package net.theluckycoder.sharpide.utils

import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan

class CustomTabWidthSpan : ReplacementSpan() {

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int = 20

    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) { }

}
