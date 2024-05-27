package com.example.myapplication.text2speech

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.myapplication.util.Constants
import com.example.myapplication.util.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject

class TextToSpeechManager @Inject constructor(
    private val context: Context,
    private val client: OkHttpClient
) {

    suspend fun textToSpeechGPT(text: String): Int {
        val mediaType = "application/json".toMediaType()

        val escapedText = text.replace("\"", "\\\"").replace("\n", "\\n")
        Log.d("textToSpeech", "escapedText: $escapedText")

        // Create the JSON string with proper formatting
        val jsonBody = """
        {
            "model": "tts-1",
            "input": "$escapedText",
            "voice": "alloy"
        }
        """.trimIndent()

        // Log the JSON string for debugging
        Log.d("TextToSpeech", "Request Body: $jsonBody")

        // Convert the JSON string to RequestBody
        val requestBody: RequestBody = jsonBody.toRequestBody(mediaType)

        // Build the request
        val request = Request.Builder()
            .url("https://api.openai.com/v1/audio/speech")
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", Constants.API_KEY)
            .build()

        Log.d("TextToSpeech", "Request URL: ${request.url}")
        Log.d("TextToSpeech", "Request Headers: ${request.headers}")

        return try {
            val response = client.newCall(request).await()
            if (response.isSuccessful) {
                var totalBytesRead = 0
                response.body?.let { responseBody ->
                    val file = File(context.filesDir, Constants.OUTPUT_FILENAME)
                    FileOutputStream(file).use { outputStream ->
                        responseBody.byteStream().use { inputStream ->
                            val buffer = ByteArray(Constants.CHUNK_SIZE)
                            var bytesRead: Int
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead
                            }
                        }
                    }

                    // Get audio information
                    val duration = MediaPlayer().apply { setDataSource(file.path) }.duration
                    val fileSize = file.length()
                    val numChunks = totalBytesRead / Constants.CHUNK_SIZE

                    // Check if audio is complete
                    val expectedFileSize = duration * Constants.bitrate / 8
                    if (fileSize < expectedFileSize) {
                        Log.e("textToSpeech", "Audio file is incomplete.")
                        0
                    } else {
                        val fileDuration = playAudio(file) / 1000
                        Log.d("textToSpeech", "Audio Duration: ${duration / 1000} s")
                        Log.d("textToSpeech", "Audio File Size: $fileSize bytes")
                        Log.d("textToSpeech", "Number of Chunks: $numChunks")
                        fileDuration
                    }
                } ?: run {
                    Log.e("TextToSpeech", "Failed to get response body.")
                    0
                }
            } else {
                Log.e("TextToSpeech", "Request failed: ${response.message}")
                0
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("TextToSpeech", "Error during request: ${e.message}")
            0
        }
    }

    private fun playAudio(file: File): Int {
        val mediaPlayer = MediaPlayer()
        try {
            Log.d("playAudio", "Playing audio from: ${file.path}")
            mediaPlayer.setDataSource(file.path)
            mediaPlayer.prepare()
            mediaPlayer.start()

            // Handler to log the playback position every second
            val handler = Handler(Looper.getMainLooper())
            val runnable = object : Runnable {
                override fun run() {
                    if (mediaPlayer.isPlaying) {
                        Log.d(
                            "playAudio",
                            "Current position: ${mediaPlayer.currentPosition / 1000} seconds"
                        )
                        handler.postDelayed(this, 1000)
                    }
                }
            }

            // Start logging playback position
            handler.post(runnable)

            mediaPlayer.setOnCompletionListener {
                Log.d("playAudio", "Audio completed, deleting file: ${file.path}")
                it.release()
                file.delete()
                handler.removeCallbacks(runnable)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("playAudio", "Error playing audio: ${e.message}")
        } finally {
            return mediaPlayer.duration
        }
    }
}
