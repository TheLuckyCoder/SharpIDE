package net.theluckycoder.sharpide

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import net.theluckycoder.sharpide.activities.ConsoleActivity
import net.theluckycoder.sharpide.utils.extensions.startActivity
import net.theluckycoder.sharpide.utils.extensions.toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.roundToInt

class EditorFile(val file: File) : Parcelable {

    private var savedToDisk = false

    constructor(parcel: Parcel) : this(parcel.readSerializable() as File) {
        savedToDisk = parcel.readByte() != 0.toByte()
    }

    fun isSavedToDisk() = savedToDisk && file.exists()

    fun getLastModified(): String =
        SimpleDateFormat.getDateTimeInstance().format(Date(file.lastModified()))

    fun getFileSize(): String {
        val fileSize = file.length()

        return when {
            fileSize < 1024 -> "${fileSize}B"
            fileSize > 1024 && fileSize < 1024 * 1024 -> "${((fileSize / 1024 * 100.0).roundToInt() / 100.0)}KB"
            else -> "${(fileSize / (1024 * 1204) * 100 / 100.0)}MB"
        }
    }

    fun computeFileInfo(lineCount: Int): String =
        "Name: ${file.nameWithoutExtension}\n" +
            "Path: ${file.parent}\n" +
            "Last Modified: ${getLastModified()}\n" +
            "Size: ${getFileSize()}\n" +
            "Lines Count: $lineCount"

    val name: String = file.name

    suspend fun saveFileAsync(content: String) {
        savedToDisk = true
        return withContext(Dispatchers.IO) { file.writeText(content) }
    }

    suspend fun saveFileForConsoleAsync(content: String, context: Context): Boolean = coroutineScope {
        try {
            val result = async(Dispatchers.IO) { file.writeText(content) }

            withContext(Dispatchers.IO) { saveConsoleFiles(content, context) }
            result.await()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    companion object CREATOR : Parcelable.Creator<EditorFile> {

        override fun createFromParcel(parcel: Parcel) = EditorFile(parcel)

        override fun newArray(size: Int): Array<EditorFile?> = arrayOfNulls(size)

        private fun saveConsoleFiles(content: String, context: Context) {
            context.openFileOutput("main.js", Context.MODE_PRIVATE).use {
                it.write(content.toByteArray())
            }

            if (!context.fileList().contains("index.html")) {
                context.openFileOutput("index.html", Context.MODE_PRIVATE).use {
                    val htmlContent = "<!DOCTYPE html><html><head>" +
                        "<script type=\"text/javascript\" src=\"main.js\"></script>" +
                        "</head><body></body></html>"
                    it.write(htmlContent.toByteArray())
                }
            }
        }

        suspend fun loadFileAsync(editorFile: EditorFile): String? = coroutineScope {
            val content = try {
                val result = withContext(Dispatchers.IO) { editorFile.file.readText() }
                editorFile.savedToDisk = true
                result
            } catch (e: Exception) {
                e.printStackTrace()
                return@coroutineScope null
            }

            content
        }
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeByte(if (savedToDisk) 1 else 0)
    }

    override fun describeContents(): Int {
        return 0
    }
}
