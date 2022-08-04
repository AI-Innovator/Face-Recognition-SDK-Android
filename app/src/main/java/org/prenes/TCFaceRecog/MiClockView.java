package org.prenes.TCFaceRecog;

import android.animation.PropertyValuesHolder;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.CornerPathEffect;
import android.graphics.LightingColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import java.util.Calendar;

import static java.lang.Math.PI;
import static java.lang.Math.cos;
import static java.lang.Math.sin;

/**
 * 仿小米时钟
 */
public class MiClockView extends View {

    /* 画布 */
    private Canvas mCanvas;
    /* 小时文本画笔 */
    private Paint mTextPaint;
    /* 测量小时文本宽高的矩形 */
    private Rect mTextRect;
    /* 小时圆圈画笔 */
    private Paint mCirclePaint;
    /* 小时圆圈线条宽度 */
    private float mCircleStrokeWidth = 2;
    /* 小时圆圈的外接矩形 */
    private RectF mCircleRectF;
    /* 刻度圆弧画笔 */
    private Paint mScaleArcPaint;
    /* 刻度圆弧的外接矩形 */
    private RectF mScaleArcRectF;
    /* 刻度线画笔 */
    private Paint mScaleLinePaint;
    /* 时针画笔 */
    private Paint mHourHandPaint;
    /* 分针画笔 */
    private Paint mMinuteHandPaint;
    /* 秒针画笔 */
    private Paint mSecondHandPaint;
    /* 时针路径 */
    private Path mHourHandPath;
    /* 分针路径 */
    private Path mMinuteHandPath;
    /* 秒针路径 */
    private Path mSecondHandPath;

    /* 亮色，用于分针、秒针、渐变终止色 */
    private int mLightColor;
    /* 暗色，圆弧、刻度线、时针、渐变起始色 */
    private int mDarkColor;
    /* 背景色 */
    private int mBackgroundColor;
    /* 小时文本字体大小 */
    private float mTextSize;
    /* 时钟半径，不包括padding值 */
    private float mRadius;
    /* 刻度线长度 */
    private float mScaleLength;

    /* 时针角度 */
    private float mHourDegree;
    /* 分针角度 */
    private float mMinuteDegree;
    /* 秒针角度 */
    private float mSecondDegree;

    /* 加一个默认的padding值，为了防止用camera旋转时钟时造成四周超出view大小 */
    private float mDefaultPadding;
    private float mPaddingLeft;
    private float mPaddingTop;
    private float mPaddingRight;
    private float mPaddingBottom;

    /* 梯度扫描渐变 */
    private SweepGradient mSweepGradient;
    /* 渐变矩阵，作用在SweepGradient */
    private Matrix mGradientMatrix;
    /* 触摸时作用在Camera的矩阵 */
    private Matrix mCameraMatrix;
    /* 照相机，用于旋转时钟实现3D效果 */
    private Camera mCamera;
    /* camera绕X轴旋转的角度 */
    private float mCameraRotateX;
    /* camera绕Y轴旋转的角度 */
    private float mCameraRotateY;
    /* camera旋转的最大角度 */
    private float mMaxCameraRotate = 10;
    /* 指针的在x轴的位移 */
    private float mCanvasTranslateX;
    /* 指针的在y轴的位移 */
    private float mCanvasTranslateY;
    /* 指针的最大位移 */
    private float mMaxCanvasTranslate;
    /* 手指松开时时钟晃动的动画 */
    private ValueAnimator mShakeAnim;

    private int PROGRESS_INTERVAL = 15;
    private int TAIL_COUNT = 3;
    private int PROGRESS_MAX = 240;
    private int TICK_LENGTH = (PROGRESS_INTERVAL - TAIL_COUNT) * 15;
    private int[] mProgress;
    private int[] mSpeed;
    private Bitmap mCameraPic;
    private Bitmap mesh;
    Bitmap mCircleMaskBitmap;

    private float m_progress;
    public MiClockView(Context context) {
        this(context, null);
    }

    public MiClockView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MiClockView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.MiClockView, defStyleAttr, 0);
        mBackgroundColor = ta.getColor(R.styleable.MiClockView_backColor, Color.parseColor("#237EAD"));
