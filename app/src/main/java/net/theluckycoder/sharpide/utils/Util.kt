package net.theluckycoder.sharpide.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.support.v4.app.ActivityCompat
import android.util.Log
import java.io.*

class Util {

    val mainFolder = Environment.getExternalStorageDirectory().absolutePath + "/SharpIDE/"
    val minifyFolder = mainFolder + "Minify/"

    fun verifyStoragePermissions(activity: Activity, permissionRequestCode : Int) {
        // Check if we have write permission
        val permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), permissionRequestCode)
        }
    }

    fun saveFile(file: File, data: Array<String>) {
        var fos: FileOutputStream? = null
        try {
            fos = FileOutputStream(file)
        } catch (e: FileNotFoundException) {
            Log.e("Error saving file", e.message, e)
        }

        try {
            try {
                for (i in data.indices) {
                    if (fos != null)
                        fos.write(data[i].toByteArray())
                    if (i < data.size - 1) {
                        if (fos != null)
                            fos.write("\n".toByteArray())
                    }
                }
            } catch (e: IOException) {
                Log.e("IOException", e.message, e)
            }

        } finally {
            try {
                if (fos != null)
                    fos.close()
            } catch (e: IOException) {
                Log.e("IOException", e.message, e)
            }

        }
    }

    private fun convertStreamToString(`is`: InputStream): String {
        val reader = BufferedReader(InputStreamReader(`is`))
        val sb = StringBuilder()
        val line = reader.readLine()

        sb.append(line).append("\n")
        reader.close()
        return sb.toString()
    }

    fun loadFile(filePath: String): String? {
        val file = File(filePath)
        var fin: FileInputStream? = null
        try {
            fin = FileInputStream(file)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        }

        var ret: String? = null
        try {
            ret = convertStreamToString(fin!!)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            if (fin != null)
                fin.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return ret
    }

    fun createFile(file: File) {
        var fos: FileOutputStream? = null
        val data = arrayOf("")
        try {
            fos = FileOutputStream(file)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        }

        try {
            try {
                for (i in data.indices) {
                    if (fos != null)
                        fos.write(data[i].toByteArray())
                    if (i < data.size - 1) {
                        if (fos != null)
                            fos.write("\n".toByteArray())
                    }
                }
            } catch (e: IOException) {
                Log.e("IOException", e.message, e)
            }

        } finally {
            try {
                if (fos != null)
                    fos.close()
            } catch (e: IOException) {
                Log.e("IOException", e.message, e)
            }

        }
    }

    @Throws(IOException::class)
    fun saveFileInternally(context: Context, fileName: String, data: String) {
        val fOut = context.openFileOutput(fileName, Context.MODE_PRIVATE)
        fOut.write(data.toByteArray())
        fOut.close()
    }
}
