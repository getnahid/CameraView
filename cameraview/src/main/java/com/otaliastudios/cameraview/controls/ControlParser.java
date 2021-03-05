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
    private SharedPreferences preference;
    private Context context;

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
        checkCamera2ApiSupport();
    }

    private int getFrontCameraEngine() {
        return preference.getInt(KEY_CAMERA_ENGINE_FRONT, Engine.CAMERA2.value());
    }

    private int getBackCameraEngine() {
        return preference.getInt(KEY_CAMERA_ENGINE_BACK, Engine.CAMERA2.value());
    }

    private void setFrontCameraEngine(int value) {
        preference.edit().putInt(KEY_CAMERA_ENGINE_FRONT, value).apply();
    }

    private void setBackCameraEngine(int value) {
        preference.edit().putInt(KEY_CAMERA_ENGINE_BACK, value).apply();
    }

    private void checkCamera2ApiSupport() {
        if (getFacing() == Facing.FRONT) {
            if (getFrontCameraEngine() == Engine.CAMERA2.value()) {
                if (!CameraUtils.isFrontCameraSupportsCamera2api(context)) {
                    setEngine(Engine.CAMERA1.value());
                }
            }
        } else if (getFacing() == Facing.BACK) {
            if (getBackCameraEngine() == Engine.CAMERA2.value()) {
                if (!CameraUtils.isBackCameraSupportsCamera2api(context)) {
                    setEngine(Engine.CAMERA1.value());
                }
            }
        }
    }

    private int getDefaultPreviewValue() {
        if(CameraUtils.isGLSurfaceSupported()) {
            return Preview.GL_SURFACE.value();
        } else {
           return Preview.TEXTURE.value();
        }
    }

    @NonNull
    public Preview getPreview() {
        int preview = preference.getInt(KEY_CAMERA_PREVIEW, getDefaultPreviewValue());
        return Preview.fromValue(preview);
    }

    public void setPreview(int preview) {
        preference.edit().putInt(KEY_CAMERA_PREVIEW, preview).apply();
    }

    @NonNull
    public Grid getGrid() {
        int grid = preference.getInt(KEY_CAMERA_GRID, Grid.DEFAULT.value());
        return Grid.fromValue(grid);
    }

    @NonNull
    public Mode getMode() {
        int mode = preference.getInt(KEY_CAMERA_MODE, Mode.DEFAULT.value());
        return Mode.fromValue(mode);
    }

    @NonNull
    public WhiteBalance getWhiteBalance() {
        int whiteBalance = preference.getInt(KEY_CAMERA_WHITE_BALANCE, WhiteBalance.DEFAULT.value());
        return WhiteBalance.fromValue(whiteBalance);
    }

    public void setWhiteBalance(int value) {
        preference.edit().putInt(KEY_CAMERA_WHITE_BALANCE, value).apply();
    }

    @NonNull
    public int getVideoBitrate() {
        return preference.getInt(KEY_CAMERA_VIDEO_BIT_RATE, 0);
    }

    public void setVideoBitrate(int value) {
        preference.edit().putInt(KEY_CAMERA_VIDEO_BIT_RATE, value).apply();
    }

    @NonNull
    public int getAudioBitrate() {
        return preference.getInt(KEY_CAMERA_AUDIO_BIT_RATE, 0);
    }

    public void setAudioBitrate(int value) {
        preference.edit().putInt(KEY_CAMERA_AUDIO_BIT_RATE, value).apply();
    }

    @NonNull
    public Hdr getHdr() {
        int hdr = preference.getInt(KEY_CAMERA_HDR, Hdr.DEFAULT.value());
        return Hdr.fromValue(hdr);
    }

    public void setHdr(int value) {
        preference.edit().putInt(KEY_CAMERA_HDR, value).apply();
    }

    @NonNull
    public AudioCodec getAudioCodec() {
        int audioCodec = preference.getInt(KEY_CAMERA_AUDIO_CODEC, AudioCodec.DEFAULT.value());
        return AudioCodec.fromValue(audioCodec);
    }
    public void setAudioCodec(int value) {
        preference.edit().putInt(KEY_CAMERA_AUDIO_CODEC, value).apply();
    }

    @NonNull
    public VideoCodec getVideoCodec() {
        int videoCodec = preference.getInt(KEY_CAMERA_VIDEO_CODEC, VideoCodec.DEFAULT.value());
        return VideoCodec.fromValue(videoCodec);
    }

    public void setVideoCodec(int value) {
        preference.edit().putInt(KEY_CAMERA_VIDEO_CODEC, value).apply();
    }


    @NonNull
    public PictureFormat getPictureFormat() {
        int pictureFormat = preference.getInt(KEY_CAMERA_PICTURE_FORMAT, PictureFormat.DEFAULT.value());
        return PictureFormat.fromValue(pictureFormat);
    }

    public boolean canShowPreviewOverOtherApp(){
        return preference.getBoolean(KEY_CAMERA_SHOW_PREVIEW_OVER_OTHER_APP, false);
    }

    public void setShowPreviewOverOtherApp(boolean value) {
        preference.edit().putBoolean(KEY_CAMERA_SHOW_PREVIEW_OVER_OTHER_APP, value).apply();
    }

    public boolean canShowPreview(){
        return preference.getBoolean(KEY_CAMERA_SHOW_PREVIEW, false);
    }

    public void setShowPreview(boolean value) {
        preference.edit().putBoolean(KEY_CAMERA_SHOW_PREVIEW, value).apply();
    }

    public float getZoom(){
        return preference.getFloat(KEY_CAMERA_ZOOM, 0.0f);
    }

    public void setZoom(float zoom) {
        preference.edit().putFloat(KEY_CAMERA_ZOOM, zoom).apply();
    }

    @NonNull
    public Facing getFacing() {
        int facing = preference.getInt(KEY_CAMERA_FACING, Facing.DEFAULT(context).value());
        return Facing.fromValue(facing);
    }

    public void setFacing(int face) {
        preference.edit().putInt(KEY_CAMERA_FACING, face).apply();
        checkCamera2ApiSupport();
    }

    @NonNull
    public Engine getEngine() {
        if (getFacing() == Facing.FRONT) {
            return Engine.fromValue(getFrontCameraEngine());
        } else if (getFacing() == Facing.BACK) {
            return Engine.fromValue(getBackCameraEngine());
        }

        return Engine.CAMERA1;
    }

    public void setEngine(int engine) {
        if (getFacing() == Facing.FRONT) {
            setFrontCameraEngine(engine);
            preference.edit().putInt(KEY_CAMERA_ENGINE_FRONT, engine).apply();
        } else if (getFacing() == Facing.BACK) {
            setBackCameraEngine(engine);
            preference.edit().putInt(KEY_CAMERA_ENGINE_BACK, engine).apply();
        }
    }

    @NonNull
    public Audio getAudio() {
        int audio = preference.getInt(KEY_CAMERA_AUDIO, Audio.DEFAULT.value());
        return Audio.fromValue(audio);
    }

    public void setAudio(int audio) {
        preference.edit().putInt(KEY_CAMERA_AUDIO, audio).apply();
    }

    @NonNull
    public AudioSource getAudioSource() {
        int audioSource = preference.getInt(KEY_CAMERA_AUDIO_SOURCE, AudioSource.DEFAULT.value());
        return AudioSource.fromValue(audioSource);
    }

    public void setAudioSource(int audioSource) {
        preference.edit().putInt(KEY_CAMERA_AUDIO_SOURCE, audioSource).apply();
    }

    @NonNull
    public Flash getFlash() {
        int flash = preference.getInt(KEY_CAMERA_FLASH, Flash.DEFAULT.value());
        return Flash.fromValue(flash);
    }

    public void setFlash(int flash) {
        preference.edit().putInt(KEY_CAMERA_FLASH, flash).apply();
    }
}
