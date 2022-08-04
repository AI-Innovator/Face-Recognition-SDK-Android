package org.prenes.face;

import android.content.Context;
import android.content.ContextWrapper;
import android.hardware.Camera;

public class FaceMethod extends ContextWrapper{

    public static final int SDK_SUCCESS = 0;
    public static final int SDK_FAILED = 1;
    public static final int SDK_NOT_INITIALIZED = 3;

    public static final int SDK_ENROLL_BEFORE_FRONT = 0;
    public static final int SDK_ENROLL_MOVE_NEAR = 5;
    public static final int SDK_ENROLL_MOVE_FAR = 6;
    public static final int SDK_ENROLL_OTHER_PERSON = 7;
    public static final int SDK_ENROLL_OCCLUSION = 8;
    public static final int SDK_ENROLL_NORMAL = 200;

    public static final int SDK_FACEDETECT_FAILED = 101;
    public static final int SDK_VERIFY_INVALID_POSE = 102;
    public static final int SDK_VERIFY_INVALID_IMAGEQUALITY = 103;
    public static final int SDK_VERIFY_INVALID_FACESIZE = 104;
    public static final int SDK_VERIFY_INVALID_FACEATTRIBUTES = 105;
    public static final int SDK_VERIFY_MATCH_FAILED = 106;

    public static final int SDK_CAMERA_EXPOSURE_SETTING_FAILED = 12;
    public static final int SDK_CAMERA_VERIFY_MODE = 1;
    public static final int SDK_CAMERA_ENROLL_MODE = 2;

    public static final int SDK_CAMERA_SETIING_STATE = 0;
    public static final int SDK_CAMERA_ENROLL_PROCESS = 1;
    public static final int SDK_CAMERA_ENROLL_SUCCESS  = 2;
    public static final int SDK_CAMERA_ENROLL_FAILED  = 3;

    public static final int SDK_CAMERA_VERIFY_FAILED = 1;
    public static final int SDK_CAMERA_VERIFY_RESULT = 2;

    public FaceMethod(Context context) {
        super(context);
    }

    public static native int initFaceLogOnSDK(String dictPath, String templatePath);
    public static native int finalizeFaceLogOnSDK();
    public static native int processMarsFaceId(byte[] aYUVData, int w, int h, int nCurExpo, int[] faceResults);

    public static native int setWorkMode(int nWorkMode);
    public static native int setCameraExposureRange(int nMinExpo, int nMaxExpo);
    
	public static native int setVerificationThreshold(float rThreshold);
    public static native int setLivenessThreshold(float rThreshold);
	public static native float getDefaultVerificationThreshold();
	public static native float getDefaultLivenessThreshold();

	public static native int allowClosedEye(boolean fSetting);
    public static native int allowOccludedKeyPoints(boolean fSetting);

    public static native int isEnrolledPerson();
    public static native int deletePerson();
    public static native int saveTemplate();
    public static native int setGyroInfo(int nGyroInfo);
    public static native int getLog(int[] anLogInfo);

    static
    {
        System.loadLibrary("face-jni");
    }
}
