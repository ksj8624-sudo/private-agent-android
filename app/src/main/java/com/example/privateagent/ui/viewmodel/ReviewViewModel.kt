package com.example.privateagent.ui.viewmodel

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.example.privateagent.data.network.NetworkModule
import com.example.privateagent.data.remote.dto.ReviewRequest
import com.example.privateagent.data.repository.AiRequestRepository
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class ReviewUiState (
    val isLoading: Boolean = false,
    val question: String = "",
    val result: String = "",
    val errorMessage: String? = null
)

class ReviewViewModel() : ViewModel() {
    private val repository = AiRequestRepository(NetworkModule.agentApi)
    var uiState by mutableStateOf(ReviewUiState())
        private set

    fun requestReview(
        workspace: String,
        contents: String
    ) {
        val request = ReviewRequest(
            agentType = "cursor",
            workspace = workspace,
            taskType = "review",
            task = contents
        )

        uiState = uiState.copy(
            isLoading = true,
            result = "",
            errorMessage = null
        )

        viewModelScope.launch {
            runCatching {
                repository.requestReview(request)
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
                    "리뷰 요청 실패"
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
                    errorMessage = throwable.message ?: "리뷰 요청에 실패했습니다."
                )
                Log.e("error", throwable.message ?: "리뷰 요청에 실패했습니다.")
            }
        }
        println("ReviewRequest = $request")
    }
}