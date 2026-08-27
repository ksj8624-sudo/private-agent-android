package com.example.privateagent.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class RefreshResponse (
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: String,
    val user: AuthUser
) {
}