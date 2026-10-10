package in.mohammad.ramiz.confess.data.repo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonObject;

import java.util.List;

import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiClient;
import in.mohammad.ramiz.confess.data.api.Calls;
import in.mohammad.ramiz.confess.data.model.EnabledRequest;
import in.mohammad.ramiz.confess.data.model.FeatureRequest;
import in.mohammad.ramiz.confess.data.model.MarkReadRequest;
import in.mohammad.ramiz.confess.data.model.NotificationPage;
import in.mohammad.ramiz.confess.data.model.PushTokenRequest;
import in.mohammad.ramiz.confess.data.model.UnreadCount;
import retrofit2.Call;

/** Notifications (now server-side — no local Room table), push registration, feedback. */
public final class ActivityRepository {

    public Call<NotificationPage> notifications(@Nullable String cursor, @NonNull ApiCallback<NotificationPage> cb) {
        return Calls.enqueue(ApiClient.api().notifications(cursor, null), cb);
    }

    public Call<UnreadCount> unreadCount(@NonNull ApiCallback<UnreadCount> cb) {
        return Calls.enqueue(ApiClient.api().unreadCount(), cb);
    }

    /** ids == null → mark all read. */
    public Call<JsonObject> markRead(@Nullable List<String> ids, @NonNull ApiCallback<JsonObject> cb) {
        return Calls.enqueue(ApiClient.api().markRead(new MarkReadRequest(ids)), cb);
    }

    public Call<JsonObject> registerPushToken(@NonNull String token, @NonNull ApiCallback<JsonObject> cb) {
        return Calls.enqueue(ApiClient.api().registerPushToken(new PushTokenRequest(token)), cb);
    }

    public Call<JsonObject> setPushEnabled(boolean enabled, @NonNull ApiCallback<JsonObject> cb) {
        return Calls.enqueue(ApiClient.api().setPushEnabled(new EnabledRequest(enabled)), cb);
    }

    /** category: feed | privacy | comments | other */
    public Call<JsonObject> requestFeature(@NonNull String text, @NonNull String category, @NonNull ApiCallback<JsonObject> cb) {
        return Calls.enqueue(ApiClient.api().requestFeature(new FeatureRequest(text, category)), cb);
    }
}
