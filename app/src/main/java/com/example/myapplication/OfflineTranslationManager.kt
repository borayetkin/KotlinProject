package com.example.myapplication

import android.content.Context
import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class OfflineTranslationManager(private val context: Context) {
    
    companion object {
        private const val TAG = "OfflineTranslation"
    }
    
    private var turkishToEnglishTranslator: Translator? = null
    private var englishToTurkishTranslator: Translator? = null
    private var isInitialized = false
    
    // Callbacks
    private var onTranslationResult: ((String, String) -> Unit)? = null // (original, translated)
    private var onError: ((String) -> Unit)? = null
    private var onModelDownloaded: (() -> Unit)? = null
    
    init {
        Log.i(TAG, "OfflineTranslationManager initialized")
        setupTranslators()
    }
    
    fun setOnTranslationResultListener(listener: (String, String) -> Unit) {
        onTranslationResult = listener
    }
    
    fun setOnErrorListener(listener: (String) -> Unit) {
        onError = listener
    }
    
    fun setOnModelDownloadedListener(listener: () -> Unit) {
        onModelDownloaded = listener
    }
    
    private fun setupTranslators() {
        // Turkish to English translator
        val turkishToEnglishOptions = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.TURKISH)
            .setTargetLanguage(TranslateLanguage.ENGLISH)
            .build()
        turkishToEnglishTranslator = Translation.getClient(turkishToEnglishOptions)
        
        // English to Turkish translator
        val englishToTurkishOptions = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.TURKISH)
            .build()
        englishToTurkishTranslator = Translation.getClient(englishToTurkishOptions)
        
        Log.i(TAG, "Translators created")
    }
    
    /**
     * Download translation models for offline use
     */
    suspend fun downloadModels(): Boolean = suspendCoroutine { continuation ->
        val conditions = DownloadConditions.Builder()
            .requireWifi() // Download only on Wi-Fi to save data
            .build()
        
        Log.i(TAG, "Starting model download...")
        
        var turkishToEnglishDownloaded = false
        var englishToTurkishDownloaded = false
        var turkishToEnglishFailed = false
        var englishToTurkishFailed = false
        var continuationResumed = false
        
        fun checkCompletion() {
            synchronized(this) {
                if (continuationResumed) return
                
                if (turkishToEnglishDownloaded && englishToTurkishDownloaded) {
                    // Both models downloaded successfully
                    isInitialized = true
                    Log.i(TAG, "All translation models downloaded successfully")
                    onModelDownloaded?.invoke()
                    continuationResumed = true
                    continuation.resume(true)
                } else if (turkishToEnglishFailed && englishToTurkishFailed) {
                    // Both models failed to download
                    Log.w(TAG, "Both translation models failed to download - continuing without translation")
                    continuationResumed = true
                    continuation.resume(false)
                } else if ((turkishToEnglishDownloaded || turkishToEnglishFailed) && 
                          (englishToTurkishDownloaded || englishToTurkishFailed)) {
                    // At least one model downloaded, we can proceed with partial functionality
                    if (turkishToEnglishDownloaded || englishToTurkishDownloaded) {
                        isInitialized = true
                        Log.i(TAG, "At least one translation model available")
                        onModelDownloaded?.invoke()
                        continuationResumed = true
                        continuation.resume(true)
                    }
                }
            }
        }
        
        // Download Turkish to English model
        turkishToEnglishTranslator?.downloadModelIfNeeded(conditions)
            ?.addOnSuccessListener {
                Log.i(TAG, "Turkish to English model downloaded")
                turkishToEnglishDownloaded = true
                checkCompletion()
            }
            ?.addOnFailureListener { exception ->
                Log.e(TAG, "Failed to download Turkish to English model: ${exception.message}")
                onError?.invoke("Failed to download Turkish translation model")
                turkishToEnglishFailed = true
                checkCompletion()
            }
        
        // Download English to Turkish model
        englishToTurkishTranslator?.downloadModelIfNeeded(conditions)
            ?.addOnSuccessListener {
                Log.i(TAG, "English to Turkish model downloaded")
                englishToTurkishDownloaded = true
                checkCompletion()
            }
            ?.addOnFailureListener { exception ->
                Log.e(TAG, "Failed to download English to Turkish model: ${exception.message}")
                onError?.invoke("Failed to download English translation model")
                englishToTurkishFailed = true
                checkCompletion()
            }
    }
    
    /**
     * Translate Turkish text to English
     */
    suspend fun translateTurkishToEnglish(text: String): String? = suspendCoroutine { continuation ->
        if (!isInitialized) {
            Log.w(TAG, "Translation models not initialized")
            continuation.resume(null)
            return@suspendCoroutine
        }
        
        if (text.trim().isEmpty()) {
            continuation.resume("")
            return@suspendCoroutine
        }
        
        Log.i(TAG, "Translating Turkish to English: \"$text\"")
        
        turkishToEnglishTranslator?.translate(text)
            ?.addOnSuccessListener { translatedText ->
                Log.i(TAG, "Translation result: \"$translatedText\"")
                onTranslationResult?.invoke(text, translatedText)
                continuation.resume(translatedText)
            }
            ?.addOnFailureListener { exception ->
                Log.e(TAG, "Translation failed: ${exception.message}")
                onError?.invoke("Translation failed: ${exception.message}")
                continuation.resume(null)
            }
    }
    
    /**
     * Translate English text to Turkish
     */
    suspend fun translateEnglishToTurkish(text: String): String? = suspendCoroutine { continuation ->
        if (!isInitialized) {
            Log.w(TAG, "Translation models not initialized")
            continuation.resume(null)
            return@suspendCoroutine
        }
        
        if (text.trim().isEmpty()) {
            continuation.resume("")
            return@suspendCoroutine
        }
        
        Log.i(TAG, "Translating English to Turkish: \"$text\"")
        
        englishToTurkishTranslator?.translate(text)
            ?.addOnSuccessListener { translatedText ->
                Log.i(TAG, "Translation result: \"$translatedText\"")
                onTranslationResult?.invoke(text, translatedText)
                continuation.resume(translatedText)
            }
            ?.addOnFailureListener { exception ->
                Log.e(TAG, "Translation failed: ${exception.message}")
                onError?.invoke("Translation failed: ${exception.message}")
                continuation.resume(null)
            }
    }
    
    /**
     * Auto-detect language and translate to the other language
     */
    suspend fun autoTranslate(text: String): Pair<String, String>? {
        if (text.trim().isEmpty()) {
            return Pair("", "")
        }
        
        // Simple heuristic to detect Turkish vs English
        // Turkish has specific characters like ç, ğ, ı, ş, ü, ö
        val turkishChars = "çğışüöÇĞIŞÜÖ"
        val hasTurkishChars = text.any { it in turkishChars }
        
        return if (hasTurkishChars) {
            // Likely Turkish, translate to English
            val translated = translateTurkishToEnglish(text)
            if (translated != null) Pair(text, translated) else null
        } else {
            // Likely English, translate to Turkish
            val translated = translateEnglishToTurkish(text)
            if (translated != null) Pair(text, translated) else null
        }
    }
    
    /**
     * Check if translation models are ready
     */
    fun isReady(): Boolean = isInitialized
    
    /**
     * Check if models are downloaded
     */
    suspend fun areModelsDownloaded(): Boolean = suspendCoroutine { continuation ->
        var turkishToEnglishReady = false
        var englishToTurkishReady = false
        
        fun checkCompletion() {
            if (turkishToEnglishReady && englishToTurkishReady) {
                continuation.resume(true)
            }
        }
        
        // Check Turkish to English model
        turkishToEnglishTranslator?.downloadModelIfNeeded()
            ?.addOnSuccessListener {
                turkishToEnglishReady = true
                checkCompletion()
            }
            ?.addOnFailureListener {
                continuation.resume(false)
            }
        
        // Check English to Turkish model
        englishToTurkishTranslator?.downloadModelIfNeeded()
            ?.addOnSuccessListener {
                englishToTurkishReady = true
                checkCompletion()
            }
            ?.addOnFailureListener {
                continuation.resume(false)
            }
    }
    
    /**
     * Clean up resources
     */
    fun destroy() {
        try {
            turkishToEnglishTranslator?.close()
            englishToTurkishTranslator?.close()
            
            turkishToEnglishTranslator = null
            englishToTurkishTranslator = null
            isInitialized = false
            
            Log.i(TAG, "OfflineTranslationManager destroyed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}")
        }
    }
} 