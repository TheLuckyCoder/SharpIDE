package net.theluckycoder.sharpide.utils.extensions

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.support.annotation.ColorRes
import android.support.annotation.StringRes
import android.support.annotation.StyleRes
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.Toast
import java.io.Serializable

fun Context.dip(value: Int): Float = value * resources.displayMetrics.density

fun Context.browse(url: String, newTask: Boolean = false): Boolean {
    return try {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(url)
        if (newTask) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        e.printStackTrace()
        false
    }
}

fun Context.email(email: String, subject: String = "", text: String = ""): Boolean {
    val intent = Intent(Intent.ACTION_SENDTO)
    intent.data = Uri.parse("mailto:")
    intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(email))

    if (subject.isNotEmpty()) intent.putExtra(Intent.EXTRA_SUBJECT, subject)
    if (text.isNotEmpty()) intent.putExtra(Intent.EXTRA_TEXT, text)

    if (intent.resolveActivity(packageManager) != null) {
        startActivity(intent)
        return true
    }
    return false
}

fun Context.alertDialog(@StyleRes style: Int): AlertDialog.Builder
    = AlertDialog.Builder(this, style)

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

fun AlertDialog.Builder.setTitleWithColor(titleId: Int, color: Int): AlertDialog.Builder {
    return setTitleWithColor(context.getText(titleId), color)
}

// region Toast
fun Context.toast(text: CharSequence) {
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}

fun Context.longToast(text: CharSequence) {
    Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}

fun Context.toast(@StringRes resId: Int) {
    Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
}

fun Context.longToast(@StringRes resId: Int) {
    Toast.makeText(this, resId, Toast.LENGTH_LONG).show()
}
// endregion Toast

// region startActivity
inline fun <reified T : Activity> Context.startActivity() = startActivity(Intent(this, T::class.java))

inline fun <reified T : Activity> Activity.startActivityForResult(requestCode: Int) =
    startActivityForResult(Intent(this, T::class.java), requestCode)

inline fun <reified T : Activity> Context.startActivity(vararg params: Pair<String, Any?>) {
    startActivity(createIntent(this, T::class.java, params))
}

inline fun <reified T : Activity> Context.startActivity(options: Bundle?, vararg params: Pair<String, Any?>) {
    startActivity(createIntent(this, T::class.java, params), options)
}

inline fun <reified T : Activity> Activity.startActivityForResult(requestCode: Int, vararg params: Pair<String, Any?>) {
    startActivityForResult(createIntent(this, T::class.java, params), requestCode)
}

fun <T> createIntent(ctx: Context, clazz: Class<out T>, params: Array<out Pair<String, Any?>>): Intent {
    val intent = Intent(ctx, clazz)
    if (params.isNotEmpty()) fillIntentArguments(intent, params)
    return intent
}

private fun fillIntentArguments(intent: Intent, params: Array<out Pair<String, Any?>>) {
    params.forEach {
        val (k, v) = it
        when (v) {
            null -> intent.putExtra(k, null as Serializable?)
            is Int -> intent.putExtra(k, v)
            is Long -> intent.putExtra(k, v)
            is CharSequence -> intent.putExtra(k, v)
            is String -> intent.putExtra(k, v)
            is Float -> intent.putExtra(k, v)
            is Double -> intent.putExtra(k, v)
            is Char -> intent.putExtra(k, v)
            is Short -> intent.putExtra(k, v)
            is Boolean -> intent.putExtra(k, v)
            is Serializable -> intent.putExtra(k, v)
            is Bundle -> intent.putExtra(k, v)
            is Parcelable -> intent.putExtra(k, v)
            is Array<*> -> when {
                v.isArrayOf<CharSequence>() -> intent.putExtra(k, v)
                v.isArrayOf<String>() -> intent.putExtra(k, v)
                v.isArrayOf<Parcelable>() -> intent.putExtra(k, v)
                else -> throw Exception("Intent extra $k has wrong type ${v.javaClass.name}")
            }
            is IntArray -> intent.putExtra(k, v)
            is LongArray -> intent.putExtra(k, v)
            is FloatArray -> intent.putExtra(k, v)
            is DoubleArray -> intent.putExtra(k, v)
            is CharArray -> intent.putExtra(k, v)
            is ShortArray -> intent.putExtra(k, v)
            is BooleanArray -> intent.putExtra(k, v)
            else -> throw Exception("Intent extra $k has wrong type ${v.javaClass.name}")
        }
    }
}
// endregion startActivity
