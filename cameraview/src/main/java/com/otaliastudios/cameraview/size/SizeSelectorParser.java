package com.otaliastudios.cameraview.size;

import android.content.SharedPreferences;
import android.media.CamcorderProfile;
import android.os.Build;

import androidx.annotation.NonNull;

import com.otaliastudios.cameraview.controls.Engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses size selectors from XML attributes.
 */
public class SizeSelectorParser {

    private SizeSelector pictureSizeSelector;
    private SizeSelector videoSizeSelector;
    private SharedPreferences preference;

    public static final String KEY_PICTURE_SIZE_MIN_WIDTH = "CameraView_cameraPictureSizeMinWidth";
    public static final String KEY_PICTURE_SIZE_MAX_WIDTH = "CameraView_cameraPictureSizeMaxWidth";
    public static final String KEY_PICTURE_SIZE_MIN_HEIGHT = "CameraView_cameraPictureSizeMinHeight";
    public static final String KEY_PICTURE_SIZE_MAX_HEIGHT = "CameraView_cameraPictureSizeMaxHeight";
    public static final String KEY_PICTURE_SIZE_MIN_AREA = "CameraView_cameraPictureSizeMinArea";
    public static final String KEY_PICTURE_SIZE_MAX_AREA = "CameraView_cameraPictureSizeMaxArea";
    public static final String KEY_PICTURE_SIZE_ASPECT_RATIO = "CameraView_cameraPictureSizeAspectRatio";
    public static final String KEY_PICTURE_SIZE_SMALLEST = "CameraView_cameraPictureSizeSmallest";
    public static final String KEY_PICTURE_SIZE_BIGGEST = "CameraView_cameraPictureSizeBiggest";

    public static final String KEY_VIDEO_SIZE_MIN_WIDTH = "CameraView_cameraVideoSizeMinWidth";
    public static final String KEY_VIDEO_SIZE_MAX_WIDTH = "CameraView_cameraVideoSizeMaxWidth";
    public static final String KEY_VIDEO_SIZE_MIN_HEIGHT = "CameraView_cameraVideoSizeMinHeight";
    public static final String KEY_VIDEO_SIZE_MAX_HEIGHT = "CameraView_cameraVideoSizeMaxHeight";
    public static final String KEY_VIDEO_SIZE_MIN_AREA = "CameraView_cameraVideoSizeMinArea";
    public static final String KEY_VIDEO_SIZE_MAX_AREA = "CameraView_cameraVideoSizeMaxArea";
    public static final String KEY_VIDEO_SIZE_ASPECT_RATIO = "CameraView_cameraVideoSizeAspectRatio";
    public static final String KEY_VIDEO_SIZE_SMALLEST = "CameraView_cameraVideoSizeSmallest";
    public static final String KEY_VIDEO_SIZE_BIGGEST = "CameraView_cameraVideoSizeBiggest";

    private int defaultWidth;
    private int defaultHeight;

