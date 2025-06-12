package in.mohammad.ramiz.confess.entityfiles;

public class ListEntites {
    private String text;
    private boolean isHeading;

    public ListEntites(String text, boolean isHeading) {
        this.text = text;
        this.isHeading = isHeading;
    }

    public String getText() {
        return text;
    }

    public boolean isHeading() {
        return isHeading;
    }
}
