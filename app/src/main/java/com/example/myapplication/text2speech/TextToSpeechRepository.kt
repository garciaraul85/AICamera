package com.example.myapplication.text2speech

import com.example.myapplication.network.TextToSpeechApiService
import com.example.myapplication.network.TextToSpeechRequest
import okhttp3.ResponseBody
import retrofit2.Response
import javax.inject.Inject

class TextToSpeechRepository @Inject constructor(
    private val textToSpeechApiService: TextToSpeechApiService
) {
    suspend fun getTextToSpeech(request: TextToSpeechRequest): Response<ResponseBody> {
        return textToSpeechApiService.getTextToSpeech(request)
    }
}