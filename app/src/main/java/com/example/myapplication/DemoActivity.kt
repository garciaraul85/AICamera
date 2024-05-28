package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.ScaleGestureDetector
import android.widget.Button
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
import com.example.myapplication.text2speech.TextToSpeechManager
import com.example.myapplication.util.ImageAnalyzer
import com.example.myapplication.util.ImageApiHandler
import com.example.myapplication.util.QuestionSingleton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DemoActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView

    @Inject
    lateinit var speechRecognizerManager: SpeechRecognizerManager
    @Inject
    lateinit var textToSpeechManager: TextToSpeechManager
    @Inject
    lateinit var imageAnalyzer: ImageAnalyzer
    @Inject
    lateinit var imageApiHandler: ImageApiHandler

    private var isListening = false
    private lateinit var imageDescriptionDao: ImageDescriptionDao

    private lateinit var cameraControl: CameraControl
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    private var currentZoomRatio = 1.0f
    private var maxZoomRatio = 1.0f

    private lateinit var textViewSubtitles: SpeedMarquee

    @SuppressLint("ClickableViewAccessibility")
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

        initImageAnalyzer()
        handleSpeechRecognition(buttonRecord)
        initCameraOrPermissions()
        handleScaleGestures()
        applyWindowInsetsPadding()
    }

    private fun initImageAnalyzer() {
        imageAnalyzer.onImageEncoded = { base64Image ->
            handleImage(base64Image)
        }
        imageApiHandler.onAnswerReceived = { answer ->
            CoroutineScope(Dispatchers.IO).launch {
                Log.d("SpeechRecognizerManager", "textToSpeech: $answer")
                val duration = textToSpeechManager.textToSpeechGPT(answer)
                runOnUiThread {
                    textViewSubtitles.text = answer
                    textViewSubtitles.startScroll()
                    textViewSubtitles.setSpeed(duration.toFloat())
                }
            }
        }
    }

    private fun applyWindowInsetsPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun handleScaleGestures() {
        // Initialize the scale gesture detector
        scaleGestureDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
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
    }

    private fun handleSpeechRecognition(buttonRecord: Button) {
        speechRecognizerManager.apply {
            onResult = { result ->
                Log.d("SpeechRecognizerManager", "SpeechRecognizerManager Result")
                QuestionSingleton.addQuestion(result)
            }
            onEndSpeech = {
                Log.d("SpeechRecognizerManager", "end")
                runOnUiThread {
                    buttonRecord.text = "Start Recording"
                    isListening = false
                }
            }
        }

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
    }

    private fun handleImage(base64Image: String) {
        CoroutineScope(Dispatchers.IO).launch {
            imageApiHandler.streamImagesAndDescribe(base64Image)

            val question = QuestionSingleton.questionQueue.poll()
            Log.d("SpeechRecognizerManager", "handleImage, Question: $question here?")
            if (question != null) {
                imageApiHandler.processQuestion(base64Image, question)
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
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}