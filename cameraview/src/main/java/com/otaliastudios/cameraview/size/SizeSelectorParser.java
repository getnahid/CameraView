package com.otaliastudios.cameraview.size;

import android.content.SharedPreferences;
import android.content.res.TypedArray;

import androidx.annotation.NonNull;

import com.otaliastudios.cameraview.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses size selectors from XML attributes.
 */
public class SizeSelectorParser {

    private SizeSelector pictureSizeSelector;
    private SizeSelector videoSizeSelector;

    public static final String KEY_PICTURE_SIZE_MIN_WIDTH = "CameraView_cameraPictureSizeMinWidth";
    public static final String KEY_PICTURE_SIZE_MAX_WIDTH = "CameraView_cameraPictureSizeMaxWidth";
    public static final String KEY_PICTURE_SIZE_MIN_HEIGHT = "CameraView_cameraPictureSizeMinHeight";
    public static final String KEY_PICTURE_SIZE_MAX_HEIGHT = "CameraView_cameraPictureSizeMaxHeight";
    public static final String KEY_PICTURE_SIZE_MIN_AREA = "CameraView_cameraPictureSizeMinArea";
    public static final String KEY_PICTURE_SIZE_MAX_AREA = "CameraView_cameraPictureSizeMaxArea";
    public static final String KEY_PICTURE_SIZE_ASPECT_RATIO = "CameraView_cameraPictureSizeAspectRatio";

    public static final String KEY_VIDEO_SIZE_MIN_WIDTH = "CameraView_cameraVideoSizeMinWidth";
    public static final String KEY_VIDEO_SIZE_MAX_WIDTH = "CameraView_cameraVideoSizeMaxWidth";
    public static final String KEY_VIDEO_SIZE_MIN_HEIGHT = "CameraView_cameraVideoSizeMinHeight";
    public static final String KEY_VIDEO_SIZE_MAX_HEIGHT = "CameraView_cameraVideoSizeMaxHeight";
    public static final String KEY_VIDEO_SIZE_MIN_AREA = "CameraView_cameraVideoSizeMinArea";
    public static final String KEY_VIDEO_SIZE_MAX_AREA = "CameraView_cameraVideoSizeMaxArea";
    public static final String KEY_VIDEO_SIZE_ASPECT_RATIO = "CameraView_cameraVideoSizeAspectRatio";

    public SizeSelectorParser(SharedPreferences preference) {
        List<SizeSelector> pictureConstraints = new ArrayList<>(3);
        //if (array.hasValue(R.styleable.CameraView_cameraPictureSizeMinWidth)) {
            pictureConstraints.add(SizeSelectors.minWidth(preference.getInt(R.styleable.CameraView_cameraPictureSizeMinWidth, 0)));
        //}
        //if (array.hasValue(R.styleable.CameraView_cameraPictureSizeMaxWidth)) {
            pictureConstraints.add(SizeSelectors.maxWidth(array.getInteger(R.styleable.CameraView_cameraPictureSizeMaxWidth, 0)));
        //}
        //if (array.hasValue(R.styleable.CameraView_cameraPictureSizeMinHeight)) {
            pictureConstraints.add(SizeSelectors.minHeight(array.getInteger(R.styleable.CameraView_cameraPictureSizeMinHeight, 0)));
        //}
        //if (array.hasValue(R.styleable.CameraView_cameraPictureSizeMaxHeight)) {
            pictureConstraints.add(SizeSelectors.maxHeight(array.getInteger(R.styleable.CameraView_cameraPictureSizeMaxHeight, 0)));
        //}
        //if (array.hasValue(R.styleable.CameraView_cameraPictureSizeMinArea)) {
            pictureConstraints.add(SizeSelectors.minArea(array.getInteger(R.styleable.CameraView_cameraPictureSizeMinArea, 0)));
        //}
        //if (array.hasValue(R.styleable.CameraView_cameraPictureSizeMaxArea)) {
            pictureConstraints.add(SizeSelectors.maxArea(array.getInteger(R.styleable.CameraView_cameraPictureSizeMaxArea, 0)));
        //}
        //if (array.hasValue(R.styleable.CameraView_cameraPictureSizeAspectRatio)) {
            //noinspection ConstantConditions
            pictureConstraints.add(SizeSelectors.aspectRatio(AspectRatio.parse(array.getString(R.styleable.CameraView_cameraPictureSizeAspectRatio)), 0));
        //}

        if (array.getBoolean(R.styleable.CameraView_cameraPictureSizeSmallest, false)) pictureConstraints.add(SizeSelectors.smallest());
        if (array.getBoolean(R.styleable.CameraView_cameraPictureSizeBiggest, false)) pictureConstraints.add(SizeSelectors.biggest());
        pictureSizeSelector = !pictureConstraints.isEmpty() ?
                SizeSelectors.and(pictureConstraints.toArray(new SizeSelector[0])) :
                SizeSelectors.biggest();

        // Video size selector
        List<SizeSelector> videoConstraints = new ArrayList<>(3);
        //if (array.hasValue(R.styleable.CameraView_cameraVideoSizeMinWidth)) {
            videoConstraints.add(SizeSelectors.minWidth(array.getInteger(R.styleable.CameraView_cameraVideoSizeMinWidth, 0)));
        //}
        //if (array.hasValue(R.styleable.CameraView_cameraVideoSizeMaxWidth)) {
            videoConstraints.add(SizeSelectors.maxWidth(array.getInteger(R.styleable.CameraView_cameraVideoSizeMaxWidth, 0)));
        //}
        //if (array.hasValue(R.styleable.CameraView_cameraVideoSizeMinHeight)) {
            videoConstraints.add(SizeSelectors.minHeight(array.getInteger(R.styleable.CameraView_cameraVideoSizeMinHeight, 0)));
        //}
        //if (array.hasValue(R.styleable.CameraView_cameraVideoSizeMaxHeight)) {
            videoConstraints.add(SizeSelectors.maxHeight(array.getInteger(R.styleable.CameraView_cameraVideoSizeMaxHeight, 0)));
        //}
        //if (array.hasValue(R.styleable.CameraView_cameraVideoSizeMinArea)) {
            videoConstraints.add(SizeSelectors.minArea(array.getInteger(R.styleable.CameraView_cameraVideoSizeMinArea, 0)));
        //}
        //if (array.hasValue(R.styleable.CameraView_cameraVideoSizeMaxArea)) {
            videoConstraints.add(SizeSelectors.maxArea(array.getInteger(R.styleable.CameraView_cameraVideoSizeMaxArea, 0)));
        //}
        //if (array.hasValue(R.styleable.CameraView_cameraVideoSizeAspectRatio)) {
            //noinspection ConstantConditions
            videoConstraints.add(SizeSelectors.aspectRatio(AspectRatio.parse(array.getString(R.styleable.CameraView_cameraVideoSizeAspectRatio)), 0));
        //}
        if (array.getBoolean(R.styleable.CameraView_cameraVideoSizeSmallest, false)) videoConstraints.add(SizeSelectors.smallest());
        if (array.getBoolean(R.styleable.CameraView_cameraVideoSizeBiggest, false)) videoConstraints.add(SizeSelectors.biggest());
        videoSizeSelector = !videoConstraints.isEmpty() ?
                SizeSelectors.and(videoConstraints.toArray(new SizeSelector[0])) :
                SizeSelectors.biggest();
    }

    @NonNull
    public SizeSelector getPictureSizeSelector() {
        return pictureSizeSelector;
    }

    @NonNull
    public SizeSelector getVideoSizeSelector() {
        return videoSizeSelector;
    }

}
