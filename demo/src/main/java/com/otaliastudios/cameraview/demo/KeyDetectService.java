package com.otaliastudios.cameraview.demo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.otaliastudios.cameraview.CameraException;
import com.otaliastudios.cameraview.CameraListener;
import com.otaliastudios.cameraview.CameraLogger;
import com.otaliastudios.cameraview.CameraOptions;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.CameraViewParent;
import com.otaliastudios.cameraview.PictureResult;
import com.otaliastudios.cameraview.VideoResult;
import com.otaliastudios.cameraview.controls.Mode;
import com.otaliastudios.cameraview.frame.Frame;
import com.otaliastudios.cameraview.frame.FrameProcessor;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class KeyDetectService extends Service implements SurfaceHolder.Callback {
    private final static CameraLogger LOG = CameraLogger.create("KeyDetectService");
    public static final String KEY_POWER = "KEY_POWER";
    public static final String KEY_VOLUME = "KEY_VOLUME";
    public static final String KEY_COMMAND = "KEY_COMMAND";
    public static final String KEY_ONE_TOUCH_RECORDING = "KEY_ONE_TOUCH_RECORDING";
    public static final String CAMERA_ERROR_ACTION = "CAMERA_ERROR_ACTION";
    public static final String CAMERA_OPEN_ACTION = "CAMERA_OPEN_ACTION";
    public static final String RECORDING_ACTION = "RECORDING_ACTION";
    public int volumeUpButtonMaxPress = 3;
    public int volumeDownButtonMaxPress = 3;
    public int powerButtonMaxPress = 3;
    public static final int MIN_BATTERY_LEVEL = 5;
    public static final int MIN_MEMORY_REQUIRED_IN_MB = 50;
    public int maxRecordingTime = 30;

    //private MediaSessionCompat mediaSession;
    //private BroadcastReceiver powerButtonReceiver;
    private int volumeButtonStartCounter = 0;
    private int volumeButtonStopCounter = 0;
    private int powerButtonStartCounter = 0;
    private int powerButtonStopCounter = 0;
    private int powerButtonDirection = 1;
    private final IBinder mBinder = new MyBinder();
    //private SmsListener smsListener;

    //Recoding service
    private String mFileName = null;
    private String mFilePath = null;

    //private DBHelper mDatabase;

    private long mStartingTimeMillis = System.currentTimeMillis();
    private static final SimpleDateFormat mTimerFormat = new SimpleDateFormat("mm:ss", Locale.getDefault());
    private SurfaceHolder surfaceHolder;
    private boolean isRecordingRunning = false;
    CameraView camera;
    private int batteryLevel = 100;
    private boolean startCameraForRecording = false;
    //private LocalBroadcastManager localBroadcastManager;
    private SharedPreferences preferences;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private int scheduledTimeInMinutes;
    private boolean scheduledRepeatRecording;
    private String scheduledCameraFace;
    private boolean isScheduleRecordingRunning = false;
    private int scheduleId;
    public static final int NOTIFICATION_ID = 4;
    private final static boolean USE_FRAME_PROCESSOR = false;
    private final static boolean DECODE_BITMAP = true;
    private CameraViewParent cameraViewParent;

    public void initCamera() {
//        if (camera != null) {
//            camera.clearCameraListeners();
//        }
//
//        camera = new CameraView(getApplicationContext());
//        camera.setSessionType(SessionType.VIDEO);
//        camera.mapGesture(Gesture.PINCH, GestureAction.NONE); // Pinch to zoom!
//        camera.mapGesture(Gesture.TAP, GestureAction.NONE); // Tap to focus!
//        camera.mapGesture(Gesture.LONG_TAP, GestureAction.NONE); // Long tap to shoot!
//        //cameraFace.start();
//        camera.addCameraListener(listener);

        camera = new CameraView(getApplicationContext());
        //camera.setLifecycleOwner(this);
        camera.addCameraListener(new Listener());

        if (USE_FRAME_PROCESSOR) {
            camera.addFrameProcessor(new FrameProcessor() {
                private long lastTime = System.currentTimeMillis();

                @Override
                public void process(@NonNull Frame frame) {
                    long newTime = frame.getTime();
                    long delay = newTime - lastTime;
                    lastTime = newTime;
                    LOG.e("Frame delayMillis:", delay, "FPS:", 1000 / delay);
                    if (DECODE_BITMAP) {
                        YuvImage yuvImage = new YuvImage(frame.getData(), ImageFormat.NV21,
                                frame.getSize().getWidth(),
                                frame.getSize().getHeight(),
                                null);
                        ByteArrayOutputStream jpegStream = new ByteArrayOutputStream();
                        yuvImage.compressToJpeg(new Rect(0, 0,
                                frame.getSize().getWidth(),
                                frame.getSize().getHeight()), 100, jpegStream);
                        byte[] jpegByteArray = jpegStream.toByteArray();
                        Bitmap bitmap = BitmapFactory.decodeByteArray(jpegByteArray, 0, jpegByteArray.length);
                        //noinspection ResultOfMethodCallIgnored
                        bitmap.toString();
                    }
                }
            });
        }
    }

    private class Listener extends CameraListener {

        @Override
        public void onCameraOpened(@NonNull CameraOptions options) {
//            ViewGroup group = (ViewGroup) controlPanel.getChildAt(0);
//            for (int i = 0; i < group.getChildCount(); i++) {
//                OptionView view = (OptionView) group.getChildAt(i);
//                view.onCameraOpened(camera, options);
//            }
            if (startCameraForRecording) {
                startCameraForRecording = false;
                //startRecording();
            }
        }

        @Override
        public void onCameraError(@NonNull CameraException exception) {
            super.onCameraError(exception);
            //message("Got CameraException #" + exception.getReason(), true);
        }

        @Override
        public void onPictureTaken(@NonNull PictureResult result) {
            super.onPictureTaken(result);
            if (camera.isTakingVideo()) {
                //message("Captured while taking video. Size=" + result.getSize(), false);
                return;
            }

            // This can happen if picture was taken with a gesture.
//            long callbackTime = System.currentTimeMillis();
//            if (mCaptureTime == 0) mCaptureTime = callbackTime - 300;
//            LOG.w("onPictureTaken called! Launching activity. Delay:", callbackTime - mCaptureTime);
//            PicturePreviewActivity.setPictureResult(result);
//            Intent intent = new Intent(CameraActivity.this, PicturePreviewActivity.class);
//            intent.putExtra("delay", callbackTime - mCaptureTime);
//            startActivity(intent);
//            mCaptureTime = 0;
//            LOG.w("onPictureTaken called! Launched activity.");
        }

        @Override
        public void onVideoTaken(@NonNull VideoResult result) {
            super.onVideoTaken(result);
            LOG.w("onVideoTaken called! Launching activity.");
            VideoPreviewActivity.setVideoResult(result);
            Intent intent = new Intent(getApplicationContext(), VideoPreviewActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
             startActivity(intent);
            LOG.w("onVideoTaken called! Launched activity.");
        }

        @Override
        public void onVideoRecordingStart() {
            super.onVideoRecordingStart();
            LOG.w("onVideoRecordingStart!");
        }

        @Override
        public void onVideoRecordingEnd() {
            super.onVideoRecordingEnd();
            //message("Video taken. Processing...", false);
            LOG.w("onVideoRecordingEnd!");
        }

        @Override
        public void onExposureCorrectionChanged(float newValue, @NonNull float[] bounds, @Nullable PointF[] fingers) {
            super.onExposureCorrectionChanged(newValue, bounds, fingers);
            //message("Exposure correction:" + newValue, false);
        }

        @Override
        public void onZoomChanged(float newValue, @NonNull float[] bounds, @Nullable PointF[] fingers) {
            super.onZoomChanged(newValue, bounds, fingers);
            //message("Zoom:" + newValue, false);
        }
    }

//    private CameraListener listener = new CameraListener() {
//        @Override
//        public void onCameraError(@NonNull CameraException exception) {
//            stopRecordingForError();
//            destroyCameraView();
//        }
//
//        @Override
//        public void onCameraOpened(CameraOptions options) {
//            super.onCameraOpened(options);
//            if (startCameraForRecording) {
//                startCameraForRecording = false;
//                startRecording();
//            }
//        }
//    };

    public void destroyCameraView() {
        if (camera != null) {
            //camera.stop();
            camera.destroy();
            camera.clearCameraListeners();
            camera = null;
            if (cameraViewParent != null) cameraViewParent.destroy();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        //mDatabase = new DBHelper(getApplicationContext());
        initCamera();
        initCameraSurface();
    }

    private void initCameraSurface() {
        WindowManager windowManager = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
        cameraViewParent = new CameraViewParent(getApplicationContext(),camera);
        //SurfaceView surfaceView = new SurfaceView(this);
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.height = 200;
        layoutParams.width = 200;
        //layoutParams.type = WindowManager.LayoutParams.TYPE_TOAST;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
//        //layoutParams.format = PixelFormat.TRANSLUCENT;

//        WindowManager.LayoutParams params = new WindowManager.LayoutParams(1, 1,
//                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
//                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
//                PixelFormat.RGB_565);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.N || Build.VERSION.SDK_INT == Build.VERSION_CODES.N_MR1) {
            layoutParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        } else {
            layoutParams.type = WindowManager.LayoutParams.TYPE_TOAST;
        }

        layoutParams.gravity = Gravity.START | Gravity.TOP;
        windowManager.addView(cameraViewParent, layoutParams);
        //surfaceView.getHolder().addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        this.surfaceHolder = surfaceHolder;
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public class MyBinder extends Binder {
        public KeyDetectService getService() {
            return KeyDetectService.this;
        }
    }

//    public void registerSMSReceiver() {
//        unregisterSMSReceiver();
//        smsListener = new SmsListener();
//        registerReceiver(this.smsListener, new IntentFilter("android.provider.Telephony.SMS_RECEIVED"));
//    }
//
//    public void unregisterSMSReceiver() {
//        if (smsListener != null) {
//            unregisterReceiver(smsListener);
//            smsListener = null;
//        }
//    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        showNotification();
        return super.onStartCommand(intent, flags, startId);
    }

    public Notification createNotification(Context context, int setSmallIcon, int setLargeIcon, String setContentTitle, String setContentText, String setSubText) {
        String channelId = "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channelId = createNotificationChannel(context);
        }

        final Intent intent = new Intent(context, CameraActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        //boolean preventAppFromClosing = Util.getPrefs(context).getBoolean(KEY_CLICK_STOP_RECORDING, false);
        //intent.putExtra(MainActivity.CAN_CLOSE_APP, !preventAppFromClosing);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId);
        //builder.setLargeIcon(BitmapFactory.decodeResource(context.getResources(), setLargeIcon));
        builder.setSmallIcon(setSmallIcon);
        builder.setContentTitle(setContentTitle);
        builder.setContentText(setContentText);
        builder.setSubText(setSubText);
        builder.setContentIntent(PendingIntent.getActivities(context, 0,
                new Intent[]{intent}, PendingIntent.FLAG_UPDATE_CURRENT));
        return builder.build();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public String createNotificationChannel(Context context) {
        String id = "SECRET_VOICE_RECORDER_CHANNEL_ID";
        CharSequence name = "Secret voice recorder channel"; //user visible
        String description = "Secret voice recorder"; //user visible
        NotificationChannel mChannel = new NotificationChannel(id, name, NotificationManager.IMPORTANCE_LOW);
        mChannel.setDescription(description);
        mChannel.enableLights(true);
        mChannel.setLightColor(Color.RED);
        mChannel.enableVibration(false);
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        notificationManager.createNotificationChannel(mChannel);
        return id;
    }

    private void showNotification() {
        Notification notification = null;

            notification = createNotification(
                    getApplicationContext(), R.drawable.ic_edit,
                    R.drawable.ic_edit,
                    getApplicationContext().getString(R.string.app_name),
                    "Demo camera app",
                    "");


        //String notificationType = Util.getPrefs(getApplicationContext()).getString(SettingsFragment.KEY_CHANGE_NOTIFICATION_STYLE, "1");
        // Notification notification = NotificationUtils.createNotification(getApplicationContext(), notificationType);
        startForeground(NOTIFICATION_ID, notification);
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // Will display the notification in the notification bar
        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    @Override
    public void onDestroy() {
        if (camera != null) {
            destroyCameraView();
        }

        super.onDestroy();
    }

    public void startPreview(){
        camera.getCameraEngine().startPreview();
    }

    public void stopPreview(){
        camera.getCameraEngine().stopPreview(true);
    }

    public void closeCamera(){
        camera.close();
    }

    public CameraView startCameraForRecording() {
        return startCameraForRecording(false);
    }

    public CameraView startCameraForRecording(boolean isScheduleRecording) {

        if (isRecordingRunning) {
            return camera;
        }

        if (camera == null) {
            initCamera();
        }


        if (camera.isOpened()) {
            //startRecording();
        } else {
            startCameraForRecording = true;

            camera.open();
        }

        return camera;
    }

    public void startRecording() {
        isRecordingRunning = true;

        //setFileNameAndPath();

        //try {
            camera.setMode(Mode.VIDEO);

//            if (!Util.isUriString(mFilePath)) {
//                camera.controller().initMediaRecorderOnThread(new File(mFilePath), surfaceHolder);
//                camera.startCapturingVideo(new File(mFilePath));
//            }else{
//                camera.controller().initMediaRecorderOnThread(null, surfaceHolder);
//                camera.startCapturingVideo(null);
//            }
        camera.takeVideo(new File(getFilesDir(), "video.mp4"), 5000);

//        } catch (SVRException e) {
//            listener.onCameraError(e);
//        }
    }

    public void stopRecordingForError() {
        isRecordingRunning = false;
    }

    public void stopRecording() {
        stopRecording(true);
    }

    public void stopRecording(boolean stopCamera) {
        isRecordingRunning = false;


        if (camera != null) {
            camera.stopVideo();

            if (stopCamera) {
                isScheduleRecordingRunning = false;
                camera.close();
            }
        }
    }

//    public void setFileNameAndPath() {
//        //String fileFormat = preferences.getString(SettingsFragment.KEY_FILE_NAME_FORMAT, "1");
//        mFileName = "MyVideo.mp4";//Util.getFormatedFileName(getApplicationContext(), fileFormat);
//
//        String selectDir = Util.getPrefs(getApplicationContext()).getString(SettingsFragment.KEY_STORAGE_DIR, Util.getCurrentStorageDir(getApplicationContext()));
//        if (Util.isUriString(selectDir)) {
//            DocumentFile doc = DocumentFile.fromTreeUri(getApplicationContext(), Uri.parse(selectDir));
//            DocumentFile file = doc.createFile("video/mp4", mFileName);
//            mFilePath = file.getUri().toString();
//            Util.getPrefs(getApplicationContext()).edit().putString(SettingsFragment.KEY_FILES_NEW_FILE_URI, mFilePath).apply();
//        } else {
//            mFilePath = selectDir;
//            mFilePath += "/" + mFileName;
//        }
//    }


    public boolean isRecordingRunning() {
        return isRecordingRunning;
    }

    public CameraView getCamera() {
        return camera;
    }

    public long getEllipseTime() {
        return (System.currentTimeMillis() - mStartingTimeMillis);
    }

    public long getEllipseTimeInMinute() {
        return TimeUnit.MILLISECONDS.toMinutes(getEllipseTime());
    }

//    private void capturePhoto() {
//        if (mCapturingPicture) return;
//        mCapturingPicture = true;
//        mCaptureTime = System.currentTimeMillis();
//        mCaptureNativeSize = cameraFace.getPictureSize();
//        //message("Capturing picture...", false);
//        cameraFace.capturePicture();
//    }
}