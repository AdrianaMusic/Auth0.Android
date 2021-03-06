package com.auth0.android.callback

import com.auth0.android.Auth0Exception

/**
 * Interface for all callbacks used with Auth0 API clients
 */
public interface Callback<T, U : Auth0Exception> {

    /**
     * Method called on success with the result.
     *
     * @param result Request result
     */
    public fun onSuccess(result: T)

    /**
     * Method called on Auth0 API request failure
     *
     * @param error The reason of the failure
     */
    public fun onFailure(error: U)
}