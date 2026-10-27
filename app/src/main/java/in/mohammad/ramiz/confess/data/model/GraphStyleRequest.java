package in.mohammad.ramiz.confess.data.model;

import com.google.gson.annotations.SerializedName;

/** POST users/me/graph — my confession graph's look and whether others can see it. */
public class GraphStyleRequest {
    public final String style;
    @SerializedName("public") public final boolean isPublic;

    public GraphStyleRequest(String style, boolean isPublic) {
        this.style = style;
        this.isPublic = isPublic;
    }
}
