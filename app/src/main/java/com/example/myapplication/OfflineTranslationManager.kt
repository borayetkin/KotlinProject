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
        try {
            // Create Turkish to English translator
            val turkishToEnglishOptions = TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.TURKISH)
                .setTargetLanguage(TranslateLanguage.ENGLISH)
                .build()
            
            turkishToEnglishTranslator = Translation.getClient(turkishToEnglishOptions)
            
            // Create English to Turkish translator  
            val englishToTurkishOptions = TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.TURKISH)
                .build()
            
            englishToTurkishTranslator = Translation.getClient(englishToTurkishOptions)
            
            Log.i(TAG, "Translators created")
            
            // Download models with improved error handling
            downloadModels()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup translators: ${e.message}")
            onError?.invoke("Translation setup failed")
        }
    }
    
    private fun downloadModels() {
        Log.i(TAG, "Starting model download...")
        
        // Log device status for debugging
        logDeviceStatus()
        
        // Option 1: Allow download on any network (recommended for development/testing)
        val conditions = DownloadConditions.Builder()
            .build() // No restrictions - downloads on WiFi or mobile data
        
        // Option 2: If you want to preserve WiFi-only requirement, uncomment below:
        // val conditions = DownloadConditions.Builder()
        //     .requireWifi()
        //     .build()
        
        var turkishToEnglishReady = false
        var englishToTurkishReady = false
        var hasErrors = false
        
        fun checkCompletion() {
            if (turkishToEnglishReady && englishToTurkishReady) {
                isInitialized = true
                Log.i(TAG, "All translation models downloaded successfully")
                onModelDownloaded?.invoke()
            } else if (hasErrors) {
                Log.w(TAG, "Translation models unavailable - continuing without translation")
                // App continues to work, just without translation feature
            }
        }
        
        // Download Turkish to English model
        Log.i(TAG, "Attempting to download Turkish to English model...")
        turkishToEnglishTranslator?.downloadModelIfNeeded(conditions)
            ?.addOnSuccessListener {
                turkishToEnglishReady = true
                Log.i(TAG, "✅ Turkish to English model ready")
                checkCompletion()
            }
            ?.addOnFailureListener { exception ->
                hasErrors = true
                Log.w(TAG, "❌ Turkish to English model failed: ${exception.javaClass.simpleName}")
                Log.w(TAG, "Full error: ${exception.message}")
                Log.w(TAG, "Possible causes:")
                Log.w(TAG, "  - Emulator limitations (most common)")
                Log.w(TAG, "  - Regional restrictions")
                Log.w(TAG, "  - Google Play Services compatibility")
                Log.w(TAG, "  - Model temporarily unavailable")
                checkCompletion()
            }
        
        // Download English to Turkish model
        Log.i(TAG, "Attempting to download English to Turkish model...")
        englishToTurkishTranslator?.downloadModelIfNeeded(conditions)
            ?.addOnSuccessListener {
                englishToTurkishReady = true
                Log.i(TAG, "✅ English to Turkish model ready")
                checkCompletion()
            }
            ?.addOnFailureListener { exception ->
                hasErrors = true
                Log.w(TAG, "❌ English to Turkish model failed: ${exception.javaClass.simpleName}")
                Log.w(TAG, "Full error: ${exception.message}")
                Log.w(TAG, "Note: App continues to work perfectly without translation")
                checkCompletion()
            }
    }
    
    private fun logDeviceStatus() {
        try {
            // Check available storage
            val internalDir = context.filesDir
            val freeSpace = internalDir.freeSpace / (1024 * 1024) // MB
            val totalSpace = internalDir.totalSpace / (1024 * 1024) // MB
            Log.i(TAG, "Storage: ${freeSpace}MB free / ${totalSpace}MB total")
            
            // Check network connectivity
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val activeNetwork = connectivityManager.activeNetworkInfo
            val networkInfo = if (activeNetwork?.isConnected == true) {
                "Connected via ${activeNetwork.typeName} (${activeNetwork.subtypeName})"
            } else {
                "No active network connection"
            }
            Log.i(TAG, "Network: $networkInfo")
            
            // Check Google Play Services availability
            try {
                val googleApiAvailability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
                val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)
                val playServicesStatus = when (resultCode) {
                    com.google.android.gms.common.ConnectionResult.SUCCESS -> "Available ✅"
                    com.google.android.gms.common.ConnectionResult.SERVICE_MISSING -> "Missing ❌"
                    com.google.android.gms.common.ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> "Needs Update ⚠️"
                    com.google.android.gms.common.ConnectionResult.SERVICE_DISABLED -> "Disabled ❌"
                    else -> "Unavailable (Code: $resultCode) ❌"
                }
                Log.i(TAG, "Google Play Services: $playServicesStatus")
                
                if (resultCode != com.google.android.gms.common.ConnectionResult.SUCCESS) {
                    Log.w(TAG, "ML Kit translation requires Google Play Services")
                    Log.w(TAG, "This is common on emulators without full Google services")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not check Google Play Services: ${e.message}")
            }
            
            if (freeSpace < 100) {
                Log.w(TAG, "Low storage warning: Translation models need ~60MB total")
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Could not check device status: ${e.message}")
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