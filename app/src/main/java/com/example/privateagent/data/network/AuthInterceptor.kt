package com.example.privateagent.data.network

import com.example.privateagent.data.local.TokenStore
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val tokenStore: TokenStore
): Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val path = originalRequest.url.encodedPath

        if (path in AUTH_EXCLUDED_PATHS) {
            return chain.proceed(originalRequest)
        }

        val accessToken = tokenStore.getAccessToken()

        if (accessToken.isNullOrBlank()) {
            return chain.proceed(originalRequest)
        }

        val authenticatedRequest = originalRequest.newBuilder()
            .header(
                AuthHeader.NAME,
                "${AuthHeader.BEARER_PREFIX}$accessToken"
            )
            .build()

        return chain.proceed(authenticatedRequest)
    }

    companion object {
        internal val AUTH_EXCLUDED_PATHS = setOf(
            "/health",
            "/auth/login",
            "/auth/refresh",
            "/api/plan"
        )
    }
}