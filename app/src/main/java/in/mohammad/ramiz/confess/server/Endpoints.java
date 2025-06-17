package in.mohammad.ramiz.confess.server;

import in.mohammad.ramiz.confess.entities.AddUserRequest;
import in.mohammad.ramiz.confess.entities.AddUserResponse;
import in.mohammad.ramiz.confess.entities.CheckPasswordRequest;
import in.mohammad.ramiz.confess.entities.CheckPasswordResponse;
import in.mohammad.ramiz.confess.entities.CheckUserPassRequest;
import in.mohammad.ramiz.confess.entities.CheckUserPassResponse;
import in.mohammad.ramiz.confess.entities.ForgotPasswordRequest;
import in.mohammad.ramiz.confess.entities.ForgotPasswordResponse;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface Endpoints {

    @POST("check-userpass")
    Call<CheckUserPassResponse> checkUserPass(@Body CheckUserPassRequest checkUserPassRequest);
    @POST("add-user")
    Call<AddUserResponse> createNewUser(@Body AddUserRequest addUserRequest);

    @POST("check-password")
    Call<CheckPasswordResponse> checkUserPassword(@Body CheckPasswordRequest checkPasswordRequest);

    @POST("forgot-password")
    Call<ForgotPasswordResponse> forgotUserPassword(@Body ForgotPasswordRequest forgotPasswordRequest);
}
