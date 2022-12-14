package org.prenes.TCFaceRecog.LockScreen;

import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.RelativeLayout;

import org.prenes.TCFaceRecog.LockScreen.matchview.MatchTextView;
import org.prenes.TCFaceRecog.R;

/**
 * Created by Administrator on 2015/5/4.
 */
public class SliderLayout extends RelativeLayout {
    private Context context;
    private int locationX = 0;
    private Handler handler = null;
    private static int BACK_DURATION = 1;
    private RelativeLayout mMatchView;
    private MatchTextView mMatchTextView;
    private OnUnlockListener mOnUnlockListener;


    public SliderLayout(Context context) {
        super(context);
        SliderLayout.this.context = context;
        init();
    }


    public SliderLayout(Context context, AttributeSet attrs) {
        super(context, attrs, 0);
        SliderLayout.this.context = context;
        init();
    }


    public SliderLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        SliderLayout.this.context = context;
        init();
    }


    public void setOnUnlockListener(OnUnlockListener mOnUnlockListener) {
        this.mOnUnlockListener = mOnUnlockListener;
    }


    private void init() {
        locationX = getScreenWidth() / 2;

        handler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                if (BackX >= 0) {
                    BackX = BackX - 5;
                    if (BackX > 0) {
                        mMatchView.scrollTo(BackX, Y);
                        handler.sendEmptyMessageDelayed(0, BACK_DURATION);
                    } else {
                        BackX = 0;
                        mMatchView.scrollTo(X, Y);
                    }
                } else {
                    BackX = BackX + 5;

                    if (BackX < 0) {
                        mMatchView.scrollTo(BackX, Y);
                        handler.sendEmptyMessageDelayed(0, BACK_DURATION);
                    } else {
                        BackX = 0;
                        mMatchView.scrollTo(X, Y);
                    }
                }
                float progress = 1 - Math.abs(BackX) * 1f / Math.abs(UnlockX);
                if (progress >= 1) {
                    progress = 1;
                } else if (progress <= 0) {
                    progress = 0.1f;
                }
                mMatchTextView.setProgress(progress);
                return true;
            }
        });
    }


    /**
     * ?????????????????????????????????????????????
     */
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
//        mMatchView = (RelativeLayout) findViewById(R.id.matchview);
//        mMatchTextView = (MatchTextView) findViewById(R.id.mMatchTextView);
        Y = mMatchView.getScrollY();
        X = mMatchView.getScrollX();
        UnlockX = getScreenWidth() / 4;
    }


    int X = 0;
    int Y = 0;
    int UnlockX = 0;
    int BackX = 0;
    int moveX = 0;
    private boolean isUnLock = false;


    /**
     * ??????????????????????????????????????????
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isUnLock) {
            return super.onTouchEvent(event);
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                locationX = (int) event.getX();
                return isActionDown(event);//?????????????????????????????????

            case MotionEvent.ACTION_MOVE: //??????x????????????????????????

                moveX = X + locationX - (int) event.getX();
                if (!isUnLocked(moveX)) {
                    mMatchView.scrollTo(moveX, Y);
                    float progress = 1 -
                            Math.abs(moveX) * 1f / Math.abs(UnlockX);
                    if (progress >= 1) {
                        progress = 1;
                    }
                    else if (progress <= 0) {
                        progress = 0.1f;
                    }
                    mMatchTextView.setProgress(progress);
                }
                else {
                    isUnLock = true;
                    mOnUnlockListener.onUnlock();
                }
                return true;

            case MotionEvent.ACTION_UP: //????????????????????????
                BackX = moveX;
                handler.sendEmptyMessage(0);
                return true;
        }
        return super.onTouchEvent(event);
    }


    /**
     * ????????????????????????????????????
     */
    private boolean isActionDown(MotionEvent event) {
        Rect rect = new Rect();
        mMatchView.getHitRect(rect);
        boolean isIn = rect.contains((int) event.getX(), (int) event.getY());
        if (isIn) {
            return true;
        }
        return false;
    }


    /**
     * ??????????????????
     */
    private boolean isUnLocked(int moveX) {
        if (Math.abs(moveX) >= Math.abs(UnlockX)) {
            return true;
        }
        return false;
    }


    /**
     * ??????????????????
     */
    private int getScreenWidth() {
        WindowManager manager = (WindowManager) context.getSystemService(
                Context.WINDOW_SERVICE);
        int width = manager.getDefaultDisplay().getWidth();
        return width;
    }


    public interface OnUnlockListener {
        public void onUnlock();
    }
}