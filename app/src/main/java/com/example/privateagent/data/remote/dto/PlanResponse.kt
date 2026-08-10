package com.example.privateagent.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PlanResponse (
    val ok: Boolean,
    val topic: String,
    val answer: String
) {
}