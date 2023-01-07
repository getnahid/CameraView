package com.otaliastudios.cameraview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.constraintlayout.solver.widgets.Rectangle;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.OnLifecycleEvent;

import com.otaliastudios.cameraview.controls.ControlParser;
import com.otaliastudios.cameraview.controls.Grid;
import com.otaliastudios.cameraview.controls.Preview;
import com.otaliastudios.cameraview.engine.CameraEngine;
import com.otaliastudios.cameraview.engine.offset.Reference;
import com.otaliastudios.cameraview.filter.Filter;
import com.otaliastudios.cameraview.filter.Filters;
import com.otaliastudios.cameraview.filter.NoFilter;
import com.otaliastudios.cameraview.filter.OneParameterFilter;
import com.otaliastudios.cameraview.filter.TwoParameterFilter;
import com.otaliastudios.cameraview.gesture.Gesture;
import com.otaliastudios.cameraview.gesture.GestureAction;
import com.otaliastudios.cameraview.gesture.GestureFinder;
import com.otaliastudios.cameraview.gesture.GestureParser;
import com.otaliastudios.cameraview.gesture.PinchGestureFinder;
import com.otaliastudios.cameraview.gesture.ScrollGestureFinder;
import com.otaliastudios.cameraview.gesture.TapGestureFinder;
import com.otaliastudios.cameraview.internal.GridLinesLayout;
import com.otaliastudios.cameraview.markers.AutoFocusMarker;
import com.otaliastudios.cameraview.markers.MarkerLayout;
import com.otaliastudios.cameraview.markers.MarkerParser;
import com.otaliastudios.cameraview.metering.MeteringRegions;
import com.otaliastudios.cameraview.overlay.OverlayLayout;
import com.otaliastudios.cameraview.preview.CameraPreview;
import com.otaliastudios.cameraview.preview.FilterCameraPreview;
import com.otaliastudios.cameraview.preview.GlCameraPreview;
import com.otaliastudios.cameraview.preview.SurfaceCameraPreview;
import com.otaliastudios.cameraview.preview.TextureCameraPreview;
import com.otaliastudios.cameraview.size.Size;

import java.io.File;
import java.util.HashMap;

import static android.view.View.MeasureSpec.AT_MOST;
import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.UNSPECIFIED;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

public class CameraPreviewView extends FrameLayout implements LifecycleObserver {
    public interface CameraPreviewViewCallback {
        void onPreviewCancelButtonClicked();

        void onPreviewStopRecordingButtonClicked();
    }

    // Gestures
    @VisibleForTesting
    PinchGestureFinder mPinchGestureFinder;
    @VisibleForTesting
    TapGestureFinder mTapGestureFinder;
    @VisibleForTesting
    ScrollGestureFinder mScrollGestureFinder;

    // Views
    @VisibleForTesting
    GridLinesLayout mGridLinesLayout;
    @VisibleForTesting
    MarkerLayout mMarkerLayout;
    // Overlays
    @VisibleForTesting
    OverlayLayout mOverlayLayout;
    private SharedPreferences preference;
    AutoFocusMarker mAutoFocusMarker;
    private CameraView.CameraCallbacks mCameraCallbacks;
    private boolean mInEditor;
    private CameraPreview mCameraPreview;
    private Lifecycle mLifecycle;
    private boolean mKeepScreenOn;
    private Preview mPreview;
    private final static String TAG = CameraView.class.getSimpleName();
    private static final CameraLogger LOG = CameraLogger.create(TAG);
    private HashMap<Gesture, GestureAction> mGestureMap = new HashMap<>(4);
    private CameraEngine mCameraEngine;
    private Filter mPendingFilter;
    private boolean mExperimental;
    private Size mLastPreviewStreamSize;
    private Context context;
    private ImageView recordingStopButton;
    private ImageView dismissButton;
    private GestureParser gestureParser;
    private Rectangle smallPreviewSize = new Rectangle();
    private Rectangle currentPreviewSize = new Rectangle();
    private Rectangle zeroPreviewSizeRect = new Rectangle();
    private Rectangle xd = new Rectangle();
    private boolean activeSmallPreview;
    private WindowManager windowManager;
    private CameraView cameraView;

