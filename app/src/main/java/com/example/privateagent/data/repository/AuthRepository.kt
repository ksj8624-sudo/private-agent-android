package com.example.privateagent.data.repository

import com.example.privateagent.data.remote.AgentApi
import com.example.privateagent.data.remote.dto.LoginRequest
import com.example.privateagent.data.remote.dto.LoginResponse

class AuthRepository (
    private val agentApi: AgentApi
) {
    suspend fun login(
        request: LoginRequest
    ) : LoginResponse {
        return agentApi.login(request)
    }
}