package com.example.myapplication.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.myapplication.db.ImageDescription
import com.example.myapplication.db.ImageDescriptionDao
import com.example.myapplication.network.Content
import com.example.myapplication.network.ImageUrl
import com.example.myapplication.network.Message
import com.example.myapplication.network.OpenAiApiService
import com.example.myapplication.network.OpenAiRequest
import com.example.myapplication.util.Constants.API_KEY
import com.pixelcarrot.base64image.Base64Image
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class ImageAnalyzer @Inject constructor(
    private val imageDescriptionDao: ImageDescriptionDao,
    private val openAiApiService: OpenAiApiService,
    var onImageEncoded: (String) -> Unit,
    var onAnswerReceived: (String) -> Unit
) : ImageAnalysis.Analyzer {
    private var lastCallTimestamp = 0L

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()

        // Throttle the streamImagesAndDescribe call to every 5 seconds
        if (currentTimestamp - lastCallTimestamp >= TimeUnit.SECONDS.toMillis(5)) {
            lastCallTimestamp = currentTimestamp
            val mediaImage = image.image
            if (mediaImage != null) {
                val bitmap = imageProxyToBitmap(image)
                bitmap?.let {
                    processImage(it)
                }
                image.close()
            }
        } else {
            image.close()
        }
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        val yBuffer: ByteBuffer = image.planes[0].buffer // Y
        val uBuffer: ByteBuffer = image.planes[1].buffer // U
        val vBuffer: ByteBuffer = image.planes[2].buffer // V

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 50, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private fun processImage(bitmap: Bitmap) {
        encodeImageToBase64(bitmap) { base64Image ->
            base64Image?.let {
                onImageEncoded(it)
                CoroutineScope(Dispatchers.IO).launch {
                    streamImagesAndDescribe(it)
                }
            } ?: run {
                Log.e(TAG, "Failed to encode image to Base64")
            }
        }
    }

    private fun encodeImageToBase64(bitmap: Bitmap, callback: (String?) -> Unit) {
        Base64Image.encode(bitmap) { base64 ->
            base64?.let {
                callback(it)
            } ?: run {
                callback(null)
                throw IllegalArgumentException("Failed to encode bitmap to Base64")
            }
        }
    }

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
                openAiApiService.getChatCompletion(API_KEY, request)
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
                    imageDescriptionDao.insertDescription(description)
                    if (imageDescriptionDao.getLast20Descriptions().size > 20) {
                        imageDescriptionDao.deleteOldest(1)
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
            imageDescriptionDao.getLast20Descriptions().map {
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
                openAiApiService.getChatCompletion(API_KEY, request)
            }
            val content = response.choices.firstOrNull()?.message?.content
            content?.let {
                Log.d(TAG, "processQuestion result: $it")
                if (it.isNotBlank()) {
                    withContext(Dispatchers.IO) {
                        onAnswerReceived(it)
                        QuestionSingleton.clearQuestion()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get response: ${e.message}")
        }
    }

    companion object {
        const val TAG = "ImageAnalyzer"
    }
}