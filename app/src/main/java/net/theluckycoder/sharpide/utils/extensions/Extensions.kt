package net.theluckycoder.sharpide.utils.extensions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.support.annotation.IdRes
import android.support.annotation.LayoutRes
import android.support.v4.app.ActivityCompat
import android.util.TypedValue
import android.view.View
import net.theluckycoder.sharpide.utils.Const

fun CharSequence.ktReplace(oldString: String, newString: String): String {
    if (isEmpty() || oldString.isEmpty()) return this.toString()

    var start = 0
    var end = this.indexOf(oldString, start)

    if (end == -1) return this.toString()

    var increase = newString.length - oldString.length
    increase = if (increase < 0) 0 else increase
    increase *= 16

    val builder = StringBuilder(this.length + increase)
    while (end != -1) {
        builder.append(this.substring(start, end)).append(newString)
        start = end + oldString.length
        end = this.indexOf(oldString, start)
    }
    builder.append(this.substring(start))

    return builder.toString()
}

fun CharSequence.containsAny(searchChars: CharArray): Boolean {
    if (isEmpty() || searchChars.isEmpty()) return false

    val last = length - 1
    val searchLast = searchChars.size - 1

    for (i in 0 until length) {
        val ch = this[i]
        for (j in 0 until searchChars.size) {
            if (searchChars[j] == ch) {
                if (Character.isHighSurrogate(ch)) {
                    if (j == searchLast) {
                        // missing low surrogate, fine, like String.indexOf(String)
                        return true
                    }
                    if (i < last && searchChars[j + 1] == this[i + 1]) {
                        return true
                    }
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

fun Activity.verifyStoragePermission() {
    val permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    if (permission != PackageManager.PERMISSION_GRANTED) {
        // We don't have permission so prompt the user
        ActivityCompat.requestPermissions(this,
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), Const.PERMISSION_REQUEST_CODE)
    }
}

fun <T : View> Activity.bind(@IdRes res: Int): Lazy<T> = lazyFast { findViewById<T>(res) }

fun <T> lazyFast(initializer: () -> T): Lazy<T> = lazy(LazyThreadSafetyMode.NONE) { initializer() }

fun Context.dpToPx(dp: Int): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics)
