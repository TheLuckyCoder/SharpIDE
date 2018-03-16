package net.theluckycoder.sharpide.repository

import android.content.Context
import kotlinx.coroutines.experimental.async
import java.io.File
import java.io.IOException

object MainRepository {

    fun loadFileContent(file: File) = async {
        var result = ""

        try {
            result = file.readText()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
        }
        result
    }

    fun saveFile(file: File, fileContent: String) = async {
        try {
            file.writeText(fileContent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveConsoleFiles(context: Context, fileContent: String) = async {
        context.openFileOutput("main.js", Context.MODE_PRIVATE).use {
            it.write(fileContent.toByteArray())
        }

        if (!context.fileList().contains("index.html")) {
            context.openFileOutput("index.html", Context.MODE_PRIVATE).use {
                val content = "<!DOCTYPE html><html><head>" +
                    "<script type=\"text/javascript\" src=\"main.js\"></script>" +
                    "</head><body></body></html>"
                it.write(content.toByteArray())
            }
        }
    }
}
