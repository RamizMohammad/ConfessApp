package in.mohammad.ramiz.confess.entities;

public class CheckUserPassRequest {
     private String email;

    public CheckUserPassRequest(String email) {
        this.email = email;
    }

    public CheckUserPassRequest() {
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
