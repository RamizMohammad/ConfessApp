package in.mohammad.ramiz.confess.debugmonitor;

import android.util.Log;

import java.io.IOException;

import in.mohammad.ramiz.confess.BuildConfig;
import okhttp3.*;

public class TelegramLogs {

    private static final OkHttpClient client = new OkHttpClient();

    public static void sendTelegramLog(String message) {
        String url = "https://api.telegram.org/bot" + BuildConfig.BOT_TOKEN + "/sendMessage";

        RequestBody formBody = new FormBody.Builder()
                .add("chat_id", BuildConfig.CHAT_ID)
                .add("text", "[ConfessBot Error Android]\n" + message)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override public void onResponse(Call call, Response response) {
                if (!response.isSuccessful()) {
                    Log.d("Telegram Logging Failed: ", ""+response);
                }
            }
        });
    }
}
