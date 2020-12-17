/*
 * WebAuthProvider.java
 *
 * Copyright (c) 2016 Auth0 (http://auth0.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.auth0.android.provider;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.auth0.android.Auth0;
import com.auth0.android.authentication.AuthenticationException;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static com.auth0.android.provider.OAuthManager.KEY_CONNECTION;
import static com.auth0.android.provider.OAuthManager.KEY_MAX_AGE;
import static com.auth0.android.provider.OAuthManager.KEY_NONCE;
import static com.auth0.android.provider.OAuthManager.KEY_STATE;

/**
 * OAuth2 Web Authentication Provider.
 * <p>
 * It uses an external browser by sending the {@link android.content.Intent#ACTION_VIEW} intent.
 */
@SuppressWarnings("WeakerAccess")
public class WebAuthProvider {

    static final String TAG = WebAuthProvider.class.getName();

    static ResumableManager managerInstance;

    public static class LogoutBuilder {

        private final Auth0 account;
        private String scheme;
        private String returnToUrl;
        private CustomTabsOptions ctOptions;

        LogoutBuilder(Auth0 account) {
            this.account = account;

            //Default values
            this.scheme = "https";
            this.ctOptions = CustomTabsOptions.newBuilder().build();
        }

        /**
         * When using a Custom Tabs compatible Browser, apply these customization options.
         *
         * @param options the Custom Tabs customization options
         * @return the current builder instance
         */
        @NonNull
        public LogoutBuilder withCustomTabsOptions(@NonNull CustomTabsOptions options) {
            this.ctOptions = options;
            return this;
        }

        /**
         * Specify a custom Scheme to use on the Return To URL. Default scheme is 'https'.
         *
         * @param scheme to use in the Return To URL.
         * @return the current builder instance
         */
        @NonNull
        public LogoutBuilder withScheme(@NonNull String scheme) {
            String lowerCase = scheme.toLowerCase(Locale.ROOT);
            if (!scheme.equals(lowerCase)) {
                Log.w(TAG, "Please provide the scheme in lowercase and make sure it's the same configured in the intent filter. Android expects the scheme to be lowercase.");
            }
            this.scheme = scheme;
            return this;
        }

        /**
         * Specify a custom Redirect To URL to use to invoke the app on redirection.
         * Normally, you wouldn't need to call this method manually as the default value is autogenerated for you.
         * The {@link LogoutBuilder#withScheme(String)} configuration is ignored when this method is called. It is your responsibility to pass a well-formed URL.
         *
         * @param returnToUrl to use to invoke the app on redirection.
         * @return the current builder instance
         */
        @NonNull
        public LogoutBuilder withReturnToUrl(@NonNull String returnToUrl) {
            this.returnToUrl = returnToUrl;
            return this;
        }

        /**
         * Request the user session to be cleared. When successful, the callback will get invoked.
         * An error is raised if there are no browser applications installed in the device.
         *
         * @param context  to run the log out
         * @param callback to invoke when log out is successful
         * @see AuthenticationException#isBrowserAppNotAvailable()
         */
        public void start(@NonNull Context context, @NonNull VoidCallback callback) {
            resetManagerInstance();

            if (!ctOptions.hasCompatibleBrowser(context.getPackageManager())) {
                AuthenticationException ex = new AuthenticationException("a0.browser_not_available", "No compatible Browser application is installed.");
                callback.onFailure(ex);
                return;
            }

            if (returnToUrl == null) {
                returnToUrl = CallbackHelper.getCallbackUri(scheme, context.getApplicationContext().getPackageName(), account.getDomainUrl());
            }
            //noinspection ConstantConditions
            LogoutManager logoutManager = new LogoutManager(this.account, callback, returnToUrl, ctOptions);

            managerInstance = logoutManager;
            logoutManager.startLogout(context);
        }
    }

    public static class Builder {

        private static final String KEY_AUDIENCE = "audience";
        private static final String KEY_SCOPE = "scope";
        private static final String KEY_CONNECTION_SCOPE = "connection_scope";
        private static final String SCOPE_TYPE_OPENID = "openid";


        private final Auth0 account;
        private final Map<String, String> values;
        private final Map<String, String> headers;
        private PKCE pkce;
        private String issuer;
        private String scheme;
        private String redirectUri;
        private CustomTabsOptions ctOptions;
        private Integer leeway;

