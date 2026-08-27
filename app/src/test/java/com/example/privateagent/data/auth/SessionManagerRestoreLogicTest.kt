package com.example.privateagent.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 앱 시작 시 세션 복원 판단 로직(F5)을 TokenStore(Android Context 필요) 없이 검증한다.
 */
class SessionManagerRestoreLogicTest {

    @Test
    fun `both tokens present restores LoggedIn`() {
        assertEquals(
            AuthState.LoggedIn,
            SessionManager.decideRestoredState("access", "refresh")
        )
    }

    @Test
    fun `both tokens missing restores LoggedOut`() {
        assertEquals(
            AuthState.LoggedOut,
            SessionManager.decideRestoredState(null, null)
        )
    }

    @Test
    fun `only access token present restores LoggedOut`() {
        assertEquals(
            AuthState.LoggedOut,
            SessionManager.decideRestoredState("access", null)
        )
    }

    @Test
    fun `only refresh token present restores LoggedOut`() {
        assertEquals(
            AuthState.LoggedOut,
            SessionManager.decideRestoredState(null, "refresh")
        )
    }

    @Test
    fun `partial pair is detected regardless of which side is missing`() {
        assertTrue(SessionManager.isPartialTokenPair("access", null))
        assertTrue(SessionManager.isPartialTokenPair(null, "refresh"))
        assertTrue(SessionManager.isPartialTokenPair("access", ""))
    }

    @Test
    fun `full pair or empty pair is not partial`() {
        assertFalse(SessionManager.isPartialTokenPair("access", "refresh"))
        assertFalse(SessionManager.isPartialTokenPair(null, null))
        assertFalse(SessionManager.isPartialTokenPair("", ""))
    }
}
