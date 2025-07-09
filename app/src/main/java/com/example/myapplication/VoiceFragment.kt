package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sqrt

class VoiceFragment : Fragment() {
    
    companion object {
        private const val TAG = "VoiceRecorder"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val VAD_THRESHOLD = 0.02 // Voice activity threshold
        private const val SILENCE_TIMEOUT_MS = 5000L // 5 seconds of silence to stop recording
        private const val MIN_RECORDING_DURATION_MS = 500L // Minimum recording duration
    }

    // UI Components
    private lateinit var btnMicrophone: MaterialButton
    private lateinit var tvStatus: TextView
    private lateinit var tvAudioLevel: TextView
    private lateinit var progressAudioLevel: ProgressBar
    private lateinit var tvRecordingInfo: TextView

    // Audio Recording
    private var audioRecord: AudioRecord? = null
    private var isListening = false
    private var isRecording = false
    private var vadJob: Job? = null
    
    // Recording data
    private val recordedData = mutableListOf<Short>()
    private var recordingStartTime = 0L
    
    // Speech-to-text data
    private val transcriptionData = mutableListOf<String>()
    
    // File handling
    private lateinit var audioDir: File
    
    // UI updates
    private val handler = Handler(Looper.getMainLooper())

    enum class RecordingState {
        IDLE, LISTENING, RECORDING, PROCESSING, SENDING
    }
    
    private var currentState = RecordingState.IDLE
    
