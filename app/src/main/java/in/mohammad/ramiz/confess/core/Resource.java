package in.mohammad.ramiz.confess.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import in.mohammad.ramiz.confess.data.api.ApiException;

/** UI state for one async load: LOADING → SUCCESS(data) | ERROR(error). */
public final class Resource<T> {

    public enum Status { LOADING, SUCCESS, ERROR }

    @NonNull public final Status status;
    @Nullable public final T data;
    @Nullable public final ApiException error;

    private Resource(@NonNull Status status, @Nullable T data, @Nullable ApiException error) {
        this.status = status;
        this.data = data;
        this.error = error;
    }

    public static <T> Resource<T> loading() {
        return new Resource<>(Status.LOADING, null, null);
    }

    /** Loading while keeping what's already on screen (pull-to-refresh). */
    public static <T> Resource<T> loading(@Nullable T current) {
        return new Resource<>(Status.LOADING, current, null);
    }

    public static <T> Resource<T> success(@NonNull T data) {
        return new Resource<>(Status.SUCCESS, data, null);
    }

    public static <T> Resource<T> error(@NonNull ApiException e) {
        return new Resource<>(Status.ERROR, null, e);
    }

    public boolean isLoading() { return status == Status.LOADING; }
    public boolean isSuccess() { return status == Status.SUCCESS; }
    public boolean isError() { return status == Status.ERROR; }
}
