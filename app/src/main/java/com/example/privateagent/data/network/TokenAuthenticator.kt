package com.example.privateagent.data.network

import com.example.privateagent.data.auth.SessionManager
import com.example.privateagent.data.local.TokenStore
import com.example.privateagent.data.remote.AgentApi
import com.example.privateagent.data.remote.dto.RefreshRequest
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(
    private val tokenStore: TokenStore,
    private val refreshApi: AgentApi
): Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        val path = response.request.url.encodedPath

        // 로그인/리프레시 요청 자체의 401은 Access Token 만료가 아니므로 절대 갱신을 시도하지 않는다.
        if (path == AUTH_LOGIN_PATH || path == AUTH_REFRESH_PATH) {
            return null
        }

        // 갱신 후 재요청도 401이면 더 이상 반복하지 않는다.
        if (responseCount(response) >= MAX_REQUEST_COUNT) {
            return null
        }

        return synchronized(this) {
            val requestAccessToken = response.request
                .header(AuthHeader.NAME)
                ?.removePrefix(AuthHeader.BEARER_PREFIX)

            val currentAccessToken = try {
                tokenStore.getAccessToken()
            } catch (_: Exception) {
                // 저장소 읽기 실패: 기존 토큰을 유지하고 이번 요청만 포기한다.
                return@synchronized null
            }

            /*
             * 대기하는 동안 다른 요청이 이미 Refresh를 완료했다면
             * 새 Refresh를 실행하지 않고 저장된 최신 토큰을 사용한다.
             */
            if (!currentAccessToken.isNullOrEmpty() &&
                currentAccessToken != requestAccessToken) {
                return@synchronized response.request.newBuilder()
                    .header(
                        AuthHeader.NAME,
                        "${AuthHeader.BEARER_PREFIX}$currentAccessToken"
                    ).build()
            }

            val refreshToken = try {
                tokenStore.getRefreshToken()
            } catch (_: Exception) {
                return@synchronized null
            }

            if (refreshToken.isNullOrEmpty()) {
                tokenStore.clearTokens()
                SessionManager.onForcedLogout()
                return@synchronized null
            }

            val refreshResponse = try {
                refreshApi.refreshAccessToken(RefreshRequest(refreshToken)).execute()
            } catch (_: Exception) {
                // 네트워크 오류인 경우 기존 토큰을 유지한다.
                return@synchronized null
            }


            if (!refreshResponse.isSuccessful) {
                if (
                    refreshResponse.code() == 400 ||
                    refreshResponse.code() == 401
                ) {
                    tokenStore.clearTokens()
                    SessionManager.onForcedLogout()
                }
                return@synchronized null
            }

            val body = refreshResponse.body() ?: return@synchronized null

            val saved = try {
                tokenStore.saveTokens(
                    accessToken = body.accessToken,
                    refreshToken = body.refreshToken
                )
            } catch (_: Exception) {
                false
            }

            if (!saved) {
                tokenStore.clearTokens()
                SessionManager.onForcedLogout()
                return@synchronized null
            }

            response.request.newBuilder()
                .header(
                    AuthHeader.NAME,
                    "${AuthHeader.BEARER_PREFIX}${body.accessToken}"
                )
                .build()
        }
    }

    companion object {
        internal const val AUTH_LOGIN_PATH = "/auth/login"
        internal const val AUTH_REFRESH_PATH = "/auth/refresh"
        internal const val MAX_REQUEST_COUNT = 2

        internal fun responseCount(response: Response): Int {
            var count = 1
            var priorResponse = response.priorResponse

            while (priorResponse != null) {
                count++
                priorResponse = priorResponse.priorResponse
            }

            return count
        }
    }
}