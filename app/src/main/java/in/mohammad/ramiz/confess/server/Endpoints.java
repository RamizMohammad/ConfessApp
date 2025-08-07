package in.mohammad.ramiz.confess.server;

import in.mohammad.ramiz.confess.commentlogic.GetCommentResponse;
import in.mohammad.ramiz.confess.commentlogic.GetCommentsRequest;
import in.mohammad.ramiz.confess.entities.AddUserRequest;
import in.mohammad.ramiz.confess.entities.AddUserResponse;
import in.mohammad.ramiz.confess.entities.BiometricRequest;
import in.mohammad.ramiz.confess.entities.BiometricResponse;
import in.mohammad.ramiz.confess.entities.ChangePasswordRequest;
import in.mohammad.ramiz.confess.entities.ChangePasswordResponse;
import in.mohammad.ramiz.confess.entities.CheckPasswordRequest;
import in.mohammad.ramiz.confess.entities.CheckPasswordResponse;
import in.mohammad.ramiz.confess.entities.CheckUserPassRequest;
import in.mohammad.ramiz.confess.entities.CheckUserPassResponse;
import in.mohammad.ramiz.confess.entities.CreateCommentRequest;
import in.mohammad.ramiz.confess.entities.CreateCommentResponse;
import in.mohammad.ramiz.confess.entities.CreatePostRequest;
import in.mohammad.ramiz.confess.entities.CreatePostResponse;
import in.mohammad.ramiz.confess.entities.FcmTokenRequest;
import in.mohammad.ramiz.confess.entities.FcmTokenResponse;
import in.mohammad.ramiz.confess.entities.FeatureDemandRequest;
import in.mohammad.ramiz.confess.entities.FeatureDemandResponse;
import in.mohammad.ramiz.confess.entities.ForgotPasswordRequest;
import in.mohammad.ramiz.confess.entities.ForgotPasswordResponse;
import in.mohammad.ramiz.confess.entities.GetProfileRequest;
import in.mohammad.ramiz.confess.entities.GetProfileResponse;
import in.mohammad.ramiz.confess.entities.UpdateProfilePictureRequest;
import in.mohammad.ramiz.confess.entities.UpdateProfilePictureResponse;
import in.mohammad.ramiz.confess.likemanager.LikeRequest;
import in.mohammad.ramiz.confess.likemanager.LikeResponse;
import in.mohammad.ramiz.confess.postdatabase.PostRequest;
import in.mohammad.ramiz.confess.postdatabase.PostResponse;
import in.mohammad.ramiz.confess.entities.UpdateAboutRequest;
import in.mohammad.ramiz.confess.entities.UpdateAboutResponse;
import in.mohammad.ramiz.confess.entities.UpdatePasswordStateRequest;
import in.mohammad.ramiz.confess.entities.UpdatePasswordStateResponse;
import in.mohammad.ramiz.confess.yourconfessiondatabase.MyPostsRequest;
import in.mohammad.ramiz.confess.yourconfessiondatabase.MyPostsResponse;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

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

    @POST("delete-user")
    Call<BiometricResponse> deleteUser(
        @Header("x-api-key") String apiKey,
        @Body BiometricRequest biometricRequest
    );

    @POST("add-password")
    Call<UpdatePasswordStateResponse> addPassword(
            @Header("x-api-key") String apiKey,
            @Body UpdatePasswordStateRequest passwordStateRequest
    );

    @POST("check-status")
    Call<CheckUserPassResponse> isStatus(
        @Header("x-api-key") String apiKey,
        @Body CheckUserPassRequest userPassRequest
    );

    @POST("change-password")
    Call<ChangePasswordResponse> changesPassword(
            @Header("x-api-key") String apiKey,
            @Body ChangePasswordRequest changePasswordRequest
    );

    @POST("fetch-allPosts")
    Call<PostResponse> getAllPosts(
            @Header("x-api-key") String apiKey,
            @Body PostRequest postRequest
    );

    @POST("fetch-myPosts")
    Call<MyPostsResponse> getMyPosts(
            @Header("x-api-key") String apiKey,
            @Body MyPostsRequest myPostsRequest
    );

    @POST("update-profile")
    Call<UpdateProfilePictureResponse> updateProfilePicture(
            @Header("x-api-key") String apiKey,
            @Body UpdateProfilePictureRequest updateProfilePictureRequest
    );

    @POST("fcm-token")
    Call<FcmTokenResponse> fcm(
            @Header("x-api-key") String apiKey,
            @Body FcmTokenRequest fcmTokenRequest
    );

    @POST("add-comments")
    Call<CreateCommentResponse> comment(
            @Header("x-api-key") String apiKey,
            @Body CreateCommentRequest createCommentRequest
    );

    @POST("all-comments")
    Call<GetCommentResponse> getComment(
            @Header("x-api-key") String apiKey,
            @Body GetCommentsRequest getCommentsRequest
    );

    @POST("add-like")
    Call<LikeResponse> addLike(
            @Header("x-api-key") String apiKey,
            @Body LikeRequest likeRequest
    );

    @POST("add-unlike")
    Call<LikeResponse> addUnlike(
            @Header("x-api-key") String apiKey,
            @Body LikeRequest likeRequest
    );

    @POST("demand-feature")
    Call<FeatureDemandResponse> featureDemand(
            @Header("x-api-key") String apiKey,
            @Body FeatureDemandRequest featureDemandRequest
    );
}
