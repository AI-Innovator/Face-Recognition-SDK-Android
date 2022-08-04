package org.prenes.TCFaceRecog;

import android.content.Context;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.renderscript.RenderScript;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import androidx.appcompat.app.AppCompatActivity;

import org.prenes.TCFaceRecog.LockScreen.Utils.VibratorUtil;
import org.prenes.face.FaceMethod;

import java.io.IOException;
import java.util.Arrays;

import static org.prenes.TCFaceRecog.Constants.FACEID_BENCHMARK;

public class ScreenOffActivity extends AppCompatActivity implements SurfaceHolder.Callback, Camera.PreviewCallback, SensorEventListener {
    private static final int VERIFY_TIMEOUT = 5;

    SurfaceView camerasurface = null;
    Camera camera = null;
    int m_nScreenOn = 0;

    private boolean mRunning;
    Bitmap mEngineBitmap = null;
    private Thread mVerifyThread = null;

    private SensorManager mySensorManager;
    private Sensor myGravitySensor;
    float thresholdGraqvity;
    int orientation = 0;
    private Button btnRetry;
    byte[] gbYV21Data = null;

    private int nMinExposure = 0;
    private int nMaxExposure = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_screen_off);

        camerasurface = (SurfaceView) findViewById(R.id.camera_preview);
        final Button btnScreenOn = (Button)findViewById(R.id.btnScreenOn);
        btnRetry = (Button)findViewById(R.id.btnRetry);

        RelativeLayout.LayoutParams para = new RelativeLayout.LayoutParams(1, 1);
