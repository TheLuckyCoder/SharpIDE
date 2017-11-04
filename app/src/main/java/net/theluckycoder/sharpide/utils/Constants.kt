package net.theluckycoder.sharpide.utils

import android.os.Environment

object Constants {
    const val PERMISSION_REQUEST_CODE = 100
    @JvmField val mainFolder = Environment.getExternalStorageDirectory().absolutePath + "/SharpIDE/"
    @JvmField val minifyFolder = mainFolder + "Minify/"
}
