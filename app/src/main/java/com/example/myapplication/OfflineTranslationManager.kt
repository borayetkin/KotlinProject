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
    private var isInitialized = false
    
    // Callbacks
    private var onTranslationResult: ((String, String) -> Unit)? = null // (original, translated)
    private var onError: ((String) -> Unit)? = null
    private var onModelDownloaded: (() -> Unit)? = null
    
    init {
        Log.i(TAG, "OfflineTranslationManager initialized")
        setupTranslator()
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
    
    private fun setupTranslator() {
        try {
            // Create Turkish to English translator (only direction needed)
            val turkishToEnglishOptions = TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.TURKISH)
                .setTargetLanguage(TranslateLanguage.ENGLISH)
                .build()
                
            turkishToEnglishTranslator = Translation.getClient(turkishToEnglishOptions)
            
            Log.i(TAG, "Turkish → English translator created")
            
            // Download model
            downloadModel()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup translator: ${e.message}")
            onError?.invoke("Translation setup failed")
        }
    }
    
    private fun downloadModel() {
        Log.i(TAG, "Starting Turkish → English model download...")
        
        // Log device status for debugging
        logDeviceStatus()
        
        val conditions = DownloadConditions.Builder()
            .build() // No restrictions - downloads on WiFi or mobile data
        
        // Download Turkish to English model
        Log.i(TAG, "Attempting to download Turkish → English model...")
        turkishToEnglishTranslator?.downloadModelIfNeeded(conditions)
            ?.addOnSuccessListener {
                isInitialized = true
                Log.i(TAG, "✅ Turkish → English model ready")
                onModelDownloaded?.invoke()
            }
            ?.addOnFailureListener { exception ->
                Log.w(TAG, "❌ Turkish → English model failed: ${exception.javaClass.simpleName}")
                Log.w(TAG, "Full error: ${exception.message}")
                Log.w(TAG, "Possible causes:")
                Log.w(TAG, "  - Emulator limitations (most common)")
                Log.w(TAG, "  - Regional restrictions")
                Log.w(TAG, "  - Google Play Services compatibility")
                Log.w(TAG, "  - Model temporarily unavailable")
                Log.w(TAG, "Note: App continues to work without translation")
            }
    }
    
    private fun logDeviceStatus() {
        try {
            val statFs = android.os.StatFs(context.filesDir.absolutePath)
            val freeSpace = statFs.availableBytes / (1024 * 1024) // MB
            val totalSpace = statFs.totalBytes / (1024 * 1024) // MB
            
            Log.i(TAG, "Storage: ${freeSpace}MB free / ${totalSpace}MB total")
            
            // Check network connectivity
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val activeNetwork = connectivityManager.activeNetworkInfo
            val networkStatus = when {
                activeNetwork?.isConnected == true -> {
                    when (activeNetwork.type) {
                        android.net.ConnectivityManager.TYPE_WIFI -> "Connected via WIFI"
                        android.net.ConnectivityManager.TYPE_MOBILE -> "Connected via Mobile Data"
                        else -> "Connected via ${activeNetwork.typeName}"
                    }
                }
                else -> "No network connection"
            }
            Log.i(TAG, "Network: $networkStatus")
            
            // Check Google Play Services
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
                Log.w(TAG, "Low storage warning: Translation models need ~30MB")
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Could not check device status: ${e.message}")
        }
    }
    
    /**
     * Translate Turkish text to English (only direction supported)
     */
    suspend fun translateTurkishToEnglish(text: String): String? = suspendCoroutine { continuation ->
        if (!isInitialized) {
            Log.w(TAG, "Translation model not initialized")
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
     * Translate Turkish speech to English (simplified since only Turkish STT is available)
     */
    suspend fun autoTranslate(text: String): Pair<String, String>? {
        if (text.trim().isEmpty()) {
            return Pair("", "")
        }
        
        // Since we only have Turkish STT, all speech input is Turkish
        val translated = translateTurkishToEnglish(text)
        return if (translated != null) Pair(text, translated) else null
    }
    
    /**
     * Check if translation model is ready
     */
    fun isReady(): Boolean = isInitialized
    
    /**
     * Check if model is downloaded
     */
    suspend fun isModelDownloaded(): Boolean = suspendCoroutine { continuation ->
        turkishToEnglishTranslator?.downloadModelIfNeeded()
            ?.addOnSuccessListener {
                continuation.resume(true)
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
            turkishToEnglishTranslator = null
            isInitialized = false
            
            Log.i(TAG, "OfflineTranslationManager destroyed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}")
        }
    }
} 