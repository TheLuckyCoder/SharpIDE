package net.theluckycoder.sharpide.utils.extensions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.TypedValue
import android.view.View
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import net.theluckycoder.sharpide.utils.Const
import kotlin.math.max

fun String.replace(oldString: String, newString: String): String {
    if (isEmpty() || oldString.isEmpty()) return this

    var start = 0
    var end = this.indexOf(oldString, start)

    if (end == -1) return this

    val increase = max(0, newString.length - oldString.length)

    val builder = StringBuilder(this.length + increase * 16)
    while (end != -1) {
        builder.append(this.substring(start, end)).append(newString)
        start = end + oldString.length
        end = indexOf(oldString, start)
    }
    builder.append(substring(start))

    return builder.toString()
}

fun CharSequence.containsAny(searchChars: CharArray): Boolean {
    if (isEmpty() || searchChars.isEmpty()) return false

    val last = length - 1
    val searchLast = searchChars.size - 1

    for (i in 0 until length) {
        val ch = this[i]
        for (j in searchChars.indices) {
            if (searchChars[j] == ch) {
                if (ch.isHighSurrogate()) {
                    if (j == searchLast) {
                        // missing low surrogate, fine, like String.indexOf(String)
                        return true
                    }
                    if (i < last && searchChars[j + 1] == this[i + 1])
                        return true
                } else {
                    // ch is in the Basic Multilingual Plane
                    return true
                }
            }
        }
    }
    return false
}

fun Context.inflate(@LayoutRes resource: Int): View = View.inflate(this, resource, null)

fun Context.checkHasPermission(): Boolean {
    return ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
        PackageManager.PERMISSION_GRANTED
}

fun Activity.verifyStoragePermission() {
    if (!checkHasPermission()) {
        // We don't have permission so prompt the user
        ActivityCompat.requestPermissions(this,
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), Const.PERMISSION_REQUEST_CODE)
    }
}

fun <T> lazyFast(initializer: () -> T): Lazy<T> = lazy(LazyThreadSafetyMode.NONE) { initializer() }

fun Context.dpToPx(dp: Int): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics)

fun Context.spToPx(sp: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)

fun setUseNightMode(use: Boolean) {
    if (use) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    } else {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }
}

