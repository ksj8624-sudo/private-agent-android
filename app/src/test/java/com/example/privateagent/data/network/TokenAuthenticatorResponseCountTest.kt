package com.example.privateagent.data.network

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TokenAuthenticator의 재시도 횟수 계산과 인증 API 경로 가드(F2)를 순수 OkHttp 객체로 검증한다.
 * TokenStore는 Android Context가 필요해 인스턴스를 만들지 않고, 상태를 갖지 않는
 * companion 함수/상수만 대상으로 한다.
 */
class TokenAuthenticatorResponseCountTest {

    private fun request(path: String = "/api/ask"): Request =
        Request.Builder().url("http://localhost:3000$path").build()

    private fun response(req: Request, prior: Response? = null): Response =
        Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .priorResponse(prior)
            .build()

    @Test
    fun `single response counts as one`() {
        assertEquals(1, TokenAuthenticator.responseCount(response(request())))
    }

    @Test
    fun `one retry counts as two`() {
        val first = response(request())
        val second = response(request(), prior = first)

        assertEquals(2, TokenAuthenticator.responseCount(second))
    }

    @Test
    fun `after one retry the request limit is reached so no further refresh is attempted`() {
        val first = response(request())
        val second = response(request(), prior = first)

        assertTrue(TokenAuthenticator.responseCount(second) >= TokenAuthenticator.MAX_REQUEST_COUNT)
    }

    @Test
    fun `auth endpoint guard paths match interceptor exclusion list`() {
        assertEquals("/auth/login", TokenAuthenticator.AUTH_LOGIN_PATH)
        assertEquals("/auth/refresh", TokenAuthenticator.AUTH_REFRESH_PATH)
        assertTrue(TokenAuthenticator.AUTH_LOGIN_PATH in AuthInterceptor.AUTH_EXCLUDED_PATHS)
        assertTrue(TokenAuthenticator.AUTH_REFRESH_PATH in AuthInterceptor.AUTH_EXCLUDED_PATHS)
    }
}
