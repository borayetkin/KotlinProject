package com.example.myapplication

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import java.io.File

class AudioPlayerManager private constructor() {
    
    companion object {
        private const val TAG = "AudioPlayer"
        
        @Volatile
        private var INSTANCE: AudioPlayerManager? = null
        
        fun getInstance(): AudioPlayerManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AudioPlayerManager().also { INSTANCE = it }
            }
        }
    }
    
    private var mediaPlayer: MediaPlayer? = null
    private var currentPlayingFile: File? = null
    private var onPlaybackStateChanged: ((isPlaying: Boolean, file: File?) -> Unit)? = null
    
    val isPlaying: Boolean
        get() = mediaPlayer?.isPlaying == true
    
    val currentFile: File?
        get() = currentPlayingFile
    
    fun setOnPlaybackStateChangedListener(listener: (isPlaying: Boolean, file: File?) -> Unit) {
        onPlaybackStateChanged = listener
    }
    
    fun playAudio(context: Context, audioFile: File) {
        try {
            // If same file is already playing, pause it
            if (isPlaying && currentPlayingFile == audioFile) {
                pauseAudio()
                return
            }
            
            // Stop any currently playing audio
            stopAudio()
            
            Log.i(TAG, "Playing audio file: ${audioFile.name}")
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                setOnPreparedListener { 
                    start()
                    currentPlayingFile = audioFile
                    onPlaybackStateChanged?.invoke(true, audioFile)
                    Log.i(TAG, "Audio playback started: ${audioFile.name}")
                }
                setOnCompletionListener {
                    Log.i(TAG, "Audio playback completed: ${audioFile.name}")
                    stopAudio()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "Audio playback error: what=$what, extra=$extra")
                    stopAudio()
                    true
                }
                prepareAsync()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio file: ${e.message}")
            stopAudio()
        }
    }
    
    fun pauseAudio() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                    onPlaybackStateChanged?.invoke(false, currentPlayingFile)
                    Log.i(TAG, "Audio paused: ${currentPlayingFile?.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing audio: ${e.message}")
        }
    }
    
    fun resumeAudio() {
        try {
            mediaPlayer?.let {
                if (!it.isPlaying) {
                    it.start()
                    onPlaybackStateChanged?.invoke(true, currentPlayingFile)
                    Log.i(TAG, "Audio resumed: ${currentPlayingFile?.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming audio: ${e.message}")
        }
    }
    
    fun stopAudio() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null
            val wasPlaying = currentPlayingFile != null
            val previousFile = currentPlayingFile
            currentPlayingFile = null
            
            if (wasPlaying) {
                onPlaybackStateChanged?.invoke(false, previousFile)
                Log.i(TAG, "Audio stopped: ${previousFile?.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio: ${e.message}")
        }
    }
    
    fun isPlayingFile(file: File): Boolean {
        return isPlaying && currentPlayingFile == file
    }
} 