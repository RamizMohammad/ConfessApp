package in.mohammad.ramiz.confess.entities;

public class FeatureDemandRequest {

    private String aliasName;
    private String feature;

    public FeatureDemandRequest(String aliasName, String feature) {
        this.aliasName = aliasName;
        this.feature = feature;
    }

    public String getAliasName() {
        return aliasName;
    }

    public void setAliasName(String aliasName) {
        this.aliasName = aliasName;
    }

    public String getFeature() {
        return feature;
    }

    public void setFeature(String feature) {
        this.feature = feature;
    }
}
