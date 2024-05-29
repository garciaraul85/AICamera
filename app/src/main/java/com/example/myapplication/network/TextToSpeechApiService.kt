package com.example.myapplication.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface TextToSpeechApiService {
    @Headers("Content-Type: application/json")
    @POST("audio/speech")
    suspend fun getTextToSpeech(
        @Body requestBody: TextToSpeechRequest
    ): Response<ResponseBody>
}