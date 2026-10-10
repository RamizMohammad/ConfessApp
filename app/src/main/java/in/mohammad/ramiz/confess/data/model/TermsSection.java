package in.mohammad.ramiz.confess.data.model;

import java.util.ArrayList;
import java.util.List;

/** One entry of assets/terms.json: {"heading": "📖 Introduction", "terms": ["…", …]}. */
public class TermsSection {
    public String heading;
    public List<String> terms = new ArrayList<>();
}
