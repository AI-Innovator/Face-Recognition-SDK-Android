package org.prenes.TCFaceRecog;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.prenes.TCFaceRecog.LockScreen.SliderLayout;
import org.prenes.TCFaceRecog.LockScreen.Utils.DataString;

import static android.content.ContentValues.TAG;

/**
 * TODO: document your custom view class.
 */
public class DateTimeView extends LinearLayout {

    private OnUnlockListener mOnUnlockListener;

    public DateTimeView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView();
    }

    public DateTimeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    public DateTimeView(Context context) {
        super(context);
        initView();
    }

    public interface OnUnlockListener {
        public void onUnlock();
    }

    public void setOnUnlockListener(OnUnlockListener mOnUnlockListener) {
        this.mOnUnlockListener = mOnUnlockListener;
    }

    View view;
    private void initView() {
        view = inflate(getContext(), R.layout.datetime, null);

        view.post(new Runnable() {
            @Override
            public void run() {
                view.setY(getHeight() - view.getHeight());
                TextView mDataTextView = (TextView) view.findViewById(R.id.txtDate);
                mDataTextView.setText(DataString.StringData());
            }
        });

        view.setOnTouchListener(new OnTouchListener() {
            public boolean  onTouch(View view, MotionEvent event) {

                int initPos = getHeight() - view.getHeight();
                switch (event.getAction()) {

                    case MotionEvent.ACTION_DOWN:

                        dY = view.getY() - event.getRawY();
                        break;
                    case MotionEvent.ACTION_UP:
                        view.animate()
                                .y(initPos)
                                .alpha(1)
                                .setDuration(50)
                                .start();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float rate = view.getY() / initPos;
                        view.animate()
                                .y(event.getRawY() + dY)
                                .alpha(rate * rate * rate)
                                .setDuration(0)
                                .start();

                        if(view.getY() < initPos * 2/3)
                            mOnUnlockListener.onUnlock();

                        break;
                    default:
                        return false;
                }
                return true;

            }
        });
        addView(view);
    }

    float dX, dY;
}