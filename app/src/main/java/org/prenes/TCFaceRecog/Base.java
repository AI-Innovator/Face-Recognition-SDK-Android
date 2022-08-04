package org.prenes.TCFaceRecog;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

import org.prenes.face.FaceMethod;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

/**
 * Created by demid on 4/22/2017.
 */

public class Base {
    public static String getAppDir(Context context) {
        PackageManager m = context.getPackageManager();
        String s = context.getPackageName();
        try {
            PackageInfo p = m.getPackageInfo(s, 0);
            return p.applicationInfo.dataDir;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w("yourtag", "Error Package name not found ", e);
        }

        return null;
    }

    public static void copyRes(Context context, String path, int resID) {
        try {
            InputStream inputStream = context.getResources().openRawResource(resID);
            byte[] dst = new byte[inputStream.available()];
            inputStream.read(dst);
            inputStream.close();

            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(path));
            bos.write(dst);
            bos.flush();
            bos.close();

            Log.e("TestEngine", "Dic Path: " + path);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void saveLivenessThres(Context context, float liveThres)
    {
        FaceMethod.setLivenessThreshold(liveThres);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putFloat("LivenessThres", liveThres);
        editor.apply();
    }

    public static void saveRecogThres(Context context, float recogThres)
    {
        FaceMethod.setVerificationThreshold(recogThres);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putFloat("RecogThres", recogThres);
        editor.apply();
    }

    public static void saveBlockClosedEye(Context context, boolean value)
    {
        FaceMethod.allowClosedEye(!value);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("BlockClosedEye", value);
        editor.apply();
    }

    public static void saveBlockMaskedFace(Context context, boolean value)
    {
        FaceMethod.allowOccludedKeyPoints(!value);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("BlockMaskedFace", value);
        editor.apply();
    }

    public static float getRecogThres(Context context)
    {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        float recogThres = preferences.getFloat("RecogThres", 83.5f);
        FaceMethod.setVerificationThreshold(recogThres);
        return recogThres;
    }

    public static float getLivenessThres(Context context)
    {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        float livenessThres = preferences.getFloat("LivenessThres", 50f);
        FaceMethod.setLivenessThreshold(livenessThres);
        return livenessThres;
    }

    public static boolean getBlockClosedEye(Context context)
    {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean value = preferences.getBoolean("BlockClosedEye", true);
        FaceMethod.allowClosedEye(!value);
        return value;
    }

    public static boolean getBlockMaskedFace(Context context)
    {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean value = preferences.getBoolean("BlockMaskedFace", true);
        FaceMethod.allowOccludedKeyPoints(!value);
        return value;

    }

}
