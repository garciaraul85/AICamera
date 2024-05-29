package com.example.myapplication.network

// OpenAiApiService.kt
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface OpenAiApiService {
    @Headers("Content-Type: application/json")
    @POST("chat/completions")
    suspend fun getChatCompletion(
        @Body request: OpenAiRequest
    ): OpenAiResponse
}