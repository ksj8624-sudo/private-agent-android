package com.example.privateagent.data.remote.dto

import kotlinx.serialization.Serializable


@Serializable
data class HealthResponse(
    val ok: Boolean,
    val status: String,
    val service: String
)