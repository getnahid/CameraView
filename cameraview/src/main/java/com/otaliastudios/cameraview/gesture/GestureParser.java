package com.otaliastudios.cameraview.gesture;

import android.content.SharedPreferences;

/**
 * Parses gestures from XML attributes.
 */
public class GestureParser {
    private SharedPreferences preference;

    public static final String KEY_CAMERA_GESTURE_TAP = "CameraView_cameraGestureTap";
    public static final String KEY_CAMERA_GESTURE_LONG_TAP = "CameraView_cameraGestureLongTap";
    public static final String KEY_CAMERA_GESTURE_PINCH = "CameraView_cameraGesturePinch";
    public static final String KEY_CAMERA_GESTURE_SCROLL_HORIZONTAL = "CameraView_cameraGestureScrollHorizontal";
    public static final String KEY_CAMERA_GESTURE_SCROLL_VERTICAL = "CameraView_cameraGestureScrollVertical";

    public GestureParser(SharedPreferences preference) {
        this.preference = preference;
    }

    public GestureAction getTapAction() {
        int value = preference.getInt(KEY_CAMERA_GESTURE_TAP, GestureAction.DEFAULT_TAP.value());
        return GestureAction.fromValue(value);
    }

    public void setTapAction(int value) {
        preference.edit().putInt(KEY_CAMERA_GESTURE_TAP, value).apply();
    }

    public GestureAction getLongTapAction() {
        int value = preference.getInt(KEY_CAMERA_GESTURE_LONG_TAP, GestureAction.DEFAULT_LONG_TAP.value());
        return GestureAction.fromValue(value);
    }

    public void setLongTapAction(int value) {
        preference.edit().putInt(KEY_CAMERA_GESTURE_LONG_TAP, value).apply();
    }

    public GestureAction getPinchAction() {
        int value = preference.getInt(KEY_CAMERA_GESTURE_PINCH, GestureAction.DEFAULT_PINCH.value());
        return GestureAction.fromValue(value);
    }

    public void setPinchAction(int value) {
        preference.edit().putInt(KEY_CAMERA_GESTURE_PINCH, value).apply();
    }

    public GestureAction getHorizontalScrollAction() {
        int value = preference.getInt(KEY_CAMERA_GESTURE_SCROLL_HORIZONTAL, GestureAction.DEFAULT_SCROLL_HORIZONTAL.value());
        return GestureAction.fromValue(value);
    }

    public void setHorizontalScrollAction(int value) {
        preference.edit().putInt(KEY_CAMERA_GESTURE_SCROLL_HORIZONTAL, value).apply();
    }

    public GestureAction getVerticalScrollAction() {
        int value = preference.getInt(KEY_CAMERA_GESTURE_SCROLL_VERTICAL, GestureAction.DEFAULT_SCROLL_VERTICAL.value());
        return GestureAction.fromValue(value);
    }

    public void setVerticalScrollAction(int value) {
        preference.edit().putInt(KEY_CAMERA_GESTURE_SCROLL_VERTICAL, value).apply();
    }
}
