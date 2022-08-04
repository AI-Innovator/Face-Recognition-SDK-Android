package org.prenes.TCFaceRecog;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.renderscript.RenderScript;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.widget.Toolbar;

import androidx.appcompat.app.AppCompatActivity;

import org.prenes.face.FaceMethod;

import java.util.Arrays;
import java.util.List;

import static org.prenes.TCFaceRecog.Constants.FACEID_BENCHMARK;

public class EnrollFaceActivity extends AppCompatActivity implements Camera.PreviewCallback, TextureView.SurfaceTextureListener, SensorEventListener {

    private static final String TAG = "EnrollFaceActivity";
    private static final int ENROLL_TIMEOUT = 30;

    private Toolbar mToolBar;

    private FrameLayout mFrameCamera;
    private TextureView mCameraPreview;
    private Camera mCamera;

    private Bitmap mEngineBitmap = null;
    private Thread mEnrollThread = null;

    private RenderScript mRS;

    private TextView mTxtProgress;
    private TextView mTxtCommand;
    private TextView mTxtState;
    private LinearLayout mLytState;
    private Button mBtnRetry;

    private SensorManager mySensorManager;
    private Sensor myGravitySensor;
    float thresholdGraqvity;
    int orientation = 0;
    byte[] gbYV21Data = null;

