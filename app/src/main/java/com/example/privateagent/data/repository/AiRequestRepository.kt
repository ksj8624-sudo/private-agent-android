package com.example.privateagent.data.repository

import com.example.privateagent.data.remote.AgentApi
import com.example.privateagent.data.remote.dto.PlanRequest
import com.example.privateagent.data.remote.dto.PlanResponse
import com.example.privateagent.data.remote.dto.ReviewRequest
import com.example.privateagent.data.remote.dto.ReviewResponse

class AiRequestRepository (
    private val agentApi: AgentApi
) {

    suspend fun requestReview(
        request: ReviewRequest
    ): ReviewResponse {
        return agentApi.requestReview(request)
    }

    suspend fun requestPlan(
        request: PlanRequest
    ): PlanResponse {
        return agentApi.requestPlan(request)
    }
}