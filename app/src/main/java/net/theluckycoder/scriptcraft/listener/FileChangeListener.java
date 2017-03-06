package net.theluckycoder.scriptcraft.listener;

@SuppressWarnings("ALL")
public interface FileChangeListener {

    void onFileOpen();
    void onFileChanged(boolean save);
    void onFileSave();
}
