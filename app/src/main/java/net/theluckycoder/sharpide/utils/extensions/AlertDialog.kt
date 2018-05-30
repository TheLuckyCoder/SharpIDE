package net.theluckycoder.sharpide.utils.extensions

import android.content.Context
import android.support.annotation.ColorRes
import android.support.annotation.StringRes
import android.support.annotation.StyleRes
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan

fun Context.alertDialog(@StyleRes style: Int) = AlertDialog.Builder(this, style)

fun AlertDialog.Builder.setTitleWithColor(titleText: CharSequence, @ColorRes color: Int): AlertDialog.Builder {
    // Initialize a new foreground color span instance
    val foregroundColorSpan = ForegroundColorSpan(ContextCompat.getColor(context, color))

    // Initialize a new spannable string builder instance
    val ssBuilder = SpannableStringBuilder(titleText)

    // Apply the text color span
    ssBuilder.setSpan(foregroundColorSpan, 0, titleText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

    setTitle(titleText)

    return this
}

fun AlertDialog.Builder.setTitleWithColor(@StringRes titleId: Int, @ColorRes color: Int): AlertDialog.Builder {
    return setTitleWithColor(context.getText(titleId), color)
}