//        para.addRule(RelativeLayout.CENTER_IN_PARENT);
        camerasurface.setLayoutParams(para);
        camerasurface.getHolder().addCallback(this);
        camerasurface.setKeepScreenOn(true);

        mRS = RenderScript.create(this);

        DateTimeView dateTimeView = (DateTimeView)findViewById(R.id.datetime_view);
        final FrameLayout viewMask = (FrameLayout)findViewById(R.id.viewMask);

        dateTimeView.setOnUnlockListener(new DateTimeView.OnUnlockListener()
        {
            @Override
            public void onUnlock() {
                hideLockScreen();
            }
        });

        btnScreenOn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FaceMethod.setWorkMode(FaceMethod.SDK_CAMERA_VERIFY_MODE);
                viewMask.setVisibility(View.INVISIBLE);
                btnScreenOn.setVisibility(View.INVISIBLE);
                btnRetry.setVisibility(View.INVISIBLE);
                m_nScreenOn = 1;
            }
        });

        btnRetry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FaceMethod.setWorkMode(FaceMethod.SDK_CAMERA_VERIFY_MODE);
                viewMask.setVisibility(View.INVISIBLE);
                btnScreenOn.setVisibility(View.INVISIBLE);
                btnRetry.setVisibility(View.INVISIBLE);
                m_nScreenOn = 1;
            }
        });

        mySensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        myGravitySensor = mySensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        thresholdGraqvity = SensorManager.STANDARD_GRAVITY/2;
    }

    @Override
    public void onAccuracyChanged(Sensor arg0, int arg1) {
// TODO Auto-generated method stub

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor source = event.sensor;
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        double degree = Math.atan2(y, x) / Math.PI;

        if(0.25 < degree && degree <= 0.75)
            orientation = 0;
        else if((0.75 < degree && degree < 1) || (-1 <degree && degree < -0.75))
            orientation = 1;
        else if(-0.75 < degree && degree <= -0.25)
            orientation = 2;
        else
            orientation = 3;
        FaceMethod.setGyroInfo(orientation);
    }

    @Override
    public void onResume() {
        super.onResume();

        startFace();
        mySensorManager.registerListener(
                this,
                myGravitySensor,
                SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onPause() {
        super.onPause();

        stopFace();
        mySensorManager.unregisterListener(this);
        finish();
    }

    private void startFace() {
        try {
            camera = Camera.open(Constants.CAMERA_ID);
            Camera.Parameters parameters = camera.getParameters();
            parameters.setPreviewSize(640, 480);
            camera.setParameters(parameters);

            initCameraParameters();
            FaceMethod.setCameraExposureRange(nMinExposure, nMaxExposure);
            camera.startPreview();
        }
        catch (Exception e){}
    }

    private void stopFace() {
        if (camera != null) {
            try {
                camera.setPreviewCallback(null);
                camera.stopPreview();
                camera.release();
                camera = null;

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        try {
            if(camera != null)
            {
                camera.setPreviewDisplay(holder);
                camera.setDisplayOrientation(90);
                camera.setPreviewCallback(this);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder)
    {
    }

    private RenderScript mRS;

    @Override
    public void onPreviewFrame(final byte[] data, Camera camera)
    {
        if(mEngineBitmap != null)
            return;
        Camera.Size size;
        try
        {
            Camera.Parameters parameters = camera.getParameters();
            size = parameters.getPreviewSize();
        } catch (Exception e) {
            return;
        }

        if (FaceMethod.isEnrolledPerson() == FaceMethod.SDK_FAILED) {
            return;
        }

        if (m_nScreenOn == 1)
        {
            gbYV21Data = Arrays.copyOf(data, data.length);

            if (!mRunning)
            {
                mVerifyThread = new Thread(mVerifyRunnable);
                mVerifyThread.setPriority(Thread.MAX_PRIORITY);
                mVerifyThread.start();
            }
        }
    }

    private void initCameraParameters() {
        Camera.Parameters mParameters = camera.getParameters();
        nMinExposure = mParameters.getMinExposureCompensation();
        nMaxExposure = mParameters.getMaxExposureCompensation();
    }

    private void hideLockScreen()
    {
        VibratorUtil.Vibrate(getApplicationContext(), 50);
        stopFace();
        finish();
        overridePendingTransition(0, 0);
        mDetectFace = 2;
    }

    private void retryScreen()
    {
        VibratorUtil.Vibrate(getApplicationContext(), 50);
        btnRetry.setVisibility(View.VISIBLE);
    }

    int nPrevExpo = 0;
    private void setCameraExposure(int nNewExpo) {
        if (nPrevExpo != nNewExpo) {
            nPrevExpo = nNewExpo;
            Camera.Parameters param = camera.getParameters();
            param.setExposureCompensation(nNewExpo);
            camera.setParameters(param);
        }
    }

    int mDetectFace = 0;

    private Runnable mVerifyRunnable = new Runnable() {
        @Override
        public void run() {
            try
            {
                mRunning = true;
                long beginMs = System.currentTimeMillis();
                mDetectFace = 0;

                int[] anInfo = new int[5];
                while(mRunning) {
                    long curMs = System.currentTimeMillis();

                    if(curMs - beginMs > VERIFY_TIMEOUT * 1000)
                    {
                        m_nScreenOn = 0;

                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            public void run() {
                                if(mDetectFace == 1)
                                {
                                    new android.os.Handler().postDelayed(
                                            new Runnable() {
                                                public void run() {
                                                    if(mDetectFace == 1)
                                                        retryScreen();
                                                }
                                            },
                                            3000);
                                }
                                else
                                    retryScreen();
                            }
                        });
                        gbYV21Data = null;
                        break;
                    }

                    if (gbYV21Data != null) {
                        Camera.Parameters param = camera.getParameters();
                        int nCurExpo = param.getExposureCompensation();
                        int[] faceResults = new int[12];

                        int ret = FaceMethod.processMarsFaceId(gbYV21Data, 640, 480, nCurExpo, faceResults);
                        FaceMethod.getLog(anInfo);
                        Log.e(FACEID_BENCHMARK, "==========  Verifying  ==========");
                        Log.e(FACEID_BENCHMARK, "Verify Result : " + faceResults[2]);
                        if(anInfo[4] != 0)
                            Log.e(FACEID_BENCHMARK, "Total process : " + anInfo[4] + " ms");
                        if(anInfo[0] != 0)
                            Log.e(FACEID_BENCHMARK, "\tFace detection : " + anInfo[0] + " ms");
                        if(anInfo[1] != 0)
                            Log.e(FACEID_BENCHMARK, "\tEye state : " + anInfo[1] + " ms");
                        if(anInfo[2] != 0)
                            Log.e(FACEID_BENCHMARK, "\tLiveness : " + anInfo[2] + " ms");
                        if(anInfo[3] != 0)
                            Log.e(FACEID_BENCHMARK, "\tFeature extraction and Compare : " + anInfo[3] + " ms");

                        if(faceResults[2] != FaceMethod.SDK_FACEDETECT_FAILED)
                            mDetectFace = 1;

                        if (faceResults[6] == FaceMethod.SDK_CAMERA_VERIFY_RESULT) {
                            m_nScreenOn = 0;

                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                public void run() {
                                    hideLockScreen();
                                }
                            });
                            FaceMethod.saveTemplate();
                            gbYV21Data = null;
                            break;
                        }

                        setCameraExposure(faceResults[9]);

                        gbYV21Data = null;
                    }
                }
                mRunning = false;
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    };
}
