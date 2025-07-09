package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sqrt
import com.example.myapplication.WavUtils.writeWav
import com.example.myapplication.WavUtils.writeTranscript

class VoiceFragment : Fragment() {
    
    companion object {
        private const val TAG = "VoiceRecorder"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val VAD_THRESHOLD = 0.02
        private const val SILENCE_TIMEOUT_MS = 5000L
        private const val MIN_RECORDING_DURATION_MS = 500L
    }

    // UI Components
    private lateinit var btnMicrophone: MaterialButton
    private lateinit var tvStatus: TextView
    private lateinit var tvAudioLevel: TextView
    private lateinit var progressAudioLevel: ProgressBar

    // Audio Recording
    private var audioRecord: AudioRecord? = null
    private var vadJob: Job? = null
    private val recordedData = mutableListOf<Short>()
    private var recordingStartTime = 0L
    private var currentAudioLevel = 0.0
    private lateinit var audioDir: File

    enum class RecordingState {
        IDLE, LISTENING, RECORDING, PROCESSING, SENDING
    }
    
    private var currentState = RecordingState.IDLE
    
    // Simplified state properties
    private val isListening get() = currentState != RecordingState.IDLE
    private val isRecording get() = currentState == RecordingState.RECORDING
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) startListening()
        // If not granted, just stay in current state
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_voice, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeViews(view)
        setupAudioDirectory()
        setupClickListeners()
        renderState()
    }

    private fun initializeViews(view: View) {
        btnMicrophone = view.findViewById(R.id.btnMicrophone)
        tvStatus = view.findViewById(R.id.tvStatus)
        tvAudioLevel = view.findViewById(R.id.tvAudioLevel)
        progressAudioLevel = view.findViewById(R.id.progressAudioLevel)
    }

    private fun setupAudioDirectory() {
        audioDir = File(requireContext().filesDir, "voice_recordings")
        if (!audioDir.exists()) {
            audioDir.mkdirs()
            Log.i(TAG, "Created voice recordings directory")
        }
    }

    private fun setupClickListeners() {
        btnMicrophone.setOnClickListener {
            when (currentState) {
                RecordingState.IDLE -> {
                    // Always request permission - handles both granted and not-granted cases
                    requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
                RecordingState.LISTENING, RecordingState.RECORDING -> {
                    Log.i(TAG, "Manual stop - Stopping continuous VAD mode")
                    stopListening()
                }
                else -> { /* Do nothing during processing/sending */ }
            }
        }
    }

    private fun startListening() {
        if (isListening) return

        // Verify permission before creating AudioRecord
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Audio permission not granted")
            currentState = RecordingState.IDLE
            renderState()
            return
        }

        // Stop any audio playback before starting recording
        AudioPlayerManager.getInstance().stopAudio()

        Log.i(TAG, "CONTINUOUS VAD MODE ACTIVATED")
        currentState = RecordingState.LISTENING
        // Optional: Clear previous recordings (remove if you want to keep old recordings)
        // clearPreviousRecordings()
        
        try {
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize
            ).apply {
                if (state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord initialization failed")
                    stopListening()
                    return
                }
                startRecording()
                if (recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    Log.e(TAG, "Recording failed to start")
                    stopListening()
                    return
                }
            }
            
            Log.i(TAG, "VAD active - Buffer: $bufferSize")
            vadJob = lifecycleScope.launch(Dispatchers.IO) { 
                monitorVoiceActivity(bufferSize) 
            }
            renderState()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording: ${e.message}")
            stopListening()
        }
    }

    private suspend fun monitorVoiceActivity(bufferSize: Int) {
        val buffer = ShortArray(bufferSize)
        var consecutiveSilenceCount = 0
        val silenceThreshold = (SILENCE_TIMEOUT_MS / 50).toInt()
        
        while (isListening && audioRecord != null) {
            try {
                val readResult = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                
                if (readResult > 0) {
                    val audioLevel = calculateAudioLevel(buffer, readResult)
                    val isSpeech = audioLevel > VAD_THRESHOLD
                    
                    withContext(Dispatchers.Main) {
                        currentAudioLevel = audioLevel
                        renderState()
                    }
                    
                    if (isSpeech) {
                        consecutiveSilenceCount = 0
                        if (!isRecording) {
                            Log.i(TAG, "SPEECH DETECTED! Recording starts...")
                            withContext(Dispatchers.Main) { startRecording() }
                        }
                        if (isRecording) {
                            synchronized(recordedData) { recordedData.addAll(buffer.take(readResult)) }
                        }
                    } else {
                        consecutiveSilenceCount++
                        if (isRecording) {
                            synchronized(recordedData) { recordedData.addAll(buffer.take(readResult)) }
                            
                            if (consecutiveSilenceCount >= silenceThreshold) {
                                val duration = System.currentTimeMillis() - recordingStartTime
                                Log.i(TAG, "SPEECH ENDED - Duration: ${duration}ms")
                                
                                withContext(Dispatchers.Main) {
                                    if (duration >= MIN_RECORDING_DURATION_MS) {
                                        stopRecordingAndContinueListening()
                                    } else {
                                        Log.w(TAG, "Speech too short, discarding...")
                                        discardRecordingAndContinueListening()
                                    }
                                }
                            }
                        }
                    }
                }
                delay(50)
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e(TAG, "VAD monitoring error: ${e.message}")
                }
                break
            }
        }
    }

    private fun calculateAudioLevel(buffer: ShortArray, length: Int): Double {
        var sum = 0.0
        for (i in 0 until length) {
            sum += (buffer[i] * buffer[i]).toDouble()
        }
        return sqrt(sum / length) / Short.MAX_VALUE
    }

    private suspend fun startRecording() {
        if (isRecording) return
        
        currentState = RecordingState.RECORDING
        recordingStartTime = System.currentTimeMillis()
        recordedData.clear()
        
        Log.i(TAG, "RECORDING SPEECH SEGMENT")
        renderState()
    }

    private suspend fun stopRecordingAndContinueListening() {
        if (!isRecording) return
        
        currentState = RecordingState.PROCESSING
        val duration = System.currentTimeMillis() - recordingStartTime
        Log.i(TAG, "SPEECH SEGMENT ENDED - Duration: ${duration}ms")
        
        renderState()
        
        val audioData = synchronized(recordedData) { recordedData.toList() }
        Log.i(TAG, "Processing ${audioData.size} audio samples...")
        
        if (audioData.isNotEmpty()) {
            createAudioAndTranscriptFiles(audioData)
        } else {
            currentState = RecordingState.LISTENING
            renderState()
        }
        
        recordedData.clear()
    }
    
    private suspend fun discardRecordingAndContinueListening() {
        if (!isRecording) return
        
        currentState = RecordingState.LISTENING
        recordedData.clear()
        renderState()
    }

    private suspend fun createAudioAndTranscriptFiles(audioData: List<Short>) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val audioFile = File(audioDir, "voice_${timestamp}.wav")
            val textFile = File(audioDir, "voice_${timestamp}.txt")
            
            Log.i(TAG, "Creating files: ${audioFile.name}, ${textFile.name}")
            
            audioFile.writeWav(audioData, SAMPLE_RATE)
            val transcription = textFile.writeTranscript(audioData, timestamp, SAMPLE_RATE)
            
            currentState = RecordingState.SENDING
            renderState()
            
            sendFilesToAPI(audioFile, textFile, transcription)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating files: ${e.message}")
            currentState = RecordingState.LISTENING
            renderState()
        }
    }

    private suspend fun sendFilesToAPI(audioFile: File, textFile: File, transcription: String) {
        try {
            Log.i(TAG, "SENDING TO API: ${audioFile.name}, ${textFile.name}")
            Log.i(TAG, "Content: \"$transcription\"")
            
            delay(1500) // Simulate API call
            
            Log.i(TAG, "SUCCESS - Speech uploaded! Continuing to listen...")
            
        } catch (e: Exception) {
            Log.e(TAG, "API Error: ${e.message}")
        } finally {
            currentState = RecordingState.LISTENING
            renderState()
        }
    }
    
    private fun clearPreviousRecordings() {
        try {
            // Keep only last 10 recordings, delete older ones
            audioDir.listFiles { _, name -> name.startsWith("voice_") }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(10) // Keep newest 10, delete the rest
                ?.forEach { 
                    it.delete()
                    Log.d(TAG, "Deleted old recording: ${it.name}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing old files: ${e.message}")
        }
    }

    private fun stopListening() {
        Log.i(TAG, "Stopping VAD")
        
        currentState = RecordingState.IDLE
        vadJob?.cancel()
        audioRecord?.apply { stop(); release() }
        audioRecord = null
        recordedData.clear()
        
        renderState()
    }

    private fun renderState() {
        val audioPercentage = (currentAudioLevel * 100).toInt().coerceIn(0, 100)
        tvAudioLevel.text = "Audio Level: $audioPercentage%"
        progressAudioLevel.progress = audioPercentage
        
        when (currentState) {
            RecordingState.IDLE -> {
                btnMicrophone.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_mic)
                tvStatus.text = getString(R.string.status_idle)
                btnMicrophone.isEnabled = true
            }
            RecordingState.LISTENING -> {
                btnMicrophone.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_mic_active)
                tvStatus.text = "Continuous listening - VAD active, waiting for speech"
                btnMicrophone.isEnabled = true
            }
            RecordingState.RECORDING -> {
                btnMicrophone.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_mic_active)
                val duration = (System.currentTimeMillis() - recordingStartTime) / 1000.0
                tvStatus.text = "Recording speech: ${String.format("%.1f", duration)}s (auto-stop on silence)"
                btnMicrophone.isEnabled = true
            }
            RecordingState.PROCESSING -> {
                btnMicrophone.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_hourglass)
                tvStatus.text = "Processing speech - creating files & sending to API..."
                btnMicrophone.isEnabled = false
            }
            RecordingState.SENDING -> {
                btnMicrophone.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_send)
                tvStatus.text = "Uploading speech to API..."
                btnMicrophone.isEnabled = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopListening()
    }

    // Removed onPause() - lifecycleScope automatically handles cleanup
} 