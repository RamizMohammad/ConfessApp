package in.mohammad.ramiz.confess.storage;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import in.mohammad.ramiz.confess.BuildConfig;

public class CloudinaryStorageManager {

    private static boolean isInitialized = false;

    // Initialize Cloudinary only once
    public static void init(Context context) {
        if (isInitialized) return;

        Map<String, Object> config = new HashMap<>();
        config.put("cloud_name", BuildConfig.CLOUD_SAVE_NAME);
        config.put("api_key", BuildConfig.CLOUD_SAVE_API);
        config.put("api_secret", BuildConfig.CLOUD_SAVE_KEY);

        try {
            MediaManager.init(context, config);
            isInitialized = true;
        } catch (Exception e) {
            Log.e("CloudinaryInit", "Cloudinary init failed: " + e.getMessage());
        }
    }

    // Upload from drawable/raw resource
    public static void uploadResourceAndGetUrl(Context context, int resId, UploadResultCallback callback) {
        try {
            File tempFile = new File(context.getCacheDir(), "temp_avatar.jpg");
            try (
                    InputStream inputStream = context.getResources().openRawResource(resId);
                    OutputStream outputStream = new FileOutputStream(tempFile)
            ) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }
            }

            Uri fileUri = Uri.fromFile(tempFile);
            uploadImage(context, fileUri, callback);

        } catch (Exception e) {
            callback.onFailure("Failed to process resource: " + e.getMessage());
        }
    }

    // Upload an image to Cloudinary and get the URL
    public static void uploadImage(Context context, Uri imageUri, UploadResultCallback callback) {
        init(context);

        if (imageUri == null) {
            callback.onFailure("Image URI is null");
            return;
        }

        String folderId = UUID.randomUUID().toString();  // Folder name
        String publicId = folderId + "/" + UUID.randomUUID().toString(); // File ID

        MediaManager.get().upload(imageUri)
                .option("public_id", publicId)
                .option("folder", folderId)
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) {
                        Log.d("Cloudinary", "Upload started: " + requestId);
                    }

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {
                        Log.d("Cloudinary", "Upload progress: " + bytes + "/" + totalBytes);
                    }

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        String url = resultData.get("secure_url").toString();
                        Log.d("Cloudinary", "Upload success: " + url);
                        callback.onSuccess(url);
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        Log.e("Cloudinary", "Upload error: " + error.getDescription());
                        callback.onFailure(error.getDescription());
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {
                        Log.e("Cloudinary", "Upload rescheduled: " + error.getDescription());
                        callback.onFailure("Rescheduled: " + error.getDescription());
                    }
                }).dispatch();
    }

    // Callback interface
    public interface UploadResultCallback {
        void onSuccess(String downloadUrl);
        void onFailure(String message);
    }
}