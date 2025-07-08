package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val VAD_THRESHOLD = 0.02 // Voice activity threshold
        private const val SILENCE_TIMEOUT_MS = 2000L // 2 seconds of silence to stop recording
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

        currentState = RecordingState.LISTENING
        isListening = true
        
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
                updateStatus("AudioRecord initialization failed")
                stopListening()
                return
            }

            audioRecord?.startRecording()
            
            if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                updateStatus("Recording failed to start")
                stopListening()
                return
            }
            
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
                            
                            // Check if we should stop recording due to prolonged silence
                            if (consecutiveSilenceCount >= silenceThreshold) {
                                val recordingDuration = System.currentTimeMillis() - recordingStartTime
                                if (recordingDuration >= MIN_RECORDING_DURATION_MS) {
                                    stopRecording()
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
        
        handler.post {
            updateUI()
        }
    }

    private suspend fun stopRecording() {
        if (!isRecording) return
        
        isRecording = false
        currentState = RecordingState.PROCESSING
        
        handler.post {
            updateUI()
        }
        
        // Process and save the recording
        val audioData = synchronized(recordedData) {
            recordedData.toList()
        }
        
        if (audioData.isNotEmpty()) {
            saveAndSendAudio(audioData)
        }
        
        // Clear recorded data for next recording
        recordedData.clear()
    }

    private suspend fun saveAndSendAudio(audioData: List<Short>) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "voice_${timestamp}.wav"
            val audioFile = File(audioDir, fileName)
            
            // Save audio as WAV file
            saveAsWav(audioFile, audioData)
            
            // Send to API
            currentState = RecordingState.SENDING
            handler.post { updateUI() }
            
            sendToAPI()
            
        } catch (e: Exception) {
            handler.post {
                updateStatus("Error saving audio: ${e.message}")
            }
        } finally {
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

    private suspend fun sendToAPI() {
        try {
            // Simulate API call
            delay(1000)
            handler.post {
                updateStatus("Successfully uploaded")
            }
            
        } catch (e: Exception) {
            handler.post {
                updateStatus("API Error: ${e.message}")
            }
        }
    }

    private fun stopListening() {
        isListening = false
        isRecording = false
        currentState = RecordingState.IDLE
        
        vadJob?.cancel()
        
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        
        recordedData.clear()
        
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

    private fun updateUI() {
        when (currentState) {
            RecordingState.IDLE -> {
                updateStatus(getString(R.string.status_idle))
                btnMicrophone.isEnabled = true
                tvRecordingInfo.text = "No active recording"
            }
            RecordingState.LISTENING -> {
                updateStatus(getString(R.string.status_listening))
                btnMicrophone.isEnabled = true
                tvRecordingInfo.text = "Listening for speech..."
            }
            RecordingState.RECORDING -> {
                updateStatus(getString(R.string.status_recording))
                btnMicrophone.isEnabled = true
                val duration = (System.currentTimeMillis() - recordingStartTime) / 1000.0
                tvRecordingInfo.text = "Recording: ${String.format("%.1f", duration)}s"
            }
            RecordingState.PROCESSING -> {
                updateStatus(getString(R.string.status_processing))
                btnMicrophone.isEnabled = false
                tvRecordingInfo.text = "Processing audio..."
            }
            RecordingState.SENDING -> {
                updateStatus(getString(R.string.status_sending))
                btnMicrophone.isEnabled = false
                tvRecordingInfo.text = "Sending to API..."
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