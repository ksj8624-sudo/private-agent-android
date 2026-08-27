package com.example.privateagent.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class RefreshRequest (
    val refreshToken: String
) {
}