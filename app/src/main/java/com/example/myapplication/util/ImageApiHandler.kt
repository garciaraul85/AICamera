package com.example.myapplication.util

import android.util.Log
import com.example.myapplication.db.ImageDescription
import com.example.myapplication.network.Content
import com.example.myapplication.network.ImageUrl
import com.example.myapplication.network.Message
import com.example.myapplication.network.OpenAiRequest
import com.example.myapplication.repo.ImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ImageApiHandler @Inject constructor(
    private val imageRepository: ImageRepository,
    var onAnswerReceived: (String) -> Unit
) {

    suspend fun streamImagesAndDescribe(imageBase64: String) {
        val imageUrl = "data:image/jpeg;base64,$imageBase64"
        val systemContent = Content(
            type = "text",
            text = "You will be acting as a context-aware virtual assistant for my Android phone. I will be streaming a series of pictures from my phone's camera to you. Your task is to carefully examine these images and describe the scene in as much detail as possible from the point of view of the camera.\nThe images have been attached.\nPlease look over the images carefully. In your description of the scene, make sure to cover:\n- What seems to be happening in the images\n- What type of event or activity the images depict\n- All the major objects, people, or other elements you see in the images\nDescribe the scene in <scene_description> tags. Try your best to remember all the key details.\nAfter describing the scene, I may ask you a follow-up question about some aspect of it."
        )
        val userContent = Content(
            type = "image_url",
            imageUrl = ImageUrl(url = imageUrl)
        )
        val request = OpenAiRequest(
            model = "gpt-4o",
            temperature = 0.0,
            messages = listOf(
                Message(
                    role = "system",
                    content = listOf(systemContent)
                ),
                Message(
                    role = "user",
                    content = listOf(userContent)
                )
            )
        )

        try {
            val response = withContext(Dispatchers.IO) {
                imageRepository.getChatCompletion(request)
            }
            val content = response.choices.firstOrNull()?.message?.content
            content?.let {
                Log.d(TAG, "streamImagesAndDescribe result: $it")
                if (it.isNotBlank()) {
                    val description = ImageDescription(
                        description = it,
                        encodedImage = imageBase64,
                        timestamp = System.currentTimeMillis()
                    )
                    imageRepository.insertDescription(description)
                    if (imageRepository.getLast20Descriptions().size > 20) {
                        imageRepository.deleteOldest(1)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get response: ${e.message}")
        }
    }

    suspend fun processQuestion(imageBase64: String, question: String) {
        Log.d(TAG, "processQuestion: $question")
        val imageUrl = "data:image/jpeg;base64,$imageBase64"
        val systemContent = Content(
            type = "text",
            text = "You will be acting as a context-aware virtual assistant. Answer the following question based on the previous descriptions and the current image. I just need you to answer the question only, I don't need anything else for you to answer."
        )
        val userContentImage = Content(
            type = "image_url",
            imageUrl = ImageUrl(url = imageUrl)
        )

        // Fetch previous descriptions on the IO thread
        val previousDescriptions = withContext(Dispatchers.IO) {
            imageRepository.getLast20Descriptions().map {
                Message(
                    role = "system",
                    content = listOf(Content(
                        type = "text",
                        text = it.description
                    ))
                )
            }
        }

        val userContentQuestion = Message(
            role = "user",
            content = listOf(Content(
                type = "text",
                text = question
            ))
        )

        val request = OpenAiRequest(
            model = "gpt-4o",
            temperature = 0.0,
            messages = listOf(
                Message(
                    role = "system",
                    content = listOf(systemContent)
                )
            ) + previousDescriptions + listOf(
                userContentQuestion,
                Message(
                    role = "user",
                    content = listOf(userContentImage)
                )
            )
        )

        try {
            val response = withContext(Dispatchers.IO) {
                imageRepository.getChatCompletion(request)
            }
            val content = response.choices.firstOrNull()?.message?.content
            content?.let {
                Log.d(TAG, "processQuestion result: $it")
                if (it.isNotBlank()) {
                    onAnswerReceived(it)
                    QuestionSingleton.clearQuestion()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get response: ${e.message}")
        }
    }

    companion object {
        const val TAG = "ImageApiHandler"
    }
}
