package net.theluckycoder.sharpide.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.support.annotation.IdRes
import android.support.annotation.LayoutRes
import android.support.v4.app.ActivityCompat
import android.view.View
import java.io.IOException

fun CharSequence.ktReplace(oldString: String, newString: String): String {
    if (this.isEmpty() || oldString.isEmpty()) return this.toString()

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

fun Context.saveInternalFile(fileName: String, content: String): Boolean {
    return try {
        val fos = openFileOutput(fileName, Context.MODE_PRIVATE)
        fos.write(content.toByteArray())
        fos.close()
        true
    } catch (e: IOException) {
        e.printStackTrace()
        false
    }
}
