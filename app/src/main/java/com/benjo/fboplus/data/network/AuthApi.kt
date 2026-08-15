package com.benjo.fboplus.data.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class RegisterRequest(val email: String, val phone: String)
data class VerifyEmailRequest(val email: String, val code: String)
data class ResendCodeRequest(val email: String)
data class PinRequest(val pin: String)

data class RegisterResponse(val token: String? = null, val message: String? = null)
data class VerifyCodeResponse(val token: String, val hasPinSet: Boolean)
data class AuthResponse(val token: String)
data class MessageResponse(val message: String)

interface AuthApi {
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>

    @POST("auth/verify-email")
    suspend fun verifyEmail(@Body request: VerifyEmailRequest): Response<VerifyCodeResponse>

    @POST("auth/resend-code")
    suspend fun resendCode(@Body request: ResendCodeRequest): Response<MessageResponse>

    @POST("auth/set-pin")
    suspend fun setPin(
        @Header("Authorization") token: String,
        @Body request: PinRequest
    ): Response<MessageResponse>

    @POST("auth/login-pin")
    suspend fun loginPin(
        @Header("Authorization") token: String,
        @Body request: PinRequest
    ): Response<AuthResponse>
}