package com.example.hospitalmanagement

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.*

class VoiceRecognitionService(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit
) : RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    var isListening = false
        private set

    init {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(this)
        }
        
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
            }
        }
    }

    fun startListening() {
        if (!isListening && speechRecognizer != null) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, 
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            
            speechRecognizer?.startListening(intent)
            isListening = true
        }
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        isListening = false
    }

    fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    fun shutdown() {
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
    }

    // RecognitionListener implementations
    override fun onReadyForSpeech(params: Bundle?) {
        Log.d("VoiceService", "Ready for speech")
    }

    override fun onBeginningOfSpeech() {
        Log.d("VoiceService", "Speech started")
    }

    override fun onRmsChanged(rmsdB: Float) {}

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        Log.d("VoiceService", "Speech ended")
        isListening = false
    }

    override fun onError(error: Int) {
        isListening = false
        val errorMsg = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
            else -> "Unknown error"
        }
        onError(errorMsg)
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            onResult(matches[0])
        }
        isListening = false
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(
            SpeechRecognizer.RESULTS_RECOGNITION
        )
        if (!matches.isNullOrEmpty()) {
            // Can be used for live transcription
            Log.d("VoiceService", "Partial: ${matches[0]}")
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}
}