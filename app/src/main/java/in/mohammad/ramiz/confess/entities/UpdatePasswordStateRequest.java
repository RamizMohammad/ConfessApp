package in.mohammad.ramiz.confess.entities;

public class UpdatePasswordStateRequest {

    private String email;
    private String password;
    private boolean isBiometric;

    public UpdatePasswordStateRequest(String email, String password, boolean isBiometric) {
        this.email = email;
        this.password = password;
        this.isBiometric = isBiometric;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isBiometric() {
        return isBiometric;
    }

    public void setBiometric(boolean biometric) {
        isBiometric = biometric;
    }
}
