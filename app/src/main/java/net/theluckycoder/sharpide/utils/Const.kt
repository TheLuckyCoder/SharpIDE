package net.theluckycoder.sharpide.utils

import android.os.Environment
import net.theluckycoder.sharpide.BuildConfig

object Const {
    const val PERMISSION_REQUEST_CODE = 100
    private val SDCARD_FOLDER = Environment.getExternalStorageDirectory().absolutePath + "/"
    val MAIN_FOLDER = SDCARD_FOLDER + "SharpIDE/"
    val MINIFY_FOLDER = MAIN_FOLDER + "Minify/"
    const val MARKET_LINK = "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}"
}
