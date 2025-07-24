package in.mohammad.ramiz.confess.storage;

import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.RawRes;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.UUID;

import in.mohammad.ramiz.confess.BuildConfig;

public class CloudinaryStorageManager {

    private static Cloudinary cloudinaryInstance;

    // Initialize Cloudinary once
    public static void init() {
        if (cloudinaryInstance == null) {
            cloudinaryInstance = new Cloudinary(ObjectUtils.asMap(
                    "cloud_name", BuildConfig.CLOUD_SAVE_NAME,
                    "api_key", BuildConfig.CLOUD_SAVE_API,
                    "api_secret", BuildConfig.CLOUD_SAVE_KEY
            ));
        }
    }

    // Upload image using Uri
    public static void uploadImage(Context context, Uri imageUri, UploadResultCallback callback) {
        init();

        if (cloudinaryInstance == null) {
            callback.onFailure("Cloudinary not initialized");
            return;
        }

        if (imageUri == null) {
            callback.onFailure("Image URI is null");
            return;
        }

        new AsyncTask<Void, Void, String>() {
            Exception exception;

            @Override
            protected String doInBackground(Void... voids) {
                try {
                    File file = createTempFileFromUri(context, imageUri);
                    String folderId = UUID.randomUUID().toString();
                    String publicId = UUID.randomUUID().toString();

                    Map uploadResult = cloudinaryInstance.uploader().upload(file, ObjectUtils.asMap(
                            "folder", folderId,
                            "public_id", publicId,
                            "resource_type", "image"
                    ));
                    return (String) uploadResult.get("secure_url");

                } catch (Exception e) {
                    exception = e;
                    return null;
                }
            }

            @Override
            protected void onPostExecute(String resultUrl) {
                if (exception != null) {
                    callback.onFailure("Upload failed: " + exception.getMessage());
                } else {
                    callback.onSuccess(resultUrl);
                }
            }
        }.execute();
    }

    // Upload image using resource ID
    public static void uploadResourceAndGetUrl(Context context, @RawRes int resId, UploadResultCallback callback) {
        Uri uri = getUriFromResId(context, resId);
        uploadImage(context, uri, callback);
    }

    // Convert resId to Uri
    public static Uri getUriFromResId(Context context, @RawRes int resId) {
        return Uri.parse("android.resource://" + context.getPackageName() + "/" + resId);
    }

    // Helper: convert Uri to File
    private static File createTempFileFromUri(Context context, Uri uri) throws Exception {
        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        String fileName = "upload_" + System.currentTimeMillis() + ".jpg";
        File tempFile = new File(context.getCacheDir(), fileName);
        OutputStream outputStream = new FileOutputStream(tempFile);

        byte[] buffer = new byte[4096];
        int bytesRead;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }

        outputStream.close();
        inputStream.close();
        return tempFile;
    }

    // Upload callback interface
    public interface UploadResultCallback {
        void onSuccess(String downloadUrl);
        void onFailure(String message);
    }
}