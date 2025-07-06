package in.mohammad.ramiz.confess.server;

import in.mohammad.ramiz.confess.BuildConfig;
import in.mohammad.ramiz.confess.entities.AddUserRequest;
import in.mohammad.ramiz.confess.entities.AddUserResponse;
import in.mohammad.ramiz.confess.entities.BiometricRequest;
import in.mohammad.ramiz.confess.entities.BiometricResponse;
import in.mohammad.ramiz.confess.entities.CheckPasswordRequest;
import in.mohammad.ramiz.confess.entities.CheckPasswordResponse;
import in.mohammad.ramiz.confess.entities.CheckUserPassRequest;
import in.mohammad.ramiz.confess.entities.CheckUserPassResponse;
import in.mohammad.ramiz.confess.entities.CreatePostRequest;
import in.mohammad.ramiz.confess.entities.CreatePostResponse;
import in.mohammad.ramiz.confess.entities.ForgotPasswordRequest;
import in.mohammad.ramiz.confess.entities.ForgotPasswordResponse;
import in.mohammad.ramiz.confess.entities.GetProfileRequest;
import in.mohammad.ramiz.confess.entities.GetProfileResponse;
import in.mohammad.ramiz.confess.entities.UpdateAboutRequest;
import in.mohammad.ramiz.confess.entities.UpdateAboutResponse;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface Endpoints {

    @POST("check-userpass")
    Call<CheckUserPassResponse> checkUserPass(
            @Header("x-api-key") String apiKey,
            @Body CheckUserPassRequest checkUserPassRequest
    );
    @POST("add-user")
    Call<AddUserResponse> createNewUser(
            @Header("x-api-key") String apiKey,
            @Body AddUserRequest addUserRequest
    );

    @POST("check-password")
    Call<CheckPasswordResponse> checkUserPassword(
            @Header("x-api-key") String apiKey,
            @Body CheckPasswordRequest checkPasswordRequest
    );

    @POST("forgot-password")
    Call<ForgotPasswordResponse> forgotUserPassword(
            @Header("x-api-key") String apiKey,
            @Body ForgotPasswordRequest forgotPasswordRequest
    );

    @POST("create-post")
    Call<CreatePostResponse> createPost(
            @Header("x-api-key") String apiKey,
            @Body CreatePostRequest createPostRequest
    );

    @POST("update-about")
    Call<UpdateAboutResponse> updateAbout(
            @Header("x-api-key") String apiKey,
            @Body UpdateAboutRequest updateAboutRequest
    );

    @POST("get-profile")
    Call<GetProfileResponse> getProfile(
            @Header("x-api-key") String apiKey,
            @Body GetProfileRequest getProfileRequest
    );

    @POST("biometric")
    Call<BiometricResponse> updateBiometric(
            @Header("x-api-key") String apiKey,
            @Body BiometricRequest biometricRequest
    );
}
