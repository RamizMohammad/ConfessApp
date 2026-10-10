package in.mohammad.ramiz.confess.ui.util;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Turns a picked photo into a small square JPEG before upload:
 * decode (EXIF orientation applied) → centre square → ≤ 768 px → JPEG 88.
 * Re-encoding also drops EXIF (location, camera) — the server strips it again.
 */
public final class PhotoPrep {

    public static final int MAX_SIDE = 768;

    public interface Callback {
        void onReady(@NonNull Bitmap preview, @NonNull byte[] jpeg);

        void onFailed();
    }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private PhotoPrep() { }

    public static void prepare(@NonNull ContentResolver resolver, @NonNull Uri uri, @NonNull Callback cb) {
        IO.execute(() -> {
            Bitmap square = null;
            byte[] jpeg = null;
            try {
                ImageDecoder.Source src = ImageDecoder.createSource(resolver, uri);
                Bitmap decoded = ImageDecoder.decodeBitmap(src, (decoder, info, s) -> {
                    int w = info.getSize().getWidth(), h = info.getSize().getHeight();
                    int shortSide = Math.min(w, h);
                    if (shortSide > MAX_SIDE) {                  // decode smaller straight away
                        float k = (float) MAX_SIDE / shortSide;
                        decoder.setTargetSize(Math.max(1, Math.round(w * k)), Math.max(1, Math.round(h * k)));
                    }
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);   // compress() needs software
                });
                int side = Math.min(decoded.getWidth(), decoded.getHeight());
                square = Bitmap.createBitmap(decoded, (decoded.getWidth() - side) / 2,
                        (decoded.getHeight() - side) / 2, side, side);
                if (side > MAX_SIDE) square = Bitmap.createScaledBitmap(square, MAX_SIDE, MAX_SIDE, true);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                square.compress(Bitmap.CompressFormat.JPEG, 88, out);
                jpeg = out.toByteArray();
            } catch (Exception | OutOfMemoryError e) {
                square = null;
            }
            final Bitmap preview = square;
            final byte[] bytes = jpeg;
            MAIN.post(() -> {
                if (preview != null && bytes != null) cb.onReady(preview, bytes);
                else cb.onFailed();
            });
        });
    }

    /** True for an uploaded-photo avatar value (vs an aura preset id). */
    public static boolean isPhoto(@Nullable String avatar) {
        return avatar != null && avatar.startsWith("https://");
    }
}
