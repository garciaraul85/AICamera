package com.example.myapplication.network

import com.google.gson.annotations.SerializedName

data class OpenAiRequest(
    @SerializedName("model") val model: String,
    @SerializedName("temperature") val temperature: Double,
    @SerializedName("messages") val messages: List<Message>
)

data class Message(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: List<Content>
)

data class Content(
    @SerializedName("type") val type: String,
    @SerializedName("text") val text: String? = null,
    @SerializedName("image_url") val imageUrl: ImageUrl? = null
)

data class ImageUrl(
    @SerializedName("url") val url: String
)