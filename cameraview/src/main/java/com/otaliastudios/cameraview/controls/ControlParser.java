package com.otaliastudios.cameraview.controls;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.NonNull;

import com.otaliastudios.cameraview.CameraUtils;

/**
 * Parses controls from XML attributes.
 */
public class ControlParser {

    private int preview;
    private int facing;
    private int flash;
    private int grid;
    private int whiteBalance;
    private int mode;
    private int hdr;
    private int audio;
    private int videoCodec;
    private int audioCodec;
    private int frontCameraEngine;
    private int backCameraEngine;
    private int pictureFormat;
    private float zoom;
    private boolean showPreview;
    private boolean showPreviewOverOtherApp;
    private SharedPreferences preference;
    private Context context;
    private int audioBitrate;
    private int videoBitrate;
    private int audioSource;

    public static final String KEY_CAMERA_PREVIEW = "CameraView_cameraPreview";
    public static final String KEY_CAMERA_FACING = "CameraView_cameraFacing";
    public static final String KEY_CAMERA_FLASH = "CameraView_cameraFlash";
    public static final String KEY_CAMERA_GRID = "CameraView_cameraGrid";
    public static final String KEY_CAMERA_WHITE_BALANCE = "CameraView_cameraWhiteBalance";
    public static final String KEY_CAMERA_MODE = "CameraView_cameraMode";
    public static final String KEY_CAMERA_HDR = "CameraView_cameraHdr";
    public static final String KEY_CAMERA_AUDIO = "CameraView_cameraAudio";
    public static final String KEY_CAMERA_AUDIO_SOURCE = "CameraView_cameraAudioSource";
    public static final String KEY_CAMERA_VIDEO_CODEC = "CameraView_cameraVideoCodec";
    public static final String KEY_CAMERA_AUDIO_CODEC = "CameraView_cameraAudioCodec";
    public static final String KEY_CAMERA_ENGINE_FRONT = "CameraView_cameraEngine_front";
    public static final String KEY_CAMERA_ENGINE_BACK = "CameraView_cameraEngine_back";
    public static final String KEY_CAMERA_PICTURE_FORMAT = "CameraView_cameraPictureFormat";
    public static final String KEY_CAMERA_ZOOM = "CameraView_cameraZoom";
    public static final String KEY_CAMERA_SHOW_PREVIEW = "CameraView_cameraShowPreview";
    public static final String KEY_CAMERA_SHOW_PREVIEW_OVER_OTHER_APP = "CameraView_cameraShowPreviewOverOtherApp";
    public static final String KEY_CAMERA_VIDEO_BIT_RATE = "CameraView_cameraVideoBitRate";
    public static final String KEY_CAMERA_AUDIO_BIT_RATE = "CameraView_cameraAudioBitRate";

    public ControlParser(@NonNull Context context, SharedPreferences preference) {
        this.preference = preference;
        this.context = context;

        if(CameraUtils.isGLSurfaceSupported()) {
            this.preview = preference.getInt(KEY_CAMERA_PREVIEW, Preview.GL_SURFACE.value());
        } else {
            this.preview = preference.getInt(KEY_CAMERA_PREVIEW, Preview.SURFACE.value());
            setPreview(this.preview);
        }

        this.facing = preference.getInt(KEY_CAMERA_FACING, Facing.DEFAULT(context).value());
        this.flash = preference.getInt(KEY_CAMERA_FLASH, Flash.DEFAULT.value());
        this.grid = preference.getInt(KEY_CAMERA_GRID, Grid.DEFAULT.value());
        this.whiteBalance = preference.getInt(KEY_CAMERA_WHITE_BALANCE, WhiteBalance.DEFAULT.value());
        this.mode = preference.getInt(KEY_CAMERA_MODE, Mode.DEFAULT.value());
        this.hdr = preference.getInt(KEY_CAMERA_HDR, Hdr.DEFAULT.value());
        this.audio = preference.getInt(KEY_CAMERA_AUDIO, Audio.DEFAULT.value());
        this.audioSource = preference.getInt(KEY_CAMERA_AUDIO_SOURCE, AudioSource.DEFAULT.value());
        this.videoCodec = preference.getInt(KEY_CAMERA_VIDEO_CODEC, VideoCodec.DEFAULT.value());
        this.audioCodec = preference.getInt(KEY_CAMERA_AUDIO_CODEC, AudioCodec.DEFAULT.value());
        this.pictureFormat = preference.getInt(KEY_CAMERA_PICTURE_FORMAT, PictureFormat.DEFAULT.value());
        this.zoom = preference.getFloat(KEY_CAMERA_ZOOM, 0.0f);
        this.showPreview = preference.getBoolean(KEY_CAMERA_SHOW_PREVIEW, false);
        this.showPreviewOverOtherApp = preference.getBoolean(KEY_CAMERA_SHOW_PREVIEW_OVER_OTHER_APP, false);
        this.frontCameraEngine = preference.getInt(KEY_CAMERA_ENGINE_FRONT, Engine.CAMERA2.value());
        this.backCameraEngine = preference.getInt(KEY_CAMERA_ENGINE_BACK, Engine.CAMERA2.value());
        this.videoBitrate = preference.getInt(KEY_CAMERA_VIDEO_BIT_RATE, 0);
        this.audioBitrate = preference.getInt(KEY_CAMERA_AUDIO_BIT_RATE, 0);

//        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P){
////            if(CameraUtils.isCamera2ApiSupportedDevice()) {
////                this.engine = preference.getInt(KEY_CAMERA_ENGINE, Engine.CAMERA2.value());
////                setEngine(engine);
////            } else {
////                this.engine = preference.getInt(KEY_CAMERA_ENGINE, Engine.DEFAULT.value());
////            }
//
//        }else {
//            this.engine = preference.getInt(KEY_CAMERA_ENGINE, Engine.DEFAULT.value());
//        }

        checkCamera2ApiSupport();
    }

