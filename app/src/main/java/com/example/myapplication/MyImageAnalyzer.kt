package com.example.myapplication

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.pixelcarrot.base64image.Base64Image
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer

class MyImageAnalyzer(private val onImageEncoded: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val client = OkHttpClient()

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val mediaImage = image.image
        if (mediaImage != null) {
            // Process the image and log details
            val bitmap = imageProxyToBitmap(image)
            if (bitmap != null) {
                processImage(bitmap)
            }
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

        // U and V are swapped
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
        try {
            encodeImageToBase64(bitmap) { base64Image ->
                base64Image?.let {
                    onImageEncoded(it)
                    CoroutineScope(Dispatchers.Main).launch {
                        askQuestionAboutImage(it)
                    }
                } ?: run {
                    Log.e("DemoActivity", "Failed to encode image to Base64")
                }
            }
        } catch (e: Exception) {
            Log.e("DemoActivity", "Failed to read image file: ${e.message}")
        }
    }

    private fun encodeImageToBase64(bitmap: Bitmap, callback: (String?) -> Unit) {
        // Encode the bitmap to a Base64 string using the Base64Image library
        Base64Image.encode(bitmap) { base64 ->
            base64?.let {
                // Success, return the base64 string through the callback
                callback(it)
            } ?: run {
                // Failed to encode
                callback(null)
                throw IllegalArgumentException("Failed to encode bitmap to Base64")
            }
        }
    }

    private fun askQuestionAboutImage(imageBase64: String) {
        val imageUrl = "data:image/jpeg;base64,$imageBase64"
        val jsonBody = JSONObject().apply {
            put("model", "gpt-4o")
            put("temperature", 0.0)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", "You are a helpful assistant that responds in Markdown. Help me with my math homework!")
                        })
                    })
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", "What's the area of the triangle?")
                        })
                        put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", imageUrl)
                            })
                        })
                    })
                })
            })
        }

        val requestBody = RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), jsonBody.toString())
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("DemoActivity", "Failed to get response: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.let {
                    val responseBody = it.string()
                    val responseJson = JSONObject(responseBody)
                    val content = responseJson.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                    Log.d("DemoActivity", "Answer: $content")
                }
            }
        })
    }
}
