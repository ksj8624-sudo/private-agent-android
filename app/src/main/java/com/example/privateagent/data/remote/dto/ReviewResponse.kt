package com.example.privateagent.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ReviewResponse(
    val ok: Boolean,
    val tool: String,
    val workspace: String,
    val type: String,
    val answer: String
) {
}