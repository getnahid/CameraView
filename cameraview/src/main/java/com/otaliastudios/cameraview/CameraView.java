package com.otaliastudios.cameraview;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.location.Location;
import android.media.MediaActionSound;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.otaliastudios.cameraview.controls.Audio;
import com.otaliastudios.cameraview.controls.AudioCodec;
import com.otaliastudios.cameraview.controls.Control;
import com.otaliastudios.cameraview.controls.ControlParser;
import com.otaliastudios.cameraview.controls.Engine;
import com.otaliastudios.cameraview.controls.Facing;
import com.otaliastudios.cameraview.controls.Flash;
import com.otaliastudios.cameraview.controls.Grid;
import com.otaliastudios.cameraview.controls.Hdr;
import com.otaliastudios.cameraview.controls.Mode;
import com.otaliastudios.cameraview.controls.PictureFormat;
import com.otaliastudios.cameraview.controls.Preview;
import com.otaliastudios.cameraview.controls.VideoCodec;
import com.otaliastudios.cameraview.controls.WhiteBalance;
import com.otaliastudios.cameraview.engine.Camera1Engine;
import com.otaliastudios.cameraview.engine.Camera2Engine;
import com.otaliastudios.cameraview.engine.CameraEngine;
import com.otaliastudios.cameraview.engine.offset.Reference;
import com.otaliastudios.cameraview.engine.orchestrator.CameraState;
import com.otaliastudios.cameraview.filter.Filter;
import com.otaliastudios.cameraview.filter.FilterParser;
import com.otaliastudios.cameraview.frame.Frame;
import com.otaliastudios.cameraview.frame.FrameProcessor;
import com.otaliastudios.cameraview.gesture.Gesture;
import com.otaliastudios.cameraview.gesture.GestureAction;
import com.otaliastudios.cameraview.gesture.GestureFinder;
import com.otaliastudios.cameraview.internal.OrientationHelper;
import com.otaliastudios.cameraview.overlay.OverlayLayout;
import com.otaliastudios.cameraview.size.Size;
import com.otaliastudios.cameraview.size.SizeSelector;
import com.otaliastudios.cameraview.size.SizeSelectorParser;
import com.otaliastudios.cameraview.size.SizeSelectors;

import java.io.File;
import java.io.FileDescriptor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static android.content.Context.MODE_PRIVATE;

/**
 * Entry point for the whole library.
 * Please read documentation for usage and full set of features.
 */
public class CameraView {
    //public class CameraView extends FrameLayout implements LifecycleObserver {

    private final static String TAG = CameraView.class.getSimpleName();
    private static final CameraLogger LOG = CameraLogger.create(TAG);

    public final static int PERMISSION_REQUEST_CODE = 16;

    final static long DEFAULT_AUTOFOCUS_RESET_DELAY_MILLIS = 3000;
    final static boolean DEFAULT_PLAY_SOUNDS = true;
    final static boolean DEFAULT_USE_DEVICE_ORIENTATION = true;
    final static boolean DEFAULT_PICTURE_METERING = true;
    final static boolean DEFAULT_PICTURE_SNAPSHOT_METERING = false;
    final static boolean DEFAULT_REQUEST_PERMISSIONS = true;
    final static int DEFAULT_FRAME_PROCESSING_POOL_SIZE = 2;
    final static int DEFAULT_FRAME_PROCESSING_EXECUTORS = 1;

    // Self managed parameters
    private boolean mPlaySounds;
    private boolean mUseDeviceOrientation;
    private boolean mRequestPermissions;
    private HashMap<Gesture, GestureAction> mGestureMap = new HashMap<>(4);
    private Preview mPreview;
    private Engine mEngine;
    private Filter mPendingFilter;
    private int mFrameProcessingExecutors;

    // Components
    private Handler mUiHandler;
    private Executor mFrameProcessingExecutor;
    @VisibleForTesting CameraCallbacks mCameraCallbacks;
    private OrientationHelper mOrientationHelper;
    private CameraEngine mCameraEngine;
    private Size mLastPreviewStreamSize;
    private MediaActionSound mSound;
    @VisibleForTesting List<CameraListener> mListeners = new CopyOnWriteArrayList<>();
    @VisibleForTesting List<FrameProcessor> mFrameProcessors = new CopyOnWriteArrayList<>();

    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private boolean mExperimental = true;
    private SharedPreferences preference;

    // Threading
    //private WorkerHandler mFrameProcessorsHandler;
    private Context context;
    private CameraViewParent cameraViewParent;
    private ControlParser controlParser;
    private SizeSelectorParser sizeSelectors;
    @VisibleForTesting OverlayLayout mOverlayLayout;

    public static final String KEY_CAMERA_PLAY_SOUND = "CameraView_cameraPlaySounds";
    public static final String KEY_CAMERA_DEVICE_ORIENTATION = "CameraView_cameraUseDeviceOrientation";
    public static final String KEY_CAMERA_EXPERIMENTAL = "CameraView_cameraExperimental";
    public static final String KEY_CAMERA_GRID_COLOR = "CameraView_cameraGridColor";
    public static final String KEY_CAMERA_VIDEO_MAX_SIZE = "CameraView_cameraVideoMaxSize";
    public static final String KEY_CAMERA_VIDEO_MAX_DURATION = "CameraView_cameraVideoMaxDuration";
    public static final String KEY_CAMERA_VIDEO_BIT_RATE = "CameraView_cameraVideoBitRate";
    public static final String KEY_CAMERA_AUDIO_BIT_RATE = "CameraView_cameraAudioBitRate";
    public static final String KEY_CAMERA_AUTO_FOCUS_RESET_DELAY = "CameraView_cameraAutoFocusResetDelay";
    public static final String KEY_CAMERA_PICTURE_METERING = "CameraView_cameraPictureMetering";
    public static final String KEY_CAMERA_SNAPSHOT_METERING = "CameraView_cameraPictureSnapshotMetering";
    public static final String KEY_PREVIEW_FRAME_RATE = "CameraView_cameraPreviewFrameRate";
    public static final String KEY_PREVIEW_FRAME_RATE_EXACT = "cameraPreviewFrameRateExact";
    public static final String KEY_CAMERA_REQUEST_PERMISSIONS = "CameraView_cameraRequestPermissions";
    public static final String KEY_CAMERA_SNAPSHOT_MAX_WIDTH = "CameraView_cameraSnapshotMaxWidth";
    public static final String KEY_CAMERA_SNAPSHOT_MAX_HEIGHT = "CameraView_cameraSnapshotMaxHeight";
    public static final String KEY_CAMERA_FRAME_PROCESSING_MAX_WIDTH = "CameraView_cameraFrameProcessingMaxWidth";
    public static final String KEY_CAMERA_FRAME_PROCESSING_MAX_HEIGHT = "CameraView_cameraFrameProcessingMaxHeight";
    public static final String KEY_CAMERA_FRAME_PROCESSING_FORMAT = "CameraView_cameraFrameProcessingFormat";
    public static final String KEY_CAMERA_FRAME_PROCESSING_POOL_SIZE = "CameraView_cameraFrameProcessingPoolSize";
    public static final String KEY_CAMERA_FRAME_PROCESSING_EXECUTORS = "CameraView_cameraFrameProcessingExecutors";


    public CameraView(@NonNull Context context) {
        //super(context, null);
        this.context = context;
        initialize(context);
    }

    public ControlParser getControlParser(){
        return controlParser;
    }

//    public CameraView(@NonNull Context context, @Nullable AttributeSet attrs) {
//        super(context, attrs);
//        initialize(context, attrs);
//    }

    //region Init

