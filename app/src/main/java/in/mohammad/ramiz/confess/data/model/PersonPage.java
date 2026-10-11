package in.mohammad.ramiz.confess.data.model;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/** GET users/me/followers | users/me/following. */
public class PersonPage {
    public List<Person> users = new ArrayList<>();
    @Nullable public String nextCursor;
}
