package net.theluckycoder.sharpide.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.support.v4.app.ActivityCompat
import android.util.Log
import java.io.*


val Any.string get() = toString()

fun Activity.verifyStoragePermission() {
    val permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    if (permission != PackageManager.PERMISSION_GRANTED) {
        // We don't have permission so prompt the user
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), Constants.PERMISSION_REQUEST_CODE)
    }
}

fun File.save(data: Array<String>): Boolean {
    val fos: FileOutputStream
    try {
        fos = FileOutputStream(this)
    } catch (e: FileNotFoundException) {
        Log.e("Error saving file", e.message, e)
        return false
    }

    try {
        try {
            for (i in data.indices) {
                fos.write(data[i].toByteArray())
                if (i < data.size - 1)
                    fos.write("\n".toByteArray())
            }
        } catch (e: IOException) {
            Log.e("IOException", e.message, e)
            return false
        }

    } finally {
        try {
            fos.close()
        } catch (e: IOException) {
            Log.e("IOException", e.message, e)
            return false
        }
    }
    return true
}

fun File.loadFile(): String {
    return try {
        val fis = FileInputStream(this)
        val dis = DataInputStream(fis)
        val br = BufferedReader(InputStreamReader(dis))

        val result = StringBuilder()

        var line = br.readLine()
        while (line != null) {
            result.append(line).append("\n")
            line = br.readLine()
        }
        dis.close()

        result.toString()
    } catch (e: IOException) {
        e.printStackTrace()
        ""
    }
}

fun File.createFile(): Boolean {
    try {
        val data = arrayOf("")
        val fos = FileOutputStream(this)

        for (i in data.indices) {
            fos.write(data[i].toByteArray())
            if (i < data.size - 1)
                fos.write("\n".toByteArray())
        }

        fos.close()
        return true
    } catch (e: FileNotFoundException) {
        e.printStackTrace()
    } catch (e: IOException) {
        e.printStackTrace()
    }
    return false
}

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