    @SuppressWarnings("WrongConstant")
    private void initialize(@NonNull Context context) {
        //setWillNotDraw(false);
        //TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.CameraView, 0, 0);
        preference = context.getSharedPreferences("camera_view", MODE_PRIVATE);
        controlParser = new ControlParser(context, preference);

        // Self managed
        boolean playSounds = preference.getBoolean(KEY_CAMERA_PLAY_SOUND, DEFAULT_PLAY_SOUNDS);
        boolean useDeviceOrientation = preference.getBoolean(KEY_CAMERA_DEVICE_ORIENTATION, DEFAULT_USE_DEVICE_ORIENTATION);
        mExperimental = preference.getBoolean(KEY_CAMERA_EXPERIMENTAL, true);
        //mPreview = controls.getPreview();
        mEngine = controlParser.getEngine();
        mRequestPermissions = preference.getBoolean(KEY_CAMERA_REQUEST_PERMISSIONS, DEFAULT_REQUEST_PERMISSIONS);

        // Camera engine params
        //int gridColor = a.getColor(R.styleable.CameraView_cameraGridColor, GridLinesLayout.DEFAULT_COLOR);
        //int gridColor = GridLinesLayout.DEFAULT_COLOR;
        long videoMaxSize = (long) preference.getFloat(KEY_CAMERA_VIDEO_MAX_SIZE, 0);
        int videoMaxDuration = preference.getInt(KEY_CAMERA_VIDEO_MAX_DURATION, 0);
        int videoBitRate = preference.getInt(KEY_CAMERA_VIDEO_BIT_RATE, 0);
        int audioBitRate = preference.getInt(KEY_CAMERA_AUDIO_BIT_RATE, 0);
        long autoFocusResetDelay = (long) preference.getInt(KEY_CAMERA_AUTO_FOCUS_RESET_DELAY, (int) DEFAULT_AUTOFOCUS_RESET_DELAY_MILLIS);
        boolean pictureMetering = preference.getBoolean(KEY_CAMERA_PICTURE_METERING, DEFAULT_PICTURE_METERING);
        boolean pictureSnapshotMetering = preference.getBoolean(KEY_CAMERA_SNAPSHOT_METERING, DEFAULT_PICTURE_SNAPSHOT_METERING);
        float videoFrameRate = (float) preference.getInt(KEY_PREVIEW_FRAME_RATE, 0);
        boolean videoFrameRateExact = preference.getBoolean(KEY_PREVIEW_FRAME_RATE_EXACT, false);
        int snapshotMaxWidth = preference.getInt(KEY_CAMERA_SNAPSHOT_MAX_WIDTH, 0);
        int snapshotMaxHeight = preference.getInt(KEY_CAMERA_SNAPSHOT_MAX_HEIGHT, 0);
        int frameMaxWidth = preference.getInt(KEY_CAMERA_FRAME_PROCESSING_MAX_WIDTH, 0);
        int frameMaxHeight = preference.getInt(KEY_CAMERA_FRAME_PROCESSING_MAX_HEIGHT, 0);
        int frameFormat = preference.getInt(KEY_CAMERA_FRAME_PROCESSING_FORMAT, 0);
        int framePoolSize = preference.getInt(KEY_CAMERA_FRAME_PROCESSING_POOL_SIZE, DEFAULT_FRAME_PROCESSING_POOL_SIZE);
        int frameExecutors = preference.getInt(KEY_CAMERA_FRAME_PROCESSING_EXECUTORS, DEFAULT_FRAME_PROCESSING_EXECUTORS);

        FilterParser filters = new FilterParser(preference);

        //a.recycle();

        // Components
        mCameraCallbacks = new CameraCallbacks();
        mUiHandler = new Handler(Looper.getMainLooper());

        // Create the engine
        doInstantiateEngine();

        // Size selectors and gestures
        mCameraEngine.collectCameraInfo(controlParser.getFacing());
        sizeSelectors = new SizeSelectorParser(preference, mCameraEngine.getCameraId(), mEngine);

        // Apply self managed
        setPlaySounds(playSounds);
        setUseDeviceOrientation(useDeviceOrientation);

        // Apply camera engine params
        // Adding new ones? See setEngine().
        setFacing(controlParser.getFacing());
        setFlash(controlParser.getFlash());
        setMode(controlParser.getMode());
        setWhiteBalance(controlParser.getWhiteBalance());
        setHdr(controlParser.getHdr());
        setAudio(controlParser.getAudio());
        setAudioBitRate(audioBitRate);
        setAudioCodec(controlParser.getAudioCodec());
        setPictureSize(sizeSelectors.getPictureSizeSelector());
        setPictureMetering(pictureMetering);
        setPictureSnapshotMetering(pictureSnapshotMetering);
        setPictureFormat(controlParser.getPictureFormat());
        setVideoSize(sizeSelectors.getVideoSizeSelector());
        setVideoCodec(controlParser.getVideoCodec());
        setVideoMaxSize(videoMaxSize);
        setVideoMaxDuration(videoMaxDuration);
        setVideoBitRate(videoBitRate);
        setAutoFocusResetDelay(autoFocusResetDelay);
        setPreviewFrameRateExact(videoFrameRateExact);
        setPreviewFrameRate(videoFrameRate);
        setSnapshotMaxWidth(snapshotMaxWidth);
        setSnapshotMaxHeight(snapshotMaxHeight);
        setFrameProcessingMaxWidth(frameMaxWidth);
        setFrameProcessingMaxHeight(frameMaxHeight);
        setFrameProcessingFormat(frameFormat);
        setFrameProcessingPoolSize(framePoolSize);
        setFrameProcessingExecutors(frameExecutors);

        // Apply filters
        //setFilter(filters.getFilter());

        // Create the orientation helper
        mOrientationHelper = new OrientationHelper(context, mCameraCallbacks);
    }

    public CameraEngine getCameraEngine(){
        return mCameraEngine;
    }

    public CameraCallbacks getCameraCallbacks(){
        return mCameraCallbacks;
    }

    public SizeSelectorParser getSizeSelectorParser(){
        return sizeSelectors;
    }

    /**
     * Engine is instantiated on creation and anytime
     * {@link #setEngine(Engine)} is called.
     */
    private void doInstantiateEngine() {
        LOG.w("doInstantiateEngine:", "instantiating. engine:", mEngine);
        mCameraEngine = instantiateCameraEngine(mEngine, mCameraCallbacks);
        LOG.w("doInstantiateEngine:", "instantiated. engine:",
                mCameraEngine.getClass().getSimpleName());
        mCameraEngine.setOverlay(mOverlayLayout);
    }

    private Context getContext(){
        return context;
    }

    /**
     * Instantiates the camera engine.
     *
     * @param engine the engine preference
     * @param callback the engine callback
     * @return the engine
     */
    @NonNull
    protected CameraEngine instantiateCameraEngine(@NonNull Engine engine,
                                                   @NonNull CameraEngine.Callback callback) {
        if (mExperimental
                && engine == Engine.CAMERA2
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return new Camera2Engine(callback);
        } else {
            mEngine = Engine.CAMERA1;
            return new Camera1Engine(callback);
        }
    }

    //@Override
    protected void onAttachedToWindow() {
        //super.onAttachedToWindow();
        //if (mInEditor) return;
        //if (mCameraPreview == null) {
            // isHardwareAccelerated will return the real value only after we are
            // attached. That's why we instantiate the preview here.
           // doInstantiatePreview(parent);
        //}
        //mOrientationHelper.enable();
    }

    //@Override
    protected void onDetachedFromWindow() {
        //if (!mInEditor)
        //mOrientationHelper.disable();
        mLastPreviewStreamSize = null;
        //super.onDetachedFromWindow();
    }

    //endregion

    //region Lifecycle APIs

    /**
     * Sets permissions flag if you want enable auto check permissions or disable it.
     * @param requestPermissions - true: auto check permissions enabled, false: auto check permissions disabled.
     */
    @SuppressWarnings("unused")
    public void setRequestPermissions(boolean requestPermissions) {
        mRequestPermissions = requestPermissions;
    }

    /**
     * Returns whether the camera engine has started.
     * @return whether the camera has started
     */
    public boolean isOpened() {
        return mCameraEngine.getState().isAtLeast(CameraState.ENGINE)
                && mCameraEngine.getTargetState().isAtLeast(CameraState.ENGINE);
    }

    private boolean isClosed() {
        return mCameraEngine.getState() == CameraState.OFF
                && !mCameraEngine.isChangingState();
    }

