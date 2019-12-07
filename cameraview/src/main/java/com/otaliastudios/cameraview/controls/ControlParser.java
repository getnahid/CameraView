package com.otaliastudios.cameraview.controls;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;

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
    private int engine;
    private SharedPreferences preference;

    public static final String KEY_CAMERA_PREVIEW = "CameraView_cameraPreview";
    public static final String KEY_CAMERA_FACING = "CameraView_cameraFacing";
    public static final String KEY_CAMERA_FLASH = "CameraView_cameraFlash";
    public static final String KEY_CAMERA_GRID = "CameraView_cameraGrid";
    public static final String KEY_CAMERA_WHITE_BALANCE = "CameraView_cameraWhiteBalance";
    public static final String KEY_CAMERA_MODE = "CameraView_cameraMode";
    public static final String KEY_CAMERA_HDR = "CameraView_cameraHdr";
    public static final String KEY_CAMERA_AUDIO = "CameraView_cameraAudio";
    public static final String KEY_CAMERA_VIDEO_CODEC = "CameraView_cameraVideoCodec";
    public static final String KEY_CAMERA_ENGINE = "CameraView_cameraEngine";

    public ControlParser(@NonNull Context context) {
        preference = PreferenceManager.getDefaultSharedPreferences(context);
        this.preview = preference.getInt(KEY_CAMERA_PREVIEW, Preview.DEFAULT.value());
        this.facing = preference.getInt(KEY_CAMERA_FACING, Facing.DEFAULT(context).value());
        this.flash = preference.getInt(KEY_CAMERA_FLASH, Flash.DEFAULT.value());
        this.grid = preference.getInt(KEY_CAMERA_GRID, Grid.DEFAULT.value());
        this.whiteBalance = preference.getInt(KEY_CAMERA_WHITE_BALANCE, WhiteBalance.DEFAULT.value());
        this.mode = preference.getInt(KEY_CAMERA_MODE, Mode.DEFAULT.value());
        this.hdr = preference.getInt(KEY_CAMERA_HDR, Hdr.DEFAULT.value());
        this.audio = preference.getInt(KEY_CAMERA_AUDIO, Audio.DEFAULT.value());
        this.videoCodec = preference.getInt(KEY_CAMERA_VIDEO_CODEC, VideoCodec.DEFAULT.value());

//        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P){
//            this.engine = preference.getInt(KEY_CAMERA_ENGINE, Engine.CAMERA2.value());
//        }else {
            this.engine = preference.getInt(KEY_CAMERA_ENGINE, Engine.DEFAULT.value());
        //}
    }

    public void setFacing(Facing facing){
        preference.edit().putInt(KEY_CAMERA_FACING, facing.value()).apply();
    }

    public void setToDefault(Context context){
        this.preview = Preview.DEFAULT.value();
        this.facing = Facing.DEFAULT(context).value();
        this.flash = Flash.DEFAULT.value();
        this.grid = Grid.DEFAULT.value();
        this.whiteBalance = WhiteBalance.DEFAULT.value();
        this.mode = Mode.DEFAULT.value();
        this.hdr = Hdr.DEFAULT.value();
        this.audio = Audio.DEFAULT.value();
        this.videoCodec = VideoCodec.DEFAULT.value();


        this.engine = Engine.DEFAULT.value();

        preference.edit().putInt(KEY_CAMERA_PREVIEW, preview).apply();
        preference.edit().putInt(KEY_CAMERA_FACING, facing).apply();
        preference.edit().putInt(KEY_CAMERA_FLASH, flash).apply();
        preference.edit().putInt(KEY_CAMERA_GRID, grid).apply();
        preference.edit().putInt(KEY_CAMERA_WHITE_BALANCE, whiteBalance).apply();
        preference.edit().putInt(KEY_CAMERA_MODE, mode).apply();
        preference.edit().putInt(KEY_CAMERA_HDR, hdr).apply();
        preference.edit().putInt(KEY_CAMERA_AUDIO, audio).apply();
        preference.edit().putInt(KEY_CAMERA_VIDEO_CODEC, videoCodec).apply();
    }

    @NonNull
    public Preview getPreview() {
        return Preview.fromValue(preview);
    }

    @NonNull
    public Facing getFacing() {
        //noinspection ConstantConditions
        return Facing.fromValue(facing);
    }

    @NonNull
    public Flash getFlash() {
        return Flash.fromValue(flash);
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

    @NonNull
    public Hdr getHdr() {
        return Hdr.fromValue(hdr);
    }

    @NonNull
    public Audio getAudio() {
        return Audio.fromValue(audio);
    }

    @NonNull
    public VideoCodec getVideoCodec() {
        return VideoCodec.fromValue(videoCodec);
    }

    public void setEngine(int engine) {
        preference.edit().putInt(KEY_CAMERA_ENGINE, engine).apply();
    }

    @NonNull
    public Engine getEngine() {
        return Engine.fromValue(engine);
    }
}
