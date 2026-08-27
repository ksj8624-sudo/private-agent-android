package com.example.privateagent.data.network

/**
 * AuthInterceptor와 TokenAuthenticator가 공유하는 Authorization 헤더 규칙.
 * BEARER_PREFIX는 반드시 공백을 포함해 "Bearer {token}" 형식을 만든다.
 */
internal object AuthHeader {
    const val NAME = "Authorization"
    const val BEARER_PREFIX = "Bearer "
}
