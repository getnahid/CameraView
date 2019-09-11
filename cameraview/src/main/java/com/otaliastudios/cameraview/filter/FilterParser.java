package com.otaliastudios.cameraview.filter;

import android.content.SharedPreferences;
import android.content.res.TypedArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.otaliastudios.cameraview.R;

/**
 * Parses filters from XML attributes.
 */
public class FilterParser {

    private Filter filter = null;
    public static final String KEY_CAMERA_FILTER = "CameraView_cameraFilter";

    public FilterParser(SharedPreferences preference) {
        String filterName = preference.getString(KEY_CAMERA_FILTER, null);
        try {
            //noinspection ConstantConditions
            Class<?> filterClass = Class.forName(filterName);
            filter = (Filter) filterClass.newInstance();
        } catch (Exception ignore) {
            filter = new NoFilter();
        }
    }

    @NonNull
    public Filter getFilter() {
        return filter;
    }
}