        Builder(Auth0 account) {
            this.account = account;
            this.values = new HashMap<>();

            //Default values
            this.scheme = "https";
            this.ctOptions = CustomTabsOptions.newBuilder().build();
            this.headers = new HashMap<>();
            withScope(SCOPE_TYPE_OPENID);
        }

        /**
         * Use a custom state in the requests
         *
         * @param state to use in the requests
         * @return the current builder instance
         */
        @NonNull
        public Builder withState(@NonNull String state) {
            this.values.put(KEY_STATE, state);
            return this;
        }

        /**
         * Specify a custom nonce value to avoid replay attacks. It will be sent in the auth request that will be returned back as a claim in the id_token
         *
         * @param nonce to use in the requests
         * @return the current builder instance
         */
        @NonNull
        public Builder withNonce(@NonNull String nonce) {
            this.values.put(KEY_NONCE, nonce);
            return this;
        }

        /**
         * Set the max age value for the authentication.
         *
         * @param maxAge to use in the requests
         * @return the current builder instance
         */
        @NonNull
        public Builder withMaxAge(@NonNull Integer maxAge) {
            this.values.put(KEY_MAX_AGE, String.valueOf(maxAge));
            return this;
        }

        /**
         * Set the leeway or clock skew to be used for ID Token verification.
         * Defaults to 60 seconds.
         *
         * @param leeway to use for ID token verification, in seconds.
         * @return the current builder instance
         */
        @NonNull
        public Builder withIdTokenVerificationLeeway(@NonNull Integer leeway) {
            this.leeway = leeway;
            return this;
        }

        /**
         * Set the expected issuer to be used for ID Token verification.
         * Defaults to the value returned by {@link Auth0#getDomainUrl()}.
         *
         * @param issuer to use for ID token verification.
         * @return the current builder instance
         */
        @NonNull
        public Builder withIdTokenVerificationIssuer(@NonNull String issuer) {
            this.issuer = issuer;
            return this;
        }

        /**
         * Use a custom audience in the requests
         *
         * @param audience to use in the requests
         * @return the current builder instance
         */
        @NonNull
        public Builder withAudience(@NonNull String audience) {
            this.values.put(KEY_AUDIENCE, audience);
            return this;
        }

        /**
         * Specify a custom Scheme to use on the Redirect URI. Default scheme is 'https'.
         *
         * @param scheme to use in the Redirect URI.
         * @return the current builder instance
         */
        @NonNull
        public Builder withScheme(@NonNull String scheme) {
            String lowerCase = scheme.toLowerCase(Locale.ROOT);
            if (!scheme.equals(lowerCase)) {
                Log.w(TAG, "Please provide the scheme in lowercase and make sure it's the same configured in the intent filter. Android expects the scheme to be lowercase.");
            }
            this.scheme = scheme;
            return this;
        }

        /**
         * Specify a custom Redirect URI to use to invoke the app on redirection.
         * Normally, you wouldn't need to call this method manually as the default value is autogenerated for you.
         * The {@link Builder#withScheme(String)} configuration is ignored when this method is called. It is your responsibility to pass a well-formed URI.
         *
         * @param redirectUri to use to invoke the app on redirection.
         * @return the current builder instance
         */
        @NonNull
        public Builder withRedirectUri(@NonNull String redirectUri) {
            this.redirectUri = redirectUri;
            return this;
        }

        /**
         * Give a scope for this request.
         *
         * @param scope to request.
         * @return the current builder instance
         */
        @NonNull
        public Builder withScope(@NonNull String scope) {
            this.values.put(KEY_SCOPE, scope);
            return this;
        }

        /**
         * Add custom headers for PKCE token request.
         *
         * @param headers for token request.
         * @return the current builder instance
         */
        public Builder withHeaders(@NonNull Map<String, String> headers) {
            this.headers.putAll(headers);
            return this;
        }

        /**
         * Give a connection scope for this request.
         *
         * @param connectionScope to request.
         * @return the current builder instance
         */
        @NonNull
        public Builder withConnectionScope(@NonNull String... connectionScope) {
            StringBuilder sb = new StringBuilder();
            for (String s : connectionScope) {
                sb.append(s.trim()).append(",");
            }
            if (sb.length() > 0) {
                sb.deleteCharAt(sb.length() - 1);
                this.values.put(KEY_CONNECTION_SCOPE, sb.toString());
            }
            return this;
        }