//        setBackgroundColor(mBackgroundColor);
//        getBackground().setAlpha(0);

        mLightColor = ta.getColor(R.styleable.MiClockView_lightColor, Color.parseColor("#ffffff"));
        mDarkColor = ta.getColor(R.styleable.MiClockView_darkColor, Color.parseColor("#80ffffff"));
        mTextSize = ta.getDimension(R.styleable.MiClockView_txtSize, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                14, context.getResources().getDisplayMetrics()));
        ta.recycle();

        mHourHandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mHourHandPaint.setStyle(Paint.Style.FILL);
        mHourHandPaint.setColor(mDarkColor);

        mMinuteHandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mMinuteHandPaint.setColor(mLightColor);

        mSecondHandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mSecondHandPaint.setStyle(Paint.Style.FILL);
        mSecondHandPaint.setColor(mLightColor);

        mScaleLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mScaleLinePaint.setStyle(Paint.Style.STROKE);
        mScaleLinePaint.setColor(mLightColor);

        mScaleArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mScaleArcPaint.setStyle(Paint.Style.STROKE);

        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setStyle(Paint.Style.FILL);
        mTextPaint.setColor(mDarkColor);
        mTextPaint.setTextSize(mTextSize);

        mCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCirclePaint.setStyle(Paint.Style.STROKE);
        mCirclePaint.setStrokeWidth(mCircleStrokeWidth);
        mCirclePaint.setColor(mLightColor);

        mTextRect = new Rect();
        mCircleRectF = new RectF();
        mScaleArcRectF = new RectF();
        mHourHandPath = new Path();
        mMinuteHandPath = new Path();
        mSecondHandPath = new Path();

        mGradientMatrix = new Matrix();
        mCameraMatrix = new Matrix();
        mCamera = new Camera();

        mProgress = new int[PROGRESS_MAX];
        mSpeed = new int[PROGRESS_MAX];
        for(int i = 0; i < PROGRESS_MAX; i++) {
            mProgress[i] = -20;
            mSpeed[i] = 0;
        }

        mesh = BitmapFactory.decodeResource(getContext().getResources(),
                R.drawable.mesh);
        m_progress = 0;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(measureDimension(widthMeasureSpec), measureDimension(heightMeasureSpec));
    }

    private int measureDimension(int measureSpec) {
        int result;
        int mode = MeasureSpec.getMode(measureSpec);
        int size = MeasureSpec.getSize(measureSpec);
        if (mode == MeasureSpec.EXACTLY) {
            result = size;
        } else {
            result = 800;
            if (mode == MeasureSpec.AT_MOST) {
                result = Math.min(result, size);
            }
        }
        return result;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        //宽和高分别去掉padding值，取min的一半即表盘的半径
        mRadius = Math.min(w - getPaddingLeft() - getPaddingRight(),
                h - getPaddingTop() - getPaddingBottom()) / 2;
        mDefaultPadding = 0.1f * mRadius;//根据比例确定默认padding大小
        mPaddingLeft = mDefaultPadding + w / 2 - mRadius + getPaddingLeft();
        mPaddingTop = mDefaultPadding + h / 2 - mRadius + getPaddingTop();
        mPaddingRight = mPaddingLeft;
        mPaddingBottom = mPaddingTop;
        mScaleLength = 0.06f * mRadius;//根据比例确定刻度线长度
        mScaleArcPaint.setStrokeWidth(mScaleLength);
        mScaleLinePaint.setStrokeWidth(0.009f * mRadius);
        mMaxCanvasTranslate = 0.02f * mRadius;
        //梯度扫描渐变，以(w/2,h/2)为中心点，两种起止颜色梯度渐变
        //float数组表示，[0,0.75)为起始颜色所占比例，[0.75,1}为起止颜色渐变所占比例
        mSweepGradient = new SweepGradient(w / 2, h / 2,
                new int[]{mDarkColor, mLightColor}, new float[]{0.75f, 1});

        if(mFaceMode)
            initFace();

        Paint eraser = new Paint();
        eraser.setColor(0xFFFFFFFF);
        eraser.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        mCircleMaskBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(mCircleMaskBitmap);
        mCircleMaskBitmap.eraseColor(Color.TRANSPARENT);
        c.drawColor(mBackgroundColor);

        int radius = (int)(mRadius * 0.75f);
        c.drawCircle(w/2, h/ 2, radius, eraser);

        setBackground(new BitmapDrawable(mCircleMaskBitmap));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        mCanvas = canvas;

        getTimeDegree();
        if(mFaceMode) {
            setCameraRotate();
            drawFace();
        }
        else {
            drawTimeText();
            drawScaleLine();
        }
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (mShakeAnim != null && mShakeAnim.isRunning()) {
                    mShakeAnim.cancel();
                }

                getCameraRotate(event.getX(), event.getY());
                getCanvasTranslate(event.getX(), event.getY());
                break;
            case MotionEvent.ACTION_MOVE:
                //根据手指坐标计算camera应该旋转的大小
                getCameraRotate(event.getX(), event.getY());
                getCanvasTranslate(event.getX(), event.getY());
                break;
            case MotionEvent.ACTION_UP:
                //松开手指，时钟复原并伴随晃动动画
                break;
        }
        return true;
    }

    private void refreshProgressBar(int fillInfo)
    {
        if(fillInfo < 0)
            return;

        for(int j = 0; j < 8; j++)
        {
            int fillBit = fillInfo & (1 << j);
            if(fillBit > 0)
            {
                int theta = j * 240 / 8;

                int nPtr = 0;
                for(int i = theta - PROGRESS_INTERVAL; i <= theta + PROGRESS_INTERVAL; i++)
                {
                    nPtr = i;
                    if(i < 0)
                        nPtr += PROGRESS_MAX;
                    else if(i > 239)
                        nPtr -= PROGRESS_MAX;

                    if(mProgress[nPtr] <= 0) {
                        mProgress[nPtr] = 1;

                        mSpeed[nPtr] = Math.abs((Math.abs(theta - i) - PROGRESS_INTERVAL + TAIL_COUNT)) + PROGRESS_INTERVAL - TAIL_COUNT - 3;
                    }
                }
            }
        }
    }
    /**
     * 获取camera旋转的大小
     *
     */
    private void getCameraRotate(float x, float y) {
        float rotateX = -(y - getHeight() / 2);
        float rotateY = (x - getWidth() / 2);
        //求出此时旋转的大小与半径之比
        float[] percentArr = getPercent(rotateX, rotateY);
        //最终旋转的大小按比例匀称改变
        mCameraRotateX = percentArr[0] * mMaxCameraRotate;
        mCameraRotateY = percentArr[1] * mMaxCameraRotate;
    }

    /**
     * 当拨动时钟时，会发现时针、分针、秒针和刻度盘会有一个较小的偏移量，形成近大远小的立体偏移效果
     * 一开始我打算使用 matrix 和 camera 的 mCamera.translate(x, y, z) 方法改变 z 的值
     * 但是并没有效果，所以就动态计算距离，然后在 onDraw()中分零件地 mCanvas.translate(x, y)
     *
     */
    private void getCanvasTranslate(float x, float y) {
        float translateX = (x - getWidth() / 2);
        float translateY = (y - getHeight() / 2);
        //求出此时位移的大小与半径之比
        float[] percentArr = getPercent(translateX, translateY);
        //最终位移的大小按比例匀称改变
        mCanvasTranslateX = percentArr[0] * mMaxCanvasTranslate;
        mCanvasTranslateY = percentArr[1] * mMaxCanvasTranslate;
    }

    /**
     * 获取一个操作旋转或位移大小的比例
     *
     * @param x x大小
     * @param y y大小
     * @return 装有xy比例的float数组
     */
    private float[] getPercent(float x, float y) {
        float[] percentArr = new float[2];
        float percentX = x / mRadius;
        float percentY = y / mRadius;
        if (percentX > 1) {
            percentX = 1;
        } else if (percentX < -1) {
            percentX = -1;
        }
        if (percentY > 1) {
            percentY = 1;
        } else if (percentY < -1) {
            percentY = -1;
        }
        percentArr[0] = percentX;
        percentArr[1] = percentY;
        return percentArr;
    }

    /**
     * 设置3D时钟效果，触摸矩阵的相关设置、照相机的旋转大小
     * 应用在绘制图形之前，否则无效
     */
    private void setCameraRotate() {
        mCameraMatrix.reset();
        mCamera.save();
        mCamera.rotateX(mCameraRotateX);//绕x轴旋转角度
        mCamera.rotateY(mCameraRotateY);//绕y轴旋转角度
        mCamera.getMatrix(mCameraMatrix);//相关属性设置到matrix中
        mCamera.restore();
        //camera在view左上角那个点，故旋转默认是以左上角为中心旋转
        //故在动作之前pre将matrix向左移动getWidth()/2长度，向上移动getHeight()/2长度
        mCameraMatrix.preTranslate(-getWidth() / 2, -getHeight() / 2);
        //在动作之后post再回到原位
        mCameraMatrix.postTranslate(getWidth() / 2, getHeight() / 2);
        mCanvas.concat(mCameraMatrix);//matrix与canvas相关联
    }

    /**
     * 时钟晃动动画
     */
    private void startShakeAnim() {
        final String cameraRotateXName = "cameraRotateX";
        final String cameraRotateYName = "cameraRotateY";
        final String canvasTranslateXName = "canvasTranslateX";
        final String canvasTranslateYName = "canvasTranslateY";
        PropertyValuesHolder cameraRotateXHolder =
                PropertyValuesHolder.ofFloat(cameraRotateXName, mCameraRotateX, 0);
        PropertyValuesHolder cameraRotateYHolder =
                PropertyValuesHolder.ofFloat(cameraRotateYName, mCameraRotateY, 0);
        PropertyValuesHolder canvasTranslateXHolder =
                PropertyValuesHolder.ofFloat(canvasTranslateXName, mCanvasTranslateX, 0);
        PropertyValuesHolder canvasTranslateYHolder =
                PropertyValuesHolder.ofFloat(canvasTranslateYName, mCanvasTranslateY, 0);
        mShakeAnim = ValueAnimator.ofPropertyValuesHolder(cameraRotateXHolder,
                cameraRotateYHolder, canvasTranslateXHolder, canvasTranslateYHolder);
        mShakeAnim.setInterpolator(new TimeInterpolator() {
            @Override
            public float getInterpolation(float input) {
                //http://inloop.github.io/interpolator/
                float f = 0.571429f;
                return (float) (Math.pow(2, -2 * input) * sin((input - f / 4) * (2 * PI) / f) + 1);
            }
        });
        mShakeAnim.setDuration(1000);
        mShakeAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mCameraRotateX = (float) animation.getAnimatedValue(cameraRotateXName);
                mCameraRotateY = (float) animation.getAnimatedValue(cameraRotateYName);
                mCanvasTranslateX = (float) animation.getAnimatedValue(canvasTranslateXName);
                mCanvasTranslateY = (float) animation.getAnimatedValue(canvasTranslateYName);
            }
        });
        mShakeAnim.start();
    }

    /**
     * 获取当前时分秒所对应的角度
     * 为了不让秒针走得像老式挂钟一样僵硬，需要精确到毫秒
     */
    private void getTimeDegree() {
        Calendar calendar = Calendar.getInstance();
        float milliSecond = calendar.get(Calendar.MILLISECOND);
        float second = calendar.get(Calendar.SECOND) + milliSecond / 1000;
        float minute = calendar.get(Calendar.MINUTE) + second / 60;
        float hour = calendar.get(Calendar.HOUR) + minute / 60;
        mSecondDegree = second / 60 * 360;
        mMinuteDegree = minute / 60 * 360;
        mHourDegree = hour / 12 * 360;
    }

    private boolean mCoverFilter = false;
    /**
     * 画最外圈的时间文本和4个弧线
     */
    private void drawTimeText() {

        int width = getWidth();
        int height = getHeight();

        mCircleRectF.set(mPaddingLeft + mTextRect.height() / 2 + mCircleStrokeWidth / 2,
                mPaddingTop + mTextRect.height() / 2 + mCircleStrokeWidth / 2,
                width - mPaddingRight - mTextRect.height() / 2 + mCircleStrokeWidth / 2,
                height - mPaddingBottom - mTextRect.height() / 2 + mCircleStrokeWidth / 2);

        mCanvas.save();
        int radius = (int)(mRadius * 0.75f);

        if(m_progress == 0)
            mCanvas.drawBitmap(mesh, new Rect(0, 0, mesh.getWidth(), mesh.getHeight()), new Rect(width/2 - radius, height/ 2 -radius, width/2 + radius, height/2 + radius), new Paint());

        mCanvas.restore();
    }

    private void drawScaleLine() {
        mCanvas.save();

        //画背景色刻度线

        float extraScaleLength;
        float preScaleLength = 0;
        for (int i = 0; i < PROGRESS_MAX; i++) {
            extraScaleLength = mScaleLength * mProgress[i] * 1.2f / TICK_LENGTH; //0.75

            if(mProgress[i] <= 0) {
                mScaleLinePaint.setColor(mDarkColor);

                if(mProgress[i] < -10)
                    preScaleLength = mScaleLength;
                else
                    preScaleLength = - mProgress[i] * mScaleLength / 10f;
            }
            else {
                preScaleLength = 0;
                mScaleLinePaint.setColor(mLightColor);
            }


            mCanvas.drawLine(getWidth() / 2, mPaddingTop + mScaleLength + mTextRect.height() / 2 - extraScaleLength + preScaleLength,
                 getWidth() / 2, mPaddingTop + 2 * mScaleLength + mTextRect.height() / 2, mScaleLinePaint);

            if(mProgress[i] > 0 && mProgress[i] < TICK_LENGTH) {
                mProgress[i] += mSpeed[i];
                if(mProgress[i] > TICK_LENGTH)
                    mProgress[i] = TICK_LENGTH;
            }
            else if(mProgress[i] < 0)
                mProgress[i]++;

            mCanvas.rotate(1.5f, getWidth() / 2, getHeight() / 2);
        }
        mCanvas.restore();
    }

    public void setProgress(int progress)
    {
        refreshProgressBar(progress);
        m_progress = progress;
    }

    private void initFace() {
        float radius=mRadius;

        facePaint=new Paint();
        facePaint.setColor(mDarkColor);
        facePaint.setAntiAlias(true);
        facePaint.setDither(true);
        facePaint.setStrokeJoin(Paint.Join.ROUND);
        facePaint.setStrokeCap(Paint.Cap.ROUND);
        facePaint.setPathEffect(new CornerPathEffect(10));
        facePaint.setShadowLayer(4,2,2,0x80000000);

        mPaint=new Paint();
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setAntiAlias(true);
        mPaint.setDither(true);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setPathEffect(new CornerPathEffect(10));
        mPaint.setStrokeWidth(radius / 14.0f);
        mPaint.setColor(mDarkColor);

        float adjust=0;
        float centerX = getWidth() / 2;
        float centerY = getHeight() / 2;

        //left eye
        float eyeLeftX=centerX-(radius*0.4f);
        float eyeTopY = centerY-(radius*0.4f);
        float eyeRightx = eyeLeftX+ (radius*0.3f);
        float eyeBottomY = eyeTopY + (radius*0.4f);

        eyeLeftRectF = new RectF(eyeLeftX+adjust,eyeTopY+adjust,eyeRightx+adjust,eyeBottomY+adjust);

        // Right Eye
        eyeLeftX = eyeRightx + (radius*0.2f);
        eyeRightx = eyeLeftX + (radius*0.3f);

        eyeRightRectF = new RectF(eyeLeftX+adjust,eyeTopY+adjust,eyeRightx+adjust,eyeBottomY+adjust);

        // Smiley Mouth
        float mouthLeftX = centerX-(radius*0.43f);
        float mouthRightX = mouthLeftX+ radius*0.86f;
        float mouthTopY = centerY - (radius*0.15f);
        float mouthBottomY = mouthTopY + (radius*0.5f);

        mouthRectF = new RectF(mouthLeftX+adjust,mouthTopY+adjust,mouthRightX+adjust,mouthBottomY+adjust);

        radius *= 0.75f;
        faceRectF = new RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius);

        mouthPath=new Path();
        mouthPath.arcTo(mouthRectF,30,120,true);
    }

    private Path mouthPath;
    private RectF eyeLeftRectF, eyeRightRectF, mouthRectF, faceRectF;
    private Paint facePaint, mPaint;
    private boolean mFaceMode = false;

    private void drawFace()
    {
        mCanvas.save();
        mCanvas.translate(mCanvasTranslateX, mCanvasTranslateY);
        mCanvas.drawArc(faceRectF, 0, 360, true, mPaint);
        mCanvas.translate(mCanvasTranslateX * 4f, mCanvasTranslateY * 4f);
        mCanvas.drawPath(mouthPath, mPaint);
        // 3. draw eyes
        //绘制椭圆
        mCanvas.drawArc(eyeLeftRectF,0,360,true,facePaint);
        mCanvas.drawArc(eyeRightRectF,0,360,true,facePaint);
        mCanvas.restore();

        Calendar calendar = Calendar.getInstance();

        System.out.print(mSecondDegree);
        float x = (float)(getWidth() / 2 - sin(mSecondDegree / 10 * PI) * getWidth()/2);
        float y = (float)(getHeight() / 2 + cos(mSecondDegree / 10 * PI) * getWidth()/2);
        getCameraRotate(x, y);
        getCanvasTranslate(x, y);
    }

    public void init()
    {
        for(int i = 0; i < PROGRESS_MAX; i++) {
            mProgress[i] = 0;
            m_progress = 0;
        }
        mCoverFilter = true;
    }

    public void nofilter()
    {
        mCoverFilter = false;
    }
}
