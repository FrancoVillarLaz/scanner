package com.inncome.scanner.config

import com.inncome.scanner.service.TrabajadoresApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.getValue
import android.content.Context
import com.google.gson.GsonBuilder
import com.inncome.scanner.service.AuthApi
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "https://api.app.inncome.net/api/test/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val publicHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private lateinit var tokenManagerInstance: TokenManager
    private lateinit var authApiService: AuthApi
    private lateinit var apiServiceInstance: TrabajadoresApi

    fun init(context: Context) {
        tokenManagerInstance = TokenManager(context.applicationContext)

        authApiService = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(publicHttpClient)
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
            .build()
            .create(AuthApi::class.java)

        val authenticatedClient = createAuthenticatedClient(tokenManagerInstance, authApiService)

        apiServiceInstance = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(authenticatedClient)
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
            .build()
            .create(TrabajadoresApi::class.java)
    }

    fun getTokenManager(): TokenManager = tokenManagerInstance
    fun getAuthApi(): AuthApi = authApiService

    fun getApiService(): TrabajadoresApi = apiServiceInstance

    fun createAuthenticatedClient(tokenManager: TokenManager, authApi: AuthApi): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(AuthInterceptor(tokenManager))
            .authenticator(TokenAuthenticator(tokenManager, authApi))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}