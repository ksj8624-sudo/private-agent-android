package com.example.privateagent.data.auth

import com.example.privateagent.data.local.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface AuthState {
    object Initializing : AuthState
    object LoggedIn : AuthState
    object LoggedOut : AuthState
}

/**
 * 앱 전체에서 관찰 가능한 단일 인증 상태 진입점.
 * TokenStore가 진실의 원천이며, 이 객체는 그 파생 상태만 보관한다.
 */
object SessionManager {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Initializing)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun restore(tokenStore: TokenStore) {
        val accessToken: String?
        val refreshToken: String?
        try {
            accessToken = tokenStore.getAccessToken()
            refreshToken = tokenStore.getRefreshToken()
        } catch (_: Exception) {
            // 저장소 읽기 자체가 실패하면 상태를 함부로 단정하지 않고 로그아웃으로 취급한다.
            _authState.value = AuthState.LoggedOut
            return
        }

        if (isPartialTokenPair(accessToken, refreshToken)) {
            // 토큰 쌍 중 하나만 존재하는 불완전한 상태는 정리하고 로그아웃으로 취급한다.
            tokenStore.clearTokens()
        }

        _authState.value = decideRestoredState(accessToken, refreshToken)
    }

    /** Access/Refresh 중 정확히 하나만 존재하는 불완전한 상태인지 판단한다(순수 함수, 테스트용). */
    internal fun isPartialTokenPair(accessToken: String?, refreshToken: String?): Boolean {
        val hasAccess = !accessToken.isNullOrEmpty()
        val hasRefresh = !refreshToken.isNullOrEmpty()
        return hasAccess != hasRefresh
    }

    /** 토큰 쌍 존재 여부만으로 복원 상태를 판단한다(순수 함수, 테스트용). */
    internal fun decideRestoredState(accessToken: String?, refreshToken: String?): AuthState {
        return if (!accessToken.isNullOrEmpty() && !refreshToken.isNullOrEmpty()) {
            AuthState.LoggedIn
        } else {
            AuthState.LoggedOut
        }
    }

    fun onLoginSuccess() {
        _authState.value = AuthState.LoggedIn
    }

    fun onForcedLogout() {
        _authState.value = AuthState.LoggedOut
    }
}
