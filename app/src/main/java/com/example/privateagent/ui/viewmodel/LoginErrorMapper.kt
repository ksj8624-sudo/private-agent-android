package com.example.privateagent.ui.viewmodel

import com.example.privateagent.data.remote.dto.AuthErrorResponse
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

/**
 * 서버/OkHttp/Retrofit이 던지는 원본 예외 메시지를 사용자에게 그대로 노출하지 않고,
 * 알려진 error 코드와 오류 유형에 따라 고정된 안내 문구로만 매핑한다.
 * Android 프레임워크에 의존하지 않아 순수 유닛 테스트가 가능하다.
 */
object LoginErrorMapper {
    private const val GENERIC_ERROR_MESSAGE = "로그인 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
    private val json = Json { ignoreUnknownKeys = true }

    fun resolve(throwable: Throwable): String {
        return when (throwable) {
            is HttpException -> when (parseAuthError(throwable)?.error) {
                "invalid_credentials" -> "이메일 또는 비밀번호를 확인해 주세요."
                "validation_failed" -> "입력값을 확인해 주세요."
                else -> GENERIC_ERROR_MESSAGE
            }
            is IOException -> "네트워크 상태를 확인한 후 다시 시도해 주세요."
            else -> GENERIC_ERROR_MESSAGE
        }
    }

    private fun parseAuthError(exception: HttpException): AuthErrorResponse? {
        return try {
            val errorBody = exception.response()?.errorBody()?.string() ?: return null
            json.decodeFromString(AuthErrorResponse.serializer(), errorBody)
        } catch (_: Exception) {
            null
        }
    }
}
