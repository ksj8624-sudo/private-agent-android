package com.example.privateagent.data.network

import android.content.Context
import com.example.privateagent.data.auth.SessionManager
import com.example.privateagent.data.config.ApiConfig
import com.example.privateagent.data.local.TokenStore
import com.example.privateagent.data.remote.AgentApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {
    private lateinit var tokenStore: TokenStore

    fun initialize(context: Context) {
        if (::tokenStore.isInitialized) {
            return
        }

        tokenStore = TokenStore(context.applicationContext)
        SessionManager.restore(tokenStore)
    }

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val refreshClient = OkHttpClient.Builder().build()
    private val refreshApi: AgentApi by lazy {
        createRetrofit(refreshClient)
            .create(AgentApi::class.java)
    }

    private val authenticatedClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(
                AuthInterceptor(tokenStore)
            )
            .authenticator(
                TokenAuthenticator(
                    tokenStore = tokenStore,
                    refreshApi = refreshApi
                )
            )
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .callTimeout(300, TimeUnit.SECONDS)
            .build()
    }

    val agentApi: AgentApi by lazy {
        createRetrofit(authenticatedClient)
            .create(AgentApi::class.java)
    }

    fun getTokenStore(): TokenStore {
        check(::tokenStore.isInitialized) {
            "NetworkModule.initialize(context)를 먼저 호출해야 합니다."
        }

        return tokenStore
    }

    private fun createRetrofit(
        client: OkHttpClient
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .client(client)
            .addConverterFactory(
                json.asConverterFactory(
                    "application/json".toMediaType()
                )
            )
            .build()
    }
}