    private int nMinExposure = 0;
    private int nMaxExposure = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enroll_face);

        mRS = RenderScript.create(this);

        mFrameCamera = (FrameLayout) findViewById(R.id.frameCamera);

        mToolBar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolBar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        miRegView = (MiClockView)findViewById(R.id.mi_register);
        mTxtProgress = (TextView)findViewById(R.id.txtProgress);
        mTxtCommand = (TextView)findViewById(R.id.txtCommand);
        mTxtState = (TextView)findViewById(R.id.txtState);
        mLytState = (LinearLayout)findViewById(R.id.lytState);
        mBtnRetry = (Button)findViewById(R.id.btnRetry);
        mBtnRetry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FaceMethod.setWorkMode(FaceMethod.SDK_CAMERA_ENROLL_MODE);
                mUIHandler.sendEmptyMessage(R.id.start_cam);
                mBtnRetry.setVisibility(View.INVISIBLE);
            }
        });

        Animation anim = new AlphaAnimation(0f, 1.0f);
        anim.setDuration(700); //You can manage the time of the blink with this parameter
        anim.setStartOffset(20);
        anim.setRepeatMode(Animation.REVERSE);
        anim.setRepeatCount(Animation.INFINITE);
        mTxtState.startAnimation(anim);
        setScreenBrightness(true);

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
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();

        mySensorManager.registerListener(
                this,
                myGravitySensor,
                SensorManager.SENSOR_DELAY_NORMAL);

        if(!mNotStartCamOnResume) {
            FaceMethod.setWorkMode(FaceMethod.SDK_CAMERA_ENROLL_MODE);
            mUIHandler.sendEmptyMessage(R.id.start_cam);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        mySensorManager.unregisterListener(this);
        releaseCameraAndPreview();

        finish();
    }

    MiClockView miRegView;

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if(mEngineBitmap != null)
            return;

        Camera.Parameters parameters = mCamera.getParameters();
        Camera.Size size = parameters.getPreviewSize();
        List<Camera.Size> previewSizes = parameters.getSupportedPreviewSizes();

        gbYV21Data = Arrays.copyOf(data, data.length);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture arg0, int arg1, int arg2) {
        try {
            mCamera = Camera.open(Constants.CAMERA_ID);
            Camera.Parameters parameters = mCamera.getParameters();

            mCameraPreview.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            Matrix transform = new Matrix();

            if(Constants.TEST_PHONE == 0) {
                mCamera.setDisplayOrientation(180);
                transform.setScale(-1, 1, mCameraPreview.getWidth() / 2, 0);
            } else {
                mCamera.setDisplayOrientation(90);
                transform.setScale(1, 1, mCameraPreview.getWidth() / 2, 0);
            }

            parameters.setPreviewSize(640, 480);
            mCamera.setParameters(parameters);
            mCamera.setPreviewCallback(this);
            mCamera.setPreviewTexture(arg0);
            mCameraPreview.setTransform(transform);

            initCameraParameters();
            Log.e("Expo setting","min Expo: " + nMinExposure + " max Expo: " + nMaxExposure);
            FaceMethod.setCameraExposureRange(nMinExposure, nMaxExposure);

            mCamera.startPreview();
        } catch (Exception e) {
            Log.i(TAG, e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture arg0) {
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
        return true;
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture arg0, int arg1,int arg2) {
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture arg0) {
    }

    private void initCameraParameters() {
        Camera.Parameters mParameters = mCamera.getParameters();
        nMinExposure = mParameters.getMinExposureCompensation();
        nMaxExposure = mParameters.getMaxExposureCompensation();
    }

    private boolean mRunning;
    private boolean startCameraInView() {
        releaseCameraAndPreview();
        // TODO add shutterCallback to constructor parameters
        mCameraPreview = new TextureView(this);
        mCameraPreview.setSurfaceTextureListener(this);

        mFrameCamera.addView(mCameraPreview);

        mEnrollThread = new Thread(mEnrollRunnable);
        mEnrollThread.setPriority(Thread.MIN_PRIORITY);
        mEnrollThread.start();
        mRunning = true;

        return true;
    }

    private void releaseCameraAndPreview() {
        if (mCameraPreview != null) {
            mRunning = false;
            mEnrollThread.interrupt();
            mFrameCamera.removeAllViews();
            mCameraPreview = null;
            gbYV21Data = null;
        }
    }

    int nPrevExpo = 0;
    private void setCameraExposure(int nNewExpo) {
        if (nNewExpo != nPrevExpo) {
            nPrevExpo = nNewExpo;
            Camera.Parameters param = mCamera.getParameters();
            param.setExposureCompensation(nNewExpo);
            mCamera.setParameters(param);
        }
    }

    private Runnable mEnrollRunnable = new Runnable()
    {
        @Override
        public void run()
        {

            try {
                Message msg;
                long beginMs = System.currentTimeMillis();

                msg = mUIHandler.obtainMessage(R.id.enroll_state,0, 0);
                mUIHandler.sendMessage(msg);

                int[] anInfo = new int[5];

                while(mRunning) {
                    long curMs = System.currentTimeMillis();

                    if(curMs - beginMs > ENROLL_TIMEOUT * 1000)
                    {
                        setResult(RESULT_OK);
                        msg = mUIHandler.obtainMessage(R.id.enroll_failed);
                        mUIHandler.sendMessage(msg);
                        break;
                    }

                    if(gbYV21Data == null) {
                        Thread.sleep(30);
                        continue;
                    }

                    int[] faceResults = new int[12];
                    Camera.Parameters param = mCamera.getParameters();
                    int nCurExpo = param.getExposureCompensation();

                    int ret = FaceMethod.processMarsFaceId(gbYV21Data, 640, 480, nCurExpo, faceResults);

                    setCameraExposure(faceResults[9]);

                    if (faceResults[0] == FaceMethod.SDK_CAMERA_ENROLL_PROCESS) {
                        msg = mUIHandler.obtainMessage(R.id.enroll_process, faceResults[4], 0);
                        mUIHandler.sendMessage(msg);

                        miRegView.setProgress(faceResults[5]);
                    }

                    if(faceResults[1] == FaceMethod.SDK_CAMERA_ENROLL_SUCCESS) {
                        FaceMethod.saveTemplate();
                        setResult(RESULT_OK);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mTxtCommand.setText("Registered successfully");
                            }
                        });

                        try {
                            Thread.sleep(700);
                        }catch (Exception e){}

                        finish();
                        break;
                    }
                    else if(faceResults[1] == FaceMethod.SDK_CAMERA_ENROLL_FAILED)
                    {
                        setResult(RESULT_OK);
                        mTxtCommand.setVisibility(View.VISIBLE);
                        mTxtCommand.setText("Registering failed");
                        msg = mUIHandler.obtainMessage(R.id.enroll_failed);
                        mUIHandler.sendMessage(msg);
                        break;
                    }
                    else if(faceResults[1] == FaceMethod.SDK_CAMERA_ENROLL_PROCESS)
                    {
                        msg = mUIHandler.obtainMessage(R.id.enroll_state, faceResults[2], 0);
                        mUIHandler.sendMessage(msg);
                    }

                    gbYV21Data = null;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    private Handler mUIHandler = new Handler(new Handler.Callback()
    {
        public boolean handleMessage(Message msg)
        {
            switch (msg.what) {
                case R.id.draw_rect: {
                    break;
                }
                case R.id.enroll_state: {
                    if(msg.arg1 != FaceMethod.SDK_ENROLL_NORMAL) {
                        switch (msg.arg1) {
                            case FaceMethod.SDK_ENROLL_BEFORE_FRONT:
                                mTxtState.setText("Please keep your face facing the viewing area");
                                break;
                            case FaceMethod.SDK_ENROLL_MOVE_NEAR:
                                mTxtState.setText("Bring your face closer");
                                break;
                            case FaceMethod.SDK_ENROLL_MOVE_FAR:
                                mTxtState.setText("Please move your face farther");
                                break;
                            case FaceMethod.SDK_ENROLL_OTHER_PERSON:
                                mTxtState.setText("Only enter one person");
                                break;
                            case FaceMethod.SDK_ENROLL_OCCLUSION:
                                mTxtState.setText("Don't mask face");
                                break;
                        }
                        mTxtCommand.setVisibility(View.GONE);
                        mLytState.setVisibility(View.VISIBLE);
                    }
                    else {
                        mTxtCommand.setText("Registering");
                        mTxtCommand.setVisibility(View.VISIBLE);
                        mLytState.setVisibility(View.GONE);
                    }
                    break;
                }
                case R.id.enroll_process: {
                    mTxtProgress.setText(String.valueOf(msg.arg1) + "%");
                    break;
                }
                case R.id.start_cam:
                {
                    startCameraInView();
                    mNotStartCamOnResume = false;
                    miRegView.nofilter();
                    break;
                }
                case R.id.enroll_failed: {
                    releaseCameraAndPreview();
                    mTxtCommand.setVisibility(View.VISIBLE);
                    mTxtCommand.setText("Registering failed");
                    mBtnRetry.setVisibility(View.VISIBLE);
                    mLytState.setVisibility(View.GONE);
                    mTxtProgress.setText("");
                    miRegView.init();

                    mNotStartCamOnResume = true;
                    break;
                }
            }
            return true;
        }
    });

    private boolean mNotStartCamOnResume = false;

    public void setScreenBrightness(boolean fMax) {
        if (fMax) {
            WindowManager.LayoutParams layout = getWindow().getAttributes();
            layout.screenBrightness = 0.5F;
            getWindow().setAttributes(layout);
        }
    }

}
