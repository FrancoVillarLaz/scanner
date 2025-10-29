package com.inncome.scanner.service

import com.inncome.scanner.data.request.LoginRequest
import com.inncome.scanner.data.request.RefreshRequest
import com.inncome.scanner.data.response.LoginResponse
import com.inncome.scanner.data.response.UserResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface AuthApi {

    @POST("auth-service/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("auth-service/auth/refresh-token")
    suspend fun refreshToken(@Body request: RefreshRequest): Response<LoginResponse>


    @GET("/auth-service/auth/user_id")
    suspend fun obtenerUser(@Query("id") id: String): Response<UserResponse>
}
