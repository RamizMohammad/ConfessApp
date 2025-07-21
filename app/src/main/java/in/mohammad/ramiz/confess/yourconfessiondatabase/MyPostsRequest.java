package in.mohammad.ramiz.confess.yourconfessiondatabase;

public class MyPostsRequest {

    private String date;
    private String email;

    public MyPostsRequest(String date, String email) {
        this.date = date;
        this.email = email;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
