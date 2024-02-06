package com.example.claraschumann

import android.Manifest
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.RecognizerIntent
import android.content.Intent
import android.widget.Button
import java.util.Locale
import android.content.pm.PackageManager
import android.graphics.Color
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.speech.tts.UtteranceProgressListener


class MainActivity : AppCompatActivity() {
    private lateinit var tts: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var startButton: Button
    private val openAI = OpenAI("OpenAI API KEY") // Replace with your API key
    private var prevMess = ""
    private var isChatActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.statusBarColor = Color.BLACK

        initTextToSpeech()
        initSpeechRecognizer()
        checkPermissions()

        startButton = findViewById(R.id.btnStart)
        startButton.setOnClickListener {
            if (isChatActive) {
                endChat()
            } else {
                startChat()
            }
        }
    }

    private fun startChat() {
        isChatActive = true
        startButton.text = "End Chat"
        startButton.setBackgroundColor(Color.RED)
        startSpeechRecognition()
    }

    private fun endChat() {
        isChatActive = false
        startButton.text = "Start Chat"
        startButton.setBackgroundColor(Color.GREEN)
        // Hier können Sie zusätzliche Bereinigungsaktionen durchführen, falls erforderlich
    }

    private val PERMISSIONS_REQUEST_RECORD_AUDIO = 1

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSIONS_REQUEST_RECORD_AUDIO)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSIONS_REQUEST_RECORD_AUDIO -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("Permission Audio", "granted")
                } else {
                    Log.d("Permission Audio", "denied")
                }
            }
        }
    }


    private fun initTextToSpeech() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts.setLanguage(Locale.GERMAN)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "The Language specified is not supported!")
                } else {
                    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            // Wird aufgerufen, wenn die Sprachausgabe beginnt
                        }

                        override fun onDone(utteranceId: String?) {
                            // Wird aufgerufen, wenn die Sprachausgabe beendet ist
                            if (isChatActive) {
                                runOnUiThread {
                                    startSpeechRecognition() // Warten auf neue Eingabe nach der Sprachausgabe
                                }
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            // Wird aufgerufen, wenn ein Fehler bei der Sprachausgabe auftritt
                        }
                    })
                }
            } else {
                Log.e("TTS", "Initialization failed!")
            }
        }
    }



    private fun initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("SpeechRecognition", "onReadyForSpeech")
                // Invoked when the recognizer is ready to listen
            }

            override fun onBeginningOfSpeech() {
                // Invoked when the user starts speaking
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Invoked when the sound level in the audio stream has changed
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Invoked when more sound has been received
            }

            override fun onEndOfSpeech() {
                // Invoked after the user stops speaking
            }

            override fun onError(error: Int) {
                Log.e("SpeechRecognition", "onError: $error")
                // Invoked when a recognition error occurs
                // Implement error handling based on error codes
            }

            override fun onResults(results: Bundle?) {
                Log.d("SpeechRecognition", "onResults")
                // Invoked when final recognition results are available
                results?.let {
                    val matches = it.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (matches != null && matches.isNotEmpty()) {
                        val text = matches[0] // Get the first match
                        handleSpeechInput(text)
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                // Invoked when partial recognition results are available
                // This can be used to give real-time feedback to the user
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                // Invoked when an event occurs
            }
        })
    }

    private fun getResponseFromOpenAI(inputText: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val chatCompletionRequest = ChatCompletionRequest(
                    model = ModelId("ft:gpt-3.5-turbo-1106:personal::8imysXT7"),
                    messages = listOf(
                        ChatMessage(
                            role = ChatRole.System,
                            content = "Clara Schumann ist eine sachkundige und doch spielerische Gesprächspartnerin aus dem 19. Jahrhundert am Telefon, die sich wie eine Frau, die am Telefon gern sehr viel von sich selbst erzählt, und sich nicht wie ein Chatbot verhält. Sie stellt gern viele Fragen und versucht immer, das Gespräch am Laufen zu halten und auf sich, ihr Leben und das Leben ihrer Bekannten zu lenken. Sie spricht einen immer mit du an und gibt sehr ausführliche Antworten. Clara Schumann hat nur Wissen über Dinge in ihrer Lebenszeit und die im 19. Jahrhundert oder eher existierten. Sie kann mit späteren Ereignissen nichts anfangen und sagt das auch, aber auf kreative Weise. Clara Schumann antwortete zuvor: $prevMess"
                        ),
                        ChatMessage(
                            role = ChatRole.User,
                            content = inputText
                        )
                    )
                )
                val completion: ChatCompletion = openAI.chatCompletion(chatCompletionRequest)
                val messageContent = completion.choices[0].message.content
                prevMess = messageContent.toString()

                Log.e("CompletionRequest", "Answer: $messageContent")

                runOnUiThread {
                    speakOut(messageContent.toString())
                }
            } catch (e: Exception) {
                Log.e("API Error", "Error during API call: ${e.message}")
            }
        }
    }

    private fun startSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE") // German language code
        speechRecognizer.startListening(intent)
    }

    private fun handleSpeechInput(text: String) {
        if (isChatActive) {
            getResponseFromOpenAI(text)
        }
    }

    private fun speakOut(text: String) {
        val utteranceId = this.hashCode().toString() + System.currentTimeMillis()
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }


    override fun onDestroy() {
        tts.shutdown()
        speechRecognizer.destroy()
        super.onDestroy()
    }
}
