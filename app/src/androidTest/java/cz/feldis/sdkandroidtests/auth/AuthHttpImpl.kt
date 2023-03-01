//package cz.feldis.sdkandroidtests.auth
//
//import com.sygic.lib.auth.AuthHttp
//import okhttp3.*
//import okhttp3.Headers.Companion.toHeaders
//import okhttp3.MediaType.Companion.toMediaType
//import okhttp3.RequestBody.Companion.toRequestBody
//import java.io.IOException
//
//internal class AuthHttpImpl: AuthHttp {
//
//    private val httpclient = OkHttpClient.Builder()
//        .connectionSpecs(listOf(ConnectionSpec.RESTRICTED_TLS))
//        .build()
//
//    override fun sendRequest(
//        request: AuthHttp.Request,
//        responseCallback: AuthHttp.ResponseCallback
//    ) {
//        val requestBody = request.getBody().toRequestBody(request.getContentType().toMediaType())
//
//        val httpRequest: Request = Request.Builder()
//            .headers(request.getHeaders().toHeaders())
//            .url(request.getUrl())
//            .post(requestBody)
//            .build()
//
//        httpclient.newCall(httpRequest).enqueue(object : Callback {
//            override fun onFailure(call: Call, e: IOException) {
//                responseCallback.onError(e.message ?: "something went wrong with okHttp")
//            }
//
//            override fun onResponse(call: Call, response: Response) {
//                responseCallback.onSuccess(response.code, response.body?.string() ?: "")
//            }
//        })
//    }
//}