    public CameraPreviewView(@NonNull Context context, CameraView cameraView, WindowManager windowManager, CameraEngine cameraEngine, CameraView.CameraCallbacks cameraCallbacks, Preview preview, GestureParser gestureParser, SharedPreferences preference) {
        super(context);
        this.mPreview = preview;
        this.context = context;
        mCameraEngine = cameraEngine;
        mCameraCallbacks = cameraCallbacks;
        this.gestureParser = gestureParser;
        this.cameraView = cameraView;
        this.windowManager = windowManager;
        this.preference = preference;
        initialize(context);
    }

    public CameraPreviewView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    private void initialize(@NonNull Context context) {
        mInEditor = isInEditMode();
        if (mInEditor) return;
        setWillNotDraw(false);
        setBackgroundColor(Color.WHITE);

        int oneFiftyDp = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 150f, context.getResources().getDisplayMetrics());
        int twoHundredDp = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 200f, context.getResources().getDisplayMetrics());
        int startY = (int) context.getResources().getDimensionPixelSize(R.dimen.camera_preview_header_height);
        zeroPreviewSizeRect.setBounds(0, startY, 1, 1);
        smallPreviewSize.setBounds(0, 0, oneFiftyDp, twoHundredDp);

        // Gestures
        mPinchGestureFinder = new PinchGestureFinder(mCameraCallbacks);
        mTapGestureFinder = new TapGestureFinder(mCameraCallbacks);
        mScrollGestureFinder = new ScrollGestureFinder(mCameraCallbacks);

        // Views
        mGridLinesLayout = new GridLinesLayout(context);
        mOverlayLayout = new OverlayLayout(context);
        mMarkerLayout = new MarkerLayout(context);

        addView(mGridLinesLayout);
        addView(mMarkerLayout);
        addView(mOverlayLayout);

        // Apply gestures
        applyGesture();
        MarkerParser markers = new MarkerParser(preference);
        // Apply markers
        setAutoFocusMarker(markers.getAutoFocusMarker());
        int gridColor = GridLinesLayout.DEFAULT_COLOR;
        setGrid(Grid.DEFAULT);
        setGridColor(gridColor);
        addButtons();
        hideButtons();
    }

    public void showSmallPreview() {
        resizePreview(smallPreviewSize);
        activeSmallPreview = true;
        showButtons();
    }

    public void minimizePreview() {
        resizePreview(zeroPreviewSizeRect);
    }

    public void resizePreview(Rectangle sizeRect) {
        if (sizeRect.x == currentPreviewSize.x && sizeRect.y == currentPreviewSize.y) {
            if (sizeRect.width == currentPreviewSize.width && sizeRect.height == currentPreviewSize.height) {
                return;
            }
        }

        WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams) getLayoutParams();
        layoutParams.x = sizeRect.x;
        layoutParams.y = sizeRect.y;
        layoutParams.height = sizeRect.height;
        layoutParams.width = sizeRect.width;
        windowManager.updateViewLayout(this, layoutParams);

        currentPreviewSize = sizeRect;
        hideButtons();
        activeSmallPreview = false;
    }


    public void applyGesture() {
        mapGesture(Gesture.TAP, gestureParser.getTapAction());
        mapGesture(Gesture.LONG_TAP, gestureParser.getLongTapAction());
        mapGesture(Gesture.PINCH, gestureParser.getPinchAction());
        mapGesture(Gesture.SCROLL_HORIZONTAL, gestureParser.getHorizontalScrollAction());
        mapGesture(Gesture.SCROLL_VERTICAL, gestureParser.getVerticalScrollAction());
    }

    public void showButtons() {
        dismissButton.setVisibility(VISIBLE);
        recordingStopButton.setVisibility(VISIBLE);
    }

    public void hideButtons() {
        mCurrentX = 0;
        mCurrentY = 0;
        dismissButton.setVisibility(INVISIBLE);
        recordingStopButton.setVisibility(INVISIBLE);
    }

    private float mCurrentX = 0;
    private float mCurrentY = 0;

    @SuppressLint("ClickableViewAccessibility")
    public void addButtons() {
        final WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        recordingStopButton = new ImageButton(context);
        recordingStopButton.setBackgroundColor(Color.parseColor("#00000000"));
        recordingStopButton.setImageResource(R.drawable.stop_button);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
        float dp5 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5, context.getResources().getDisplayMetrics());
        lp.setMargins(0, 0, 0, (int) dp5);
        recordingStopButton.setLayoutParams(lp);

        dismissButton = new ImageButton(context);
        dismissButton.setBackgroundColor(Color.parseColor("#00000000"));
        dismissButton.setImageResource(R.drawable.preview_close);
        lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.RIGHT | Gravity.TOP;
        float dp8 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, context.getResources().getDisplayMetrics());
        lp.setMargins(0, 0, (int) dp8, 0);
        dismissButton.setLayoutParams(lp);

        addView(recordingStopButton);
        addView(dismissButton);

        final boolean[] enabled = {true};

        this.setOnTouchListener(new OnTouchListener() {
            private float mDx;
            private float mDy;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (!activeSmallPreview) {
                    return false;
                }

                int action = event.getAction();
                if (action == MotionEvent.ACTION_DOWN) {
                    mDx = mCurrentX - event.getRawX();
                    mDy = mCurrentY - event.getRawY();
                } else if (action == MotionEvent.ACTION_MOVE) {
                    mCurrentX = (int) (event.getRawX() + mDx);
                    mCurrentY = (int) (event.getRawY() + mDy);
                    WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams) getLayoutParams();
                    layoutParams.x = (int) mCurrentX;
                    layoutParams.y = (int) mCurrentY;
                    layoutParams.height = view.getHeight();
                    layoutParams.width = view.getWidth();
                    windowManager.updateViewLayout(view, layoutParams);
                }

                if (!enabled[0]) {
                    return true;
                }

                enabled[0] = false;

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        enabled[0] = true;
                    }
                }, 500);

                int x = (int) event.getX();
                int y = (int) event.getY();

                if (x >= recordingStopButton.getLeft() && x <= recordingStopButton.getRight()
                        && y >= recordingStopButton.getTop() && y <= recordingStopButton.getBottom()) {
                    if (cameraView.isTakingVideo()) {
                        mCameraCallbacks.onPreviewCancelButtonClicked();
                        mCameraCallbacks.onPreviewStopRecordingButtonClicked();

                    }
                }

                if (x >= dismissButton.getLeft() && x <= dismissButton.getRight()
                        && y >= dismissButton.getTop() && y <= dismissButton.getBottom()) {
                    mCameraCallbacks.onPreviewCancelButtonClicked();
                }

                return true;
            }
        });
    }

    /**
     * Sets an {@link AutoFocusMarker} to be notified of metering start, end and fail events
     * so that it can draw elements on screen.
     *
     * @param autoFocusMarker the marker, or null
     */
    public void setAutoFocusMarker(@Nullable AutoFocusMarker autoFocusMarker) {
        mAutoFocusMarker = autoFocusMarker;
        mMarkerLayout.onMarker(MarkerLayout.TYPE_AUTOFOCUS, autoFocusMarker);
    }

    /**
     * Instantiates the camera preview.
     *
     * @param preview   current preview value
     * @param context   a context
     * @param container the container
     * @return the preview
     */
    @NonNull
    protected CameraPreview instantiatePreview(@NonNull Preview preview,
                                               @NonNull Context context,
                                               @NonNull ViewGroup container) {
        switch (preview) {
            case SURFACE:
                return new SurfaceCameraPreview(context, container);
            case TEXTURE: {
                if (isHardwareAccelerated()) {
                    // TextureView is not supported without hardware acceleration.
                    return new TextureCameraPreview(context, container);
                }
            }
            case GL_SURFACE:
            default: {
                mPreview = Preview.GL_SURFACE;
                return new GlCameraPreview(context, container);
            }
        }
    }

    /**
     * Controls the preview engine. Should only be called
     * if this CameraView was never added to any window
     * (like if you created it programmatically).
     * Otherwise, it has no effect.
     *
     * @param preview desired preview engine
     * @see Preview#SURFACE
     * @see Preview#TEXTURE
     * @see Preview#GL_SURFACE
     */
    public void setPreview(@NonNull Preview preview) {
        boolean isNew = preview != mPreview;
        if (isNew) {
            mPreview = preview;
            boolean isAttachedToWindow = getWindowToken() != null;
            if (!isAttachedToWindow && mCameraPreview != null) {
                // Null the preview: will create another when re-attaching.
                mCameraPreview.onDestroy();
                mCameraPreview = null;
            }
        }
    }

    /**
     * Returns the current preview control.
     *
     * @return the current preview control
     * @see #setPreview(Preview)
     */
    @NonNull
    public Preview getPreview() {
        return mPreview;
    }

    /**
     * Controls the grids to be drawn over the current layout.
     *
     * @param gridMode desired grid mode
     * @see Grid#OFF
     * @see Grid#DRAW_3X3
     * @see Grid#DRAW_4X4
     * @see Grid#DRAW_PHI
     */
    public void setGrid(@NonNull Grid gridMode) {
        mGridLinesLayout.setGridMode(gridMode);
    }

    /**
     * Gets the current grid mode.
     *
     * @return the current grid mode
     */
    @NonNull
    public Grid getGrid() {
        return mGridLinesLayout.getGridMode();
    }

    /**
     * Controls the color of the grid lines that will be drawn
     * over the current layout.
     *
     * @param color a resolved color
     */
    public void setGridColor(@ColorInt int color) {
        mGridLinesLayout.setGridColor(color);
    }

    /**
     * Returns the current grid color.
     *
     * @return the current grid color
     */
    public int getGridColor() {
        return mGridLinesLayout.getGridColor();
    }

    /**
     * Sets the lifecycle owner for this view. This means you don't need
     * to call {@link #open()}, {@link #close()} or {@link #destroy()} at all.
     *
     * @param owner the owner activity or fragment
     */
    public void setLifecycleOwner(@NonNull LifecycleOwner owner) {
        if (mLifecycle != null) mLifecycle.removeObserver(this);
        mLifecycle = owner.getLifecycle();
        mLifecycle.addObserver(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        //if (mInEditor) return;
        if (mCameraPreview == null) {
            // isHardwareAccelerated will return the real value only after we are
            // attached. That's why we instantiate the preview here.
            doInstantiatePreview();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        //if (!mInEditor) mOrientationHelper.disable();
        super.onDetachedFromWindow();
    }

    /**
     * Preview is instantiated {@link #onAttachedToWindow()}, because
     * we want to know if we're hardware accelerated or not.
     * However, in tests, we might want to create the preview right after constructor.
     */
    @VisibleForTesting
    public void doInstantiatePreview() {
        LOG.w("doInstantiateEngine:", "instantiating. preview:", mPreview);
        mCameraPreview = instantiatePreview(mPreview, getContext(), this);
        LOG.w("doInstantiateEngine:", "instantiated. preview:",
                mCameraPreview.getClass().getSimpleName());
        mCameraEngine.setPreview(mCameraPreview);
        if (mPendingFilter != null) {
            setFilter(mPendingFilter);
            mPendingFilter = null;
        }
    }


    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    public void open() {
        if (mInEditor) return;
        if (mCameraPreview != null) mCameraPreview.onResume();
//        if (checkPermissions(getAudio())) {
//            // Update display orientation for current CameraEngine
//            mOrientationHelper.enable(getContext());
//            mCameraEngine.getAngles().setDisplayOffset(mOrientationHelper.getDisplayOffset());
//            mCameraEngine.start();
//        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    public void close() {
        if (mInEditor) return;
        //mCameraEngine.stop();
        if (mCameraPreview != null) mCameraPreview.onPause();
    }

    /**
     * Destroys this instance, releasing immediately
     * the camera resource.
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    public void destroy() {
        if (mInEditor) return;
        //clearCameraListeners();
        //clearFrameProcessors();
        //mCameraEngine.destroy();
        if (mCameraPreview != null) mCameraPreview.onDestroy();
    }

    //region Overlays

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attributeSet) {
        if (!mInEditor && mOverlayLayout.isOverlay(attributeSet)) {
            return mOverlayLayout.generateLayoutParams(attributeSet);
        }
        return super.generateLayoutParams(attributeSet);
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (!mInEditor && mOverlayLayout.isOverlay(params)) {
            mOverlayLayout.addView(child, params);
        } else {
            super.addView(child, index, params);
        }
    }

    @Override
    public void removeView(View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!mInEditor && params != null && mOverlayLayout.isOverlay(params)) {
            mOverlayLayout.removeView(view);
        } else {
            super.removeView(view);
        }
    }

    //endregion

    /**
     * Applies a real-time filter to the camera preview, if it supports it.
     * The only preview type that does so is currently {@link Preview#GL_SURFACE}.
     * <p>
     * The filter will be applied to any picture snapshot taken with
     * {@link CameraView#takePictureSnapshot()} and any video snapshot taken with
     * {@link CameraView#takeVideoSnapshot(File)}.
     * <p>
     * Use {@link NoFilter} to clear the existing filter,
     * and take a look at the {@link Filters} class for commonly used filters.
     * <p>
     * This method will throw an exception if the current preview does not support real-time
     * filters. Make sure you use {@link Preview#GL_SURFACE} (the default).
     *
     * @param filter a new filter
     * @see Filters
     */
    public void setFilter(@NonNull Filter filter) {
        if (mCameraPreview == null) {
            mPendingFilter = filter;
        } else {
            boolean isNoFilter = filter instanceof NoFilter;
            boolean isFilterPreview = mCameraPreview instanceof FilterCameraPreview;
            // If not a filter preview, we only allow NoFilter (called on creation).
            if (!isNoFilter && !isFilterPreview) {
                throw new RuntimeException("Filters are only supported by the GL_SURFACE preview." +
                        " Current preview:" + mPreview);
            }
            // If we have a filter preview, apply.
            if (isFilterPreview) {
                ((FilterCameraPreview) mCameraPreview).setFilter(filter);
            }
            // No-op: !isFilterPreview && isNoPreview
        }
    }

    /**
     * Returns the current real-time filter applied to the camera preview.
     * <p>
     * This method will throw an exception if the current preview does not support real-time
     * filters. Make sure you use {@link Preview#GL_SURFACE} (the default).
     *
     * @return the current filter
     * @see #setFilter(Filter)
     */
    @NonNull
    public Filter getFilter() {
        if (mCameraPreview == null) {
            return mPendingFilter;
        } else if (mCameraPreview instanceof FilterCameraPreview) {
            return ((FilterCameraPreview) mCameraPreview).getCurrentFilter();
        } else {
            throw new RuntimeException("Filters are only supported by the GL_SURFACE preview. " +
                    "Current:" + mPreview);
        }

    }

    //endregion

    //end region overlay

    /**
     * Starts a 3A touch metering process at the given coordinates, with respect
     * to the view width and height.
     *
     * @param x should be between 0 and getWidth()
     * @param y should be between 0 and getHeight()
     */
    public void startAutoFocus(float x, float y) {
        if (x < 0 || x > getWidth()) {
            throw new IllegalArgumentException("x should be >= 0 and <= getWidth()");
        }
        if (y < 0 || y > getHeight()) {
            throw new IllegalArgumentException("y should be >= 0 and <= getHeight()");
        }
        Size size = new Size(getWidth(), getHeight());
        PointF point = new PointF(x, y);
        MeteringRegions regions = MeteringRegions.fromPoint(size, point);
        mCameraEngine.startAutoFocus(null, regions, point);
    }

    /**
     * Starts a 3A touch metering process at the given coordinates, with respect
     * to the view width and height.
     *
     * @param region should be between 0 and getWidth() / getHeight()
     */
    public void startAutoFocus(@NonNull RectF region) {
        RectF full = new RectF(0, 0, getWidth(), getHeight());
        if (!full.contains(region)) {
            throw new IllegalArgumentException("Region is out of view bounds! " + region);
        }
        Size size = new Size(getWidth(), getHeight());
        MeteringRegions regions = MeteringRegions.fromArea(size, region);
        mCameraEngine.startAutoFocus(null, regions,
                new PointF(region.centerX(), region.centerY()));
    }

    //region Measuring behavior

    private String ms(int mode) {
        switch (mode) {
            case AT_MOST:
                return "AT_MOST";
            case EXACTLY:
                return "EXACTLY";
            case UNSPECIFIED:
                return "UNSPECIFIED";
        }
        return null;
    }

    /**
     * Measuring is basically controlled by layout params width and height.
     * The basic semantics are:
     * <p>
     * - MATCH_PARENT: CameraView should completely fill this dimension, even if this might mean
     * not respecting the preview aspect ratio.
     * - WRAP_CONTENT: CameraView should try to adapt this dimension to respect the preview
     * aspect ratio.
     * <p>
     * When both dimensions are MATCH_PARENT, CameraView will fill its
     * parent no matter the preview. Thanks to what happens in {@link CameraPreview}, this acts like
     * a CENTER CROP scale type.
     * <p>
     * When both dimensions are WRAP_CONTENT, CameraView will take the biggest dimensions that
     * fit the preview aspect ratio. This acts like a CENTER INSIDE scale type.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mInEditor) {
            final int width = MeasureSpec.getSize(widthMeasureSpec);
            final int height = MeasureSpec.getSize(heightMeasureSpec);
            super.onMeasure(MeasureSpec.makeMeasureSpec(width, EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, EXACTLY));
            return;
        }

        mLastPreviewStreamSize = mCameraEngine.getPreviewStreamSize(Reference.VIEW);
        if (mLastPreviewStreamSize == null) {
            LOG.w("onMeasure:", "surface is not ready. Calling default behavior.");
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        // Let's which dimensions need to be adapted.
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        final int widthValue = MeasureSpec.getSize(widthMeasureSpec);
        final int heightValue = MeasureSpec.getSize(heightMeasureSpec);
        final float previewWidth = mLastPreviewStreamSize.getWidth();
        final float previewHeight = mLastPreviewStreamSize.getHeight();

        // Pre-process specs
        final ViewGroup.LayoutParams lp = getLayoutParams();
        if (!mCameraPreview.supportsCropping()) {
            // We can't allow EXACTLY constraints in this case.
            if (widthMode == EXACTLY) widthMode = AT_MOST;
            if (heightMode == EXACTLY) heightMode = AT_MOST;
        } else {
            // If MATCH_PARENT is interpreted as AT_MOST, transform to EXACTLY
            // to be consistent with our semantics (and our docs).
            if (widthMode == AT_MOST && lp.width == MATCH_PARENT) widthMode = EXACTLY;
            if (heightMode == AT_MOST && lp.height == MATCH_PARENT) heightMode = EXACTLY;
        }

        LOG.i("onMeasure:", "requested dimensions are ("
                + widthValue + "[" + ms(widthMode) + "]x"
                + heightValue + "[" + ms(heightMode) + "])");
        LOG.i("onMeasure:", "previewSize is", "("
                + previewWidth + "x" + previewHeight + ")");

        // (1) If we have fixed dimensions (either 300dp or MATCH_PARENT), there's nothing we
        // should do, other than respect it. The preview will eventually be cropped at the sides
        // (by Preview scaling) except the case in which these fixed dimensions manage to fit
        // exactly the preview aspect ratio.
        if (widthMode == EXACTLY && heightMode == EXACTLY) {
            LOG.i("onMeasure:", "both are MATCH_PARENT or fixed value. We adapt.",
                    "This means CROP_CENTER.", "(" + widthValue + "x" + heightValue + ")");
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        // (2) If both dimensions are free, with no limits, then our size will be exactly the
        // preview size. This can happen rarely, for example in 2d scrollable containers.
        if (widthMode == UNSPECIFIED && heightMode == UNSPECIFIED) {
            LOG.i("onMeasure:", "both are completely free.",
                    "We respect that and extend to the whole preview size.",
                    "(" + previewWidth + "x" + previewHeight + ")");
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec((int) previewWidth, EXACTLY),
                    MeasureSpec.makeMeasureSpec((int) previewHeight, EXACTLY));
            return;
        }

        // It's sure now that at least one dimension can be determined (either because EXACTLY
        // or AT_MOST). This starts to seem a pleasant situation.

        // (3) If one of the dimension is completely free (e.g. in a scrollable container),
        // take the other and fit the ratio.
        // One of the two might be AT_MOST, but we use the value anyway.
        float ratio = previewHeight / previewWidth;
        if (widthMode == UNSPECIFIED || heightMode == UNSPECIFIED) {
            boolean freeWidth = widthMode == UNSPECIFIED;
            int height, width;
            if (freeWidth) {
                height = heightValue;
                width = Math.round(height / ratio);
            } else {
                width = widthValue;
                height = Math.round(width * ratio);
            }
            LOG.i("onMeasure:", "one dimension was free, we adapted it to fit the ratio.",
                    "(" + width + "x" + height + ")");
            super.onMeasure(MeasureSpec.makeMeasureSpec(width, EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, EXACTLY));
            return;
        }

        // (4) At this point both dimensions are either AT_MOST-AT_MOST, EXACTLY-AT_MOST or
        // AT_MOST-EXACTLY. Let's manage this sanely. If only one is EXACTLY, we can TRY to fit
        // the aspect ratio, but it is not guaranteed to succeed. It depends on the AT_MOST
        // value of the other dimensions.
        if (widthMode == EXACTLY || heightMode == EXACTLY) {
            boolean freeWidth = widthMode == AT_MOST;
            int height, width;
            if (freeWidth) {
                height = heightValue;
                width = Math.min(Math.round(height / ratio), widthValue);
            } else {
                width = widthValue;
                height = Math.min(Math.round(width * ratio), heightValue);
            }
            LOG.i("onMeasure:", "one dimension was EXACTLY, another AT_MOST.",
                    "We have TRIED to fit the aspect ratio, but it's not guaranteed.",
                    "(" + width + "x" + height + ")");
            super.onMeasure(MeasureSpec.makeMeasureSpec(width, EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, EXACTLY));
            return;
        }

        // (5) Last case, AT_MOST and AT_MOST. Here we can SURELY fit the aspect ratio by
        // filling one dimension and adapting the other.
        int height, width;
        float atMostRatio = (float) heightValue / (float) widthValue;
        if (atMostRatio >= ratio) {
            // We must reduce height.
            width = widthValue;
            height = Math.round(width * ratio);
        } else {
            height = heightValue;
            width = Math.round(height / ratio);
        }
        LOG.i("onMeasure:", "both dimension were AT_MOST.",
                "We fit the preview aspect ratio.",
                "(" + width + "x" + height + ")");
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, EXACTLY),
                MeasureSpec.makeMeasureSpec(height, EXACTLY));
    }

    //endregion

    //region Gesture APIs

    /**
     * Maps a {@link Gesture} to a certain gesture action.
     * For example, you can assign zoom control to the pinch gesture by just calling:
     * <code>
     * cameraView.mapGesture(Gesture.PINCH, GestureAction.ZOOM);
     * </code>
     * <p>
     * Not all actions can be assigned to a certain gesture. For example, zoom control can't be
     * assigned to the Gesture.TAP gesture. Look at {@link Gesture} to know more.
     * This method returns false if they are not assignable.
     *
     * @param gesture which gesture to map
     * @param action  which action should be assigned
     * @return true if this action could be assigned to this gesture
     */
    public boolean mapGesture(@NonNull Gesture gesture, @NonNull GestureAction action) {
        GestureAction none = GestureAction.NONE;
        if (gesture.isAssignableTo(action)) {
            mGestureMap.put(gesture, action);
            switch (gesture) {
                case PINCH:
                    mPinchGestureFinder.setActive(mGestureMap.get(Gesture.PINCH) != none);
                    break;
                case TAP:
                    // case DOUBLE_TAP:
                case LONG_TAP:
                    mTapGestureFinder.setActive(
                            mGestureMap.get(Gesture.TAP) != none ||
                                    // mGestureMap.get(Gesture.DOUBLE_TAP) != none ||
                                    mGestureMap.get(Gesture.LONG_TAP) != none);
                    break;
                case SCROLL_HORIZONTAL:
                case SCROLL_VERTICAL:
                    mScrollGestureFinder.setActive(
                            mGestureMap.get(Gesture.SCROLL_HORIZONTAL) != none ||
                                    mGestureMap.get(Gesture.SCROLL_VERTICAL) != none);
                    break;
            }
            return true;
        }
        mapGesture(gesture, none);
        return false;
    }

    /**
     * Clears any action mapped to the given gesture.
     *
     * @param gesture which gesture to clear
     */
    public void clearGesture(@NonNull Gesture gesture) {
        mapGesture(gesture, GestureAction.NONE);
    }

    /**
     * Returns the action currently mapped to the given gesture.
     *
     * @param gesture which gesture to inspect
     * @return mapped action
     */
    @NonNull
    public GestureAction getGestureAction(@NonNull Gesture gesture) {
        //noinspection ConstantConditions
        return mGestureMap.get(gesture);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return true; // Steal our own events.
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!cameraView.isOpened()) return true;

        if (activeSmallPreview) {
            return false;
        }

        // Pass to our own GestureLayouts
        CameraOptions options = mCameraEngine.getCameraOptions(); // Non null
        if (options == null) throw new IllegalStateException("Options should not be null here.");
        if (mPinchGestureFinder.onTouchEvent(event)) {
            LOG.i("onTouchEvent", "pinch!");
            onGesture(mPinchGestureFinder, options);
        } else if (mScrollGestureFinder.onTouchEvent(event)) {
            LOG.i("onTouchEvent", "scroll!");
            onGesture(mScrollGestureFinder, options);
        } else if (mTapGestureFinder.onTouchEvent(event)) {
            LOG.i("onTouchEvent", "tap!");
            onGesture(mTapGestureFinder, options);
        }

        return true;
    }

    // Some gesture layout detected a gesture. It's not known at this moment:
    // (1) if it was mapped to some action (we check here)
    // (2) if it's supported by the camera (CameraEngine checks)
    private void onGesture(@NonNull GestureFinder source, @NonNull CameraOptions options) {
        Gesture gesture = source.getGesture();
        GestureAction action = mGestureMap.get(gesture);
        PointF[] points = source.getPoints();
        float oldValue, newValue;
        //noinspection ConstantConditions
        switch (action) {

            case TAKE_PICTURE:
                //takePicture();
                break;

            case AUTO_FOCUS:
                Size size = new Size(getWidth(), getHeight());
                MeteringRegions regions = MeteringRegions.fromPoint(size, points[0]);
                mCameraEngine.startAutoFocus(gesture, regions, points[0]);
                break;

            case ZOOM:
                oldValue = mCameraEngine.getZoomValue();
                newValue = source.computeValue(oldValue, 0, 1);
                if (newValue != oldValue) {
                    mCameraEngine.setZoom(newValue, points, true);
                }
                break;

            case EXPOSURE_CORRECTION:
                oldValue = mCameraEngine.getExposureCorrectionValue();
                float minValue = options.getExposureCorrectionMinValue();
                float maxValue = options.getExposureCorrectionMaxValue();
                newValue = source.computeValue(oldValue, minValue, maxValue);
                if (newValue != oldValue) {
                    float[] bounds = new float[]{minValue, maxValue};
                    mCameraEngine.setExposureCorrection(newValue, bounds, points, true);
                }
                break;

            case FILTER_CONTROL_1:
                if (getFilter() instanceof OneParameterFilter) {
                    OneParameterFilter filter = (OneParameterFilter) getFilter();
                    oldValue = filter.getParameter1();
                    newValue = source.computeValue(oldValue, 0, 1);
                    if (newValue != oldValue) {
                        filter.setParameter1(newValue);
                    }
                }
                break;

            case FILTER_CONTROL_2:
                if (getFilter() instanceof TwoParameterFilter) {
                    TwoParameterFilter filter = (TwoParameterFilter) getFilter();
                    oldValue = filter.getParameter2();
                    newValue = source.computeValue(oldValue, 0, 1);
                    if (newValue != oldValue) {
                        filter.setParameter2(newValue);
                    }
                }
                break;
        }
    }

    //endregion
}
