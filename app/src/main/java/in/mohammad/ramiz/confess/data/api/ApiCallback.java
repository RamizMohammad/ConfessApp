package in.mohammad.ramiz.confess.data.api;

/** Result of an API call, delivered on the main thread. */
public interface ApiCallback<T> {
    void onSuccess(T data);

    void onError(ApiException error);
}