        /**
         * Use extra parameters on the request.
         *
         * @param parameters to add
         * @return the current builder instance
         */
        @NonNull
        public Builder withParameters(@NonNull Map<String, Object> parameters) {
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                if (entry.getValue() != null) {
                    this.values.put(entry.getKey(), entry.getValue().toString());
                }
            }
            return this;
        }

        /**
         * Use the given connection. By default no connection is specified, so the login page will be displayed.
         *
         * @param connectionName to use
         * @return the current builder instance
         */
        @NonNull
        public Builder withConnection(@NonNull String connectionName) {
            this.values.put(KEY_CONNECTION, connectionName);
            return this;
        }

        /**
         * When using a Custom Tabs compatible Browser, apply these customization options.
         *
         * @param options the Custom Tabs customization options
         * @return the current builder instance
         */
        @NonNull
        public Builder withCustomTabsOptions(@NonNull CustomTabsOptions options) {
            this.ctOptions = options;
            return this;
        }

        @VisibleForTesting
        Builder withPKCE(@Nullable PKCE pkce) {
            this.pkce = pkce;
            return this;
        }

        /**
         * Request user Authentication. The result will be received in the callback.
         * An error is raised if there are no browser applications installed in the device.
         *
         * @param activity context to run the authentication
         * @param callback to receive the parsed results
         * @see AuthenticationException#isBrowserAppNotAvailable()
         */
        public void start(@NonNull Activity activity, @NonNull AuthCallback callback) {
            resetManagerInstance();

            if (!ctOptions.hasCompatibleBrowser(activity.getPackageManager())) {
                AuthenticationException ex = new AuthenticationException("a0.browser_not_available", "No compatible Browser application is installed.");
                callback.onFailure(ex);
                return;
            }

            OAuthManager manager = new OAuthManager(account, callback, values, ctOptions);
            manager.setHeaders(headers);
            manager.setPKCE(pkce);
            manager.setIdTokenVerificationLeeway(leeway);
            manager.setIdTokenVerificationIssuer(issuer);

            managerInstance = manager;

            if (redirectUri == null) {
                redirectUri = CallbackHelper.getCallbackUri(scheme, activity.getApplicationContext().getPackageName(), account.getDomainUrl());
            }
            manager.startAuthentication(activity, redirectUri, 110);
        }
    }

    // Public methods

    /**
     * Initialize the WebAuthProvider instance for logging out the user using an account. Additional settings can be configured
     * in the LogoutBuilder, like changing the scheme of the return to URL.
     *
     * @param account to use for authentication
     * @return a new Builder instance to customize.
     */
    @NonNull
    public static LogoutBuilder logout(@NonNull Auth0 account) {
        return new LogoutBuilder(account);
    }

    /**
     * Initialize the WebAuthProvider instance for authenticating the user using an account. Additional settings can be configured
     * in the Builder, like setting the connection name or authentication parameters.
     *
     * @param account to use for authentication
     * @return a new Builder instance to customize.
     */
    @NonNull
    public static Builder login(@NonNull Auth0 account) {
        return new Builder(account);
    }

    /**
     * Finishes the authentication or log out flow by passing the data received in the activity's onNewIntent() callback.
     * The final result will be delivered to the callback specified when calling start().
     * <p>
     * This is no longer required to be called, the authentication is handled internally as long as you've correctly setup the intent-filter.
     *
     * @param intent the data received on the onNewIntent() call. When null is passed, the authentication will be considered canceled.
     * @return true if a result was expected and has a valid format, or false if not. When true is returned a call on the callback is expected.
     */
    public static boolean resume(@Nullable Intent intent) {
        if (managerInstance == null) {
            Log.w(TAG, "There is no previous instance of this provider.");
            return false;
        }

        final AuthorizeResult result = new AuthorizeResult(intent);
        boolean success = managerInstance.resume(result);
        if (success) {
            resetManagerInstance();
        }
        return success;

    }

    // End Public methods

    @VisibleForTesting
    static ResumableManager getManagerInstance() {
        return managerInstance;
    }

    @VisibleForTesting
    static void resetManagerInstance() {
        managerInstance = null;
    }

}
