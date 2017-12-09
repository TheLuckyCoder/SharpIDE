package net.theluckycoder.sharpide.utils

import android.os.Environment

object Const {
    const val PERMISSION_REQUEST_CODE = 100
    private val SDCARD_FOLDER = Environment.getExternalStorageDirectory().absolutePath + "/"
    val MAIN_FOLDER = SDCARD_FOLDER + "SharpIDE/"
    val MINIFY_FOLDER = MAIN_FOLDER + "Minify/"
}
