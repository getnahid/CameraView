package com.otaliastudios.cameraview.demo;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.otaliastudios.cameraview.CameraException;
import com.otaliastudios.cameraview.CameraListener;
import com.otaliastudios.cameraview.CameraOptions;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.gesture.Gesture;
import com.otaliastudios.cameraview.gesture.GestureAction;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class KeyDetectService extends Service implements SurfaceHolder.Callback {
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
    private LocalBroadcastManager localBroadcastManager;
    private SharedPreferences preferences;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private int scheduledTimeInMinutes;
    private boolean scheduledRepeatRecording;
    private String scheduledCameraFace;
    private boolean isScheduleRecordingRunning = false;
    private int scheduleId;
    public static final int NOTIFICATION_ID = 4;

    public void initCamera() {
        if (camera != null) {
            camera.clearCameraListeners();
        }

        camera = new CameraView(getApplicationContext());
        camera.setSessionType(SessionType.VIDEO);
        camera.mapGesture(Gesture.PINCH, GestureAction.NONE); // Pinch to zoom!
        camera.mapGesture(Gesture.TAP, GestureAction.NONE); // Tap to focus!
        camera.mapGesture(Gesture.LONG_TAP, GestureAction.NONE); // Long tap to shoot!
        //cameraFace.start();
        camera.addCameraListener(listener);
    }

    private CameraListener listener = new CameraListener() {
        @Override
        public void onCameraError(@NonNull CameraException exception) {
            stopRecordingForError();
            destroyCameraView();
        }

        @Override
        public void onCameraOpened(CameraOptions options) {
            super.onCameraOpened(options);
            if (startCameraForRecording) {
                startCameraForRecording = false;
                startRecording();
            }
        }
    };

    public void destroyCameraView() {
        if (camera != null) {
            camera.stop();
            camera.destroy();
            camera.clearCameraListeners();
            camera = null;
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
        SurfaceView surfaceView = new SurfaceView(this);
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.height = 1;
        layoutParams.width = 1;
        //layoutParams.type = WindowManager.LayoutParams.TYPE_TOAST;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        layoutParams.format = PixelFormat.TRANSLUCENT;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.N || Build.VERSION.SDK_INT == Build.VERSION_CODES.N_MR1) {
            layoutParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        } else {
            layoutParams.type = WindowManager.LayoutParams.TYPE_TOAST;
        }

        layoutParams.gravity = Gravity.START | Gravity.TOP;
        windowManager.addView(surfaceView, layoutParams);
        surfaceView.getHolder().addCallback(this);
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

    private void showNotification() {
        Notification notification = null;

            notification = NotificationUtils.createNotification(
                    getApplicationContext(), R.drawable.ic_videocam_black_24dp,
                    R.drawable.ic_videocam_black_24dp,
                    getApplicationContext().getString(R.string.app_name),
                    getApplicationContext().getString(R.string.service_sub_description),
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


        if (camera.isStarted()) {
            startRecording();
        } else {
            startCameraForRecording = true;

            camera.start();
        }

        return camera;
    }

    private void startRecording() {
        isRecordingRunning = true;

        setFileNameAndPath();

        try {
            camera.setSessionType(SessionType.VIDEO);

            if (!Util.isUriString(mFilePath)) {
                camera.controller().initMediaRecorderOnThread(new File(mFilePath), surfaceHolder);
                camera.startCapturingVideo(new File(mFilePath));
            }else{
                camera.controller().initMediaRecorderOnThread(null, surfaceHolder);
                camera.startCapturingVideo(null);
            }

        } catch (SVRException e) {
            listener.onCameraError(e);
        }
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
            camera.stopCapturingVideo();

            if (stopCamera) {
                isScheduleRecordingRunning = false;
                camera.stop();
            }
        }
    }

    public void setFileNameAndPath() {
        String fileFormat = preferences.getString(SettingsFragment.KEY_FILE_NAME_FORMAT, "1");
        mFileName = Util.getFormatedFileName(getApplicationContext(), fileFormat);

        String selectDir = Util.getPrefs(getApplicationContext()).getString(SettingsFragment.KEY_STORAGE_DIR, Util.getCurrentStorageDir(getApplicationContext()));
        if (Util.isUriString(selectDir)) {
            DocumentFile doc = DocumentFile.fromTreeUri(getApplicationContext(), Uri.parse(selectDir));
            DocumentFile file = doc.createFile("video/mp4", mFileName);
            mFilePath = file.getUri().toString();
            Util.getPrefs(getApplicationContext()).edit().putString(SettingsFragment.KEY_FILES_NEW_FILE_URI, mFilePath).apply();
        } else {
            mFilePath = selectDir;
            mFilePath += "/" + mFileName;
        }
    }


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