package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ScaleGestureDetector
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication.db.ImageDescriptionDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.resumeWithException

class DemoActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var speechRecognizerManager: SpeechRecognizerManager
    private var isListening = false
    private lateinit var imageAnalyzer: MyImageAnalyzer
    private lateinit var imageDescriptionDao: ImageDescriptionDao

    private lateinit var cameraControl: CameraControl
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    private var currentZoomRatio = 1.0f
    private var maxZoomRatio = 1.0f

    private lateinit var textViewSubtitles: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_demo)
        previewView = findViewById(R.id.previewView)
        textViewSubtitles = findViewById(R.id.textView_subtitles)

        // Get the database instance from the application context
        val app = application as App
        imageDescriptionDao = app.database.imageDescriptionDao()

        val buttonRecord: Button = findViewById(R.id.button_record)

        imageAnalyzer = MyImageAnalyzer(imageDescriptionDao, { base64Image ->
            handleImage(base64Image)
        }, { answer ->
            CoroutineScope(Dispatchers.IO).launch {
                Log.d("SpeechRecognizerManager", "textToSpeech: $answer")
//                textToSpeech(answer)
                textToSpeechGPT(answer)
            }
        })

        speechRecognizerManager = SpeechRecognizerManager(this, { result ->
            Log.d("SpeechRecognizerManager", "SpeechRecognizerManager Result")
            imageAnalyzer.addQuestion(result)
        }, {
            // Callback for end of speech
            Log.d("SpeechRecognizerManager", "end")
            runOnUiThread {
                buttonRecord.text = "Start Recording"
                isListening = false
            }
        })

        buttonRecord.setOnClickListener {
            if (checkPermissions()) {
                if (isListening) {
                    speechRecognizerManager.stopListening()
                    buttonRecord.text = "Start Recording"
                } else {
                    speechRecognizerManager.startListening()
                    buttonRecord.text = "Stop Recording"
                }
                isListening = !isListening
            }
        }

        initCameraOrPermissions()

        // Initialize the scale gesture detector
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor
                currentZoomRatio = (currentZoomRatio * scaleFactor).coerceIn(1.0f, maxZoomRatio)
                cameraControl.setZoomRatio(currentZoomRatio)
                return true
            }
        })

        // Attach touch listener to the preview view
        previewView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            true
        }


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun handleImage(base64Image: String) {
        CoroutineScope(Dispatchers.IO).launch {
            // Send the image description request
            imageAnalyzer.streamImagesAndDescribe(base64Image)

            // Process the question if there is any
            val question = imageAnalyzer.questionQueue.poll()
            Log.d("SpeechRecognizerManager", "handleImage, Question: $question here?")
            if (question != null) {
                imageAnalyzer.processQuestion(base64Image, question)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizerManager.destroy()
    }

    private fun checkPermissions(): Boolean {
        val requiredPermissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET
        )
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        return if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 0)
            false
        } else {
            true
        }
    }

    private fun initCameraOrPermissions() {
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(ContextCompat.getMainExecutor(this), imageAnalyzer)
                }

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer
                )
                cameraControl = camera.cameraControl
                maxZoomRatio = camera.cameraInfo.zoomState.value?.maxZoomRatio ?: 1.0f
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    val CHUNK_SIZE = 1024
    val XI_API_KEY = ""
    val VOICE_ID = ""
    val OUTPUT_FILENAME = "output.mp3"
    val bitrate = 128000
    private val API = ""

    private suspend fun textToSpeechGPT(text: String) {
        val client = OkHttpClient()
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
            .addHeader("Authorization", API)
            .build()

        Log.d("TextToSpeech", "Request URL: ${request.url}")
        Log.d("TextToSpeech", "Request Headers: ${request.headers}")

        try {
            val response = client.newCall(request).await()
            if (response.isSuccessful) {
                var totalBytesRead = 0
                response.body?.let { responseBody ->
                    val file = File(filesDir, OUTPUT_FILENAME)
                    FileOutputStream(file).use { outputStream ->
                        responseBody.byteStream().use { inputStream ->
                            val buffer = ByteArray(CHUNK_SIZE)
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
                    val numChunks = totalBytesRead / CHUNK_SIZE

                    // Log audio information
                    Log.d("textToSpeech", "Audio Duration: $duration ms")
                    Log.d("textToSpeech", "Audio File Size: $fileSize bytes")
                    Log.d("textToSpeech", "Number of Chunks: $numChunks")

                    // Check if audio is complete
                    val expectedFileSize = duration * bitrate / 8
                    if (fileSize < expectedFileSize) {
                        Log.e("textToSpeech", "Audio file is incomplete.")
                    } else {
                        playAudio(file)
                    }
                    // Update TextView with TTS text
                    runOnUiThread {
                        textViewSubtitles.text = text
                    }
                } ?: println("Failed to get response body.")
            } else {
                Log.e("TextToSpeech", "Request failed: ${response.message}")
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("TextToSpeech", "Error during request: ${e.message}")
        }
  }

    private suspend fun textToSpeech(text: String) {
        val client = OkHttpClient()
        val mediaType = "application/json".toMediaType()
        val escapedText = text.replace("\"", "\\\"").replace("\n", "\\n")
        Log.d("textToSpeech", "escapedText: $escapedText")
        val requestBodyString = """
    {
        "text": "$escapedText",
        "model_id": "eleven_multilingual_v2",
        "voice_settings": {
            "stability": 0.5,
            "similarity_boost": 0.8,
            "style": 0.0,
            "use_speaker_boost": true
        }
    }
    """.trimIndent()

        // Log the JSON string
        Log.d("textToSpeech", "Request Body: $requestBodyString")

        // Convert the JSON string to RequestBody
        val requestBody = requestBodyString.toRequestBody(mediaType)

        val request = Request.Builder()
            .url("https://api.elevenlabs.io/v1/text-to-speech/$VOICE_ID/stream")
            .post(requestBody)
            .addHeader("xi-api-key", XI_API_KEY)
            .addHeader("Content-Type", "application/json")
            .build()

        // Log the request details
        Log.d("textToSpeech", "Request URL: ${request.url}")
        Log.d("textToSpeech", "Request Headers: ${request.headers}")

        try {
            val response = client.newCall(request).await()

            if (response.isSuccessful) {
                var totalBytesRead = 0

                response.body?.let { responseBody ->
                    val file = File(filesDir, OUTPUT_FILENAME)
                    FileOutputStream(file).use { outputStream ->
                        responseBody.byteStream().use { inputStream ->
                            val buffer = ByteArray(CHUNK_SIZE)
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
                    val numChunks = totalBytesRead / CHUNK_SIZE

                    // Log audio information
                    Log.d("textToSpeech", "Audio Duration: $duration ms")
                    Log.d("textToSpeech", "Audio File Size: $fileSize bytes")
                    Log.d("textToSpeech", "Number of Chunks: $numChunks")

                    // Check if audio is complete
                    val expectedFileSize = duration * bitrate / 8
                    if (fileSize < expectedFileSize) {
                        Log.e("textToSpeech", "Audio file is incomplete.")
                    } else {
                        playAudio(file)
                    }
                } ?: println("Failed to get response body.")
            } else {
                println("Request failed: $response")
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) {}
            }

            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }
        })
    }

    private fun playAudio(file: File) {
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
                        Log.d("playAudio", "Current position: ${mediaPlayer.currentPosition / 1000} seconds")
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
        }
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}