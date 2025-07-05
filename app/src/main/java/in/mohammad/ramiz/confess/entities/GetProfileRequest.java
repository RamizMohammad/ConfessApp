package in.mohammad.ramiz.confess.entities;

public class GetProfileRequest {
    private String email;

    public GetProfileRequest(String email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
