package com.example.privateagent.data.network

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 1차 Review Critical C1(Bearer 접두사 공백 누락) 회귀 방지용 테스트.
 */
class AuthHeaderTest {

    @Test
    fun `bearer prefix includes trailing space`() {
        assertEquals("Bearer ", AuthHeader.BEARER_PREFIX)
    }

    @Test
    fun `token can be attached and extracted without altering the value`() {
        val token = "abc123.def456.ghi789"
        val headerValue = "${AuthHeader.BEARER_PREFIX}$token"

        assertEquals("Bearer $token", headerValue)
        assertEquals(token, headerValue.removePrefix(AuthHeader.BEARER_PREFIX))
    }
}
