package com.otaliastudios.cameraview.demo;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.otaliastudios.cameraview.CameraLogger;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.controls.Mode;
import com.otaliastudios.cameraview.filter.Filters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class CameraActivity extends AppCompatActivity implements View.OnClickListener, OptionView.Callback {

    private final static CameraLogger LOG = CameraLogger.create("DemoApp");


    private CameraView camera;
    private ViewGroup controlPanel;
    private long mCaptureTime;

    private int mCurrentFilter = 0;
    private final Filters[] mAllFilters = Filters.values();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        CameraLogger.setLogLevel(CameraLogger.LEVEL_VERBOSE);


        findViewById(R.id.edit).setOnClickListener(this);
        findViewById(R.id.capturePicture).setOnClickListener(this);
        findViewById(R.id.capturePictureSnapshot).setOnClickListener(this);
        findViewById(R.id.captureVideo).setOnClickListener(this);
        findViewById(R.id.captureVideoSnapshot).setOnClickListener(this);
        findViewById(R.id.toggleCamera).setOnClickListener(this);
        findViewById(R.id.changeFilter).setOnClickListener(this);

        findViewById(R.id.openCamera).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                service.startCameraForRecording();
            }
        });

        findViewById(R.id.closeCamera).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                service.closeCamera();
            }
        });

        findViewById(R.id.startPreview).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                service.startPreview();
            }
        });

        findViewById(R.id.stopPreview).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                service.stopPreview();
            }
        });

        findViewById(R.id.startRecording).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                service.startRecording();
            }
        });

        findViewById(R.id.stopRecording).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                service.stopRecording(false);
            }
        });

        controlPanel = findViewById(R.id.controls);
        ViewGroup group = (ViewGroup) controlPanel.getChildAt(0);
        //final View watermark = findViewById(R.id.watermark);

        List<Option<?>> options = Arrays.asList(
                // Layout
                new Option.Width(), new Option.Height(),
                // Engine and preview
                new Option.Mode(), new Option.Engine(), new Option.Preview(),
                // Some controls
                new Option.Flash(), new Option.WhiteBalance(), new Option.Hdr(),
                new Option.PictureMetering(), new Option.PictureSnapshotMetering(),
                new Option.PictureFormat(),
                // Video recording
                new Option.PreviewFrameRate(), new Option.VideoCodec(), new Option.Audio(),
                // Gestures
                new Option.Pinch(), new Option.HorizontalScroll(), new Option.VerticalScroll(),
                new Option.Tap(), new Option.LongTap(),
                // Watermarks
//                new Option.OverlayInPreview(watermark),
//                new Option.OverlayInPictureSnapshot(watermark),
//                new Option.OverlayInVideoSnapshot(watermark),
                // Other
                new Option.Grid(), new Option.GridColor(), new Option.UseDeviceOrientation()
        );
        List<Boolean> dividers = Arrays.asList(
                // Layout
                false, true,
                // Engine and preview
                false, false, true,
                // Some controls
                false, false, false, false, false, true,
                // Video recording
                false, false, true,
                // Gestures
                false, false, false, false, true,
                // Watermarks
                false, false, true,
                // Other
                false, false, true
        );
        for (int i = 0; i < options.size(); i++) {
            OptionView view = new OptionView(this);
            //noinspection unchecked
            view.setOption(options.get(i), this);
            view.setHasDivider(dividers.get(i));
            group.addView(view,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        controlPanel.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                BottomSheetBehavior b = BottomSheetBehavior.from(controlPanel);
                b.setState(BottomSheetBehavior.STATE_HIDDEN);
            }
        });

        // Animate the watermark just to show we record the animation in video snapshots
        ValueAnimator animator = ValueAnimator.ofFloat(1F, 0.8F);
        animator.setDuration(300);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float scale = (float) animation.getAnimatedValue();
