package in.mohammad.ramiz.confess.postdatabase;

public class PostRequest {

    private String date;
    private boolean endDate;
    private boolean topDate;

    public PostRequest(String date, boolean endDate, boolean topDate) {
        this.date = date;
        this.endDate = endDate;
        this.topDate = topDate;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public boolean isEndDate() {
        return endDate;
    }

    public void setEndDate(boolean endDate) {
        this.endDate = endDate;
    }

    public boolean isTopDate() {
        return topDate;
    }

    public void setTopDate(boolean topDate) {
        this.topDate = topDate;
    }
}
