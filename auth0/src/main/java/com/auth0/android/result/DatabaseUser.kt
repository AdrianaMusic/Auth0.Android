package com.auth0.android.result

import com.auth0.android.request.internal.JsonRequired
import com.google.gson.annotations.SerializedName

/**
 * Auth0 user created in a Database connection.
 *
 * @see [com.auth0.android.authentication.AuthenticationAPIClient.signUp]
 */
public class DatabaseUser(
    @field:JsonRequired @field:SerializedName("email") public val email: String,
    @field:SerializedName(
        "username"
    ) public val username: String?,
    @field:SerializedName("email_verified") public val isEmailVerified: Boolean
)