    public SizeSelectorParser(SharedPreferences preference, int cameraId, Engine mEngine) {
        this.preference = preference;

        getPictureSizeSelector();
        getVideoSizeSelector();

        if (mEngine == Engine.CAMERA2) {
            defaultWidth = 0;
            defaultHeight = 0;
        } else {
            CamcorderProfile profile = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_HIGH);
            defaultWidth = profile.videoFrameHeight;
            defaultHeight = profile.videoFrameWidth;
        }
    }

    public int getVideoSizeMaxWidth() {
        return preference.getInt(KEY_VIDEO_SIZE_MAX_WIDTH, defaultWidth);
    }

    public void setVideoSizeMaxWidth(int width) {
        preference.edit().putInt(KEY_VIDEO_SIZE_MAX_WIDTH, width).apply();
    }

    public int getVideoSizeMaxHeight() {
        return preference.getInt(KEY_VIDEO_SIZE_MAX_HEIGHT, defaultHeight);
    }

    public void setVideoSizeMaxHeight(int height) {
        preference.edit().putInt(KEY_VIDEO_SIZE_MAX_HEIGHT, height).apply();
    }

    @NonNull
    public SizeSelector getPictureSizeSelector() {
        List<SizeSelector> pictureConstraints = new ArrayList<>(3);
        if (preference.getInt(KEY_PICTURE_SIZE_MIN_WIDTH, 0) != 0) {
            pictureConstraints.add(SizeSelectors.minWidth(preference.getInt(KEY_PICTURE_SIZE_MIN_WIDTH, 0)));
        }
        if (preference.getInt(KEY_PICTURE_SIZE_MAX_WIDTH, 0) != 0) {
            pictureConstraints.add(SizeSelectors.maxWidth(preference.getInt(KEY_PICTURE_SIZE_MAX_WIDTH, 0)));
        }
        if (preference.getInt(KEY_PICTURE_SIZE_MIN_HEIGHT, 0) != 0) {
            pictureConstraints.add(SizeSelectors.minHeight(preference.getInt(KEY_PICTURE_SIZE_MIN_HEIGHT, 0)));
        }
        if (preference.getInt(KEY_PICTURE_SIZE_MAX_HEIGHT, 0) != 0) {
            pictureConstraints.add(SizeSelectors.maxHeight(preference.getInt(KEY_PICTURE_SIZE_MAX_HEIGHT, 0)));
        }
        if (preference.getInt(KEY_PICTURE_SIZE_MIN_AREA, 0) != 0) {
            pictureConstraints.add(SizeSelectors.minArea(preference.getInt(KEY_PICTURE_SIZE_MIN_AREA, 0)));
        }
        if (preference.getInt(KEY_PICTURE_SIZE_MAX_AREA, 0) != 0) {
            pictureConstraints.add(SizeSelectors.maxArea(preference.getInt(KEY_PICTURE_SIZE_MAX_AREA, 0)));
        }
        if (preference.getString(KEY_PICTURE_SIZE_ASPECT_RATIO, null) != null) {
            //noinspection ConstantConditions
            pictureConstraints.add(SizeSelectors.aspectRatio(AspectRatio.parse(preference.getString(KEY_PICTURE_SIZE_ASPECT_RATIO, null)), 0));
        }

        if (preference.getBoolean(KEY_PICTURE_SIZE_SMALLEST, false))
            pictureConstraints.add(SizeSelectors.smallest());
        if (preference.getBoolean(KEY_PICTURE_SIZE_BIGGEST, false))
            pictureConstraints.add(SizeSelectors.biggest());
        pictureSizeSelector = !pictureConstraints.isEmpty() ?
                SizeSelectors.and(pictureConstraints.toArray(new SizeSelector[0])) :
                SizeSelectors.biggest();
        return pictureSizeSelector;
    }

    @NonNull
    public SizeSelector getVideoSizeSelector() {
        // Video size selector
        List<SizeSelector> videoConstraints = new ArrayList<>(3);
        if (preference.getInt(KEY_VIDEO_SIZE_MIN_WIDTH, 0) != 0) {
            videoConstraints.add(SizeSelectors.minWidth(preference.getInt(KEY_VIDEO_SIZE_MIN_WIDTH, 0)));
        }
        if (preference.getInt(KEY_VIDEO_SIZE_MAX_WIDTH, defaultWidth) != 0) {
            videoConstraints.add(SizeSelectors.maxWidth(preference.getInt(KEY_VIDEO_SIZE_MAX_WIDTH, defaultWidth)));
        }
        if (preference.getInt(KEY_VIDEO_SIZE_MIN_HEIGHT, 0) != 0) {
            videoConstraints.add(SizeSelectors.minHeight(preference.getInt(KEY_VIDEO_SIZE_MIN_HEIGHT, 0)));
        }
        if (preference.getInt(KEY_VIDEO_SIZE_MAX_HEIGHT, defaultHeight) != 0) {
            videoConstraints.add(SizeSelectors.maxHeight(preference.getInt(KEY_VIDEO_SIZE_MAX_HEIGHT, defaultHeight)));
        }
        if (preference.getInt(KEY_VIDEO_SIZE_MIN_AREA, 0) != 0) {
            videoConstraints.add(SizeSelectors.minArea(preference.getInt(KEY_VIDEO_SIZE_MIN_AREA, 0)));
        }
        if (preference.getInt(KEY_VIDEO_SIZE_MAX_AREA, 0) != 0) {
            videoConstraints.add(SizeSelectors.maxArea(preference.getInt(KEY_VIDEO_SIZE_MAX_AREA, 0)));
        }
        if (preference.getString(KEY_VIDEO_SIZE_ASPECT_RATIO, null) != null) {
            //noinspection ConstantConditions
            videoConstraints.add(SizeSelectors.aspectRatio(AspectRatio.parse(preference.getString(KEY_VIDEO_SIZE_ASPECT_RATIO, null)), 0));
        }
        if (preference.getBoolean(KEY_VIDEO_SIZE_SMALLEST, false))
            videoConstraints.add(SizeSelectors.smallest());
        if (preference.getBoolean(KEY_VIDEO_SIZE_BIGGEST, false))
            videoConstraints.add(SizeSelectors.biggest());
        videoSizeSelector = !videoConstraints.isEmpty() ?
                SizeSelectors.and(videoConstraints.toArray(new SizeSelector[0])) :
                SizeSelectors.biggest();

        return videoSizeSelector;
    }

}
