package com.example.privateagent.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthInterceptorExcludedPathsTest {

    @Test
    fun `excluded paths match documented auth-exempt endpoints`() {
        assertEquals(
            setOf("/health", "/auth/login", "/auth/refresh", "/api/plan"),
            AuthInterceptor.AUTH_EXCLUDED_PATHS
        )
    }

    @Test
    fun `protected endpoints are not excluded`() {
        assertFalse("/dev/agent" in AuthInterceptor.AUTH_EXCLUDED_PATHS)
        assertFalse("/api/ask" in AuthInterceptor.AUTH_EXCLUDED_PATHS)
        assertFalse("/api/review" in AuthInterceptor.AUTH_EXCLUDED_PATHS)
    }

    @Test
    fun `login and refresh paths are excluded`() {
        assertTrue("/auth/login" in AuthInterceptor.AUTH_EXCLUDED_PATHS)
        assertTrue("/auth/refresh" in AuthInterceptor.AUTH_EXCLUDED_PATHS)
    }
}
