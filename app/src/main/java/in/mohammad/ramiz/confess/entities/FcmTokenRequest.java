package in.mohammad.ramiz.confess.entities;

public class FcmTokenRequest {

    private String email;
    private String token;
    private String status;

    public FcmTokenRequest(String email, String token, String status) {
        this.email = email;
        this.token = token;
        this.status = status;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
