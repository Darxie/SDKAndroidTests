package cz.feldis.sdkandroidtests.auth

import com.sygic.lib.auth.*
import com.sygic.sdk.auth.Auth

class MyAuthLibWrapper (authConfig: AuthConfig, authStorage: Storage, authHttp: AuthHttp): Auth {

    private val signInStateChangeListener = object : SignInStateChangeListener {
        override fun onStateChanged(newState: SignInState) {}
        override fun onSignedOutWithoutRequest(
            error: ErrorCode,
            errorMessage: String
        ) {
        }
    }

    private val auth =
        com.sygic.lib.auth.Auth.build(authConfig, signInStateChangeListener, authHttp, authStorage)

    override fun buildHeaders(callback: com.sygic.sdk.auth.BuildHeadersCallback) {
        auth.buildHeaders(object : BuildHeadersCallback {
            override fun onError(error: ErrorCode, errorMessage: String) {
                callback.onError(error.toSdkErrorCode(), errorMessage)
            }

            override fun onSuccess(headers: Map<String, String>) {
                callback.onSuccess(headers)
            }
        })
    }

    override fun notifyAuthRejected() {
        auth.notifyAuthRejected()
    }



    private fun ErrorCode.toSdkErrorCode(): com.sygic.sdk.auth.ErrorCode {
        return when (this) {
            ErrorCode.NetworkError -> com.sygic.sdk.auth.ErrorCode.NetworkError
            ErrorCode.BadResponseData -> com.sygic.sdk.auth.ErrorCode.BadResponseData
            ErrorCode.NotAuthenticated -> com.sygic.sdk.auth.ErrorCode.NotAuthenticated
            ErrorCode.WrongCredentials -> com.sygic.sdk.auth.ErrorCode.WrongCredentials
            ErrorCode.TokenExpired -> com.sygic.sdk.auth.ErrorCode.TokenExpired
            ErrorCode.ServerError -> com.sygic.sdk.auth.ErrorCode.ServerError
            ErrorCode.UnrecognizedResponseError -> com.sygic.sdk.auth.ErrorCode.UnrecognizedResponseError
            ErrorCode.TokenNotFound -> TODO()
        }
    }

}