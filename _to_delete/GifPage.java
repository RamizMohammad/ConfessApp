package in.mohammad.ramiz.confess.data.model;

import java.util.List;

/** GET /api/v2/gifs: one page of the picker (trending or a search), from the server's shared cache. */
public class GifPage {
    public List<Gif> gifs;
    public int nextOffset;
    public boolean more;
    public boolean cached;
}
