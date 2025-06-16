package in.mohammad.ramiz.confess.server;

import in.mohammad.ramiz.confess.entities.CheckUserPassRequest;
import in.mohammad.ramiz.confess.entities.CheckUserPassResponse;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface Endpoints {

    @POST("check-userpass")
    Call<CheckUserPassResponse> chechUserPass(@Body CheckUserPassRequest checkUserPassRequest);
}
