package com.example.privateagent.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.privateagent.data.auth.SessionManager
import com.example.privateagent.data.network.NetworkModule
import com.example.privateagent.data.remote.dto.LoginRequest
import com.example.privateagent.data.repository.AuthRepository
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class AuthViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val repository = AuthRepository(NetworkModule.agentApi)
    private val tokenStore = NetworkModule.getTokenStore()

    var uiState by mutableStateOf(LoginUiState())
        private set

    fun login(
        email: String,
        password: String
    ) {
        if (uiState.isLoading) {
            return
        }
        if (email.isBlank() || password.isBlank()) {
            uiState = uiState.copy(
                isLoading = false,
                errorMessage = "이메일과 비밀번호를 입력해 주세요."
            )
            return
        }

        val request = LoginRequest(
            email = email.trim(),
            password = password
        )

        uiState = uiState.copy(
            isLoading = true,
            errorMessage = null
        )

        viewModelScope.launch {
            runCatching {
                val response = repository.login(request = request)

                val saved = tokenStore.saveTokens(
                    response.accessToken,
                    response.refreshToken
                )

                if (!saved) {
                    tokenStore.clearTokens()
                    throw IllegalStateException("token save failed")
                }

                response
            }.onSuccess { response ->
                uiState = uiState.copy(
                    email = response.user.email,
                    isLoading = false,
                    errorMessage = null
                )
                SessionManager.onLoginSuccess()
            }.onFailure { throwable ->
                Log.e(TAG, "login failed", throwable)

                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = LoginErrorMapper.resolve(throwable)
                )
            }
        }
    }

    fun clearError() {
        uiState = uiState.copy(
            errorMessage = null
        )
    }

    companion object {
        private const val TAG = "AuthViewModel"
    }
}