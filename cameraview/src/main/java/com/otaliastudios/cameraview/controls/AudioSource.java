package com.otaliastudios.cameraview.controls;


import androidx.annotation.NonNull;

import com.otaliastudios.cameraview.CameraOptions;
import com.otaliastudios.cameraview.CameraView;

/**
 * Flash value indicates the flash mode to be used.
 *
 * @see CameraView#setAudioSource(AudioSource)
 */
public enum AudioSource implements Control {

    DEFAULT_AUDIO(0),

    EXTERNAL_MIC(1),

    CAMCORDER(2),

    OPTIMIZE_FOR_VOICE_CALL(3),

    OPTIMIZE_FOR_VOICE_RECOGNITION(4),

    UNCOMPRESSED(5);

    static final AudioSource DEFAULT = CAMCORDER;

    public int value;

    AudioSource(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    @NonNull
    static AudioSource fromValue(int value) {
        AudioSource[] list = AudioSource.values();
        for (AudioSource action : list) {
            if (action.value() == value) {
                return action;
            }
        }
        return DEFAULT;
    }
}
