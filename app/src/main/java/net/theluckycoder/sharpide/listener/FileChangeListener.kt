package net.theluckycoder.sharpide.listener

interface FileChangeListener {
    fun onFileOpen()
    fun onFileChanged(save: Boolean)
    fun onFileSave()
}
