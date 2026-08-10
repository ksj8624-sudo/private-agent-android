package com.example.privateagent.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.privateagent.data.network.NetworkModule
import com.example.privateagent.data.repository.HealthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HealthViewModel : ViewModel() {
    private val repository = HealthRepository(NetworkModule.agentApi)
    private val _serverStatus = MutableStateFlow("서버 확인 전")
    val serverStatus: StateFlow<String> = _serverStatus.asStateFlow()

    fun checkServer() {
        viewModelScope.launch {
            _serverStatus.value = "서버 확인 중..."
            runCatching {
                repository.getHealth()
            }.onSuccess { response ->
                _serverStatus.value = response.status;
            }.onFailure { throwable ->
                _serverStatus.value = throwable.message ?: "서버 연결 실패"
            }
        }
    }
}