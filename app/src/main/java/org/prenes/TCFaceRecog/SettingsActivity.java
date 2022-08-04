package org.prenes.TCFaceRecog;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.adorkable.iosdialog.AlertDialog;
import org.prenes.face.FaceMethod;
//import com.yokkomi.commons.preference.seekbar.FollowSeekBarPreference;
//import com.yokkomi.commons.preference.seekbar.SeekBarPreference;

import java.io.File;

import static org.prenes.face.FaceMethod.allowClosedEye;

public class SettingsActivity extends AppCompatActivity {

    private final int ACTIVATION_CODE = 12;
    private final int OVERLAY_PERMISSION_CODE = 11;
    private final int REGISTER_CODE = 10;
    private int mActiviated;
    final String TAG = "SettingsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar_settings);
        setSupportActionBar(toolbar);
        mActiviated = FaceMethod.SDK_SUCCESS;

        addPermissions();
        init();

        refreshView();
    }

    public void init()
    {
        try {
            if(mActiviated == FaceMethod.SDK_SUCCESS) {
                copyDict();
                long prev = Debug.getNativeHeapAllocatedSize();
                int ret = FaceMethod.initFaceLogOnSDK(Base.getAppDir(this), Base.getAppDir(this));
                long last = Debug.getNativeHeapAllocatedSize();
                Log.e(TAG, "Init Face: " + ret + "memory: " + (last - prev));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void copyDict() {
        File resFile1 = new File(Base.getAppDir(this) + "/rajk.bin");
        if(!resFile1.exists()) {
            Base.copyRes(this, Base.getAppDir(this) + "/rajk.bin", R.raw.rajk);
        }

        resFile1 = new File(Base.getAppDir(this) + "/how.bin");
        if(!resFile1.exists())
        {
            Base.copyRes(this, Base.getAppDir(this) + "/how.bin", R.raw.how);
        }

        resFile1 = new File(Base.getAppDir(this) + "/ay105.bin");
        if(!resFile1.exists()) {
            Base.copyRes(this, Base.getAppDir(this) + "/ay105.bin", R.raw.ay105);
        }

        resFile1 = new File(Base.getAppDir(this) + "/occ.bin");
        if(!resFile1.exists()) {
            Base.copyRes(this, Base.getAppDir(this) + "/occ.bin", R.raw.occ);
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();
    }

    public void addPermissions()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED)
            {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                        1);
            }

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        2);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED)
            {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        3);
            }
        }
    }

    public void refreshView()
    {
        if (FaceMethod.isEnrolledPerson() == FaceMethod.SDK_SUCCESS)
        {
            getFragmentManager().beginTransaction().replace(R.id.main_frame, new DeleteFacePreferenceFragment()).commit();
        }
        else
        {
            getFragmentManager().beginTransaction().replace(R.id.main_frame, new RegisterFacePreferenceFragment()).commit();
        }
    }

    public void deleteFace()
    {
        FaceMethod.deletePerson();
        refreshView();
    }

    public void registerFace()
    {
        Intent intent = new Intent(this, EnrollFaceActivity.class);
        intent.putExtra("mode", 0);
        intent.putExtra("name", "Administrator");
        startActivityForResult(intent, REGISTER_CODE);
    }

    private int permissonAccepted = 0;
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch (requestCode)
        {
            case REGISTER_CODE:
            {
                if(resultCode == RESULT_OK)
                {
                    refreshView();
                }
            }
            break;
            case ACTIVATION_CODE:
            {
                if (resultCode != RESULT_OK)
                {
                    finish();
                }
                else
                {
                    mActiviated = data.getExtras().getInt("Result");
                    if(mActiviated == FaceMethod.SDK_SUCCESS) {
                        copyDict();
                        int ret = FaceMethod.initFaceLogOnSDK(Base.getAppDir(this), Base.getAppDir(this));
                        addPermissions();
                    }
                }
                break;
            }
            case OVERLAY_PERMISSION_CODE: {
            }
        }
    }
    /**
     * This fragment shows data and sync preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class RegisterFacePreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_register);
            setHasOptionsMenu(true);

            Preference myPref = (Preference) findPreference("registerFace");
            myPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference preference) {
                    //open browser or intent here
                    final SettingsActivity self = (SettingsActivity) getActivity();
                    self.registerFace();
                    return true;
                }
            });
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * This fragment shows data and sync preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class DeleteFacePreferenceFragment extends PreferenceFragment {
//        FollowSeekBarPreference recogThresPref;
//        FollowSeekBarPreference livenessThresPref;
        SwitchPreference closedEyePref;
        SwitchPreference maskedFacePref;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_remove);
            setHasOptionsMenu(true);
/////////////////////////////////////////////////////////////////////////////////////////////////////
            Preference myPref = (Preference) findPreference("deleteFace");
            myPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
            {
                public boolean onPreferenceClick(Preference preference)
                {
                     new AlertDialog(getActivity()).builder().setTitle("Delete Registered Face")
                            .setMsg("Face ID will not continue to use, delete?")
                            .setPositiveButton("Delete", new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    final SettingsActivity self = (SettingsActivity) getActivity();
                                    self.deleteFace();
                                }
                            }).setNegativeButton("Cancel", new View.OnClickListener()
                     {
                        @Override
                        public void onClick(View v)
                        {

                        }
                    }).show();
                    return true;
                }
            });
/////////////////////////////////////////////////////////////////////////////////////////////////////
//            recogThresPref = (FollowSeekBarPreference) findPreference("recogThres");
//            recogThresPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
//                @Override
//                public boolean onPreferenceClick(Preference preference) {
//                    recogThresPref.setValue(Float.valueOf(recogThresPref.getSummary().toString()));
//                    return true;
//                }
//            });
//            recogThresPref.setOnThresSavedListener(new SeekBarPreference.OnThresSavedListener() {
//                @Override
//                public void onThresSaved(float thres) {
//                    Base.saveRecogThres(getContext(), thres);
//                }
//            });
/////////////////////////////////////////////////////////////////////////////////////////////////////
//            livenessThresPref = (FollowSeekBarPreference) findPreference("liveThres");
//            livenessThresPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
//                @Override
//                public boolean onPreferenceClick(Preference preference) {
//                    livenessThresPref.setValue(Float.valueOf(livenessThresPref.getSummary().toString()));
//                    return true;
//                }
//            });
//            livenessThresPref.setOnThresSavedListener(new SeekBarPreference.OnThresSavedListener() {
//                @Override
//                public void onThresSaved(float thres) {
//                    Base.saveLivenessThres(getContext(), thres);
//                }
//            });
/////////////////////////////////////////////////////////////////////////////////////////////////////
            closedEyePref = (SwitchPreference)findPreference("closedEye");
            closedEyePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    Base.saveBlockClosedEye(getContext(), !closedEyePref.isChecked()); // Set Updated Value
                    return true;
                }
            });
/////////////////////////////////////////////////////////////////////////////////////////////////////
            maskedFacePref = (SwitchPreference)findPreference("maskedFace");
            maskedFacePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    Base.saveBlockMaskedFace(getContext(), !maskedFacePref.isChecked()); // Set Updated Value
                    return true;
                }
            });
/////////////////////////////////////////////////////////////////////////////////////////////////////
            Preference offPref = findPreference("turnOff");
            offPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startActivity(new Intent(getActivity(), ScreenOffActivity.class));
                    getActivity().overridePendingTransition(0, 0);
                    return true;
                }
            });
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            return super.onOptionsItemSelected(item);
        }

        @Override
        public void onResume()
        {
            super.onResume();
//            recogThresPref.setSummary(String.valueOf(Base.getRecogThres(getContext())));
//            livenessThresPref.setSummary(String.valueOf(Base.getLivenessThres(getContext())));
            closedEyePref.setChecked(Base.getBlockClosedEye(getContext()));
            maskedFacePref.setChecked(Base.getBlockMaskedFace(getContext()));
        }
    }
}
