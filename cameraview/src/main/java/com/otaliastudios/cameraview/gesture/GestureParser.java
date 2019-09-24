package com.otaliastudios.cameraview.gesture;

import android.content.SharedPreferences;

/**
 * Parses gestures from XML attributes.
 */
public class GestureParser {

    private int tapAction;
    private int longTapAction;
    private int pinchAction;
    private int horizontalScrollAction;
    private int verticalScrollAction;

    public static final String KEY_CAMERA_GESTURE_TAP = "CameraView_cameraGestureTap";
    public static final String KEY_CAMERA_GESTURE_LONG_TAP = "CameraView_cameraGestureLongTap";
    public static final String KEY_CAMERA_GESTURE_PINCH = "CameraView_cameraGesturePinch";
    public static final String KEY_CAMERA_GESTURE_SCROLL_HORIZONTAL = "CameraView_cameraGestureScrollHorizontal";
    public static final String KEY_CAMERA_GESTURE_SCROLL_VERTICAL = "CameraView_cameraGestureScrollVertical";

    public GestureParser(SharedPreferences preference) {
        this.tapAction = preference.getInt(KEY_CAMERA_GESTURE_TAP, GestureAction.DEFAULT_TAP.value());
        this.longTapAction = preference.getInt(KEY_CAMERA_GESTURE_LONG_TAP, GestureAction.DEFAULT_LONG_TAP.value());
        this.pinchAction = preference.getInt(KEY_CAMERA_GESTURE_PINCH, GestureAction.DEFAULT_PINCH.value());
        this.horizontalScrollAction = preference.getInt(KEY_CAMERA_GESTURE_SCROLL_HORIZONTAL, GestureAction.DEFAULT_SCROLL_HORIZONTAL.value());
        this.verticalScrollAction = preference.getInt(KEY_CAMERA_GESTURE_SCROLL_VERTICAL, GestureAction.DEFAULT_SCROLL_VERTICAL.value());
    }

    private GestureAction get(int which) {
        return GestureAction.fromValue(which);
    }

    public GestureAction getTapAction() {
        return get(tapAction);
    }

    public GestureAction getLongTapAction() {
        return get(longTapAction);
    }

    public GestureAction getPinchAction() {
        return get(pinchAction);
    }

    public GestureAction getHorizontalScrollAction() {
        return get(horizontalScrollAction);
    }

    public GestureAction getVerticalScrollAction() {
        return get(verticalScrollAction);
    }

}
