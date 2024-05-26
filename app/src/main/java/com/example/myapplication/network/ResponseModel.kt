package com.example.myapplication.network

import com.google.gson.annotations.SerializedName

data class OpenAiResponse(
    @SerializedName("id") val id: String,
    @SerializedName("object") val obj: String,
    @SerializedName("created") val created: Long,
    @SerializedName("model") val model: String,
    @SerializedName("choices") val choices: List<Choice>,
    @SerializedName("usage") val usage: Usage
)

data class Choice(
    @SerializedName("message") val message: MessageResponse,
    @SerializedName("finish_reason") val finishReason: String,
    @SerializedName("index") val index: Int
)

data class MessageResponse(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String
)

data class Usage(
    @SerializedName("prompt_tokens") val promptTokens: Int,
    @SerializedName("completion_tokens") val completionTokens: Int,
    @SerializedName("total_tokens") val totalTokens: Int
)