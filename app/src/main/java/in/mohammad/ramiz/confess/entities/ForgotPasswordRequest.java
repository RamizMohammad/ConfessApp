package in.mohammad.ramiz.confess.entities;

public class ForgotPasswordRequest {

    private String email;

    public ForgotPasswordRequest(String email) {
        this.email = email;
    }

    public ForgotPasswordRequest() {
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
