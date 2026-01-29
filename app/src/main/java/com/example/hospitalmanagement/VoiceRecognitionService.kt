package com.example.hospitalmanagement

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.*

class VoiceRecognitionService(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onPartialResult: ((String) -> Unit)? = null // Default null for backward compatibility
) : RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    var isListening = false
        private set
    var isSpeaking = false
        private set

    // Configuration
    private var continuousMode = false
    private var shouldRestart = false

    // State
    private var lastPartialResult = ""
    private val mainHandler = Handler(Looper.getMainLooper())

    // Runnable to handle "Silence" or "Hang" in Assistant Mode
    private val resultTimeoutRunnable = Runnable {
        if (!continuousMode) { // Only force result in Assistant mode
            if (lastPartialResult.isNotBlank()) {
                Log.d("VoiceService", "Timeout - Using partial: $lastPartialResult")
                onResult(lastPartialResult)
                stopListening()
            } else {
                onError("I didn't catch that.")
                stopListening()
            }
        }
    }

    init {
        initializeRecognizer()
        initializeTTS()
    }

    private fun initializeRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(this)
        }
    }

    private fun initializeTTS() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
            }
        }
    }

    /**
     * @param continuous If true, the service restarts automatically after results (for Consultation).
     * If false, it stops after one result (for Assistant).
     */
    fun startListening(continuous: Boolean = false) {
        if (isListening || isSpeaking) return

        this.continuousMode = continuous
        this.shouldRestart = continuous
        this.lastPartialResult = ""

        mainHandler.post {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

                if (continuous) {
                    // Optimized for dictation
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000)
                } else {
                    // Optimized for commands
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000)
                }
            }

            try {
                speechRecognizer?.startListening(intent)
                isListening = true
                Log.d("VoiceService", "Started listening (Continuous: $continuous)")
            } catch (e: Exception) {
                onError("Could not start microphone")
            }
        }
    }

    fun stopListening() {
        shouldRestart = false
        continuousMode = false
        mainHandler.removeCallbacks(resultTimeoutRunnable)
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
            Log.d("VoiceService", "Stopped listening")
        }
    }

    fun speak(text: String) {
        stopListening() // Don't listen to yourself
        isSpeaking = true
        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "AI_RESPONSE")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "AI_RESPONSE")

        // Reset speaking flag after estimated time
        val estimateDuration = (text.length / 15) * 1000L
        mainHandler.postDelayed({ isSpeaking = false }, estimateDuration + 1000)
    }

    fun stopSpeaking() {
        if (tts?.isSpeaking == true) {
            tts?.stop()
        }
        isSpeaking = false
    }

    fun shutdown() {
        stopListening()
        stopSpeaking()
        speechRecognizer?.destroy()
        tts?.shutdown()
    }

    // --- Recognition Listener ---

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() { lastPartialResult = "" }
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        isListening = false
        if (!continuousMode) {
            // Assistant Mode: Wait 500ms for final result, else use partial
            mainHandler.postDelayed(resultTimeoutRunnable, 500)
        }
        // Continuous Mode: Do nothing, wait for onResults or onError to restart
    }

    override fun onError(error: Int) {
        // Ignore "Client side" errors that happen during init
        if (error == SpeechRecognizer.ERROR_CLIENT) return

        // 1. Handle Continuous Mode Restarts (Silence/Timeout/NoMatch)
        if (continuousMode && shouldRestart) {
            if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                Log.d("VoiceService", "Continuous error $error - Restarting...")
                restartListeningDelay(300)
                return
            }
        }

        // 2. Handle Assistant Mode Fallbacks
        if (!continuousMode && lastPartialResult.isNotBlank() && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
            mainHandler.removeCallbacks(resultTimeoutRunnable)
            onResult(lastPartialResult)
            return
        }

        isListening = false
        val msg = when(error) {
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permission denied"
            else -> "Error code: $error"
        }

        // Only trigger UI error callback if it's a real failure, not a loop reset
        if (!continuousMode) {
            onError(msg)
        }
    }

    override fun onResults(results: Bundle?) {
        mainHandler.removeCallbacks(resultTimeoutRunnable)
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull() ?: lastPartialResult

        if (text.isNotBlank()) {
            onResult(text)
        }
        isListening = false

        if (continuousMode && shouldRestart) {
            restartListeningDelay(100)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            lastPartialResult = matches[0]
            onPartialResult?.invoke(lastPartialResult) // Call the callback for live UI
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    private fun restartListeningDelay(delayMs: Long) {
        mainHandler.postDelayed({
            if (shouldRestart) startListening(continuous = true)
        }, delayMs)
    }
}