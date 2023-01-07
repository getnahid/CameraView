package com.otaliastudios.cameraview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.os.Build;
import android.os.Handler;

import com.otaliastudios.cameraview.controls.Facing;
import com.otaliastudios.cameraview.engine.mappers.Camera1Mapper;
import com.otaliastudios.cameraview.internal.ExifHelper;
import com.otaliastudios.cameraview.internal.WorkerHandler;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.exifinterface.media.ExifInterface;

import com.otaliastudios.cameraview.controls.Facing;
import com.otaliastudios.cameraview.engine.mappers.Camera1Mapper;
import com.otaliastudios.cameraview.internal.ExifHelper;
import com.otaliastudios.cameraview.internal.WorkerHandler;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static android.hardware.camera2.CameraCharacteristics.LENS_FACING;

/**
 * Static utilities for dealing with camera I/O, orientations, etc.
 */
@SuppressWarnings("unused")
public class CameraUtils {

    private final static String TAG = CameraUtils.class.getSimpleName();
    private final static CameraLogger LOG = CameraLogger.create(TAG);
    public static final String KEY_FRONT_CAMERA_SUPPORT_CAMERA2_API = "key_front_camera_support_camera2_api";
    public static final String KEY_BACK_CAMERA_SUPPORT_CAMERA2_API = "key_back_camera_support_camera2_api";
    public static final String KEY_FRONT_SUPPORT_HARDWARE_LEVEL_3 = "key_front_support_hardware_level_3";
    public static final String KEY_BACK_SUPPORT_HARDWARE_LEVEL_3 = "key_back_support_hardware_level_3";
    /**
     * Determines whether the device has valid camera sensors, so the library
     * can be used.
     *
     * @param context a valid Context
     * @return whether device has cameras
     */
    @SuppressWarnings("WeakerAccess")
    public static boolean hasCameras(@NonNull Context context) {
        PackageManager manager = context.getPackageManager();
        // There's also FEATURE_CAMERA_EXTERNAL , should we support it?
        return manager.hasSystemFeature(PackageManager.FEATURE_CAMERA)
                || manager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT);
    }


    /**
     * Determines whether the device has a valid camera sensor with the given
     * Facing value, so that a session can be started.
     *
     * @param context a valid context
     * @param facing either {@link Facing#BACK} or {@link Facing#FRONT}
     * @return true if such sensor exists
     */
    public static boolean hasCameraFacing(@SuppressWarnings("unused") @NonNull Context context,
                                          @NonNull Facing facing) {
        try{
            int internal = Camera1Mapper.get().mapFacing(facing);
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            for (int i = 0, count = Camera.getNumberOfCameras(); i < count; i++) {
                Camera.getCameraInfo(i, cameraInfo);
                if (cameraInfo.facing == internal) return true;
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return true;
        }
    }


    /**
     * Simply writes the given data to the given file. It is done synchronously. If you are
     * running on the UI thread, please use {@link #writeToFile(byte[], File, FileCallback)}
     * and pass a file callback.
     *
     * If any error is encountered, this returns null.
     *
     * @param data the data to be written
     * @param file the file to write into
     * @return the source file, or null if error
     */
    @SuppressWarnings("WeakerAccess")
    @Nullable
    @WorkerThread
    @SuppressLint("NewApi")
    public static File writeToFile(@NonNull final byte[] data, @NonNull File file) {
        if (file.exists() && !file.delete()) return null;
        try (OutputStream stream = new BufferedOutputStream(new FileOutputStream(file))) {
            stream.write(data);
            stream.flush();
            return file;
        } catch (IOException e) {
            LOG.e("writeToFile:", "could not write file.", e);
            return null;
        }
    }


    /**
     * Writes the given data to the given file in a background thread, returning on the
     * original thread (typically the UI thread) once writing is done.
     * If some error is encountered, the {@link FileCallback} will return null instead of the
     * original file.
     *
     * @param data the data to be written
     * @param file the file to write into
     * @param callback a callback
     */
    @SuppressWarnings("WeakerAccess")
    public static void writeToFile(@NonNull final byte[] data,
                                   @NonNull final File file,
                                   @NonNull final FileCallback callback) {
        final Handler ui = new Handler();
        WorkerHandler.execute(new Runnable() {
            @Override
            public void run() {
                final File result = writeToFile(data, file);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onFileReady(result);
                    }
                });
            }
        });
    }

    /**
     * Decodes an input byte array and outputs a Bitmap that is ready to be displayed.
     * The difference with {@link android.graphics.BitmapFactory#decodeByteArray(byte[], int, int)}
     * is that this cares about orientation, reading it from the EXIF header.
     *
     * @param source a JPEG byte array
     * @return decoded bitmap or null if error is encountered
     */
    @SuppressWarnings("WeakerAccess")
    @Nullable
    @WorkerThread
    public static Bitmap decodeBitmap(@NonNull final byte[] source) {
        return decodeBitmap(source, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    /**
     * Decodes an input byte array and outputs a Bitmap that is ready to be displayed.
     * The difference with {@link android.graphics.BitmapFactory#decodeByteArray(byte[], int, int)}
     * is that this cares about orientation, reading it from the EXIF header.
     * This is executed in a background thread, and returns the result to the original thread.
     *
     * @param source a JPEG byte array
     * @param callback a callback to be notified
     */
    @SuppressWarnings("WeakerAccess")
    public static void decodeBitmap(@NonNull final byte[] source,
                                    @NonNull final BitmapCallback callback) {
        decodeBitmap(source, Integer.MAX_VALUE, Integer.MAX_VALUE, callback);
    }

    /**
     * Decodes an input byte array and outputs a Bitmap that is ready to be displayed.
     * The difference with {@link android.graphics.BitmapFactory#decodeByteArray(byte[], int, int)}
     * is that this cares about orientation, reading it from the EXIF header.
     * This is executed in a background thread, and returns the result to the original thread.
     *
     * The image is also downscaled taking care of the maxWidth and maxHeight arguments.
     *
     * @param source a JPEG byte array
     * @param maxWidth the max allowed width
     * @param maxHeight the max allowed height
     * @param callback a callback to be notified
     */
    @SuppressWarnings("WeakerAccess")
    public static void decodeBitmap(@NonNull final byte[] source,
                                    final int maxWidth,
                                    final int maxHeight,
                                    @NonNull final BitmapCallback callback) {
        decodeBitmap(source, maxWidth, maxHeight, new BitmapFactory.Options(), callback);
    }

    /**
     * Decodes an input byte array and outputs a Bitmap that is ready to be displayed.
     * The difference with {@link android.graphics.BitmapFactory#decodeByteArray(byte[], int, int)}
     * is that this cares about orientation, reading it from the EXIF header.
     * This is executed in a background thread, and returns the result to the original thread.
     *
     * The image is also downscaled taking care of the maxWidth and maxHeight arguments.
     *
     * @param source a JPEG byte array
     * @param maxWidth the max allowed width
     * @param maxHeight the max allowed height
     * @param options the options to be passed to decodeByteArray
     * @param callback a callback to be notified
     */
    @SuppressWarnings("WeakerAccess")
    public static void decodeBitmap(@NonNull final byte[] source,
                                    final int maxWidth,
                                    final int maxHeight,
                                    @NonNull final BitmapFactory.Options options,
                                    @NonNull final BitmapCallback callback) {
        decodeBitmap(source, maxWidth, maxHeight, options, -1, callback);
    }

    static void decodeBitmap(@NonNull final byte[] source,
                             final int maxWidth,
                             final int maxHeight,
                             @NonNull final BitmapFactory.Options options,
                             final int rotation,
                             @NonNull final BitmapCallback callback) {
        final Handler ui = new Handler();
        WorkerHandler.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap bitmap = decodeBitmap(source, maxWidth, maxHeight, options, rotation);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onBitmapReady(bitmap);
                    }
                });
            }
        });
    }

    /**
     * Decodes an input byte array and outputs a Bitmap that is ready to be displayed.
     * The difference with {@link android.graphics.BitmapFactory#decodeByteArray(byte[], int, int)}
     * is that this cares about orientation, reading it from the EXIF header.
     *
     * The image is also downscaled taking care of the maxWidth and maxHeight arguments.
     *
     * @param source a JPEG byte array
     * @param maxWidth the max allowed width
     * @param maxHeight the max allowed height
     * @return decoded bitmap or null if error is encountered
     */
    @SuppressWarnings("SameParameterValue")
    @Nullable
    @WorkerThread
    public static Bitmap decodeBitmap(@NonNull byte[] source, int maxWidth, int maxHeight) {
        return decodeBitmap(source, maxWidth, maxHeight, new BitmapFactory.Options());
    }

    /**
     * Decodes an input byte array and outputs a Bitmap that is ready to be displayed.
     * The difference with {@link android.graphics.BitmapFactory#decodeByteArray(byte[], int, int)}
     * is that this cares about orientation, reading it from the EXIF header.
     *
     * The image is also downscaled taking care of the maxWidth and maxHeight arguments.
     *
     * @param source a JPEG byte array
     * @param maxWidth the max allowed width
     * @param maxHeight the max allowed height
     * @param options the options to be passed to decodeByteArray
     * @return decoded bitmap or null if error is encountered
     */
    @SuppressWarnings("WeakerAccess")
    @Nullable
    @WorkerThread
    public static Bitmap decodeBitmap(@NonNull byte[] source,
                                      int maxWidth,
                                      int maxHeight,
                                      @NonNull BitmapFactory.Options options) {
        return decodeBitmap(source, maxWidth, maxHeight, options, -1);
    }

    // Null means we got OOM
    // Ignores flipping, but it should be super rare.
    @SuppressWarnings("TryFinallyCanBeTryWithResources")
    @Nullable
    private static Bitmap decodeBitmap(@NonNull byte[] source,
                                       int maxWidth,
                                       int maxHeight,
                                       @NonNull BitmapFactory.Options options,
                                       int rotation) {
        if (maxWidth <= 0) maxWidth = Integer.MAX_VALUE;
        if (maxHeight <= 0) maxHeight = Integer.MAX_VALUE;
        int orientation;
        boolean flip;
        if (rotation == -1) {
            InputStream stream = null;
            try {
                // http://sylvana.net/jpegcrop/exif_orientation.html
                stream = new ByteArrayInputStream(source);
                ExifInterface exif = new ExifInterface(stream);
                int exifOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL);
                orientation = ExifHelper.getOrientation(exifOrientation);
                flip = exifOrientation == ExifInterface.ORIENTATION_FLIP_HORIZONTAL ||
                        exifOrientation == ExifInterface.ORIENTATION_FLIP_VERTICAL ||
                        exifOrientation == ExifInterface.ORIENTATION_TRANSPOSE ||
                        exifOrientation == ExifInterface.ORIENTATION_TRANSVERSE;
                LOG.i("decodeBitmap:", "got orientation from EXIF.", orientation);
            } catch (IOException e) {
                LOG.e("decodeBitmap:", "could not get orientation from EXIF.", e);
                orientation = 0;
                flip = false;
            } finally {
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (Exception ignored) { }
                }
            }
        } else {
            orientation = rotation;
            flip = false;
            LOG.i("decodeBitmap:", "got orientation from constructor.", orientation);
        }

        Bitmap bitmap;
        try {
            if (maxWidth < Integer.MAX_VALUE || maxHeight < Integer.MAX_VALUE) {
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(source, 0, source.length, options);

                int outHeight = options.outHeight;
                int outWidth = options.outWidth;
                if (orientation % 180 != 0) {
                    //noinspection SuspiciousNameCombination
                    outHeight = options.outWidth;
                    //noinspection SuspiciousNameCombination
                    outWidth = options.outHeight;
                }

                options.inSampleSize = computeSampleSize(outWidth, outHeight, maxWidth, maxHeight);
                options.inJustDecodeBounds = false;
                bitmap = BitmapFactory.decodeByteArray(source, 0, source.length, options);
            } else {
                bitmap = BitmapFactory.decodeByteArray(source, 0, source.length);
            }

            if (orientation != 0 || flip) {
                Matrix matrix = new Matrix();
                matrix.setRotate(orientation);
                Bitmap temp = bitmap;
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                        bitmap.getHeight(), matrix, true);
                temp.recycle();
            }
        } catch (OutOfMemoryError e) {
            bitmap = null;
        }
        return bitmap;
    }

    private static int computeSampleSize(int width, int height, int maxWidth, int maxHeight) {
        // https://developer.android.com/topic/performance/graphics/load-bitmap.html
        int inSampleSize = 1;
        if (height > maxHeight || width > maxWidth) {
            while ((height / inSampleSize) >= maxHeight
                    || (width / inSampleSize) >= maxWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    public static boolean isUriString(String path) {
        if (path != null && path.contains("://")) {
            return true;
        }

        return false;
    }

    public static boolean isCamera2ApiSupportedDevice() {
//        if(Build.MANUFACTURER.equals("samsung")) {
//            return false;
//        } else
        if (Build.MODEL.equals("Mi A1") || Build.MODEL.equals("MI 8 Lite") || Build.MODEL.equals("Mi A2 Lite") || Build.MODEL.equals("Redmi Note 6 Pro") || Build.MODEL.equals("Redmi Note 5 Pro")
                || Build.MODEL.equals("Mi A2") || Build.MODEL.equals("Redmi S2") || Build.MODEL.equals("Redmi Y2") || Build.MODEL.equals("Redmi 6 Pro") || Build.MODEL.equals("MI6")
                || Build.MODEL.equals("MI MAX 3") || Build.MODEL.equals("MI Note 3") || Build.MODEL.equals("MIX 2") || Build.MODEL.equals("MI 6X") || Build.MODEL.equals("Redmi 7")) {
            return false;
        } else if (Build.MODEL.contains("ZenFone Max Pro M1") || Build.MODEL.contains("ZenFone Max Pro M2") || Build.MODEL.contains("ZenFone Max M2") || Build.MODEL.contains("ZenFone 5 Lite")) {
            return false;
        } else if (Build.MODEL.equals("realme 2") || Build.MODEL.equals("realme 2 Pro") || Build.MODEL.equals("CPH1893") || Build.MODEL.equals("R15 Pro") || Build.MODEL.equals("realme C1")) {
            return false;
        } else if (Build.MODEL.equals("vivo 1804") || Build.MODEL.equals("vivo 1723") || Build.MODEL.equals("vivo 1727") || Build.MODEL.equals("vivo 1725")) {
            return false;
        } else if (Build.MODEL.equals("moto g(6) play") || Build.MODEL.equals("Moto Z (2) Play") || Build.MODEL.equals("Moto X (4) (payton)")) {
            return false;
        }

        return true;
    }

    public static boolean isGLSurfaceSupported() {
        if (Build.MODEL.toLowerCase().contains("k11".toLowerCase())
                || Build.MODEL.equalsIgnoreCase("LG K11")
                || Build.MODEL.equalsIgnoreCase("LG X charge")
                || Build.MODEL.equalsIgnoreCase("LG K10 Power") || Build.MODEL.equalsIgnoreCase("LG G Pad F2 8.0")) {
            return false;
        } else if (Build.MODEL.equalsIgnoreCase("Galaxy Core2") || Build.MODEL.equalsIgnoreCase("Galaxy J7")
                || Build.MODEL.equalsIgnoreCase("Galaxy A2 Core") || Build.MODEL.equalsIgnoreCase("Galaxy J7 Neo")
                || Build.MODEL.equalsIgnoreCase("Galaxy J2 Core") || Build.MODEL.equalsIgnoreCase("Galaxy S8")
                || Build.MODEL.equalsIgnoreCase("Galaxy A20")) {
            return false;
        } else if (Build.MODEL.equalsIgnoreCase("CPH1729") || Build.MODEL.equalsIgnoreCase("F11 Pro")
                || Build.MODEL.equalsIgnoreCase("CPH1723")) {
            return false;
        } else if (Build.MODEL.equalsIgnoreCase("Xperia XA1 Plus")) {
            return false;
        } else if (Build.MODEL.equalsIgnoreCase("LG Tribute Dynasty")) {
            return false;
        } else if (Build.MODEL.equalsIgnoreCase("Galaxy S6 edge") || Build.MODEL.equalsIgnoreCase("Galaxy Tab A")
                || Build.MODEL.equalsIgnoreCase("Galaxy A10e") || Build.MODEL.equalsIgnoreCase("Galaxy J7(2016)")
                || Build.MODEL.equalsIgnoreCase("Galaxy J3 V") || Build.MODEL.equalsIgnoreCase("Galaxy J2 Prime")
                || Build.MODEL.equalsIgnoreCase("Galaxy A10") || Build.MODEL.equalsIgnoreCase("Galaxy J7 Prime2")
                || Build.MODEL.equalsIgnoreCase("Galaxy S10+") || Build.MODEL.equalsIgnoreCase("Galaxy S7 edge")
                || Build.MODEL.equalsIgnoreCase("Galaxy S7")) {
            return false;
        } else if (Build.MODEL.equalsIgnoreCase("Honor 8X") || Build.MODEL.equalsIgnoreCase("HUAWEI Y9 2018")
                || Build.MODEL.equalsIgnoreCase("GR3 Smart touch")) {
            return false;
        } else if (Build.MODEL.equalsIgnoreCase("K5 Note")) {
            return false;
        } else return Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1;
    }

    private static boolean hasCameraSupport(Context context, String cameraId, int cameraSupportMetaData) {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            int support = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);

            if (support == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY)
                Log.d(TAG, "Camera " + cameraId + " has LEGACY Camera2 support");
            else if (support == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED)
                Log.d(TAG, "Camera " + cameraId + " has LIMITED Camera2 support");
            else if (support == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL)
                Log.d(TAG, "Camera " + cameraId + " has FULL Camera2 support");
            else if (support == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3)
                Log.d(TAG, "Camera " + cameraId + " has LEVEL_3 Camera2 support");
            else
                Log.d(TAG, "Camera " + cameraId + " has unknown Camera2 support?!");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                //return support == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL || support == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3;
                // This is because some LEVEL_3 devices does not support camera2 api
                return support == cameraSupportMetaData;
            } else {
                return support == cameraSupportMetaData;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static void checkCamera2Support(Context context) {
        try {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            String frontCameraId = getIdCamera2(context, CameraCharacteristics.LENS_FACING_FRONT);
            if (frontCameraId == null) {
                frontCameraId = getIdCamera1(Camera.CameraInfo.CAMERA_FACING_FRONT);
            }

            String backCameraId = getIdCamera2(context, CameraCharacteristics.LENS_FACING_BACK);
            if (backCameraId == null) {
                backCameraId = getIdCamera1(Camera.CameraInfo.CAMERA_FACING_BACK);
            }

            if (!isSamsungDevice() && hasCameraSupport(context, frontCameraId, CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL)) {
                preferences.edit().putBoolean(KEY_FRONT_CAMERA_SUPPORT_CAMERA2_API, true).apply();
            }

            if (!isSamsungDevice() && hasCameraSupport(context, backCameraId, CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL)) {
                preferences.edit().putBoolean(KEY_BACK_CAMERA_SUPPORT_CAMERA2_API, true).apply();
            }

            if (hasCameraSupport(context, frontCameraId, CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3)) {
                preferences.edit().putBoolean(KEY_FRONT_SUPPORT_HARDWARE_LEVEL_3, true).apply();
            }

            if (hasCameraSupport(context, backCameraId, CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3)) {
                preferences.edit().putBoolean(KEY_BACK_SUPPORT_HARDWARE_LEVEL_3, true).apply();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean isSamsungDevice() {
        return Build.MANUFACTURER.equals("samsung");
    }

    public static boolean isVivoDevice() {
        return Build.MANUFACTURER.contains("vivo");
    }

    public static boolean isOppoDevice() {
        return Build.MANUFACTURER.contains("OPPO");
    }

    private static boolean isFrontCameraSupportsFullCamera2api(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getBoolean(KEY_FRONT_CAMERA_SUPPORT_CAMERA2_API, false);
    }

    private static boolean isBackCameraSupportsFullCamera2api(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getBoolean(KEY_BACK_CAMERA_SUPPORT_CAMERA2_API, false);
    }

    private static boolean isFrontCameraHardwareLevel3Supported(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getBoolean(KEY_FRONT_SUPPORT_HARDWARE_LEVEL_3, false);
    }

    private static boolean isBackCameraHardwareLevel3Supported(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getBoolean(KEY_BACK_SUPPORT_HARDWARE_LEVEL_3, false);
    }

    public static boolean isFrontCameraSupportCamera2api(Context context) {
        return CameraUtils.isFrontCameraSupportsFullCamera2api(context)
                || CameraUtils.isFrontCameraHardwareLevel3Supported(context);
    }

    public static boolean isBackCameraSupportCamera2api(Context context) {
        return CameraUtils.isBackCameraSupportsFullCamera2api(context)
                || CameraUtils.isBackCameraHardwareLevel3Supported(context);
    }

    private static String getIdCamera2(Context context, int cameraCharacteristicsId) {
        try {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = manager.getCameraCharacteristics(id);
                //Seek frontal camera.
                if (cameraCharacteristics.get(LENS_FACING) == cameraCharacteristicsId) {
                    Log.i(TAG, "Camara face id " + id);
                    return id;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        return null;
    }

    private static String getIdCamera1(int facing) {
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        int cameraCount = Camera.getNumberOfCameras();
        for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
            Camera.getCameraInfo(camIdx, cameraInfo);
            if (cameraInfo.facing == facing) {
                return camIdx + "";
            }
        }

        return null;
    }
}
