package com.example.privateagent.data.repository

import com.example.privateagent.data.remote.AgentApi
import com.example.privateagent.data.remote.dto.HealthResponse

class HealthRepository (
    private val agentApi: AgentApi
) {
    suspend fun getHealth(): HealthResponse {
        return agentApi.getHealth()
    }
}