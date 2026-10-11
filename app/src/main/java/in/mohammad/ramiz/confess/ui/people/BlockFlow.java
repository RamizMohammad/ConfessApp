package in.mohammad.ramiz.confess.ui.people;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonObject;

import java.util.Iterator;
import java.util.List;

import in.mohammad.ramiz.confess.R;
import in.mohammad.ramiz.confess.core.Haptics;
import in.mohammad.ramiz.confess.core.PostEvents;
import in.mohammad.ramiz.confess.data.api.ApiCallback;
import in.mohammad.ramiz.confess.data.api.ApiException;
import in.mohammad.ramiz.confess.data.model.StoryGroup;
import in.mohammad.ramiz.confess.data.repo.UserRepository;
import in.mohammad.ramiz.confess.ui.stories.StoryTrayCache;

/**
 * "Block @alias?" → block on the server → their confessions leave every list I have open,
 * their stories leave the tray. The other person is never told.
 */
public final class BlockFlow {

    private BlockFlow() { }

    public static void confirm(@NonNull Context ctx, @NonNull String alias, @Nullable Runnable onBlocked) {
        new MaterialAlertDialogBuilder(ctx)
                .setTitle(ctx.getString(R.string.block_title, alias))
                .setMessage(R.string.block_body)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.block_confirm, (d, w) -> block(ctx, alias, onBlocked))
                .show();
    }

    private static void block(@NonNull Context ctx, @NonNull String alias, @Nullable Runnable onBlocked) {
        Context app = ctx.getApplicationContext();
        Haptics.click(ctx);
        new UserRepository().setBlocked(alias, true, new ApiCallback<JsonObject>() {
            @Override public void onSuccess(JsonObject r) {
                dropStories(alias);
                PostEvents.blocked(alias);
                Toast.makeText(app, app.getString(R.string.blocked_toast, alias), Toast.LENGTH_SHORT).show();
                if (onBlocked != null) onBlocked.run();
            }
            @Override public void onError(ApiException e) {
                Toast.makeText(app, e.userMessage(app), Toast.LENGTH_LONG).show();
            }
        });
    }

    private static void dropStories(@NonNull String alias) {
        List<StoryGroup> groups = StoryTrayCache.get();
        boolean changed = false;
        synchronized (StoryTrayCache.class) {
            for (Iterator<StoryGroup> it = groups.iterator(); it.hasNext(); ) {
                StoryGroup g = it.next();
                if (g.author != null && alias.equalsIgnoreCase(g.author.alias)) { it.remove(); changed = true; }
            }
        }
        if (changed) StoryTrayCache.changed();
    }
}
