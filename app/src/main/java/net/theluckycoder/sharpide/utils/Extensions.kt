package net.theluckycoder.sharpide.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.support.annotation.IdRes
import android.support.v4.app.ActivityCompat
import android.view.View
import java.io.IOException


val Any.string get() = toString()

fun Activity.verifyStoragePermission() {
    val permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    if (permission != PackageManager.PERMISSION_GRANTED) {
        // We don't have permission so prompt the user
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), Constants.PERMISSION_REQUEST_CODE)
    }
}

fun <T : View> Activity.bind(@IdRes res: Int): Lazy<T> =
        lazy(LazyThreadSafetyMode.NONE) { findViewById<T>(res) }

fun Context.saveFileInternally(fileName: String, data: String): Boolean {
    return try {
        val fos = openFileOutput(fileName, Context.MODE_PRIVATE)
        fos.write(data.toByteArray())
        fos.close()
        true
    } catch (e: IOException) {
        e.printStackTrace()
        false
    }
}