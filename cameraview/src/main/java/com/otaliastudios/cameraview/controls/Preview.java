package com.otaliastudios.cameraview.controls;


import androidx.annotation.NonNull;

import com.otaliastudios.cameraview.CameraView;

/**
 * The preview engine to be used.
 *
 * @see com.otaliastudios.cameraview.CameraPreviewView#setPreview(Preview)
 */
public enum Preview implements Control {

    /**
     * Preview engine based on {@link android.view.SurfaceView}.
     * Not recommended.
     */
    SURFACE(0),

    /**
     * Preview engine based on {@link android.view.TextureView}.
     * Stable, but does not support all features (like video snapshots,
     * or picture snapshot while taking videos).
     */
    TEXTURE(1),

    /**
     * Preview engine based on {@link android.opengl.GLSurfaceView}.
     * This is the best engine available. Supports video snapshots,
     * supports picture snapshots while taking videos, supports
     * watermarks and overlays, supports real-time filters.
     */
    GL_SURFACE(2);

    public final static Preview DEFAULT = GL_SURFACE;

    private int value;

    Preview(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    @NonNull
    static Preview fromValue(int value) {
        Preview[] list = Preview.values();
        for (Preview action : list) {
            if (action.value() == value) {
                return action;
            }
        }
        return DEFAULT;
    }
}
