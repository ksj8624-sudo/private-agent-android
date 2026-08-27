package com.example.privateagent.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class AuthUser (
    val id: Int,
    val email: String
) {
}