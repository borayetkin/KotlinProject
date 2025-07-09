package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.*

class SpeechRecognizerManager(private val context: Context) {
    
    companion object {
        private const val TAG = "SpeechRecognizer"
    }
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var onTranscriptionResult: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    
    fun setOnTranscriptionResultListener(listener: (String) -> Unit) {
        onTranscriptionResult = listener
    }
    
    fun setOnErrorListener(listener: (String) -> Unit) {
        onError = listener
    }
    
    fun startListening() {
        if (isListening) {
            Log.w(TAG, "SpeechRecognizer already listening")
            return
        }
        
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "Speech recognition not available on this device")
            onError?.invoke("Speech recognition not available")
            return
        }
        
        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(recognitionListener)
            
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
                // Don't show UI dialog
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }
            
            speechRecognizer?.startListening(intent)
            isListening = true
            Log.i(TAG, "SpeechRecognizer started listening")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting SpeechRecognizer: ${e.message}")
            onError?.invoke("Failed to start speech recognition: ${e.message}")
            cleanup()
        }
    }
    
    fun stopListening() {
        if (!isListening) return
        
        try {
            speechRecognizer?.stopListening()
            Log.i(TAG, "SpeechRecognizer stopped listening")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping SpeechRecognizer: ${e.message}")
        }
    }
    
    fun destroy() {
        cleanup()
    }
    
    private fun cleanup() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            isListening = false
            Log.i(TAG, "SpeechRecognizer cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up SpeechRecognizer: ${e.message}")
        }
    }
    
    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Ready for speech")
        }
        
        override fun onBeginningOfSpeech() {
            Log.d(TAG, "Beginning of speech detected")
        }
        
        override fun onRmsChanged(rmsdB: Float) {
            // Audio level feedback - we already have this in VAD
        }
        
        override fun onBufferReceived(buffer: ByteArray?) {
            // Raw audio buffer - not needed for our use case
        }
        
        override fun onEndOfSpeech() {
            Log.d(TAG, "End of speech detected")
            isListening = false
        }
        
        override fun onError(error: Int) {
            val errorMessage = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error (mic conflict?)"
                SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech match found"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy (mic in use?)"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech input timeout"
                else -> "Unknown error: $error"
            }
            
            Log.w(TAG, "Speech recognition error: $errorMessage (Code: $error)")
            
            // Provide empty result for common non-critical errors
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                SpeechRecognizer.ERROR_AUDIO,
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    Log.i(TAG, "Non-critical STT error, providing empty result")
                    onTranscriptionResult?.invoke("")
                }
                else -> {
                    Log.e(TAG, "Critical STT error: $errorMessage")
                    onError?.invoke(errorMessage)
                    onTranscriptionResult?.invoke("") // Still provide empty result to continue flow
                }
            }
            
            cleanup()
        }
        
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val transcription = matches[0]
                Log.i(TAG, "Speech recognition result: \"$transcription\"")
                onTranscriptionResult?.invoke(transcription)
            } else {
                Log.w(TAG, "No speech recognition results")
                onTranscriptionResult?.invoke("")
            }
            cleanup()
        }
        
        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val partialText = matches[0]
                Log.d(TAG, "Partial speech result: \"$partialText\"")
                // Could use this for real-time feedback if needed
            }
        }
        
        override fun onEvent(eventType: Int, params: Bundle?) {
            Log.d(TAG, "Speech recognition event: $eventType")
        }
    }
} 