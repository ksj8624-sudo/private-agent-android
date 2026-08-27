package com.example.privateagent.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse (
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: String,
    val user: AuthUser
) {
}