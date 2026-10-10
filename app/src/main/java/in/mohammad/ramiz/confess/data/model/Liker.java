package in.mohammad.ramiz.confess.data.model;

/** Someone who felt my confession (GET posts/{id}/likes — writer only). */
public class Liker {
    public String alias;
    public String avatar;
    public String likedAt;
    /** felt · hug · same · strength · tears (absent from an older server → felt). */
    public String reaction;
}
