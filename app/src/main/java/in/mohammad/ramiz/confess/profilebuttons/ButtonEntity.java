package in.mohammad.ramiz.confess.profilebuttons;

public class ButtonEntity {

    private int id;
    private int iconId;
    private String buttonName;

    public ButtonEntity(int id, int iconId, String buttonName) {
        this.id = id;
        this.iconId = iconId;
        this.buttonName = buttonName;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getIconId() {
        return iconId;
    }

    public void setIconId(int iconId) {
        this.iconId = iconId;
    }

    public String getButtonName() {
        return buttonName;
    }

    public void setButtonName(String buttonName) {
        this.buttonName = buttonName;
    }
}
