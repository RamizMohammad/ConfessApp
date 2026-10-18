package in.mohammad.ramiz.confess.data.model;

/** POST users/me/banner — "bn1:p3.c5", or "" to follow the avatar. */
public class BannerRequest {
    public final String banner;

    public BannerRequest(String banner) {
        this.banner = banner;
    }
}