    private void checkCamera2ApiSupport() {
        if (this.facing == Facing.FRONT.value()) {
            if (frontCameraEngine == Engine.CAMERA2.value()) {
                if (!CameraUtils.isFrontCameraSupportsCamera2api(context)) {
                    setEngine(Engine.CAMERA1.value());
                }
            }
        } else if (this.facing == Facing.BACK.value()) {
            if (backCameraEngine == Engine.CAMERA2.value()) {
                if (!CameraUtils.isBackCameraSupportsCamera2api(context)) {
                    setEngine(Engine.CAMERA1.value());
                }
            }
        }
    }

    @NonNull
    public Preview getPreview() {
        return Preview.fromValue(preview);
    }

    public void setPreview(int preview) {
        this.preview = preview;
        preference.edit().putInt(KEY_CAMERA_PREVIEW, preview).apply();
    }

    @NonNull
    public Grid getGrid() {
        return Grid.fromValue(grid);
    }

    @NonNull
    public Mode getMode() {
        return Mode.fromValue(mode);
    }

    @NonNull
    public WhiteBalance getWhiteBalance() {
        return WhiteBalance.fromValue(whiteBalance);
    }

    public void setWhiteBalance(int value) {
        this.whiteBalance = value;
        preference.edit().putInt(KEY_CAMERA_WHITE_BALANCE, value).apply();
    }

    @NonNull
    public int getVideoBitrate() {
        return videoBitrate;
    }

    public void setVideoBitrate(int value) {
        this.videoBitrate = value;
        preference.edit().putInt(KEY_CAMERA_VIDEO_BIT_RATE, value).apply();
    }

    @NonNull
    public int getAudioBitrate() {
        return audioBitrate;
    }

    public void setAudioBitrate(int value) {
        this.audioBitrate = value;
        preference.edit().putInt(KEY_CAMERA_AUDIO_BIT_RATE, value).apply();
    }

    @NonNull
    public Hdr getHdr() {
        return Hdr.fromValue(hdr);
    }

    public void setHdr(int value) {
        this.hdr = value;
        preference.edit().putInt(KEY_CAMERA_HDR, value).apply();
    }

    @NonNull
    public AudioCodec getAudioCodec() {
        return AudioCodec.fromValue(audioCodec);
    }
    public void setAudioCodec(int value) {
        this.audioCodec = value;
        preference.edit().putInt(KEY_CAMERA_AUDIO_CODEC, value).apply();
    }

    @NonNull
    public VideoCodec getVideoCodec() {
        return VideoCodec.fromValue(videoCodec);
    }

    public void setVideoCodec(int value) {
        this.videoCodec = value;
        preference.edit().putInt(KEY_CAMERA_VIDEO_CODEC, value).apply();
    }


    @NonNull
    public PictureFormat getPictureFormat() {
        return PictureFormat.fromValue(pictureFormat);
    }

    public boolean canShowPreviewOverOtherApp(){
        return showPreviewOverOtherApp;
    }

    public void setShowPreviewOverOtherApp(boolean value) {
        this.showPreviewOverOtherApp = value;
        preference.edit().putBoolean(KEY_CAMERA_SHOW_PREVIEW_OVER_OTHER_APP, value).apply();
    }

    public boolean canShowPreview(){
        return showPreview;
    }

    public void setShowPreview(boolean value) {
        this.showPreview = value;
        preference.edit().putBoolean(KEY_CAMERA_SHOW_PREVIEW, value).apply();
    }

    public float getZoom(){
        return zoom;
    }

    public void setZoom(float zoom) {
        this.zoom = zoom;
        preference.edit().putFloat(KEY_CAMERA_ZOOM, zoom).apply();
    }

    @NonNull
    public Facing getFacing() {
        //noinspection ConstantConditions
        return Facing.fromValue(facing);
    }

    public void setFacing(int face) {
        this.facing = face;
        preference.edit().putInt(KEY_CAMERA_FACING, facing).apply();
        checkCamera2ApiSupport();
    }

    @NonNull
    public Engine getEngine() {
        if (this.facing == Facing.FRONT.value()) {
            return Engine.fromValue(frontCameraEngine);
        } else if (this.facing == Facing.BACK.value()) {
            return Engine.fromValue(backCameraEngine);
        }

        return Engine.CAMERA1;
    }

    public void setEngine(int engine) {
        if (this.facing == Facing.FRONT.value()) {
            this.frontCameraEngine = engine;
            preference.edit().putInt(KEY_CAMERA_ENGINE_FRONT, engine).apply();
        } else if (this.facing == Facing.BACK.value()) {
            this.backCameraEngine = engine;
            preference.edit().putInt(KEY_CAMERA_ENGINE_BACK, engine).apply();
        }
    }

    @NonNull
    public Audio getAudio() {
        return Audio.fromValue(audio);
    }

    public void setAudio(int audio) {
        this.audio = audio;
        preference.edit().putInt(KEY_CAMERA_AUDIO, audio).apply();
    }

    @NonNull
    public AudioSource getAudioSource() {
        return AudioSource.fromValue(audioSource);
    }

    public void setAudioSource(int audioSource) {
        this.audioSource = audioSource;
        preference.edit().putInt(KEY_CAMERA_AUDIO_SOURCE, audioSource).apply();
    }

    @NonNull
    public Flash getFlash() {
        return Flash.fromValue(flash);
    }

    public void setFlash(int flash) {
        this.flash = flash;
        preference.edit().putInt(KEY_CAMERA_FLASH, flash).apply();
    }
}
