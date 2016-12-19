package net.theluckycoder.scriptcraft.utils;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class Util {
    public static final String mainFolder = Environment.getExternalStorageDirectory().getAbsolutePath() + "/ScriptCraft/";

    private static final int REQUEST_EXTERNAL_STORAGE = 1;

    public static void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_EXTERNAL_STORAGE);
        }
    }

    public static void saveFile(File file, String[] data) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            Log.e("Error saving file", e.getMessage(), e);
        }
        try {
            try {
                for (int i = 0; i < data.length; i++) {
                    if (fos != null)
                        fos.write(data[i].getBytes());
                    if (i < data.length-1) {
                        if (fos != null)
                            fos.write("\n".getBytes());
                    }
                }
            }
            catch (IOException e) {
                Log.e("IOException", e.getMessage(), e);
            }
        }
        finally {
            try {
                if (fos != null)
                    fos.close();
            }
            catch (IOException e) {
                Log.e("IOException", e.getMessage(), e);
            }
        }
    }

    private static String convertStreamToString(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null)
            sb.append(line).append("\n");
        reader.close();
        return sb.toString();
    }

    public static String loadFile(String filePath) throws Exception {
        File fl = new File(filePath);
        FileInputStream fin = new FileInputStream(fl);
        String ret = convertStreamToString(fin);
        //Make sure you close all streams.
        fin.close();
        return ret;
    }

    public static void createFile(File file) {
        FileOutputStream fos = null;
        String[] data = new String[]{""};
        try {
            fos = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            Log.e("FileNotFoundException", e.getMessage(), e);
        }
        try {
            try {
                for (int i = 0; i < data.length; i++) {
                    if (fos != null)
                        fos.write(data[i].getBytes());
                    if (i < data.length - 1) {
                        if (fos != null)
                            fos.write("\n".getBytes());
                    }
                }
            }
            catch (IOException e) {
                Log.e("IOException", e.getMessage(), e);
            }
        }
        finally {
            try {
                if (fos != null)
                    fos.close();
            }
            catch (IOException e) {
                Log.e("IOException", e.getMessage(), e);
            }
        }
    }
}
