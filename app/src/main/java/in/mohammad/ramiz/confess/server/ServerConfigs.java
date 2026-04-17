package in.mohammad.ramiz.confess.server;

import in.mohammad.ramiz.confess.BuildConfig;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.util.concurrent.TimeUnit;

public class ServerConfigs {

    private static Retrofit retrofit;
    private static final String primaryUrl = BuildConfig.SERVER_URL;
    private static final String backupUrl = BuildConfig.BACKUP_URL;

    // once switched, app stays on backup until restart
    private static boolean usingBackup = false;

    private static final Interceptor failOverInterceptor = chain -> {
        Request request = chain.request();

        // Which base are we using right now?
        String base = usingBackup ? backupUrl : primaryUrl;

        HttpUrl newBase = HttpUrl.parse(base);
        if (newBase == null) throw new IllegalStateException("Invalid base URL: " + base);

        HttpUrl newUrl = request.url().newBuilder()
                .scheme(newBase.scheme())
                .host(newBase.host())
                .port(newBase.port())
                .encodedPath(newBase.encodedPath() + request.url().encodedPath().replaceFirst("^/", ""))
                .build();

        Request newRequest = request.newBuilder().url(newUrl).build();

        try {
            Response response = chain.proceed(newRequest);

            // If primary gives HTTP error (not network error), failover to backup
            if (!response.isSuccessful() && !usingBackup) {
                usingBackup = true;
                return chain.proceed(rebuildWithBackup(request)); // retry transparently
            }

            return response;

        } catch (Exception e) {
            if (!usingBackup) {
                usingBackup = true;
                try {
                    return chain.proceed(rebuildWithBackup(request)); // ✅ backup retry
                } catch (Exception ex) {
                    throw ex; // only now let Retrofit call onFailure()
                }
            }
            throw e; // already using backup and still failed → propagate
        }
    };

    private static Request rebuildWithBackup(Request originalRequest) {
        HttpUrl backupBase = HttpUrl.parse(backupUrl);
        if (backupBase == null) throw new IllegalStateException("Invalid backup URL: " + backupUrl);

        HttpUrl newUrl = originalRequest.url().newBuilder()
                .scheme(backupBase.scheme())
                .host(backupBase.host())
                .port(backupBase.port())
                .encodedPath(backupBase.encodedPath() + originalRequest.url().encodedPath().replaceFirst("^/", ""))
                .build();

        return originalRequest.newBuilder().url(newUrl).build();
    }

    public static Retrofit getInstance() {
        if (retrofit == null) {
            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(failOverInterceptor)
                    // ⏳ Fail fast
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .callTimeout(10, TimeUnit.SECONDS) // hard cap for any call
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(primaryUrl) // always start with primary
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;

    }

    public static boolean isUsingBackup() {
        return usingBackup;
    }
}
