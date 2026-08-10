package com.example.privateagent.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ReviewRequest (
    val agentType: String,
    val workspace: String,
    val taskType: String,
    val task: String
) {
}