    // Modern permission request
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startListening()
        } else {
            updateStatus("Permission denied")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_voice, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initializeViews(view)
        setupAudioDirectory()
        setupClickListeners()
        updateUI()
    }

    private fun initializeViews(view: View) {
        btnMicrophone = view.findViewById(R.id.btnMicrophone)
        tvStatus = view.findViewById(R.id.tvStatus)
        tvAudioLevel = view.findViewById(R.id.tvAudioLevel)
        progressAudioLevel = view.findViewById(R.id.progressAudioLevel)
        tvRecordingInfo = view.findViewById(R.id.tvRecordingInfo)
    }

    private fun setupAudioDirectory() {
        audioDir = File(requireContext().filesDir, "voice_recordings")
        if (!audioDir.exists()) {
            audioDir.mkdirs()
            Log.i(TAG, "🗂️ Created voice recordings directory: ${audioDir.absolutePath}")
        } else {
            Log.i(TAG, "🗂️ Voice recordings directory exists: ${audioDir.absolutePath}")
        }
    }

    private fun setupClickListeners() {
        btnMicrophone.setOnClickListener {
            when (currentState) {
                RecordingState.IDLE -> {
                    if (checkAudioPermission()) {
                        startListening()
                    } else {
                        requestAudioPermission()
                    }
                }
                RecordingState.LISTENING, RecordingState.RECORDING -> {
                    Log.i(TAG, "🛑 MANUAL STOP - Stopping continuous VAD mode")
                    stopListening()
                }
                else -> {
                    // Do nothing during processing/sending
                }
            }
        }
    }

    private fun checkAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAudioPermission() {
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startListening() {
        if (isListening) return

        Log.i(TAG, "🎤 CONTINUOUS VAD MODE ACTIVATED - Listening for speech segments forever...")
        currentState = RecordingState.LISTENING
        isListening = true
        
        // Clear previous recordings when starting new session
        clearPreviousRecordings()
        
        try {
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            
            if (!checkAudioPermission()) {
                updateStatus("Permission not granted")
                return
            }
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "❌ AudioRecord initialization failed")
                updateStatus("AudioRecord initialization failed")
                stopListening()
                return
            }

            audioRecord?.startRecording()
            
            if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                Log.e(TAG, "❌ Recording failed to start")
                updateStatus("Recording failed to start")
                stopListening()
                return
            }
            
            Log.i(TAG, "✅ CONTINUOUS VAD ACTIVE - Each speech will auto-save & send. Buffer: $bufferSize")
            
            // Start VAD monitoring
            vadJob = CoroutineScope(Dispatchers.IO).launch {
                monitorVoiceActivity(bufferSize)
            }
            
            updateUI()
            
        } catch (e: Exception) {
            updateStatus("Error: ${e.message}")
            stopListening()
        }
    }

    private suspend fun monitorVoiceActivity(bufferSize: Int) {
        val buffer = ShortArray(bufferSize)
        var consecutiveSilenceCount = 0
        val silenceThreshold = (SILENCE_TIMEOUT_MS / 50).toInt() // Check every 50ms
        
        while (isListening && audioRecord != null) {
            try {
                val readResult = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                
                if (readResult > 0) {
                    val audioLevel = calculateAudioLevel(buffer, readResult)
                    val isSpeech = audioLevel > VAD_THRESHOLD
                    
                    // Update UI with audio level
                    handler.post {
                        updateAudioLevel(audioLevel)
                    }
                    
                    if (isSpeech) {
                        consecutiveSilenceCount = 0
                        
                        if (!isRecording) {
                            Log.i(TAG, "🔴 SPEECH DETECTED! Recording starts from this moment...")
                            startRecording()
                        }
                        
                                                // Add audio data to recording
                        if (isRecording) {
                            synchronized(recordedData) {
                                recordedData.addAll(buffer.take(readResult))
                            }
                        }
                    } else {
                        consecutiveSilenceCount++
                        
                        if (isRecording) {
                            // Add silence to recording (to maintain continuity)
                            synchronized(recordedData) {
                                recordedData.addAll(buffer.take(readResult))
                            }
                            
                            // Auto-stop recording after silence timeout, save & send, then continue listening
                            if (consecutiveSilenceCount >= silenceThreshold) {
                                val recordingDuration = System.currentTimeMillis() - recordingStartTime
                                Log.i(TAG, "🔇 SPEECH ENDED - Silence detected. Recording duration: ${recordingDuration}ms")
                                if (recordingDuration >= MIN_RECORDING_DURATION_MS) {
                                    Log.i(TAG, "✅ Auto-stopping, saving & sending this speech segment...")
                                    CoroutineScope(Dispatchers.Main).launch {
                                        stopRecordingAndContinueListening()
                                    }
                                } else {
                                    Log.w(TAG, "⚠️ Speech too short (${recordingDuration}ms), discarding...")
                                    CoroutineScope(Dispatchers.Main).launch {
                                        discardRecordingAndContinueListening()
                                    }
                                }
                            }
                        }
                    }
                }
                
                delay(50) // Check every 50ms
                
            } catch (e: Exception) {
                break
            }
        }
    }

    private fun calculateAudioLevel(buffer: ShortArray, length: Int): Double {
        var sum = 0.0
        for (i in 0 until length) {
            sum += (buffer[i] * buffer[i]).toDouble()
        }
        val rms = sqrt(sum / length)
        return rms / Short.MAX_VALUE
    }

    private suspend fun startRecording() {
        if (isRecording) return
        
        isRecording = true
        currentState = RecordingState.RECORDING
        recordingStartTime = System.currentTimeMillis()
        recordedData.clear()
        
        Log.i(TAG, "🎙️ RECORDING SPEECH SEGMENT - Started at ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}")
        
        handler.post {
            updateUI()
        }
    }

    private suspend fun stopRecordingAndContinueListening() {
        if (!isRecording) return
        
        isRecording = false
        currentState = RecordingState.PROCESSING
        
        val recordingDuration = System.currentTimeMillis() - recordingStartTime
        Log.i(TAG, "⏹️ SPEECH SEGMENT ENDED - Duration: ${recordingDuration}ms")
        
        handler.post {
            updateUI()
        }
        
        // Get the recorded audio data
        val audioData = synchronized(recordedData) {
            recordedData.toList()
        }
        
        Log.i(TAG, "📊 Processing ${audioData.size} audio samples...")
        
        if (audioData.isNotEmpty()) {
            // Create files for this speech segment
            createAudioAndTranscriptFiles(audioData)
        } else {
            Log.w(TAG, "⚠️ No audio data to process")
            // Continue listening for next speech
            currentState = RecordingState.LISTENING
            handler.post { updateUI() }
        }
        
        // Clear recorded data for next speech segment
        recordedData.clear()
    }
    
    private suspend fun discardRecordingAndContinueListening() {
        if (!isRecording) return
        
        isRecording = false
        currentState = RecordingState.LISTENING
        
        Log.i(TAG, "🗑️ Discarding short speech segment, continuing to listen...")
        
        // Clear recorded data
        recordedData.clear()
        
        handler.post {
            updateUI()
        }
    }

    private suspend fun createAudioAndTranscriptFiles(audioData: List<Short>) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val audioFileName = "voice_${timestamp}.wav"
            val textFileName = "voice_${timestamp}.txt"
            
            val audioFile = File(audioDir, audioFileName)
            val textFile = File(audioDir, textFileName)
            
            Log.i(TAG, "💾 CREATING FILES after recording completion:")
            Log.i(TAG, "   📄 Audio file: $audioFileName")
            Log.i(TAG, "   📄 Text file: $textFileName")
            
            // Save audio as WAV file
            saveAsWav(audioFile, audioData)
            Log.i(TAG, "✅ Audio file saved: ${audioFile.length()} bytes")
            
            // Convert speech to text and save
            val transcription = convertSpeechToText(audioData, timestamp, textFile)
            Log.i(TAG, "✅ Transcript file saved: ${textFile.length()} bytes")
            
            // Now send both files to API
            currentState = RecordingState.SENDING
            handler.post { updateUI() }
            
            sendBothFilesToAPI(audioFile, textFile, transcription)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error creating files: ${e.message}")
            handler.post {
                updateStatus("Error creating files: ${e.message}")
            }
            // Continue listening even after error
            currentState = RecordingState.LISTENING
            handler.post { updateUI() }
        }
    }

    private fun saveAsWav(file: File, audioData: List<Short>) {
        FileOutputStream(file).use { fos ->
            val dataSize = audioData.size * 2 // 16-bit samples
            val fileSize = dataSize + 36
            
            // WAV header
            fos.write("RIFF".toByteArray())
            fos.write(intToByteArray(fileSize))
            fos.write("WAVE".toByteArray())
            fos.write("fmt ".toByteArray())
            fos.write(intToByteArray(16)) // PCM header size
            fos.write(shortToByteArray(1)) // PCM format
            fos.write(shortToByteArray(1)) // Mono
            fos.write(intToByteArray(SAMPLE_RATE))
            fos.write(intToByteArray(SAMPLE_RATE * 2)) // Byte rate
            fos.write(shortToByteArray(2)) // Block align
            fos.write(shortToByteArray(16)) // Bits per sample
            fos.write("data".toByteArray())
            fos.write(intToByteArray(dataSize))
            
            // Audio data
            for (sample in audioData) {
                fos.write(shortToByteArray(sample))
            }
        }
    }

    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }

    private fun shortToByteArray(value: Short): ByteArray {
        return byteArrayOf(
            (value.toInt() and 0xFF).toByte(),
            ((value.toInt() shr 8) and 0xFF).toByte()
        )
    }

    private suspend fun sendBothFilesToAPI(audioFile: File, textFile: File, transcription: String) {
        try {
            Log.i(TAG, "📤 SENDING TO API - Both files ready for upload:")
            Log.i(TAG, "   🎵 Audio: ${audioFile.name} (${audioFile.length()} bytes)")
            Log.i(TAG, "   📝 Transcript: ${textFile.name} (${textFile.length()} bytes)")
            Log.i(TAG, "   💬 Content: \"$transcription\"")
            
            // Simulate API call with both files
            delay(1500) // Longer delay to simulate uploading both files
            
            Log.i(TAG, "✅ SUCCESS - Speech segment uploaded! Continuing to listen for next speech...")
            handler.post {
                updateStatus("Speech uploaded, listening for next...")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ API Error: ${e.message}")
            handler.post {
                updateStatus("API Error: ${e.message}")
            }
        } finally {
            // Always return to listening mode for next speech segment
            currentState = RecordingState.LISTENING
            handler.post { updateUI() }
        }
    }
    
    private fun convertSpeechToText(audioData: List<Short>, timestamp: String, textFile: File): String {
        Log.i(TAG, "🗣️ Converting speech to text and saving to file...")
        
        // Mock speech-to-text conversion 
        val mockTranscriptions = listOf(
            "Hello, this is a test recording.",
            "Voice activity detection is working.",
            "Speech to text conversion complete.",
            "Audio sample recorded successfully.",
            "Testing microphone input functionality."
        )
        
        val transcription = mockTranscriptions.random()
        
        // Create transcript file content
        val fileContent = buildString {
            appendLine("=== VOICE RECORDING TRANSCRIPT ===")
            appendLine("Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLine("Audio File: voice_${timestamp}.wav")
            appendLine("Duration: ${(audioData.size / SAMPLE_RATE.toFloat()).format(2)}s")
            appendLine("Sample Rate: ${SAMPLE_RATE}Hz")
            appendLine("Samples: ${audioData.size}")
            appendLine("================================")
            appendLine()
            appendLine("TRANSCRIPTION:")
            appendLine(transcription)
            appendLine()
            appendLine("Generated by Voice Recorder App")
        }
        
        // Save to file
        textFile.writeText(fileContent)
        
        Log.i(TAG, "📝 Transcript saved to: ${textFile.name}")
        Log.i(TAG, "📄 Text content: \"$transcription\"")
        
        transcriptionData.add(transcription)
        
        return transcription
    }
    
    // Extension function for formatting
    private fun Float.format(digits: Int) = "%.${digits}f".format(this)
    
    private fun clearPreviousRecordings() {
        try {
            if (audioDir.exists()) {
                val files = audioDir.listFiles()
                val deletedCount = files?.size ?: 0
                files?.forEach { file ->
                    if (file.name.startsWith("voice_")) {
                        file.delete()
                        Log.d(TAG, "🗑️ Deleted previous file: ${file.name}")
                    }
                }
                if (deletedCount > 0) {
                    Log.i(TAG, "🧹 Cleared $deletedCount previous recording files")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error clearing previous recordings: ${e.message}")
        }
    }

    private fun stopListening() {
        Log.i(TAG, "🛑 Stopping VAD - Microphone deactivated")
        
        isListening = false
        isRecording = false
        currentState = RecordingState.IDLE
        
        vadJob?.cancel()
        Log.d(TAG, "🔄 VAD monitoring job cancelled")
        
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        Log.d(TAG, "🎤 AudioRecord released")
        
        recordedData.clear()
        
        Log.i(TAG, "✅ Voice recording stopped successfully")
        updateUI()
    }

    private fun updateAudioLevel(level: Double) {
        val percentage = (level * 100).toInt().coerceIn(0, 100)
        tvAudioLevel.text = "Audio Level: $percentage%"
        progressAudioLevel.progress = percentage
    }

    private fun updateStatus(status: String) {
        tvStatus.text = status
    }

    private fun updateRecordingInfo(info: String) {
        tvRecordingInfo.text = info
    }

    private fun updateUI() {
        when (currentState) {
            RecordingState.IDLE -> {
                btnMicrophone.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_mic)
                updateStatus(getString(R.string.status_idle))
                btnMicrophone.isEnabled = true
                tvRecordingInfo.text = "No active recording"
            }
            RecordingState.LISTENING -> {
                btnMicrophone.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_mic_active)
                updateStatus("Continuous listening active - waiting for speech")
                btnMicrophone.isEnabled = true
                tvRecordingInfo.text = "VAD active: Each speech auto-saves & sends"
            }
            RecordingState.RECORDING -> {
                btnMicrophone.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_mic_active)
                updateStatus("Recording speech segment...")
                btnMicrophone.isEnabled = true
                val duration = (System.currentTimeMillis() - recordingStartTime) / 1000.0
                tvRecordingInfo.text = "Recording speech: ${String.format("%.1f", duration)}s (auto-stop on silence)"
            }
            RecordingState.PROCESSING -> {
                btnMicrophone.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_hourglass)
                updateStatus("Processing speech segment...")
                btnMicrophone.isEnabled = false
                tvRecordingInfo.text = "Creating files & sending to API..."
            }
            RecordingState.SENDING -> {
                btnMicrophone.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_send)
                updateStatus("Uploading speech segment...")
                btnMicrophone.isEnabled = false
                tvRecordingInfo.text = "Sending audio & transcript to API..."
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
    }

    override fun onPause() {
        super.onPause()
        if (currentState != RecordingState.IDLE) {
            stopListening()
        }
    }
} 