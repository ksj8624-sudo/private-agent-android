package com.example.privateagent.data.remote

import com.example.privateagent.data.remote.dto.HealthResponse
import com.example.privateagent.data.remote.dto.LoginRequest
import com.example.privateagent.data.remote.dto.LoginResponse
import com.example.privateagent.data.remote.dto.PlanRequest
import com.example.privateagent.data.remote.dto.PlanResponse
import com.example.privateagent.data.remote.dto.RefreshRequest
import com.example.privateagent.data.remote.dto.RefreshResponse
import com.example.privateagent.data.remote.dto.ReviewRequest
import com.example.privateagent.data.remote.dto.ReviewResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AgentApi {
    @GET("health")
    suspend fun getHealth(): HealthResponse

    @POST("dev/agent")
    suspend fun requestReview(
        @Body request: ReviewRequest
    ): ReviewResponse

    @POST("api/plan")
    suspend fun requestPlan(
        @Body request: PlanRequest
    ): PlanResponse

    @POST("auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): LoginResponse

    @POST("auth/refresh")
    fun refreshAccessToken(
        @Body request: RefreshRequest
    ): Call<RefreshResponse>
}