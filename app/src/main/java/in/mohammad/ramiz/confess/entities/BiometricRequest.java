package in.mohammad.ramiz.confess.entities;

public class BiometricRequest {

    private String email;

    public BiometricRequest(String email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
