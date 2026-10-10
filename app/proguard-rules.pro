# ── Confess release rules ─────────────────────────────────────────────────────

# Gson models (fields are read by reflection) — every DTO lives in data.model.
-keep class in.mohammad.ramiz.confess.data.model.** { *; }
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod

# Retrofit 3: keep generic signatures of service interfaces + suspend/Call types.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# Gson TypeToken (used for generic envelopes)
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }

# Credential Manager / Google ID
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }

# ── Release hardening ─────────────────────────────────────────────────────────
# Flatten package names (decompiled code shows a.b.c instead of our structure).
-repackageclasses ''
-allowaccessmodification
# Strip verbose/debug/info logging from release builds (warnings/errors stay).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
# Keep source-file/line info out of the APK; map files stay on the build machine.
-renamesourcefileattribute ''
