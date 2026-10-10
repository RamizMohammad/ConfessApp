package in.mohammad.ramiz.confess.data.model;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/** A page of notifications (newest first). */
public class NotificationPage {
    public List<Notification> notifications = new ArrayList<>();
    @Nullable public String nextCursor;
}
