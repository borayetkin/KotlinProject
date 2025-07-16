package com.example.myapplication

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

class VoskSTTManager(private val context: Context) {
    
    companion object {
        private const val TAG = "VoskSTT"
        private const val SAMPLE_RATE = 16000.0f
        private const val MODEL_NAME = "vosk-model-small-tr-0.3" // Turkish model
    }
    
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var isInitialized = AtomicBoolean(false)
    private var isProcessing = AtomicBoolean(false)
    
    // Callbacks
    private var onTranscriptionResult: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private var onInitialized: (() -> Unit)? = null
    
    // Coroutines
    private val initScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    init {
        try {
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            Log.i(TAG, "VoskSTTManager initialized successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to initialize Vosk native library: ${e.message}")
            // The app will fall back to indicating STT is unavailable
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize VoskSTTManager: ${e.message}")
        }
    }
    
    fun setOnTranscriptionResultListener(listener: (String) -> Unit) {
        onTranscriptionResult = listener
    }
    
    fun setOnErrorListener(listener: (String) -> Unit) {
        onError = listener
    }
    
    fun setOnInitializedListener(listener: () -> Unit) {
        onInitialized = listener
    }
    
    /**
     * Initialize Vosk model - call this once during app startup
     */
    fun initializeModel() {
        if (isInitialized.get()) {
            Log.i(TAG, "Model already initialized")
            onInitialized?.invoke()
            return
        }
        
        initScope.launch {
            try {
                Log.i(TAG, "Initializing Vosk model...")
                
                // Check if model exists in assets or download if needed
                val modelDir = extractModelFromAssets()
                
                if (modelDir == null) {
                    // Fallback to English model if Turkish not available
                    Log.w(TAG, "Turkish model not found, using English model")
                    model = createFallbackEnglishModel()
                } else {
                    model = Model(modelDir.absolutePath)
                }
                
                model?.let {
                    recognizer = Recognizer(it, SAMPLE_RATE)
                    isInitialized.set(true)
                    Log.i(TAG, "Vosk model initialized successfully")
                    
                    withContext(Dispatchers.Main) {
                        onInitialized?.invoke()
                    }
                } ?: run {
                    throw Exception("Failed to create Vosk model")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Vosk model: ${e.message}")
                withContext(Dispatchers.Main) {
                    onError?.invoke("Failed to initialize speech recognition: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Process audio data for speech recognition
     */
    fun processAudio(audioData: ShortArray, length: Int): String? {
        if (!isInitialized.get()) {
            Log.w(TAG, "Model not initialized")
            return null
        }
        
        if (isProcessing.get()) {
            Log.d(TAG, "Already processing audio")
            return null
        }
        
        return try {
            isProcessing.set(true)
            
            // Convert shorts to bytes for Vosk
            val audioBytes = ByteArray(length * 2)
            for (i in 0 until length) {
                val value = audioData[i].toInt()
                audioBytes[i * 2] = (value and 0xFF).toByte()
                audioBytes[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
            }
            
            val result = recognizer?.acceptWaveForm(audioBytes, audioBytes.size)
            
            if (result == true) {
                // Final result
                val resultJson = recognizer?.finalResult
                parseTranscriptionResult(resultJson)
            } else {
                // Partial result
                val partialJson = recognizer?.partialResult
                parsePartialResult(partialJson)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing audio: ${e.message}")
            null
        } finally {
            isProcessing.set(false)
        }
    }
    
    /**
     * Finalize current recognition session and get final result
     */
    fun finalizeRecognition(): String? {
        if (!isInitialized.get()) return null
        
        return try {
            val finalJson = recognizer?.finalResult
            val result = parseTranscriptionResult(finalJson)
            
            // Reset recognizer for next session
            recognizer?.reset()
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error finalizing recognition: ${e.message}")
            null
        }
    }
    
    private fun parseTranscriptionResult(json: String?): String? {
        return try {
            json?.let {
                val jsonObject = JSONObject(it)
                val text = jsonObject.optString("text", "").trim()
                
                if (text.isNotEmpty()) {
                    Log.i(TAG, "Transcription result: \"$text\"")
                    onTranscriptionResult?.invoke(text)
                    text
                } else {
                    Log.d(TAG, "Empty transcription result")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing transcription result: ${e.message}")
            null
        }
    }
    
    private fun parsePartialResult(json: String?): String? {
        return try {
            json?.let {
                val jsonObject = JSONObject(it)
                val partial = jsonObject.optString("partial", "").trim()
                
                if (partial.isNotEmpty()) {
                    Log.d(TAG, "Partial result: \"$partial\"")
                    // Could emit partial results for real-time feedback
                }
                partial
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing partial result: ${e.message}")
            null
        }
    }
    
    private suspend fun extractModelFromAssets(): File? {
        return try {
            val modelDir = File(context.filesDir, MODEL_NAME)
            
            if (modelDir.exists()) {
                Log.i(TAG, "Model already extracted: ${modelDir.absolutePath}")
                return modelDir
            }
            
            // Extract model from assets
            Log.i(TAG, "Extracting Turkish Vosk model from assets...")
            
            val assetManager = context.assets
            
            // Check if model exists in assets
            val assetFiles = try {
                assetManager.list(MODEL_NAME)
            } catch (e: Exception) {
                Log.e(TAG, "Model not found in assets: $MODEL_NAME")
                return null
            }
            
            if (assetFiles.isNullOrEmpty()) {
                Log.e(TAG, "Model directory empty in assets: $MODEL_NAME")
                return null
            }
            
            // Create destination directory
            if (!modelDir.mkdirs()) {
                Log.e(TAG, "Failed to create model directory: ${modelDir.absolutePath}")
                return null
            }
            
            // Recursively copy all model files from assets
            copyAssetDir(assetManager, MODEL_NAME, modelDir)
            
            Log.i(TAG, "Turkish model extracted successfully to: ${modelDir.absolutePath}")
            modelDir
            
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting model from assets: ${e.message}")
            null
        }
    }
    
    private fun copyAssetDir(assetManager: android.content.res.AssetManager, assetPath: String, targetDir: File) {
        val assets = assetManager.list(assetPath) ?: return
        
        for (asset in assets) {
            val assetFile = "$assetPath/$asset"
            val targetFile = File(targetDir, asset)
            
            val subAssets = assetManager.list(assetFile)
            if (subAssets != null && subAssets.isNotEmpty()) {
                // It's a directory
                targetFile.mkdirs()
                copyAssetDir(assetManager, assetFile, targetFile)
            } else {
                // It's a file
                assetManager.open(assetFile).use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
    
    private fun createFallbackEnglishModel(): Model? {
        return try {
            Log.w(TAG, "Turkish model not available in assets")
            Log.i(TAG, "To enable Turkish STT, please:")
            Log.i(TAG, "1. Download: https://alphacephei.com/vosk/models/vosk-model-small-tr-0.3.zip")
            Log.i(TAG, "2. Extract and place in: app/src/main/assets/vosk-model-small-tr-0.3/")
            Log.i(TAG, "3. Rebuild the app")
            
            // For now, return null to indicate no model available
            // The app will work but without STT functionality
            Log.w(TAG, "STT disabled - Turkish model not found")
            null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in fallback model creation: ${e.message}")
            null
        }
    }
    
    /**
     * Check if STT is ready to process audio
     */
    fun isReady(): Boolean = isInitialized.get()
    
    /**
     * Clean up resources
     */
    fun destroy() {
        try {
            recognizer?.close()
            model?.close()
            initScope.cancel()
            
            isInitialized.set(false)
            Log.i(TAG, "VoskSTTManager destroyed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}")
        }
    }
    
    /**
     * Reset recognizer state for new recording session
     */
    fun reset() {
        try {
            recognizer?.reset()
            Log.d(TAG, "Recognizer reset")
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting recognizer: ${e.message}")
        }
    }
} 