//                watermark.setScaleX(scale);
//                watermark.setScaleY(scale);
//                watermark.setRotation(watermark.getRotation() + 2);
            }
        });
        animator.start();

        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.CAMERA);
        permissions.add(Manifest.permission.RECORD_AUDIO);
        requestPermissions(permissions.toArray(new String[0]),
                    102);


        //if(!canDrawOverlays(this)){
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, 100);
        //}else{
            startKeyPressService();
            doBindService();
        //}
    }

    public boolean canDrawOverlays(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
//        else if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
//            return Settings.canDrawOverlays(context);
//        }
        else {
            if (Settings.canDrawOverlays(context)) return true;
            try {
                WindowManager mgr = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                if (mgr == null) return false; //getSystemService might return null
                View viewToAdd = new View(context);
                WindowManager.LayoutParams params = new WindowManager.LayoutParams(0, 0, android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSPARENT);
                viewToAdd.setLayoutParams(params);
                mgr.addView(viewToAdd, params);
                mgr.removeView(viewToAdd);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }
    }

    public boolean isInstallFromUpdate() {
        try {
            long firstInstallTime =  getPackageManager().getPackageInfo(getPackageName(), 0).firstInstallTime;
            long lastUpdateTime = getPackageManager().getPackageInfo(getPackageName(), 0).lastUpdateTime;
            return firstInstallTime != lastUpdateTime;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void startKeyPressService() {
        Intent intent = new Intent(this, KeyDetectService.class);

//        if (oneTouchRecording) {
//            intent.putExtra(KeyDetectService.KEY_COMMAND, KeyDetectService.KEY_ONE_TOUCH_RECORDING);
//        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private KeyDetectService service;

    public class MyServiceConnection implements ServiceConnection {
        public void onServiceConnected(ComponentName className, IBinder binder) {
            service = ((KeyDetectService.MyBinder) binder).getService();

            if (service.getCamera() != null) {
                if (service.isRecordingRunning()) {
                    //startClock(service.getEllipseTime());
                }
            }

//            if (preferences.getBoolean(SettingsFragment.KEY_SMS_PERMISSION, false)) {
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && getContext().checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED){
//                    ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.RECEIVE_SMS},
//                            PERMISSIONS_REQUESTS_CODE_SMS);
//                }
//                else{
//                    service.registerSMSReceiver();
//                }
//            }
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.d("ServiceConnection", "disconnected");
            service = null;
        }
    }

    MyServiceConnection myConnection;

    public void doBindService() {
        myConnection = new MyServiceConnection();
        Intent intent = new Intent(this, KeyDetectService.class);
        bindService(intent, myConnection, Context.BIND_AUTO_CREATE);
    }

    private void message(@NonNull String content, boolean important) {
        if (important) {
            LOG.w(content);
            Toast.makeText(this, content, Toast.LENGTH_LONG).show();
        } else {
            LOG.i(content);
            Toast.makeText(this, content, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.edit: edit(); break;
            case R.id.capturePicture: capturePicture(); break;
            case R.id.capturePictureSnapshot: capturePictureSnapshot(); break;
            case R.id.captureVideo: captureVideo(); break;
            case R.id.captureVideoSnapshot: captureVideoSnapshot(); break;
            case R.id.toggleCamera: toggleCamera(); break;
            case R.id.changeFilter: changeCurrentFilter(); break;
        }
    }

    @Override
    public void onBackPressed() {
        BottomSheetBehavior b = BottomSheetBehavior.from(controlPanel);
        if (b.getState() != BottomSheetBehavior.STATE_HIDDEN) {
            b.setState(BottomSheetBehavior.STATE_HIDDEN);
            return;
        }
        super.onBackPressed();
    }

    private void edit() {
        BottomSheetBehavior b = BottomSheetBehavior.from(controlPanel);
        b.setState(BottomSheetBehavior.STATE_COLLAPSED);
    }

    private void capturePicture() {
        if (camera.getMode() == Mode.VIDEO) {
            message("Can't take HQ pictures while in VIDEO mode.", false);
            return;
        }
        if (camera.isTakingPicture()) return;
        mCaptureTime = System.currentTimeMillis();
        message("Capturing picture...", false);
        camera.takePicture();
    }

    private void capturePictureSnapshot() {
//        if (camera.isTakingPicture()) return;
//        if (camera.getPreview() != Preview.GL_SURFACE) {
//            message("Picture snapshots are only allowed with the GL_SURFACE preview.", true);
//            return;
//        }
//        mCaptureTime = System.currentTimeMillis();
//        message("Capturing picture snapshot...", false);
//        camera.takePictureSnapshot();
    }

    private void captureVideo() {
//        if (camera.getMode() == Mode.PICTURE) {
//            message("Can't record HQ videos while in PICTURE mode.", false);
//            return;
//        }
//        if (camera.isTakingPicture() || camera.isTakingVideo()) return;
//        message("Recording for 5 seconds...", true);
//        camera.takeVideo(new File(getFilesDir(), "video.mp4"), 5000);
        //service.startCameraForRecording();
    }

    private void captureVideoSnapshot() {
//        if (camera.isTakingVideo()) {
//            message("Already taking video.", false);
//            return;
//        }
//        if (camera.getPreview() != Preview.GL_SURFACE) {
//            message("Video snapshots are only allowed with the GL_SURFACE preview.", true);
//            return;
//        }
//        message("Recording snapshot for 5 seconds...", true);
//        camera.takeVideoSnapshot(new File(getFilesDir(), "video.mp4"), 5000);
        service.startRecording();
    }

    private void toggleCamera() {
        if (camera.isTakingPicture() || camera.isTakingVideo()) return;
        switch (camera.toggleFacing()) {
            case BACK:
                message("Switched to back camera!", false);
                break;

            case FRONT:
                message("Switched to front camera!", false);
                break;
        }
    }

    private void changeCurrentFilter() {
//        if (camera.getPreview() != Preview.GL_SURFACE) {
//            message("Filters are supported only when preview is Preview.GL_SURFACE.", true);
//            return;
//        }
//        if (mCurrentFilter < mAllFilters.length - 1) {
//            mCurrentFilter++;
//        } else {
//            mCurrentFilter = 0;
//        }
//        Filters filter = mAllFilters[mCurrentFilter];
//        message(filter.toString(), false);
//
//        // Normal behavior:
//        camera.setFilter(filter.newInstance());

        // To test MultiFilter:
        // DuotoneFilter duotone = new DuotoneFilter();
        // duotone.setFirstColor(Color.RED);
        // duotone.setSecondColor(Color.GREEN);
        // camera.setFilter(new MultiFilter(duotone, filter.newInstance()));
    }

    @Override
    public <T> boolean onValueChanged(@NonNull Option<T> option, @NonNull T value, @NonNull String name) {
//        if ((option instanceof Option.Width || option instanceof Option.Height)) {
//            Preview preview = camera.getPreview();
//            boolean wrapContent = (Integer) value == ViewGroup.LayoutParams.WRAP_CONTENT;
//            if (preview == Preview.SURFACE && !wrapContent) {
//                message("The SurfaceView preview does not support width or height changes. " +
//                        "The view will act as WRAP_CONTENT by default.", true);
//                return false;
//            }
//        }
//        option.set(camera, value);
//        BottomSheetBehavior b = BottomSheetBehavior.from(controlPanel);
//        b.setState(BottomSheetBehavior.STATE_HIDDEN);
//        message("Changed " + option.getName() + " to " + name, false);
        return true;
    }

    //region Permissions

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        boolean valid = true;
//        for (int grantResult : grantResults) {
//            valid = valid && grantResult == PackageManager.PERMISSION_GRANTED;
//        }
//        if (valid && !camera.isOpened()) {
//            camera.open();
//        }
    }

    //endregion
}
