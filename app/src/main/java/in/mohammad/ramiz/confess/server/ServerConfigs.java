package in.mohammad.ramiz.confess.server;

import in.mohammad.ramiz.confess.BuildConfig;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ServerConfigs {

    private static Retrofit retrofit;

    public static Retrofit getInstance(){
        retrofit = new Retrofit.Builder()
                .baseUrl(BuildConfig.SERVER_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        return retrofit;
    }
}
