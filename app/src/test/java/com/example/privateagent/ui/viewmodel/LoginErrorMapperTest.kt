package com.example.privateagent.ui.viewmodel

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * 1차 Review M2(원본 예외 메시지 노출) 회귀 방지용 테스트.
 * 서버 message를 그대로 신뢰하지 않고 error 코드/예외 유형별 고정 문구만 반환하는지 검증한다.
 */
class LoginErrorMapperTest {

    private fun httpException(code: Int, body: String): HttpException {
        val responseBody = body.toResponseBody("application/json".toMediaType())
        return HttpException(Response.error<Any>(code, responseBody))
    }

    @Test
    fun `invalid credentials maps to account check message`() {
        val exception = httpException(
            401,
            """{"error":"invalid_credentials","message":"이메일 또는 비밀번호가 올바르지 않습니다."}"""
        )

        assertEquals("이메일 또는 비밀번호를 확인해 주세요.", LoginErrorMapper.resolve(exception))
    }

    @Test
    fun `validation failed maps to input check message`() {
        val exception = httpException(
            400,
            """{"error":"validation_failed","message":"입력값이 올바르지 않습니다."}"""
        )

        assertEquals("입력값을 확인해 주세요.", LoginErrorMapper.resolve(exception))
    }

    @Test
    fun `unknown error body maps to generic message, not raw server text`() {
        val exception = httpException(500, "")

        assertEquals(
            "로그인 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.",
            LoginErrorMapper.resolve(exception)
        )
    }

    @Test
    fun `network failure maps to network message`() {
        val exception = IOException("Unable to resolve host \"private-agent-backend\"")

        assertEquals(
            "네트워크 상태를 확인한 후 다시 시도해 주세요.",
            LoginErrorMapper.resolve(exception)
        )
    }

    @Test
    fun `unexpected exception never exposes its raw message`() {
        val exception = IllegalStateException("token save failed")

        val message = LoginErrorMapper.resolve(exception)

        assertEquals("로그인 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.", message)
        assertFalseContains(message, "token save failed")
    }

    private fun assertFalseContains(actual: String, forbidden: String) {
        org.junit.Assert.assertFalse(actual.contains(forbidden))
    }
}
