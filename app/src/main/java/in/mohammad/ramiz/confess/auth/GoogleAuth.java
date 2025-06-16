package in.mohammad.ramiz.confess.auth;

import android.content.Context;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.FirebaseAuth;

import in.mohammad.ramiz.confess.BuildConfig;

public class GoogleAuth {

    private final FirebaseAuth mAuth;
    private final GoogleSignInClient signInClient;

    public GoogleAuth(Context context){
        mAuth = FirebaseAuth.getInstance();

        GoogleSignInOptions signInOptions = new GoogleSignInOptions.Builder(
                GoogleSignInOptions.DEFAULT_SIGN_IN
        ).requestIdToken(BuildConfig.WEB_CLIENT_ID)
                .requestEmail()
                .build();

        signInClient = GoogleSignIn.getClient(context, signInOptions);
    }

    public GoogleSignInClient googleSignInClient(){
        return signInClient;
    }

    public FirebaseAuth getFirebaseAuth(){
        return mAuth;
    }
}
