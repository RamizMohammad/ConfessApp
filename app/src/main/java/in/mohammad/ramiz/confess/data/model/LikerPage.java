package in.mohammad.ramiz.confess.data.model;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/** A page of people who felt a confession (newest first). */
public class LikerPage {
    public List<Liker> users = new ArrayList<>();
    @Nullable public String nextCursor;
}
