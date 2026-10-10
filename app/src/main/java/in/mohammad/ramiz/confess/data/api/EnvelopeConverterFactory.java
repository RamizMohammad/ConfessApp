package in.mohammad.ramiz.confess.data.api;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Converter;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Every 2xx reply is {"success": true, "data": {...}, "error": null, "request_id": "..."}.
 * This unwraps "data" so response types map to the inner payload.
 * Request bodies go through plain Gson (nulls omitted).
 * Ported from the old server/EnvelopeConverterFactory.
 */
public final class EnvelopeConverterFactory extends Converter.Factory {

    private final Gson gson;
    private final GsonConverterFactory delegate;

    private EnvelopeConverterFactory(Gson gson) {
        this.gson = gson;
        this.delegate = GsonConverterFactory.create(gson);
    }

    public static EnvelopeConverterFactory create(Gson gson) {
        return new EnvelopeConverterFactory(gson);
    }

    @Nullable
    @Override
    public Converter<ResponseBody, ?> responseBodyConverter(Type type, Annotation[] annotations, Retrofit retrofit) {
        return (Converter<ResponseBody, Object>) body -> {
            try {
                JsonElement root = JsonParser.parseString(body.string());
                if (root.isJsonObject() && root.getAsJsonObject().has("success")) {
                    JsonElement data = root.getAsJsonObject().get("data");
                    if (data == null || data.isJsonNull()) data = new JsonObject();
                    return gson.fromJson(data, type);
                }
                return gson.fromJson(root, type);    // defensive: non-enveloped payload
            } finally {
                body.close();
            }
        };
    }

    @Nullable
    @Override
    public Converter<?, RequestBody> requestBodyConverter(Type type, Annotation[] parameterAnnotations,
                                                          Annotation[] methodAnnotations, Retrofit retrofit) {
        return delegate.requestBodyConverter(type, parameterAnnotations, methodAnnotations, retrofit);
    }
}
