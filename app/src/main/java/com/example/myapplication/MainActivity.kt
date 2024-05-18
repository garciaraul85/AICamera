package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ImageContent
import dev.langchain4j.data.message.TextContent
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.model.openai.OpenAiModelName.GPT_4_VISION_PREVIEW
import dev.langchain4j.model.output.Response
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val apiKey = ""
    val scope = CoroutineScope(Dispatchers.Default)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create an instance of the model using the API key
        val model = OpenAiChatModel.withApiKey(apiKey)

        scope.launch {
//            // Generate a response using the model
//            val answer = model.generate("Hello world!")
//
//            // Print the response
//            println("answer = $answer")

            val model = OpenAiChatModel.builder()
                .apiKey(apiKey) // Safe fallback if env var is null
                .modelName(GPT_4_VISION_PREVIEW)
                .maxTokens(50)
                .build()

            val userMessage = UserMessage.from(
                TextContent.from("What do you see?"),
                ImageContent.from("https://upload.wikimedia.org/wikipedia/commons/4/47/PNG_transparency_demonstration_1.png")
            )

            val response: Response<AiMessage> = model.generate(userMessage)
            println(response.content().text())
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        openMap("NY")
    }

    private fun openMap(address: String) {
        val location = "Statue of Liberty, New York"
        val uri = Uri.parse("geo:0,0?q=${Uri.encode(location)}")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setPackage("com.google.android.apps.maps")
        startActivity(intent)
    }
}
