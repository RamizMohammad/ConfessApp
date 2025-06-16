package in.mohammad.ramiz.confess.server;

import in.mohammad.ramiz.confess.entities.AddUserRequest;
import in.mohammad.ramiz.confess.entities.AddUserResponse;
import in.mohammad.ramiz.confess.entities.CheckUserPassRequest;
import in.mohammad.ramiz.confess.entities.CheckUserPassResponse;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface Endpoints {

    @POST("check-userpass")
    Call<CheckUserPassResponse> checkUserPass(@Body CheckUserPassRequest checkUserPassRequest);
    @POST("add-user")
    Call<AddUserResponse> createNewUser(@Body AddUserRequest addUserRequest);
}
