package com.example.myapplication

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Testing utility for emulator - provides pre-recorded audio data
 * 
 * Usage:
 * 1. Place your test audio files in assets/test_audio/
 * 2. Enable test mode by setting ENABLE_TEST_MODE = true
 * 3. Audio files should be WAV format: 16kHz, mono, 16-bit PCM
 * 
 * Supported test files:
 * - test_audio_1.wav (Turkish speech)
 * - test_audio_2.wav (Turkish speech)
 * - test_audio_3.wav (Turkish speech)
 */
object TestAudioProvider {
    
    private const val TAG = "TestAudioProvider"
    
    // 🎯 ENABLE/DISABLE TEST MODE HERE
    const val ENABLE_TEST_MODE = true  // Set to true for emulator testing
    
    private const val TEST_AUDIO_DIR = "test_audio"
    private val testFiles = listOf(
        "test_audio_1.wav",
        "test_audio_2.wav", 
        "test_audio_3.wav"
    )
    
    private var currentTestIndex = 0
    
    /**
     * Simulate microphone recording with pre-recorded audio
     */
    suspend fun simulateRecording(context: Context): TestRecordingResult? {
        if (!ENABLE_TEST_MODE) return null
        
        Log.i(TAG, "🎬 Simulating recording with test audio...")
        
        try {
            // Cycle through test files
            val fileName = testFiles[currentTestIndex % testFiles.size]
            currentTestIndex++
            
            Log.i(TAG, "Loading test file: $fileName")
            
            // Load audio data from assets
            val audioData = loadAudioFromAssets(context, fileName)
            if (audioData == null) {
                Log.w(TAG, "Test file not found: $fileName - falling back to real recording")
                return null
            }
            
            // Simulate recording time (add some delay for realism)
            val recordingDuration = (audioData.size / 16000.0 * 1000).toLong() // Calculate duration in ms
            delay(500) // Initial delay
            
            Log.i(TAG, "✅ Test recording loaded: ${audioData.size} samples, ${recordingDuration}ms")
            
            return TestRecordingResult(
                audioData = audioData,
                durationMs = recordingDuration,
                fileName = fileName
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading test audio: ${e.message}")
            return null
        }
    }
    
    /**
     * Load WAV audio file from assets and convert to Short array
     */
    private fun loadAudioFromAssets(context: Context, fileName: String): List<Short>? {
        return try {
            val inputStream = context.assets.open("$TEST_AUDIO_DIR/$fileName")
            val wavData = inputStream.readBytes()
            inputStream.close()
            
            // Parse WAV file (skip 44-byte header, read PCM data)
            if (wavData.size < 44) {
                Log.e(TAG, "Invalid WAV file: too small")
                return null
            }
            
            // Extract WAV header information for diagnostics
            val sampleRate = extractSampleRate(wavData)
            val channels = extractChannels(wavData)
            val bitsPerSample = extractBitsPerSample(wavData)
            val duration = (wavData.size - 44) / (sampleRate * channels * (bitsPerSample / 8)).toFloat()
            
            Log.i(TAG, "📊 WAV Analysis for $fileName:")
            Log.i(TAG, "   Sample Rate: ${sampleRate}Hz ${if (sampleRate == 16000) "✅" else "❌ Should be 16000Hz"}")
            Log.i(TAG, "   Channels: $channels ${if (channels == 1) "✅" else "❌ Should be 1 (mono)"}")
            Log.i(TAG, "   Bits/Sample: $bitsPerSample ${if (bitsPerSample == 16) "✅" else "❌ Should be 16-bit"}")
            Log.i(TAG, "   Duration: ${"%.2f".format(duration)}s")
            Log.i(TAG, "   File Size: ${wavData.size} bytes")
            
            // Convert bytes to shorts (16-bit PCM)
            val audioBytes = wavData.drop(44) // Skip WAV header
            val audioData = mutableListOf<Short>()
            
            for (i in audioBytes.indices step 2) {
                if (i + 1 < audioBytes.size) {
                    val low = audioBytes[i].toInt() and 0xFF
                    val high = (audioBytes[i + 1].toInt() and 0xFF) shl 8
                    val sample = (high or low).toShort()
                    audioData.add(sample)
                }
            }
            
            // Audio level analysis
            analyzeAudioLevels(audioData, fileName)
            
            Log.i(TAG, "Loaded ${audioData.size} audio samples from $fileName")
            audioData
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load audio from assets: ${e.message}")
            null
        }
    }
    
    /**
     * Analyze audio levels to diagnose quality issues
     */
    private fun analyzeAudioLevels(audioData: List<Short>, fileName: String) {
        if (audioData.isEmpty()) return
        
        val maxAmplitude = audioData.maxOrNull()?.toInt() ?: 0
        val minAmplitude = audioData.minOrNull()?.toInt() ?: 0
        val avgAmplitude = audioData.map { kotlin.math.abs(it.toInt()) }.average()
        val silentSamples = audioData.count { kotlin.math.abs(it.toInt()) < 1000 }
        val silentPercentage = (silentSamples.toFloat() / audioData.size) * 100
        
        Log.i(TAG, "🔊 Audio Level Analysis for $fileName:")
        Log.i(TAG, "   Max Amplitude: $maxAmplitude ${getAmplitudeStatus(maxAmplitude)}")
        Log.i(TAG, "   Min Amplitude: $minAmplitude")
        Log.i(TAG, "   Avg Amplitude: ${"%.0f".format(avgAmplitude)} ${getAvgAmplitudeStatus(avgAmplitude)}")
        Log.i(TAG, "   Silent Samples: ${"%.1f".format(silentPercentage)}% ${getSilentStatus(silentPercentage)}")
        Log.i(TAG, "   Dynamic Range: ${maxAmplitude - minAmplitude}")
        
        // Detect potential issues
        val issues = mutableListOf<String>()
        if (maxAmplitude < 5000) issues.add("⚠️ Audio too quiet")
        if (maxAmplitude > 30000) issues.add("⚠️ Audio might be clipped")
        if (avgAmplitude < 1000) issues.add("⚠️ Very low average volume")
        if (silentPercentage > 50) issues.add("⚠️ Too much silence")
        if (silentPercentage < 5) issues.add("⚠️ No silence (might be noisy)")
        
        if (issues.isNotEmpty()) {
            Log.w(TAG, "❌ Potential audio issues found:")
            issues.forEach { Log.w(TAG, "   $it") }
        } else {
            Log.i(TAG, "✅ Audio levels look good for STT")
        }
    }
    
    private fun getAmplitudeStatus(max: Int): String = when {
        max < 3000 -> "❌ Too quiet"
        max > 30000 -> "⚠️ Might be clipped"
        else -> "✅ Good level"
    }
    
    private fun getAvgAmplitudeStatus(avg: Double): String = when {
        avg < 1000 -> "❌ Too quiet"
        avg > 15000 -> "⚠️ Very loud"
        else -> "✅ Good level"
    }
    
    private fun getSilentStatus(silent: Float): String = when {
        silent > 70 -> "❌ Too much silence"
        silent < 2 -> "⚠️ No silence (noisy?)"
        else -> "✅ Good balance"
    }
    
    // WAV header parsing functions
    private fun extractSampleRate(wavData: ByteArray): Int {
        return if (wavData.size >= 28) {
            (wavData[24].toInt() and 0xFF) or
            ((wavData[25].toInt() and 0xFF) shl 8) or
            ((wavData[26].toInt() and 0xFF) shl 16) or
            ((wavData[27].toInt() and 0xFF) shl 24)
        } else 0
    }
    
    private fun extractChannels(wavData: ByteArray): Int {
        return if (wavData.size >= 24) {
            (wavData[22].toInt() and 0xFF) or ((wavData[23].toInt() and 0xFF) shl 8)
        } else 0
    }
    
    private fun extractBitsPerSample(wavData: ByteArray): Int {
        return if (wavData.size >= 36) {
            (wavData[34].toInt() and 0xFF) or ((wavData[35].toInt() and 0xFF) shl 8)
        } else 0
    }
    
    /**
     * Check if test audio files exist in assets
     */
    fun checkTestFilesAvailable(context: Context): List<String> {
        val availableFiles = mutableListOf<String>()
        
        testFiles.forEach { fileName ->
            try {
                context.assets.open("$TEST_AUDIO_DIR/$fileName").use {
                    availableFiles.add(fileName)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Test file not found: $fileName")
            }
        }
        
        return availableFiles
    }
    
    /**
     * Get instructions for setting up test files
     */
    fun getSetupInstructions(): String {
        return """
            📁 Test Audio Setup Instructions:
            
            1. Create folder: app/src/main/assets/test_audio/
            2. Add WAV files with these specifications:
               - Format: WAV (PCM)
               - Sample Rate: 16kHz
               - Channels: Mono
               - Bit Depth: 16-bit
               - Duration: 3-10 seconds of Turkish speech
            
            3. Name your files:
               - test_audio_1.wav
               - test_audio_2.wav  
               - test_audio_3.wav
            
            4. Enable test mode: Set ENABLE_TEST_MODE = true
            
            💡 Tip: You can record these files with any audio software
            and convert them to the correct format using tools like:
            - Audacity (free)
            - FFmpeg: ffmpeg -i input.mp3 -ar 16000 -ac 1 -f wav output.wav
        """.trimIndent()
    }
}

/**
 * Result of a test recording simulation
 */
data class TestRecordingResult(
    val audioData: List<Short>,
    val durationMs: Long,
    val fileName: String
) 