    /**
     * Starts the camera preview, if not started already.
     * This should be called onResume(), or when you are ready with permissions.
     */
    //@OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    public void open() {
        //if (mInEditor) return;
        //if (mCameraPreview != null) mCameraPreview.onResume();
        if (checkPermissions(getAudio())) {
            // Update display orientation for current CameraEngine
            mOrientationHelper.enable();
            mCameraEngine.getAngles().setDisplayOffset(mOrientationHelper.getLastDisplayOffset());
            mCameraEngine.start();
        }
    }

    /**
     * Checks that we have appropriate permissions.
     * This means checking that we have audio permissions if audio = Audio.ON.
     * @param audio the audio setting to be checked
     * @return true if we can go on, false otherwise.
     */
    @SuppressWarnings("ConstantConditions")
    @SuppressLint("NewApi")
    protected boolean checkPermissions(@NonNull Audio audio) {
        checkPermissionsManifestOrThrow(audio);
        // Manifest is OK at this point. Let's check runtime permissions.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;

        Context c = getContext();
        boolean needsCamera = true;
        boolean needsAudio = audio == Audio.ON || audio == Audio.MONO || audio == Audio.STEREO;

        needsCamera = needsCamera && c.checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED;
        needsAudio = needsAudio && c.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED;

        if (!needsCamera && !needsAudio) {
            return true;
        } else if (mRequestPermissions) {
            requestPermissions(needsCamera, needsAudio);
            return false;
        } else {
            return false;
        }
    }

    /**
     * If audio is on we will ask for RECORD_AUDIO permission.
     * If the developer did not add this to its manifest, throw and fire warnings.
     */
    private void checkPermissionsManifestOrThrow(@NonNull Audio audio) {
        if (audio == Audio.ON || audio == Audio.MONO || audio == Audio.STEREO) {
            try {
                PackageManager manager = getContext().getPackageManager();
                PackageInfo info = manager.getPackageInfo(getContext().getPackageName(),
                        PackageManager.GET_PERMISSIONS);
                for (String requestedPermission : info.requestedPermissions) {
                    if (requestedPermission.equals(Manifest.permission.RECORD_AUDIO)) {
                        return;
                    }
                }
                String message = LOG.e("Permission error: when audio is enabled (Audio.ON)" +
                        " the RECORD_AUDIO permission should be added to the app manifest file.");
                throw new IllegalStateException(message);
            } catch (PackageManager.NameNotFoundException e) {
                // Not possible.
            }
        }
    }

    /**
     * Stops the current preview, if any was started.
     * This should be called onPause().
     */
    //@OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    public void close() {
        //if (mInEditor) return;
        mCameraEngine.stop(false);
        //if (mCameraPreview != null) mCameraPreview.onPause();
    }

    /**
     * Destroys this instance, releasing immediately
     * the camera resource.
     */
    //@OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    public void destroy() {
        //if (mInEditor) return;
        clearCameraListeners();
        clearFrameProcessors();
        mCameraEngine.destroy(true);
        //if (mCameraPreview != null) mCameraPreview.onDestroy();
        if(cameraViewParent != null){
            cameraViewParent.destroy();
        }
    }

    //endregion

    //region Public APIs for controls

    /**
     * Sets the experimental flag which occasionally can enable
     * new, unstable beta features.
     * @param experimental true to enable new features
     */
    public void setExperimental(boolean experimental) {
        mExperimental = experimental;
    }

    /**
     * Shorthand for the appropriate set* method.
     * For example, if control is a {@link Grid}, this calls {@link #setGrid(Grid)}.
     *
     * @param control desired value
     */
    public void set(@NonNull Control control) {
        if (control instanceof Audio) {
            setAudio((Audio) control);
        } else if (control instanceof Facing) {
            setFacing((Facing) control);
        } else if (control instanceof Flash) {
            setFlash((Flash) control);
        } else if (control instanceof Grid) {
            //setGrid((Grid) control);
        } else if (control instanceof Hdr) {
            setHdr((Hdr) control);
        } else if (control instanceof Mode) {
            setMode((Mode) control);
        } else if (control instanceof WhiteBalance) {
            setWhiteBalance((WhiteBalance) control);
        } else if (control instanceof VideoCodec) {
            setVideoCodec((VideoCodec) control);
        } else if (control instanceof AudioCodec) {
            setAudioCodec((AudioCodec) control);
        } else if (control instanceof Preview) {
            //setPreview((Preview) control);
        } else if (control instanceof Engine) {
            setEngine((Engine) control);
        } else if (control instanceof PictureFormat) {
            setPictureFormat((PictureFormat) control);
        }
    }

    /**
     * Shorthand for the appropriate get* method.
     * For example, if control class is a {@link Grid}, this calls {@link #getGrid()}.
     *
     * @param controlClass desired value class
     * @param <T> the class type
     * @return the control
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public <T extends Control> T get(@NonNull Class<T> controlClass) {
        if (controlClass == Audio.class) {
            return (T) getAudio();
        } else if (controlClass == Facing.class) {
            return (T) getFacing();
        } else if (controlClass == Flash.class) {
            return (T) getFlash();
        } else if (controlClass == Grid.class) {
            return null;//(T) getGrid();
        } else if (controlClass == Hdr.class) {
            return (T) getHdr();
        } else if (controlClass == Mode.class) {
            return (T) getMode();
        } else if (controlClass == WhiteBalance.class) {
            return (T) getWhiteBalance();
        } else if (controlClass == VideoCodec.class) {
            return (T) getVideoCodec();
        } else if (controlClass == AudioCodec.class) {
            return (T) getAudioCodec();
        } else if (controlClass == Preview.class) {
            return (T) null;//getPreview();
        } else if (controlClass == Engine.class) {
            return (T) getEngine();
        } else if (controlClass == PictureFormat.class) {
            return (T) getPictureFormat();
        } else {
            throw new IllegalArgumentException("Unknown control class: " + controlClass);
        }
    }

    /**
     * Controls the core engine. Should only be called
     * if this CameraView is closed (open() was never called).
     * Otherwise, it has no effect.
     *
     * @see Engine#CAMERA1
     * @see Engine#CAMERA2
     *
     * @param engine desired engine
     */
    public void setEngine(@NonNull Engine engine) {
        if (!isClosed()) return;
        mEngine = engine;
        CameraEngine oldEngine = mCameraEngine;
        doInstantiateEngine();
        //if (mCameraPreview != null) mCameraEngine.setPreview(mCameraPreview);

        // Set again all parameters
        setFacing(oldEngine.getFacing());
        setFlash(oldEngine.getFlash());
        setMode(oldEngine.getMode());
        setWhiteBalance(oldEngine.getWhiteBalance());
        setHdr(oldEngine.getHdr());
        setAudio(oldEngine.getAudio());
        setAudioBitRate(oldEngine.getAudioBitRate());
        setAudioCodec(oldEngine.getAudioCodec());
        setPictureSize(oldEngine.getPictureSizeSelector());
        setPictureFormat(oldEngine.getPictureFormat());
        setVideoSize(oldEngine.getVideoSizeSelector());
        setVideoCodec(oldEngine.getVideoCodec());
        setVideoMaxSize(oldEngine.getVideoMaxSize());
        setVideoMaxDuration(oldEngine.getVideoMaxDuration());
        setVideoBitRate(oldEngine.getVideoBitRate());
        setAutoFocusResetDelay(oldEngine.getAutoFocusResetDelay());
        setPreviewFrameRate(oldEngine.getPreviewFrameRate());
        setPreviewFrameRateExact(oldEngine.getPreviewFrameRateExact());
        setSnapshotMaxWidth(oldEngine.getSnapshotMaxWidth());
        setSnapshotMaxHeight(oldEngine.getSnapshotMaxHeight());
        setFrameProcessingMaxWidth(oldEngine.getFrameProcessingMaxWidth());
        setFrameProcessingMaxHeight(oldEngine.getFrameProcessingMaxHeight());
        setFrameProcessingFormat(0 /* this is very engine specific, so do not pass */);
        setFrameProcessingPoolSize(oldEngine.getFrameProcessingPoolSize());
        mCameraEngine.setHasFrameProcessors(!mFrameProcessors.isEmpty());
    }

    /**
     * Returns the current engine control.
     *
     * @see #setEngine(Engine)
     * @return the current engine control
     */
    @NonNull
    public Engine getEngine() {
        return mEngine;
    }

    /**
     * Returns a {@link CameraOptions} instance holding supported options for this camera
     * session. This might change over time. It's better to hold a reference from
     * {@link CameraListener#onCameraOpened(CameraOptions)}.
     *
     * @return an options map, or null if camera was not opened
     */
    @Nullable
    public CameraOptions getCameraOptions() {
        return mCameraEngine.getCameraOptions();
    }

    /**
     * Sets exposure adjustment, in EV stops. A positive value will mean brighter picture.
     *
     * If camera is not opened, this will have no effect.
     * If {@link CameraOptions#isExposureCorrectionSupported()} is false, this will have no effect.
     * The provided value should be between the bounds returned by {@link CameraOptions}, or it will
     * be capped.
     *
     * @see CameraOptions#getExposureCorrectionMinValue()
     * @see CameraOptions#getExposureCorrectionMaxValue()
     *
     * @param EVvalue exposure correction value.
     */
    public void setExposureCorrection(float EVvalue) {
        CameraOptions options = getCameraOptions();
        if (options != null) {
            float min = options.getExposureCorrectionMinValue();
            float max = options.getExposureCorrectionMaxValue();
            if (EVvalue < min) EVvalue = min;
            if (EVvalue > max) EVvalue = max;
            float[] bounds = new float[]{min, max};
            mCameraEngine.setExposureCorrection(EVvalue, bounds, null, false);
        }
    }

    /**
     * Returns the current exposure correction value, typically 0
     * at start-up.
     * @return the current exposure correction value
     */
    public float getExposureCorrection() {
        return mCameraEngine.getExposureCorrectionValue();
    }

    /**
     * Sets a zoom value. This is not guaranteed to be supported by the current device,
     * but you can take a look at {@link CameraOptions#isZoomSupported()}.
     * This will have no effect if called before the camera is opened.
     *
     * Zoom value should be between 0 and 1, where 1 will be the maximum available zoom.
     * If it's not, it will be capped.
     *
     * @param zoom value in [0,1]
     */
    public void setZoom(float zoom) {
        if (zoom < 0) zoom = 0;
        if (zoom > 1) zoom = 1;
        mCameraEngine.setZoom(zoom, null, false);
    }

    /**
     * Returns the current zoom value, something between 0 and 1.
     * @return the current zoom value
     */
    public float getZoom() {
        return mCameraEngine.getZoomValue();
    }

    /**
     * Controls the grids to be drawn over the current layout.
     *
     * @see Hdr#OFF
     * @see Hdr#ON
     *
     * @param hdr desired hdr value
     */
    public void setHdr(@NonNull Hdr hdr) {
        mCameraEngine.setHdr(hdr);
    }

    /**
     * Gets the current hdr value.
     * @return the current hdr value
     */
    @NonNull
    public Hdr getHdr() {
        return mCameraEngine.getHdr();
    }

    /**
     * Set location coordinates to be found later in the EXIF header
     *
     * @param latitude current latitude
     * @param longitude current longitude
     */
    public void setLocation(double latitude, double longitude) {
        Location location = new Location("Unknown");
        location.setTime(System.currentTimeMillis());
        location.setAltitude(0);
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        mCameraEngine.setLocation(location);
    }

    /**
     * Set location values to be found later in the EXIF header
     *
     * @param location current location
     */
    public void setLocation(@Nullable Location location) {
        mCameraEngine.setLocation(location);
    }

    /**
     * Retrieves the location previously applied with setLocation().
     *
     * @return the current location, if any.
     */
    @Nullable
    public Location getLocation() {
        return mCameraEngine.getLocation();
    }

    /**
     * Sets desired white balance to current camera session.
     *
     * @see WhiteBalance#AUTO
     * @see WhiteBalance#INCANDESCENT
     * @see WhiteBalance#FLUORESCENT
     * @see WhiteBalance#DAYLIGHT
     * @see WhiteBalance#CLOUDY
     *
     * @param whiteBalance desired white balance behavior.
     */
    public void setWhiteBalance(@NonNull WhiteBalance whiteBalance) {
        mCameraEngine.setWhiteBalance(whiteBalance);
    }

    /**
     * Returns the current white balance behavior.
     * @return white balance value.
     */
    @NonNull
    public WhiteBalance getWhiteBalance() {
        return mCameraEngine.getWhiteBalance();
    }

    /**
     * Sets which camera sensor should be used.
     *
     * @see Facing#FRONT
     * @see Facing#BACK
     *
     * @param facing a facing value.
     */
    public void setFacing(@NonNull Facing facing) {
        mCameraEngine.setFacing(facing);
    }

    /**
     * Gets the facing camera currently being used.
     * @return a facing value.
     */
    @NonNull
    public Facing getFacing() {
        return mCameraEngine.getFacing();
    }

    /**
     * Toggles the facing value between {@link Facing#BACK}
     * and {@link Facing#FRONT}.
     *
     * @return the new facing value
     */
    public Facing toggleFacing() {
        Facing facing = mCameraEngine.getFacing();
        switch (facing) {
            case BACK:
                setFacing(Facing.FRONT);
                break;

            case FRONT:
                setFacing(Facing.BACK);
                break;
        }

        return mCameraEngine.getFacing();
    }

    /**
     * Sets the flash mode.
     *
     * @see Flash#OFF
     * @see Flash#ON
     * @see Flash#AUTO
     * @see Flash#TORCH

     * @param flash desired flash mode.
     */
    public void setFlash(@NonNull Flash flash) {
        mCameraEngine.setFlash(flash);
    }

    /**
     * Gets the current flash mode.
     * @return a flash mode
     */
    @NonNull
    public Flash getFlash() {
        return mCameraEngine.getFlash();
    }

    /**
     * Controls the audio mode.
     *
     * @see Audio#OFF
     * @see Audio#ON
     * @see Audio#MONO
     * @see Audio#STEREO
     *
     * @param audio desired audio value
     */
    public void setAudio(@NonNull Audio audio) {

        if (audio == getAudio() || isClosed()) {
            // Check did took place, or will happen on start().
            mCameraEngine.setAudio(audio);

        } else if (checkPermissions(audio)) {
            // Camera is running. Pass.
            mCameraEngine.setAudio(audio);

        } else {
            // This means that the audio permission is being asked.
            // Stop the camera so it can be restarted by the developer onPermissionResult.
            // Developer must also set the audio value again...
            // Not ideal but good for now.
            close();
        }
    }

    /**
     * Gets the current audio value.
     * @return the current audio value
     */
    @NonNull
    public Audio getAudio() {
        return mCameraEngine.getAudio();
    }


    /**
     * Sets the current delay in milliseconds to reset the focus after a metering event.
     *
     * @param delayMillis desired delay (in milliseconds). If the delay
     *                    is less than or equal to 0 or equal to Long.MAX_VALUE,
     *                    the values will not be reset.
     */
    public void setAutoFocusResetDelay(long delayMillis) {
        mCameraEngine.setAutoFocusResetDelay(delayMillis);
    }

    /**
     * Returns the current delay in milliseconds to reset the focus after a metering event.
     *
     * @return the current reset delay in milliseconds
     */
    @SuppressWarnings("unused")
    public long getAutoFocusResetDelay() { return mCameraEngine.getAutoFocusResetDelay(); }

    /**
     * <strong>ADVANCED FEATURE</strong> - sets a size selector for the preview stream.
     * The {@link SizeSelector} will be invoked with the list of available sizes, and the first
     * acceptable size will be accepted and passed to the internal engine and surface.
     *
     * This is typically NOT NEEDED. The default size selector is already smart enough to respect
     * the picture/video output aspect ratio, and be bigger than the surface so that there is no
     * upscaling. If all you want is set an aspect ratio, use {@link #setPictureSize(SizeSelector)}
     * and {@link #setVideoSize(SizeSelector)}.
     *
     * When stream size changes, the {@link CameraView} is remeasured so any WRAP_CONTENT dimension
     * is recomputed accordingly.
     *
     * See the {@link SizeSelectors} class for handy utilities for creating selectors.
     *
     * @param selector a size selector
     */
    public void setPreviewStreamSize(@NonNull SizeSelector selector) {
        mCameraEngine.setPreviewStreamSizeSelector(selector);
    }

    /**
     * Set the current session type to either picture or video.
     *
     * @see Mode#PICTURE
     * @see Mode#VIDEO
     *
     * @param mode desired session type.
     */
    public void setMode(@NonNull Mode mode) {
        mCameraEngine.setMode(mode);
    }

    /**
     * Gets the current mode.
     * @return the current mode
     */
    @NonNull
    public Mode getMode() {
        return mCameraEngine.getMode();
    }

    /**
     * Sets a capture size selector for picture mode.
     * The {@link SizeSelector} will be invoked with the list of available sizes, and the first
     * acceptable size will be accepted and passed to the internal engine.
     * See the {@link SizeSelectors} class for handy utilities for creating selectors.
     *
     * @param selector a size selector
     */
    public void setPictureSize(@NonNull SizeSelector selector) {
        mCameraEngine.setPictureSizeSelector(selector);
    }

    /**
     * Whether the engine should perform a metering sequence before taking pictures requested
     * with {@link #takePicture()}. A metering sequence includes adjusting focus, exposure
     * and white balance to ensure a good quality of the result.
     *
     * When this parameter is true, the quality of the picture increases, but the latency
     * increases as well. Defaults to true.
     *
     * This is a CAMERA2 only API. On CAMERA1, picture metering is always enabled.
     *
     * @see #setPictureSnapshotMetering(boolean)
     * @param enable true to enable
     */
    public void setPictureMetering(boolean enable) {
        mCameraEngine.setPictureMetering(enable);
    }

    /**
     * Whether the engine should perform a metering sequence before taking pictures requested
     * with {@link #takePicture()}. See {@link #setPictureMetering(boolean)}.
     *
     * @see #setPictureMetering(boolean)
     * @return true if picture metering is enabled
     */
    public boolean getPictureMetering() {
        return mCameraEngine.getPictureMetering();
    }

    /**
     * Whether the engine should perform a metering sequence before taking pictures requested
     * with {@link #takePictureSnapshot()}. A metering sequence includes adjusting focus,
     * exposure and white balance to ensure a good quality of the result.
     *
     * When this parameter is true, the quality of the picture increases, but the latency
     * increases as well. To keep snapshots fast, this defaults to false.
     *
     * This is a CAMERA2 only API. On CAMERA1, picture snapshot metering is always disabled.
     *
     * @see #setPictureMetering(boolean)
     * @param enable true to enable
     */
    public void setPictureSnapshotMetering(boolean enable) {
        mCameraEngine.setPictureSnapshotMetering(enable);
    }

    /**
     * Whether the engine should perform a metering sequence before taking pictures requested
     * with {@link #takePictureSnapshot()}. See {@link #setPictureSnapshotMetering(boolean)}.
     *
     * @see #setPictureSnapshotMetering(boolean)
     * @return true if picture metering is enabled
     */
    public boolean getPictureSnapshotMetering() {
        return mCameraEngine.getPictureSnapshotMetering();
    }

    /**
     * Sets the format for pictures taken with {@link #takePicture()}. This format does not apply
     * to picture snapshots taken with {@link #takePictureSnapshot()}.
     * The {@link PictureFormat#JPEG} is always supported - for other values, please check
     * the {@link CameraOptions#getSupportedPictureFormats()} value.
     *
     * @param pictureFormat new format
     */
    public void setPictureFormat(@NonNull PictureFormat pictureFormat) {
        mCameraEngine.setPictureFormat(pictureFormat);
    }

    /**
     * Returns the current picture format.
     * @see #setPictureFormat(PictureFormat)
     * @return the picture format
     */
    @NonNull
    public PictureFormat getPictureFormat() {
        return mCameraEngine.getPictureFormat();
    }


    /**
     * Sets a capture size selector for video mode.
     * The {@link SizeSelector} will be invoked with the list of available sizes, and the first
     * acceptable size will be accepted and passed to the internal engine.
     * See the {@link SizeSelectors} class for handy utilities for creating selectors.
     *
     * @param selector a size selector
     */
    public void setVideoSize(@NonNull SizeSelector selector) {
        mCameraEngine.setVideoSizeSelector(selector);
    }

    /**
     * Sets the bit rate in bits per second for video capturing.
     * Will be used by both {@link #takeVideo(File)} and {@link #takeVideoSnapshot(File)}.
     *
     * @param bitRate desired bit rate
     */
    public void setVideoBitRate(int bitRate) {
        mCameraEngine.setVideoBitRate(bitRate);
    }

    /**
     * Returns the current video bit rate.
     * @return current bit rate
     */
    @SuppressWarnings("unused")
    public int getVideoBitRate() {
        return mCameraEngine.getVideoBitRate();
    }

    /**
     * A flag to control the behavior when calling {@link #setPreviewFrameRate(float)}.
     *
     * If the value is set to true, {@link #setPreviewFrameRate(float)} will choose the preview
     * frame range as close to the desired new frame rate as possible. Which mean it may choose a
     * narrow range around the desired frame rate. Note: This option will give you as exact fps as
     * you want but the sensor will have less freedom when adapting the exposure to the environment,
     * which may lead to dark preview.
     *
     * If the value is set to false, {@link #setPreviewFrameRate(float)} will choose as broad range
     * as it can.
     *
     * @param videoFrameRateExact whether want a more exact preview frame range
     *
     * @see #setPreviewFrameRate(float)
     */
    public void setPreviewFrameRateExact(boolean videoFrameRateExact) {
        mCameraEngine.setPreviewFrameRateExact(videoFrameRateExact);
    }

    /**
     * Returns whether we want to set preview fps as exact as we set through
     * {@link #setPreviewFrameRate(float)}.
     *
     * @see #setPreviewFrameRateExact(boolean)
     * @see #setPreviewFrameRate(float)
     * @return current option
     */
    public boolean getPreviewFrameRateExact() {
        return mCameraEngine.getPreviewFrameRateExact();
    }

    /**
     * Sets the preview frame rate in frames per second.
     * This rate will be used, for example, by the frame processor and in video
     * snapshot taken through {@link #takeVideo(File)}.
     *
     * A value of 0F will restore the rate to a default value.
     *
     * @param frameRate desired frame rate
     */
    public void setPreviewFrameRate(float frameRate) {
        mCameraEngine.setPreviewFrameRate(frameRate);
    }

    /**
     * Returns the current preview frame rate.
     * This can return 0F if no frame rate was set.
     *
     * @see #setPreviewFrameRate(float)
     * @return current frame rate
     */
    public float getPreviewFrameRate() {
        return mCameraEngine.getPreviewFrameRate();
    }

    /**
     * Sets the bit rate in bits per second for audio capturing.
     * Will be used by both {@link #takeVideo(File)} and {@link #takeVideoSnapshot(File)}.
     *
     * @param bitRate desired bit rate
     */
    public void setAudioBitRate(int bitRate) {
        mCameraEngine.setAudioBitRate(bitRate);
    }

    /**
     * Returns the current audio bit rate.
     * @return current bit rate
     */
    @SuppressWarnings("unused")
    public int getAudioBitRate() {
        return mCameraEngine.getAudioBitRate();
    }

    /**
     * Sets the encoder for audio recordings.
     * Defaults to {@link AudioCodec#DEVICE_DEFAULT}.
     *
     * @see AudioCodec#DEVICE_DEFAULT
     * @see AudioCodec#AAC
     * @see AudioCodec#HE_AAC
     * @see AudioCodec#AAC_ELD
     *
     * @param codec requested audio codec
     */
    public void setAudioCodec(@NonNull AudioCodec codec) {
        mCameraEngine.setAudioCodec(codec);
    }

    /**
     * Gets the current encoder for audio recordings.
     * @return the current audio codec
     */
    @NonNull
    public AudioCodec getAudioCodec() {
        return mCameraEngine.getAudioCodec();
    }

    /**
     * Adds a {@link CameraListener} instance to be notified of all
     * interesting events that happen during the camera lifecycle.
     *
     * @param cameraListener a listener for events.
     */
    public void addCameraListener(@NonNull CameraListener cameraListener) {
        mListeners.add(cameraListener);
    }

    /**
     * Remove a {@link CameraListener} that was previously registered.
     *
     * @param cameraListener a listener for events.
     */
    public void removeCameraListener(@NonNull CameraListener cameraListener) {
        mListeners.remove(cameraListener);
    }

    /**
     * Clears the list of {@link CameraListener} that are registered
     * to camera events.
     */
    public void clearCameraListeners() {
        mListeners.clear();
    }

    /**
     * Asks the camera to capture an image of the current scene.
     * This will trigger {@link CameraListener#onPictureTaken(PictureResult)} if a listener
     * was registered.
     *
     * @see #takePictureSnapshot()
     */
    public void takePicture() {
        PictureResult.Stub stub = new PictureResult.Stub();
        mCameraEngine.takePicture(stub);
    }

    /**
     * Asks the camera to capture a snapshot of the current preview.
     * This eventually triggers {@link CameraListener#onPictureTaken(PictureResult)} if a listener
     * was registered.
     *
     * The difference with {@link #takePicture()} is that this capture is faster, so it might be
     * better on slower cameras, though the result can be generally blurry or low quality.
     *
     * @see #takePicture()
     */
    public void takePictureSnapshot() {
        PictureResult.Stub stub = new PictureResult.Stub();
        mCameraEngine.takePictureSnapshot(stub);
    }

    /**
     * Starts recording a video. Video will be written to the given file,
     * so callers should ensure they have appropriate permissions to write to the file.
     *
     * @param file a file where the video will be saved
     */
    public void takeVideo(@NonNull File file) {
        takeVideo(file, null);
    }

    /**
     * Starts recording a video. Video will be written to the given file,
     * so callers should ensure they have appropriate permissions to write to the file.
     *
     * @param fileDescriptor a file descriptor where the video will be saved
     */
    public void takeVideo(@NonNull FileDescriptor fileDescriptor) {
        takeVideo(null, fileDescriptor);
    }

    private void takeVideo(@Nullable File file, @Nullable FileDescriptor fileDescriptor) {
        VideoResult.Stub stub = new VideoResult.Stub();
        if (file != null) {
            mCameraEngine.takeVideo(stub, file, null);
        } else if (fileDescriptor != null) {
            mCameraEngine.takeVideo(stub, null, fileDescriptor);
        } else {
            throw new IllegalStateException("file and fileDescriptor are both null.");
        }
//        mUiHandler.post(new Runnable() {
//            @Override
//            public void run() {
//                mKeepScreenOn = getKeepScreenOn();
//                if (!mKeepScreenOn) setKeepScreenOn(true);
//            }
//        });
    }

    /**
     * Starts recording a fast, low quality video snapshot. Video will be written to the given file,
     * so callers should ensure they have appropriate permissions to write to the file.
     *
     * Throws an exception if API level is below 18, or if the preview being used is not
     * {@link Preview#GL_SURFACE}.
     *
     * @param file a file where the video will be saved
     */
    public void takeVideoSnapshot(@NonNull File file) {
        VideoResult.Stub stub = new VideoResult.Stub();
        mCameraEngine.takeVideoSnapshot(stub, file);
//        mUiHandler.post(new Runnable() {
//            @Override
//            public void run() {
//                mKeepScreenOn = getKeepScreenOn();
//                if (!mKeepScreenOn) setKeepScreenOn(true);
//            }
//        });
    }

    /**
     * Starts recording a video. Video will be written to the given file,
     * so callers should ensure they have appropriate permissions to write to the file.
     * Recording will be automatically stopped after the given duration, overriding
     * temporarily any duration limit set by {@link #setVideoMaxDuration(int)}.
     *
     * @param file a file where the video will be saved
     * @param durationMillis recording max duration
     */
    public void takeVideo(@NonNull File file, int durationMillis) {
        takeVideo(file, null, durationMillis);
    }

    /**
     * Starts recording a video. Video will be written to the given file,
     * so callers should ensure they have appropriate permissions to write to the file.
     * Recording will be automatically stopped after the given duration, overriding
     * temporarily any duration limit set by {@link #setVideoMaxDuration(int)}.
     *
     * @param fileDescriptor a file descriptor where the video will be saved
     * @param durationMillis recording max duration
     */
    @SuppressWarnings("unused")
    public void takeVideo(@NonNull FileDescriptor fileDescriptor, int durationMillis) {
        takeVideo(null, fileDescriptor, durationMillis);
    }

    private void takeVideo(@Nullable File file, @Nullable FileDescriptor fileDescriptor,
                           int durationMillis) {
        final int old = getVideoMaxDuration();
        addCameraListener(new CameraListener() {
            @Override
            public void onVideoTaken(@NonNull VideoResult result) {
                setVideoMaxDuration(old);
                removeCameraListener(this);
            }

            @Override
            public void onCameraError(@NonNull CameraException exception) {
                super.onCameraError(exception);
                if (exception.getReason() == CameraException.REASON_VIDEO_FAILED) {
                    setVideoMaxDuration(old);
                    removeCameraListener(this);
                }
            }
        });
        setVideoMaxDuration(durationMillis);
        takeVideo(file, fileDescriptor);
    }

    /**
     * Starts recording a fast, low quality video snapshot. Video will be written to the given file,
     * so callers should ensure they have appropriate permissions to write to the file.
     * Recording will be automatically stopped after the given duration, overriding
     * temporarily any duration limit set by {@link #setVideoMaxDuration(int)}.
     *
     * Throws an exception if API level is below 18, or if the preview being used is not
     * {@link Preview#GL_SURFACE}.
     *
     * @param file a file where the video will be saved
     * @param durationMillis recording max duration
     *
     */
    public void takeVideoSnapshot(@NonNull File file, int durationMillis) {
        final int old = getVideoMaxDuration();
        addCameraListener(new CameraListener() {
            @Override
            public void onVideoTaken(@NonNull VideoResult result) {
                setVideoMaxDuration(old);
                removeCameraListener(this);
            }

            @Override
            public void onCameraError(@NonNull CameraException exception) {
                super.onCameraError(exception);
                if (exception.getReason() == CameraException.REASON_VIDEO_FAILED) {
                    setVideoMaxDuration(old);
                    removeCameraListener(this);
                }
            }
        });
        setVideoMaxDuration(durationMillis);
        takeVideoSnapshot(file);
    }

    // TODO: pauseVideo and resumeVideo? There is mediarecorder.pause(), but API 24...

    /**
     * Stops capturing video or video snapshots being recorded, if there was any.
     * This will fire {@link CameraListener#onVideoTaken(VideoResult)}.
     */
    public void stopVideo() {
        mCameraEngine.stopVideo();
//        mUiHandler.post(new Runnable() {
//            @Override
//            public void run() {
//                if (getKeepScreenOn() != mKeepScreenOn) setKeepScreenOn(mKeepScreenOn);
//            }
//        });
    }

    /**
     * Sets the max width for snapshots taken with {@link #takePictureSnapshot()} or
     * {@link #takeVideoSnapshot(File)}. If the snapshot width exceeds this value, the snapshot
     * will be scaled down to match this constraint.
     *
     * @param maxWidth max width for snapshots
     */
    public void setSnapshotMaxWidth(int maxWidth) {
        mCameraEngine.setSnapshotMaxWidth(maxWidth);
    }

    /**
     * Sets the max height for snapshots taken with {@link #takePictureSnapshot()} or
     * {@link #takeVideoSnapshot(File)}. If the snapshot height exceeds this value, the snapshot
     * will be scaled down to match this constraint.
     *
     * @param maxHeight max height for snapshots
     */
    public void setSnapshotMaxHeight(int maxHeight) {
        mCameraEngine.setSnapshotMaxHeight(maxHeight);
    }

    /**
     * The max width for snapshots.
     * @see #setSnapshotMaxWidth(int)
     * @return max width
     */
    public int getSnapshotMaxWidth() {
        return mCameraEngine.getSnapshotMaxWidth();
    }

    /**
     * The max height for snapshots.
     * @see #setSnapshotMaxHeight(int)
     * @return max height
     */
    public int getSnapshotMaxHeight() {
        return mCameraEngine.getSnapshotMaxHeight();
    }

    /**
     * Returns the size used for snapshots, or null if it hasn't been computed
     * (for example if the surface is not ready). This is the preview size, rotated to match
     * the output orientation, and cropped to the visible part.
     *
     * This also includes the {@link #setSnapshotMaxWidth(int)} and
     * {@link #setSnapshotMaxHeight(int)} constraints.
     *
     * This does NOT include any constraints specific to video encoding, which are
     * device specific and depend on the capabilities of the device codec.
     *
     * @return the size of snapshots
     */
    @Nullable
    public Size getSnapshotSize() {
//        if (getWidth() == 0 || getHeight() == 0) return null;
//
//        // Get the preview size and crop according to the current view size.
//        // It's better to do calculations in the REF_VIEW reference, and then flip if needed.
//        Size preview = mCameraEngine.getUncroppedSnapshotSize(Reference.VIEW);
//        if (preview == null) return null; // Should never happen.
//        AspectRatio viewRatio = AspectRatio.of(getWidth(), getHeight());
//        Rect crop = CropHelper.computeCrop(preview, viewRatio);
//        Size cropSize = new Size(crop.width(), crop.height());
//        if (mCameraEngine.getAngles().flip(Reference.VIEW, Reference.OUTPUT)) {
//            return cropSize.flip();
//        } else {
//            return cropSize;
//        }
        return null;
    }

    /**
     * Returns the size used for pictures taken with {@link #takePicture()},
     * or null if it hasn't been computed (for example if the surface is not ready),
     * or null if we are in video mode.
     *
     * The size is rotated to match the output orientation.
     *
     * @return the size of pictures
     */
    @Nullable
    public Size getPictureSize() {
        return mCameraEngine.getPictureSize(Reference.OUTPUT);
    }

    /**
     * Returns the size used for videos taken with {@link #takeVideo(File)},
     * or null if it hasn't been computed (for example if the surface is not ready),
     * or null if we are in picture mode.
     *
     * The size is rotated to match the output orientation.
     *
     * @return the size of videos
     */
    @Nullable
    public Size getVideoSize() {
        return mCameraEngine.getVideoSize(Reference.OUTPUT);
    }

    // If we end up here, we're in M.
    @TargetApi(Build.VERSION_CODES.M)
    private void requestPermissions(boolean requestCamera, boolean requestAudio) {
        Activity activity = null;
        Context context = getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                activity = (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }

        List<String> permissions = new ArrayList<>();
        if (requestCamera) permissions.add(Manifest.permission.CAMERA);
        if (requestAudio) permissions.add(Manifest.permission.RECORD_AUDIO);
        if (activity != null) {
            activity.requestPermissions(permissions.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
        }
    }

    @SuppressLint("NewApi")
    private void playSound(int soundType) {
        if (mPlaySounds) {
            if (mSound == null) mSound = new MediaActionSound();
            mSound.play(soundType);
        }
    }

    /**
     * Controls whether CameraView should play sound effects on certain
     * events (picture taken, focus complete). Note that:
     * - On API level {@literal <} 16, this flag is always false
     * - Camera1 will always play the shutter sound when taking pictures
     *
     * @param playSounds whether to play sound effects
     */
    public void setPlaySounds(boolean playSounds) {
        mPlaySounds = playSounds && Build.VERSION.SDK_INT >= 16;
        mCameraEngine.setPlaySounds(playSounds);
    }

    /**
     * Gets the current sound effect behavior.
     *
     * @see #setPlaySounds(boolean)
     * @return whether sound effects are supported
     */
    public boolean getPlaySounds() {
        return mPlaySounds;
    }

    /**
     * Controls whether picture and video output should consider the current device orientation.
     * For example, when true, if the user rotates the device before taking a picture, the picture
     * will be rotated as well.
     *
     * @param useDeviceOrientation true to consider device orientation for outputs
     */
    public void setUseDeviceOrientation(boolean useDeviceOrientation) {
        mUseDeviceOrientation = useDeviceOrientation;
    }

    /**
     * Gets the current behavior for considering the device orientation when returning picture
     * or video outputs.
     *
     * @see #setUseDeviceOrientation(boolean)
     * @return whether we are using the device orientation for outputs
     */
    public boolean getUseDeviceOrientation() {
        return mUseDeviceOrientation;
    }

    /**
     * Sets the encoder for video recordings.
     * Defaults to {@link VideoCodec#DEVICE_DEFAULT}.
     *
     * @see VideoCodec#DEVICE_DEFAULT
     * @see VideoCodec#H_263
     * @see VideoCodec#H_264
     *
     * @param codec requested video codec
     */
    public void setVideoCodec(@NonNull VideoCodec codec) {
        mCameraEngine.setVideoCodec(codec);
    }

    /**
     * Gets the current encoder for video recordings.
     * @return the current video codec
     */
    @NonNull
    public VideoCodec getVideoCodec() {
        return mCameraEngine.getVideoCodec();
    }

    /**
     * Sets the maximum size in bytes for recorded video files.
     * Once this size is reached, the recording will automatically stop.
     * Defaults to unlimited size. Use 0 or negatives to disable.
     *
     * @param videoMaxSizeInBytes The maximum video size in bytes
     */
    public void setVideoMaxSize(long videoMaxSizeInBytes) {
        mCameraEngine.setVideoMaxSize(videoMaxSizeInBytes);
    }

    /**
     * Returns the maximum size in bytes for recorded video files, or 0
     * if no size was set.
     *
     * @see #setVideoMaxSize(long)
     * @return the maximum size in bytes
     */
    public long getVideoMaxSize() {
        return mCameraEngine.getVideoMaxSize();
    }

    /**
     * Sets the maximum duration in milliseconds for video recordings.
     * Once this duration is reached, the recording will automatically stop.
     * Defaults to unlimited duration. Use 0 or negatives to disable.
     *
     * @param videoMaxDurationMillis The maximum video duration in milliseconds
     */
    public void setVideoMaxDuration(int videoMaxDurationMillis) {
        mCameraEngine.setVideoMaxDuration(videoMaxDurationMillis);
    }

    /**
     * Returns the maximum duration in milliseconds for video recordings, or 0
     * if no limit was set.
     *
     * @see #setVideoMaxDuration(int)
     * @return the maximum duration in milliseconds
     */
    public int getVideoMaxDuration() {
        return mCameraEngine.getVideoMaxDuration();
    }

    /**
     * Returns true if the camera is currently recording a video
     * @return boolean indicating if the camera is recording a video
     */
    public boolean isTakingVideo() {
        return mCameraEngine.isTakingVideo();
    }

    /**
     * Returns true if the camera is currently capturing a picture
     * @return boolean indicating if the camera is capturing a picture
     */
    public boolean isTakingPicture() {
        return mCameraEngine.isTakingPicture();
    }

    //endregion

    //region Callbacks and dispatching

    @VisibleForTesting
    class CameraCallbacks implements
            CameraEngine.Callback,
            OrientationHelper.Callback,
            GestureFinder.Controller {

        private final String TAG = CameraCallbacks.class.getSimpleName();
        private final CameraLogger LOG = CameraLogger.create(TAG);

        @NonNull
        @Override
        public Context getContext() {
            return CameraView.this.getContext();
        }

        @Override
        public int getWidth() {
            return cameraViewParent.getWidth();
        }

        @Override
        public int getHeight() {
            return cameraViewParent.getHeight();
        }

        @Override
        public void dispatchOnCameraOpened(@NonNull final CameraOptions options) {
            LOG.i("dispatchOnCameraOpened", options);
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    for (CameraListener listener : mListeners) {
                        listener.onCameraOpened(options);
                    }
                }
            });
        }

        @Override
        public void dispatchOnCameraBinded() {
            LOG.i("dispatchOnCameraBinded");
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    for (CameraListener listener : mListeners) {
                        listener.onCameraBinded();
                    }
                }
            });
        }

        @Override
        public void dispatchHidePreview() {
            LOG.i("dispatchOnVideoRecordingEnd");
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    for (CameraListener listener : mListeners) {
                        listener.onHidePreview();
                    }
                }
            });
        }

        @Override
        public void dispatchStopVideoRecording() {
            LOG.i("dispatchStopVideoRecording");
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    for (CameraListener listener : mListeners) {
                        listener.stopVideoRecording();
                    }
                }
            });
        }

        @Override
        public void dispatchOnCameraClosed() {
            LOG.i("dispatchOnCameraClosed");
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    for (CameraListener listener : mListeners) {
                        listener.onCameraClosed();
                    }
                }
            });
        }

        @Override
        public void onCameraPreviewStreamSizeChanged() {
            // Camera preview size has changed.
            // Request a layout pass for onMeasure() to do its stuff.
            // Potentially this will change CameraView size, which changes Surface size,
            // which triggers a new Preview size. But hopefully it will converge.
            Size previewSize = mCameraEngine.getPreviewStreamSize(Reference.VIEW);
            if (previewSize == null) {
                throw new RuntimeException("Preview stream size should not be null here.");
            } else if (previewSize.equals(mLastPreviewStreamSize)) {
                LOG.i("onCameraPreviewStreamSizeChanged:",
                        "swallowing because the preview size has not changed.", previewSize);
            } else {
                LOG.i("onCameraPreviewStreamSizeChanged: posting a requestLayout call.",
                        "Preview stream size:", previewSize);
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if(cameraViewParent != null)
                            cameraViewParent.requestLayout();
                    }
                });
            }
        }

        @Override
        public void onShutter(boolean shouldPlaySound) {
            if (shouldPlaySound && mPlaySounds) {
                playSound(MediaActionSound.SHUTTER_CLICK);
            }
        }

        @Override
        public void dispatchOnPictureTaken(@NonNull final PictureResult.Stub stub) {
            LOG.i("dispatchOnPictureTaken", stub);
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    PictureResult result = new PictureResult(stub);
                    for (CameraListener listener : mListeners) {
                        listener.onPictureTaken(result);
                    }
                }
            });
        }

        @Override
        public void dispatchOnVideoTaken(@NonNull final VideoResult.Stub stub) {
            LOG.i("dispatchOnVideoTaken", stub);
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    VideoResult result = new VideoResult(stub);
                    for (CameraListener listener : mListeners) {
                        listener.onVideoTaken(result);
                    }
                }
            });
        }

        @Override
        public void dispatchOnFocusStart(@Nullable final Gesture gesture,
                                         @NonNull final PointF point) {
            LOG.i("dispatchOnFocusStart", gesture, point);
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
//                    mMarkerLayout.onEvent(MarkerLayout.TYPE_AUTOFOCUS, new PointF[]{ point });
//                    if (mAutoFocusMarker != null) {
//                        AutoFocusTrigger trigger = gesture != null ?
//                                AutoFocusTrigger.GESTURE : AutoFocusTrigger.METHOD;
//                        mAutoFocusMarker.onAutoFocusStart(trigger, point);
//                    }

                    for (CameraListener listener : mListeners) {
                        listener.onAutoFocusStart(point);
                    }
                }
            });
        }

        @Override
        public void dispatchOnFocusEnd(@Nullable final Gesture gesture,
                                       final boolean success,
                                       @NonNull final PointF point) {
            LOG.i("dispatchOnFocusEnd", gesture, success, point);
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (success && mPlaySounds) {
                        playSound(MediaActionSound.FOCUS_COMPLETE);
                    }
//
//                    if (mAutoFocusMarker != null) {
//                        AutoFocusTrigger trigger = gesture != null ?
//                                AutoFocusTrigger.GESTURE : AutoFocusTrigger.METHOD;
//                        mAutoFocusMarker.onAutoFocusEnd(trigger, success, point);
//                    }

                    for (CameraListener listener : mListeners) {
                        listener.onAutoFocusEnd(success, point);
                    }
                }
            });
        }

        @Override
        public void onDeviceOrientationChanged(int deviceOrientation) {
            LOG.i("onDeviceOrientationChanged", deviceOrientation);
            int displayOffset = mOrientationHelper.getLastDisplayOffset();
            if (!mUseDeviceOrientation) {
                // To fool the engine to return outputs in the VIEW reference system,
                // The device orientation should be set to -displayOffset.
                int fakeDeviceOrientation = (360 - displayOffset) % 360;
                mCameraEngine.getAngles().setDeviceOrientation(fakeDeviceOrientation);
            } else {
                mCameraEngine.getAngles().setDeviceOrientation(deviceOrientation);
            }
            final int value = (deviceOrientation + displayOffset) % 360;
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    for (CameraListener listener : mListeners) {
                        listener.onOrientationChanged(value);
                    }
                }
            });
        }

        @Override
        public void onDisplayOffsetChanged(int displayOffset, boolean willRecreate) {
            LOG.i("onDisplayOffsetChanged", displayOffset, "recreate:", willRecreate);
            if (isOpened() && !willRecreate) {
                // Display offset changes when the device rotation lock is off and the activity
                // is free to rotate. However, some changes will NOT recreate the activity, namely
                // 180 degrees flips. In this case, we must restart the camera manually.
                LOG.w("onDisplayOffsetChanged", "restarting the camera.");
                close();
                open();
            }
        }

        @Override
        public void dispatchOnZoomChanged(final float newValue, @Nullable final PointF[] fingers) {
            LOG.i("dispatchOnZoomChanged", newValue);
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    for (CameraListener listener : mListeners) {
                        listener.onZoomChanged(newValue, new float[]{0, 1}, fingers);
                    }
                }
            });
        }

        @Override
        public void dispatchOnExposureCorrectionChanged(final float newValue,
                                                        @NonNull final float[] bounds,
                                                        @Nullable final PointF[] fingers) {
            LOG.i("dispatchOnExposureCorrectionChanged", newValue);
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    for (CameraListener listener : mListeners) {
                        listener.onExposureCorrectionChanged(newValue, bounds, fingers);
                    }
                }
            });
        }

        @Override
        public void dispatchFrame(@NonNull final Frame frame) {
            // The getTime() below might crash if developers incorrectly release
            // frames asynchronously.
            LOG.v("dispatchFrame:", frame.getTime(), "processors:", mFrameProcessors.size());
            if (mFrameProcessors.isEmpty()) {
                // Mark as released. This instance will be reused.
                frame.release();
            } else {
                // Dispatch this frame to frame processors.
                mFrameProcessingExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        LOG.v("dispatchFrame: executing. Passing", frame.getTime(),
                                "to processors.");
                        for (FrameProcessor processor : mFrameProcessors) {
                            try {
                                processor.process(frame);
                            } catch (Exception e) {
                                LOG.w("Frame processor crashed:", e);
                            }
                        }
                        frame.release();
                    }
                });
            }
        }

        @Override
        public void dispatchError(final CameraException exception) {
            LOG.i("dispatchError", exception);
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    for (CameraListener listener : mListeners) {
                        listener.onCameraError(exception);
                    }
                }
            });
        }

        @Override
        public void dispatchOnVideoRecordingStart() {
            LOG.i("dispatchOnVideoRecordingStart");
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    for (CameraListener listener : mListeners) {
                        listener.onVideoRecordingStart();
                    }
                }
            });
        }

        @Override
        public void dispatchOnVideoRecordingEnd() {
            LOG.i("dispatchOnVideoRecordingEnd");
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    for (CameraListener listener : mListeners) {
                        listener.onVideoRecordingEnd();
                    }
                }
            });
        }
    }

    //endregion

    //region Frame Processing

    /**
     * Adds a {@link FrameProcessor} instance to be notified of
     * new frames in the preview stream.
     *
     * @param processor a frame processor.
     */
    public void addFrameProcessor(@Nullable FrameProcessor processor) {
        if (processor != null) {
            mFrameProcessors.add(processor);
            if (mFrameProcessors.size() == 1) {
                mCameraEngine.setHasFrameProcessors(true);
            }
        }
    }

    /**
     * Remove a {@link FrameProcessor} that was previously registered.
     *
     * @param processor a frame processor
     */
    public void removeFrameProcessor(@Nullable FrameProcessor processor) {
        if (processor != null) {
            mFrameProcessors.remove(processor);
            if (mFrameProcessors.size() == 0) {
                mCameraEngine.setHasFrameProcessors(false);
            }
        }
    }

    /**
     * Clears the list of {@link FrameProcessor} that have been registered
     * to preview frames.
     */
    public void clearFrameProcessors() {
        boolean had = mFrameProcessors.size() > 0;
        mFrameProcessors.clear();
        if (had) {
            mCameraEngine.setHasFrameProcessors(false);
        }
    }

    /**
     * Sets the max width for frame processing {@link Frame}s.
     * This option is only supported by {@link Engine#CAMERA2} and will have no effect
     * on other engines.
     *
     * @param maxWidth max width for frames
     */
    public void setFrameProcessingMaxWidth(int maxWidth) {
        mCameraEngine.setFrameProcessingMaxWidth(maxWidth);
    }

    /**
     * Sets the max height for frame processing {@link Frame}s.
     * This option is only supported by {@link Engine#CAMERA2} and will have no effect
     * on other engines.
     *
     * @param maxHeight max height for frames
     */
    public void setFrameProcessingMaxHeight(int maxHeight) {
        mCameraEngine.setFrameProcessingMaxHeight(maxHeight);
    }

    /**
     * The max width for frame processing frames.
     * @see #setFrameProcessingMaxWidth(int)
     * @return max width
     */
    public int getFrameProcessingMaxWidth() {
        return mCameraEngine.getFrameProcessingMaxWidth();
    }

    /**
     * The max height for frame processing frames.
     * @see #setFrameProcessingMaxHeight(int)
     * @return max height
     */
    public int getFrameProcessingMaxHeight() {
        return mCameraEngine.getFrameProcessingMaxHeight();
    }

    /**
     * Sets the {@link android.graphics.ImageFormat} for frame processing.
     * Before applying you should check {@link CameraOptions#getSupportedFrameProcessingFormats()}.
     *
     * @param format image format
     */
    public void setFrameProcessingFormat(int format) {
        mCameraEngine.setFrameProcessingFormat(format);
    }

    /**
     * Returns the current frame processing format.
     * @see #setFrameProcessingFormat(int)
     * @return image format
     */
    public int getFrameProcessingFormat() {
        return mCameraEngine.getFrameProcessingFormat();
    }

    /**
     * Sets the frame processing pool size. This is (roughly) the max number of
     * {@link Frame} instances that can exist at a given moment in the frame pipeline,
     * excluding frozen frames.
     *
     * Defaults to 2 - higher values will increase the memory usage with little benefit.
     * Can be higher than 2 if {@link #setFrameProcessingExecutors(int)} is used.
     * These values should be tuned together. We recommend setting a pool size that's equal to
     * the number of executors plus 1, so that there's always a free Frame for the camera engine.
     *
     * Changing this value after camera initialization will have no effect.
     * @param poolSize pool size
     */
    public void setFrameProcessingPoolSize(int poolSize) {
        mCameraEngine.setFrameProcessingPoolSize(poolSize);
    }

    /**
     * Returns the current frame processing pool size.
     * @see #setFrameProcessingPoolSize(int)
     * @return pool size
     */
    public int getFrameProcessingPoolSize() {
        return mCameraEngine.getFrameProcessingPoolSize();
    }

    /**
     * Sets the thread pool size for frame processing. This means that if the processing rate
     * is slower than the preview rate, you can set this value to something bigger than 1
     * to avoid losing frames.
     * Defaults to 1 and this should be OK for most applications.
     *
     * Should be tuned depending on the task, the processor implementation, and along with
     * {@link #setFrameProcessingPoolSize(int)}. We recommend choosing a pool size that is
     * equal to the executors plus 1.
     * @param executors thread count
     */
    public void setFrameProcessingExecutors(int executors) {
        if (executors < 1) {
            throw new IllegalArgumentException("Need at least 1 executor, got " + executors);
        }
        mFrameProcessingExecutors = executors;
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                executors,
                executors,
                4,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(),
                new ThreadFactory() {
                    private final AtomicInteger mCount = new AtomicInteger(1);
                    @Override
                    public Thread newThread(@NonNull Runnable r) {
                        return new Thread(r, "FrameExecutor #" + mCount.getAndIncrement());
                    }
                }
        );
        executor.allowCoreThreadTimeOut(true);
        mFrameProcessingExecutor = executor;
    }

    /**
     * Returns the current executors count.
     * @see #setFrameProcessingExecutors(int)
     * @return thread count
     */
    public int getFrameProcessingExecutors() {
        return mFrameProcessingExecutors;
    }

    //endregion
}
