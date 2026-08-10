package com.example.privateagent.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PlanRequest (
    val topic: String
) {
}