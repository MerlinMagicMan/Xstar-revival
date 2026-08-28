package io.xstarrevival.sdkprobe;

import android.app.Application;
import android.util.Log;

import com.autel.common.CallbackWithOneParam;
import com.autel.common.author.AuthorityState;
import com.autel.common.error.AutelError;
import com.autel.sdk.Autel;
import com.autel.sdk.AutelSdkConfig;

public final class XStarProbeApplication extends Application {
    private static final String TAG = "XStarSdkProbe";

    private volatile String authStatus = "NOT_INITIALIZED";

    @Override
    public void onCreate() {
        super.onCreate();

        final String appKey = BuildConfig.AUTEL_APP_KEY;
        if (appKey == null || appKey.trim().isEmpty()) {
            authStatus = "NO_APP_KEY";
            Log.w(TAG, "AUTEL_APP_KEY is not configured; SDK initialization skipped.");
            return;
        }

        authStatus = "AUTHENTICATING";
        AutelSdkConfig config = new AutelSdkConfig.AutelSdkConfigBuilder()
                .setAppKey(appKey)
                .setPostOnUi(true)
                .create();

        Autel.init(this, config, new CallbackWithOneParam<AuthorityState>() {
            @Override
            public void onSuccess(AuthorityState authorityState) {
                authStatus = "AUTHORIZED:" + String.valueOf(authorityState);
                Log.i(TAG, "Autel SDK authorization succeeded: " + authorityState);
            }

            @Override
            public void onFailure(AutelError error) {
                authStatus = "AUTH_FAILED:" + safeError(error);
                Log.e(TAG, "Autel SDK authorization failed: " + safeError(error));
            }
        });
    }

    public String getAuthStatus() {
        return authStatus;
    }

    static String safeError(AutelError error) {
        if (error == null) return "unknown";
        try {
            return error.getDescription();
        } catch (Throwable ignored) {
            return String.valueOf(error);
        }
    }
}
