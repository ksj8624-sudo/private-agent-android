package com.example.privateagent.ui.viewmodel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.privateagent.data.network.NetworkModule
import com.example.privateagent.data.remote.dto.PlanRequest
import com.example.privateagent.data.repository.AiRequestRepository
import kotlinx.coroutines.launch

data class PlanUiState (
    val isLoading: Boolean = false,
    val question: String = "",
    val result: String = "",
    val errorMessage: String? = null
)

class PlanViewModel() : ViewModel() {
    private val repository = AiRequestRepository(NetworkModule.agentApi)

    var uiState by mutableStateOf(PlanUiState())
        private set

    fun requestPlan(
        contents: String
    ) {
        val request = PlanRequest(
            topic = contents
        )

        uiState = uiState.copy(
            isLoading = true,
            result = "",
            errorMessage = null
        )

        viewModelScope.launch {
            runCatching {
                repository.requestPlan(request)
            }.onSuccess { response ->
                Log.i("success", response.answer)
                val result = if (response.ok) {
                    response.answer
                } else {
                    ""
                }

                val error = if (response.ok) {
                    null
                } else {
                    "플랜 요청 실패"
                }

                uiState = uiState.copy(
                    isLoading = false,
                    result = result,
                    errorMessage = error
                )
            }.onFailure { throwable ->
                Log.e(
                    "ReviewViewModel",
                    """
                        request failed
                        type=${throwable::class.java.name}
                        message=${throwable.message}
                        cause=${throwable.cause}
                        """.trimIndent(),
                                    throwable
                                )

                uiState = uiState.copy(
                    isLoading = false,
                    result = "",
                    errorMessage = throwable.message ?: "플랜 요청에 실패했습니다."
                )
                Log.e("error", throwable.message ?: "플랜 요청에 실패했습니다.")
            }
        }
        println("ReviewRequest = $request")
    }
}