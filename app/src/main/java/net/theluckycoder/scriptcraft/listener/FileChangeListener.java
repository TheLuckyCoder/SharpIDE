package net.theluckycoder.scriptcraft.listener;

public interface FileChangeListener {

    void onFileOpen();
    void onFileChanged(boolean save);
    void onFileSave();
}
