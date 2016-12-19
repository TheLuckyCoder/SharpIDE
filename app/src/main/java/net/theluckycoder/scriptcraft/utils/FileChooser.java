package net.theluckycoder.scriptcraft.utils;

import android.content.Context;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import net.theluckycoder.scriptcraft.R;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;

public class FileChooser {

    private static final String PARENT_DIR = "...(Parent Directory)";

    private final Context activity;
    private final ListView list;
    private final AlertDialog.Builder builder;
    private AlertDialog dialog;
    private File currentPath;

    // file selection event handling
    public interface FileSelectedListener {
        void fileSelected(File file);
    }

    public void setFileListener(FileSelectedListener fileListener) {
        this.fileListener = fileListener;
    }

    private FileSelectedListener fileListener;

    public FileChooser(final Context activity) {
        this.activity = activity;
        builder = new AlertDialog.Builder(activity);
        list = new ListView(activity);

        list.setPadding((int)activity.getResources().getDimension(R.dimen.activity_margin), 0, (int)activity.getResources().getDimension(R.dimen.activity_margin), 0);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int which, long id) {
                String fileChosen = (String) list.getItemAtPosition(which);

                File chosenFile = getChosenFile(fileChosen);

                if (chosenFile.isDirectory())
                    refresh(chosenFile);
                else {
                    if (fileListener != null)
                        fileListener.fileSelected(chosenFile);
                    dialog.dismiss();
                }
            }
        });

        builder.setView(list);
        builder.setNegativeButton(android.R.string.cancel, null);
        refresh(Environment.getExternalStorageDirectory());

        dialog = builder.create();
        dialog.show();
    }

    //Sort, filter and display the files for the given path.
    private void refresh(File path) {
        this.currentPath = path;
        if (path.exists()) {
            File[] dirs = path.listFiles(new FileFilter() {
                @Override public boolean accept(File file) {
                    return (file.isDirectory() && file.canRead() && !file.getName().substring(0, 1).equals("."));
                }
            });
            File[] files = path.listFiles(new FileFilter() {
                @Override public boolean accept(File file) {
                    String fileName = file.getName();
                    return (!file.isDirectory() && file.canRead() && !fileName.substring(0, 1).equals(".") && fileName.substring(fileName.lastIndexOf(".") + 1, fileName.length()).equals("js"));
                }
            });

            // convert to an array
            int i = 0;
            String[] fileList;
            if (path.getParentFile() == null || path.getAbsolutePath().equals(Environment.getExternalStorageDirectory().getAbsolutePath()))
                fileList = new String[dirs.length + files.length];
            else {
                fileList = new String[dirs.length + files.length + 1];
                fileList[i++] = PARENT_DIR;
            }
            Arrays.sort(dirs);
            Arrays.sort(files);
            for (File dir : dirs) { fileList[i++] = dir.getName(); }
            for (File file : files ) { fileList[i++] = file.getName(); }

            // refresh the user interface
            builder.setTitle(R.string.choose_file);
            list.setAdapter(new ArrayAdapter(activity, android.R.layout.simple_list_item_1, fileList) {
                @NonNull
                @Override
                public View getView(int pos, View view, @NonNull ViewGroup parent) {
                    view = super.getView(pos, view, parent);
                    ((TextView) view).setSingleLine(true);
                    return view;
                }
            });
        }
    }

    //Convert a relative filename into an actual File object.
    private File getChosenFile(String fileChosen) {
        if (fileChosen.equals(PARENT_DIR))
            return currentPath.getParentFile();
        else
            return new File(currentPath, fileChosen);
    }
}

