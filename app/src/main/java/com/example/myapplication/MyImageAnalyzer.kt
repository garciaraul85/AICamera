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
                            put("text", "You will be acting as a context-aware virtual assistant for my Android phone. I will be streaming a series of pictures from my phone's camera to you. Your task is to carefully examine these images and describe the scene in as much detail as possible from the point of view of the camera.\n" +
                                    "The images have been attached.\n" +
                                    "Please look over the images carefully. In your description of the scene, make sure to cover:\n" +
                                    "- What seems to be happening in the images\n" +
                                    "- What type of event or activity the images depict\n" +
                                    "- All the major objects, people, or other elements you see in the images\n" +
                                    "Describe the scene in <scene_description> tags. Try your best to remember all the key details.\n" +
                                    "After describing the scene, I may ask you a follow-up question about some aspect of it. ")
                        })
                    })
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", "<question>\n" +
                                    "Was my tv turn on or off?\n" +
                                    "</question>\n" +
                                    "If a question is listed, please answer it as thoroughly as you can based on the details you remember from the scene. Provide your answer in <answer> tags. If the question cannot be answered based on the information in the images, say so.\n" +
                                    "If no question is listed, then simply await further instructions. Remember, your goal is to be a helpful virtual assistant by understanding the context provided in the images and answering any questions about the scene to the best of your knowledge and recollection. Always be honest about what you do and do not know based on the limited information available in the images.")
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
                    Log.d("DemoActivity", "Response: $responseBody");
                    val content = responseJson.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                    Log.d("DemoActivity", "Answer: $content")
                }
            }
        })
    }
}
