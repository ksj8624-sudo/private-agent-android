package com.example.privateagent.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class AuthErrorResponse (
    val error: String,
    val message: String
) {
}