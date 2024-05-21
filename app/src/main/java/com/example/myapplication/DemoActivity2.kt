package com.example.myapplication

import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
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
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

class DemoActivity2 : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var imageView: ImageView
    private lateinit var openDocumentLauncher: ActivityResultLauncher<Intent>
    private val openAI: OpenAI by lazy {
        createOpenAIClient()
    }

    private val PERMISSION_REQUEST_CODE = 1
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_demo)
        enableEdgeToEdge()
        previewView = findViewById(R.id.previewView)

        // Initialize the document picker
        openDocumentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                result.data?.data?.also { uri ->
                    processImage(uri)
                }
            }
        }

        if (checkPermissions()) {
            openDocument()
        } else {
            requestPermissions()
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun checkPermissions(): Boolean {
        val readImagesPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
        val readVideoPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
        val readAudioPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
        return readImagesPermission == PackageManager.PERMISSION_GRANTED ||
                readVideoPermission == PackageManager.PERMISSION_GRANTED ||
                readAudioPermission == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        Log.d("DemoActivity", "Requesting permissions")
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_MEDIA_IMAGES) ||
            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_MEDIA_VIDEO) ||
            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_MEDIA_AUDIO)) {
            showPermissionExplanation()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun showPermissionExplanation() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Storage Permission Needed")
        builder.setMessage("This app needs the Storage permission to access and process the image.")
        builder.setPositiveButton("OK") { _, _ ->
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO),
                PERMISSION_REQUEST_CODE
            )
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }
        builder.create().show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d("DemoActivity", "onRequestPermissionsResult called")
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.d("DemoActivity", "Permissions granted")
                openDocument()
            } else {
                Log.e("DemoActivity", "Permissions denied by the user.")
                val shouldShowRationale = permissions.any {
                    ActivityCompat.shouldShowRequestPermissionRationale(this, it)
                }
                if (!shouldShowRationale) {
                    showPermissionDeniedExplanation()
                } else {
                    Toast.makeText(this, "Permissions denied.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showPermissionDeniedExplanation() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Permission Denied")
        builder.setMessage("Storage permissions were denied. Please enable them in settings to proceed.")
        builder.setPositiveButton("Settings") { _, _ ->
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", packageName, null)
            intent.data = uri
            startActivity(intent)
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }
        builder.create().show()
    }

    private fun openDocument() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        openDocumentLauncher.launch(intent)
    }

    private fun processImage(uri: Uri) {
        Log.d("DemoActivity", "Processing image at URI: $uri")
        try {
            encodeImageToBase64(uri, contentResolver) { base64Image ->
                base64Image?.let {
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

    private fun createOpenAIClient(): OpenAI {
        val apiKey = ""
        val config = OpenAIConfig(
            token = apiKey,
            logLevel = LogLevel.All,
            timeout = Timeout(connect = 60.seconds, socket = 60.seconds) // 60 seconds timeout for both connect and socket
        )
        return OpenAI(config)
    }

    private fun encodeImageToBase64(uri: Uri, contentResolver: ContentResolver, callback: (String?) -> Unit) {
        val inputStream = contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        // Ensure the bitmap is not null
        if (bitmap == null) {
            callback(null)
            throw IllegalArgumentException("Failed to decode bitmap from input stream")
        }

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
                            put("text", "Describe this art style.")
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
