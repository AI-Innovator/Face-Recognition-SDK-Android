/*
 * Copyright 2014 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.prenes.TCFaceRecog.LockScreen.render;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;

import org.prenes.TCFaceRecog.LockScreen.render.util.ArtDetailViewport;
import org.prenes.TCFaceRecog.LockScreen.render.util.BitmapRegionLoader;
import org.prenes.TCFaceRecog.LockScreen.render.util.ImageBlurrer;
import org.prenes.TCFaceRecog.LockScreen.render.util.ImageUtil;
import org.prenes.TCFaceRecog.LockScreen.render.util.MathUtil;
import org.prenes.TCFaceRecog.LockScreen.render.util.TickingFloatAnimator;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class BlurRenderer implements GLSurfaceView.Renderer {

    private static final int CROSSFADE_ANIMATION_DURATION = 750;
    private static final int BLUR_ANIMATION_DURATION = 750;

    public static final int DEFAULT_BLUR = 250; // max 500
    public static final int DEFAULT_GREY = 0; // max 500
    public static final int DEMO_DIM = 64;
    public static final int DEFAULT_MAX_DIM = 128; // technical max 255
    public static final float DIM_RANGE = 0.5f; // percent of max dim

    private boolean mDemoMode;
    private boolean mPreview;
    private int mMaxPrescaledBlurPixels;
    private int mBlurKeyframes = 3;
    private int mBlurredSampleSize;
    private int mMaxDim;
    private int mMaxGrey;

    // Model and view matrices. Projection and MVP stored in picture set
    private final float[] mMMatrix = new float[16];
    private final float[] mVMatrix = new float[16];

    private Callbacks mCallbacks;

    private float mAspectRatio;
    private int mHeight;

    private GLPictureSet mCurrentGLPictureSet;
    private GLPictureSet mNextGLPictureSet;
    private GLColorOverlay mColorOverlay;

    private BitmapRegionLoader mQueuedNextBitmapRegionLoader;

    private boolean mSurfaceCreated;

    private volatile float mNormalOffsetX;
    private volatile RectF mCurrentViewport = new RectF();
            // [-1, -1] to [1, 1], flipped

    private Context mContext;

    private boolean mIsBlurred = true;
    private boolean mBlurRelatedToArtDetailMode = false;
    private Interpolator mBlurInterpolator
            = new AccelerateDecelerateInterpolator();
    private TickingFloatAnimator mBlurAnimator;
    private TickingFloatAnimator mCrossfadeAnimator
            = TickingFloatAnimator.create().from(0);


    public BlurRenderer(Context context, Callbacks callbacks) {
        mContext = context;
        mCallbacks = callbacks;

        mBlurKeyframes = getNumberOfKeyframes();//???????????????1???????????????5
        mBlurAnimator = TickingFloatAnimator.create().from(mBlurKeyframes);

        mCurrentGLPictureSet = new GLPictureSet(0);
        mNextGLPictureSet = new GLPictureSet(
                1); // for transitioning to next pictures
        setNormalOffsetX(0);
        recomputeMaxPrescaledBlurPixels();
        recomputeMaxDimAmount();
        recomputeGreyAmount();
    }


    @TargetApi(Build.VERSION_CODES.KITKAT) private int getNumberOfKeyframes() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            ActivityManager activityManager
                    = (ActivityManager) mContext.getSystemService(
                    Context.ACTIVITY_SERVICE);
            if (activityManager.isLowRamDevice()) {
                return 1;
            }
        }

        return 5;
    }


    public void recomputeMaxPrescaledBlurPixels() {
        // Compute blur sizes
        float maxBlurRadiusOverScreenHeight =
                PreferenceManager.getDefaultSharedPreferences(mContext)
                                 .getInt("blur_amount", DEFAULT_BLUR) * 0.0001f;
        DisplayMetrics dm = mContext.getResources().getDisplayMetrics();
        int maxBlurPx = (int) (dm.heightPixels * maxBlurRadiusOverScreenHeight);
        mBlurredSampleSize = 4;
        while (maxBlurPx / mBlurredSampleSize >
                ImageBlurrer.MAX_SUPPORTED_BLUR_PIXELS) {
            mBlurredSampleSize <<= 1;
        }
        mMaxPrescaledBlurPixels = maxBlurPx / mBlurredSampleSize;
    }


    public void recomputeMaxDimAmount() {
        mMaxDim = PreferenceManager.getDefaultSharedPreferences(mContext)
                                   .getInt("dim_amount", DEFAULT_MAX_DIM);
    }


    public void recomputeGreyAmount() {
        mMaxGrey = PreferenceManager.getDefaultSharedPreferences(mContext)
                                    .getInt("grey_amount", DEFAULT_GREY);
    }


    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        mSurfaceCreated = false;
        GLES20.glEnable(GLES20.GL_BLEND);
        //        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glBlendFuncSeparate(GLES20.GL_SRC_ALPHA,
                GLES20.GL_ONE_MINUS_SRC_ALPHA, GLES20.GL_ONE, GLES20.GL_ONE);
        GLES20.glClearColor(0, 0, 0, 0);

        // Set the camera position (View matrix)
        Matrix.setLookAtM(mVMatrix, 0, 0, 0, 1, 0, 0, -1, 0, 1, 0);

        GLColorOverlay.initGl();
        GLPicture.initGl();

        mColorOverlay = new GLColorOverlay(0);

        mSurfaceCreated = true;
        if (mQueuedNextBitmapRegionLoader != null) {
            BitmapRegionLoader loader = mQueuedNextBitmapRegionLoader;
            mQueuedNextBitmapRegionLoader = null;
            setAndConsumeBitmapRegionLoader(loader);
        }
    }


    public void onSurfaceChanged(GL10 unused, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        hintViewportSize(width, height);
        if (!mDemoMode && !mPreview) {
            // Reset art detail viewports
            ArtDetailViewport.getInstance().setViewport(0, 0, 0, 0, 0, false);
            ArtDetailViewport.getInstance().setViewport(1, 0, 0, 0, 0, false);
        }
        mCurrentGLPictureSet.recomputeTransformMatrices();
        mNextGLPictureSet.recomputeTransformMatrices();
        recomputeMaxPrescaledBlurPixels();
    }


    public void hintViewportSize(int width, int height) {
        mHeight = height;
        mAspectRatio = width * 1f / height;
    }


    public void onDrawFrame(GL10 unused) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        Matrix.setIdentityM(mMMatrix, 0);

        boolean stillAnimating = mCrossfadeAnimator.tick();
        stillAnimating |= mBlurAnimator.tick();

        if (mBlurRelatedToArtDetailMode) {
            mCurrentGLPictureSet.recomputeTransformMatrices();
            mNextGLPictureSet.recomputeTransformMatrices();
        }

        float dimAmount = mCurrentGLPictureSet.mDimAmount;
        mCurrentGLPictureSet.drawFrame(1);
        if (mCrossfadeAnimator.isRunning()) {
            dimAmount = MathUtil.interpolate(dimAmount,
                    mNextGLPictureSet.mDimAmount,
                    mCrossfadeAnimator.currentValue());
            mNextGLPictureSet.drawFrame(mCrossfadeAnimator.currentValue());
        }

        mColorOverlay.setColor(Color.argb(
                (int) (dimAmount * mBlurAnimator.currentValue() /
                        mBlurKeyframes), 0, 0, 0));
        mColorOverlay.draw(
                mMMatrix); // don't need any perspective or anything for color overlay

        if (stillAnimating) {
            mCallbacks.requestRender();
        }
    }


    public void setNormalOffsetX(float x) {
        mNormalOffsetX = MathUtil.constrain(0, 1, x);
        onViewportChanged();
    }


    private void onViewportChanged() {
        mCurrentGLPictureSet.recomputeTransformMatrices();
        mNextGLPictureSet.recomputeTransformMatrices();
        if (mSurfaceCreated) {
            mCallbacks.requestRender();
        }
    }


    private float blurRadiusAtFrame(float f) {
        return mMaxPrescaledBlurPixels *
                mBlurInterpolator.getInterpolation(f / mBlurKeyframes);
    }


    public void setAndConsumeBitmapRegionLoader(final BitmapRegionLoader bitmapRegionLoader) {
        if (!mSurfaceCreated) {
            mQueuedNextBitmapRegionLoader = bitmapRegionLoader;
            return;
        }

        if (mCrossfadeAnimator.isRunning()) {
            if (mQueuedNextBitmapRegionLoader != null) {
                mQueuedNextBitmapRegionLoader.destroy();
            }
            mQueuedNextBitmapRegionLoader = bitmapRegionLoader;
            return;
        }

        mNextGLPictureSet.load(bitmapRegionLoader);

        mCrossfadeAnimator.from(0)
                          .to(1)
                          .withDuration(CROSSFADE_ANIMATION_DURATION)
                          .withEndListener(new Runnable() {
                              @Override
                              public void run() {
                                  // swap current and next picturesets
                                  final GLPictureSet oldGLPictureSet
                                          = mCurrentGLPictureSet;
                                  mCurrentGLPictureSet = mNextGLPictureSet;
                                  mNextGLPictureSet = new GLPictureSet(
                                          oldGLPictureSet.mId);
                                  mCallbacks.requestRender();
                                  oldGLPictureSet.destroyPictures();
                                  System.gc();
                                  if (mQueuedNextBitmapRegionLoader != null) {
                                      BitmapRegionLoader
                                              queuedNextBitmapRegionLoader
                                              = mQueuedNextBitmapRegionLoader;
                                      mQueuedNextBitmapRegionLoader = null;
                                      setAndConsumeBitmapRegionLoader(
                                              queuedNextBitmapRegionLoader);
                                  }
                              }
                          })
                          .start();
        mCallbacks.requestRender();
    }


    public void setDemoMode(boolean demoMode) {
        mDemoMode = demoMode;
    }


    public void setIsPreview(boolean preview) {
        mPreview = preview;
    }


    private class GLPictureSet {
        private int mId;
        private volatile float[] mPMatrix = new float[16];
        private final float[] mMVPMatrix = new float[16];
        private GLPicture[] mPictures = new GLPicture[mBlurKeyframes + 1];
        private boolean mHasBitmap = false;
        private float mBitmapAspectRatio = 1f;
        private int mDimAmount = 0;


        public GLPictureSet(int id) {
            mId = id;
        }


        public void load(BitmapRegionLoader bitmapRegionLoader) {
            mHasBitmap = (bitmapRegionLoader != null);
            mBitmapAspectRatio = mHasBitmap ?
                                 bitmapRegionLoader.getWidth() * 1f /
                                         bitmapRegionLoader.getHeight() : 1f;

            mDimAmount = DEFAULT_MAX_DIM;

            destroyPictures();

            if (bitmapRegionLoader != null) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                Rect rect = new Rect();
                int originalWidth = bitmapRegionLoader.getWidth();
                int originalHeight = bitmapRegionLoader.getHeight();

                // Calculate image darkness to determine dim amount
                rect.set(0, 0, originalWidth, originalHeight);
                options.inSampleSize = ImageUtil.calculateSampleSize(
                        originalHeight, 64);
                Bitmap tempBitmap = bitmapRegionLoader.decodeRegion(rect,
                        options);
                float darkness = ImageUtil.calculateDarkness(tempBitmap);
                mDimAmount = mDemoMode
                             ? DEMO_DIM
                             : (int) (mMaxDim * ((1 - DIM_RANGE) +
                                     DIM_RANGE * Math.sqrt(darkness)));
                tempBitmap.recycle();

                // Create the GLPicture objects
                mPictures[0] = new GLPicture(bitmapRegionLoader, mHeight);
                if (mMaxPrescaledBlurPixels == 0) {
                    for (int f = 1; f <= mBlurKeyframes; f++) {
                        mPictures[f] = mPictures[0];
                    }
                }
                else {
                    ImageBlurrer blurrer = new ImageBlurrer(mContext);
                    // To blur, first load the entire bitmap region, but at a very large
                    // sample size that's appropriate for the final blurred image
                    options.inSampleSize = ImageUtil.calculateSampleSize(
                            originalHeight, mHeight / mBlurredSampleSize);
                    rect.set(0, 0, originalWidth, originalHeight);
                    tempBitmap = bitmapRegionLoader.decodeRegion(rect, options);

                    // Next, create a scaled down version of the bitmap so that the blur radius
                    // looks appropriate (tempBitmap will likely be bigger than the final blurred
                    // bitmap, and thus the blur may look smaller if we just used tempBitmap as
                    // the final blurred bitmap).

                    // Note that image width should be a multiple of 4 to avoid
                    // issues with RenderScript allocations.
                    int scaledHeight = Math.max(2,
                            MathUtil.floorEven(mHeight / mBlurredSampleSize));
                    int scaledWidth = Math.max(4, MathUtil.roundMult4(
                            (int) (scaledHeight * mBitmapAspectRatio)));
                    Bitmap scaledBitmap = Bitmap.createScaledBitmap(tempBitmap,
                            scaledWidth, scaledHeight, true);

                    tempBitmap.recycle();

                    // And finally, create a blurred copy for each keyframe.
                    for (int f = 1; f <= mBlurKeyframes; f++) {
                        float desaturateAmount = mMaxGrey / 500f * f /
                                mBlurKeyframes;
                        Bitmap blurredBitmap = blurrer.blurBitmap(scaledBitmap,
                                blurRadiusAtFrame(f), desaturateAmount);
                        mPictures[f] = new GLPicture(blurredBitmap);
                        blurredBitmap.recycle();
                    }

                    scaledBitmap.recycle();
                    blurrer.destroy();
                }
            }

            recomputeTransformMatrices();
            mCallbacks.requestRender();
        }


        private void recomputeTransformMatrices() {
            float screenToBitmapAspectRatio = mAspectRatio / mBitmapAspectRatio;

            // Ensure the bitmap is wider than the screen relatively by applying zoom
            // if necessary. Vary width but keep height the same.
            float zoom = Math.max(1f, 1.15f * screenToBitmapAspectRatio);

            // Total scale factors in both zoom and scale due to aspect ratio.
            float totalScale = zoom / screenToBitmapAspectRatio;

            mCurrentViewport.left = MathUtil.interpolate(
                    -1f * Math.min(1f, screenToBitmapAspectRatio),
                    // remove screenToBitmapAspectRatio to unconstrain panning amount
                    1f * Math.min(1f, screenToBitmapAspectRatio),
                    mNormalOffsetX * (totalScale - 1) / totalScale);
            mCurrentViewport.right = mCurrentViewport.left + 2f / totalScale;
            mCurrentViewport.bottom = -1f / zoom;
            mCurrentViewport.top = 1f / zoom;

            float focusAmount =
                    (mBlurKeyframes - mBlurAnimator.currentValue()) /
                            mBlurKeyframes;
            if (mBlurRelatedToArtDetailMode && focusAmount > 0) {
                RectF artDetailViewport = ArtDetailViewport.getInstance()
                                                           .getViewport(mId);
                if (artDetailViewport.width() == 0 ||
                        artDetailViewport.height() == 0) {
                    if (!mDemoMode && !mPreview) {
                        // reset art detail viewport
                        ArtDetailViewport.getInstance()
                                         .setViewport(mId,
                                                 MathUtil.uninterpolate(-1, 1,
                                                         mCurrentViewport.left),
                                                 MathUtil.uninterpolate(1, -1,
                                                         mCurrentViewport.top),
                                                 MathUtil.uninterpolate(-1, 1,
                                                         mCurrentViewport.right),
                                                 MathUtil.uninterpolate(1, -1,
                                                         mCurrentViewport.bottom),
                                                 false);
                    }
                }
                else {
                    // interpolate
                    mCurrentViewport.left = MathUtil.interpolate(
                            mCurrentViewport.left,
                            MathUtil.interpolate(-1, 1, artDetailViewport.left),
                            focusAmount);
                    mCurrentViewport.top = MathUtil.interpolate(
                            mCurrentViewport.top,
                            MathUtil.interpolate(1, -1, artDetailViewport.top),
                            focusAmount);
                    mCurrentViewport.right = MathUtil.interpolate(
                            mCurrentViewport.right, MathUtil.interpolate(-1, 1,
                                    artDetailViewport.right), focusAmount);
                    mCurrentViewport.bottom = MathUtil.interpolate(
                            mCurrentViewport.bottom, MathUtil.interpolate(1, -1,
                                    artDetailViewport.bottom), focusAmount);
                }
            }

            Matrix.orthoM(mPMatrix, 0, mCurrentViewport.left,
                    mCurrentViewport.right, mCurrentViewport.bottom,
                    mCurrentViewport.top, 1, 10);
        }


        public void drawFrame(float globalAlpha) {
            if (!mHasBitmap) {
                return;
            }

            Matrix.multiplyMM(mMVPMatrix, 0, mVMatrix, 0, mMMatrix, 0);
            Matrix.multiplyMM(mMVPMatrix, 0, mPMatrix, 0, mMVPMatrix, 0);

            float blurFrame = mBlurAnimator.currentValue();
            int lo = (int) Math.floor(blurFrame);
            int hi = (int) Math.ceil(blurFrame);

            float localHiAlpha = (blurFrame - lo);
            if (globalAlpha <= 0) {
                // Nothing to draw
            }
            else if (lo == hi) {
                // Just draw one
                mPictures[lo].draw(mMVPMatrix, globalAlpha);
            }
            else if (globalAlpha == 1) {
                // Simple drawing
                mPictures[lo].draw(mMVPMatrix, 1);
                mPictures[hi].draw(mMVPMatrix, localHiAlpha);
            }
            else {
                // If there's both a global and local alpha, re-compose alphas, to
                // effectively compose hi and lo before composing the result
                // with the background.
                //
                // The math, where a1,a2 are previous alphas and b1,b2 are new alphas:
                //   b1 = a1 * (a2 - 1) / (a1 * a2 - 1)
                //   b2 = a1 * a2
                float newLocalLoAlpha = globalAlpha * (localHiAlpha - 1) /
                        (globalAlpha * localHiAlpha - 1);
                float newLocalHiAlpha = globalAlpha * localHiAlpha;
                mPictures[lo].draw(mMVPMatrix, newLocalLoAlpha);
                mPictures[hi].draw(mMVPMatrix, newLocalHiAlpha);
            }
        }


        public void destroyPictures() {
            for (int i = 0; i < mPictures.length; i++) {
                if (mPictures[i] == null) {
                    continue;
                }

                mPictures[i].destroy();
                mPictures[i] = null;
            }
        }
    }


    public void destroy() {
        mCurrentGLPictureSet.destroyPictures();
        mNextGLPictureSet.destroyPictures();
    }


    public boolean isBlurred() {
        return mIsBlurred;
    }


    public void setIsBlurred(final boolean isBlurred, final boolean artDetailMode) {
        if (artDetailMode && !isBlurred && !mDemoMode && !mPreview) {
            // Reset art detail viewport
            ArtDetailViewport.getInstance().setViewport(0, 0, 0, 0, 0, false);
            ArtDetailViewport.getInstance().setViewport(1, 0, 0, 0, 0, false);
        }

        mBlurRelatedToArtDetailMode = artDetailMode;
        mIsBlurred = isBlurred;
        mBlurAnimator.cancel();
        mBlurAnimator.to(isBlurred ? mBlurKeyframes : 0)
                     .withDuration(
                             BLUR_ANIMATION_DURATION * (mDemoMode ? 5 : 1))
                     .withEndListener(new Runnable() {
                         @Override
                         public void run() {
                             if (isBlurred && artDetailMode) {
                                 System.gc();
                             }
                         }
                     })
                     .start();
        mCallbacks.requestRender();
    }


    public static interface Callbacks {
        void requestRender();
    }
}
