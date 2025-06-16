package in.mohammad.ramiz.confess.entities;

public class AddUserResponse {
    private boolean isAliasName;
    private boolean isUserCreated;

    public AddUserResponse(boolean isAliasName, boolean isUserCreated) {
        this.isAliasName = isAliasName;
        this.isUserCreated = isUserCreated;
    }

    public AddUserResponse() {
    }

    public boolean isAliasName() {
        return isAliasName;
    }

    public void setAliasName(boolean aliasName) {
        isAliasName = aliasName;
    }

    public boolean isUserCreated() {
        return isUserCreated;
    }

    public void setUserCreated(boolean userCreated) {
        isUserCreated = userCreated;
    }
}
