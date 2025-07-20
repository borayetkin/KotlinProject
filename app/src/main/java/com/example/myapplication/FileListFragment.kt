package com.example.myapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.app.AlertDialog
import java.io.File

class FileListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FileAdapter
    private var currentDirectory: File? = null
    private lateinit var audioPlayer: AudioPlayerManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_file_list, container, false)
        recyclerView = root.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        
        // Initialize audio player
        audioPlayer = AudioPlayerManager.getInstance()
        audioPlayer.setOnPlaybackStateChangedListener { isPlaying, file ->
            // Refresh adapter to update play/pause icons
            adapter.notifyDataSetChanged()
            
            // Show user feedback
            if (isPlaying && file != null) {
                Toast.makeText(context, "Playing: ${file.name}", Toast.LENGTH_SHORT).show()
            } else if (!isPlaying && file != null) {
                Toast.makeText(context, "Paused: ${file.name}", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Start from main directory
        currentDirectory = null
        loadFiles()
        return root
    }

    private fun loadFiles() {
        try {
            val targetDirectory = currentDirectory ?: requireContext().filesDir
            var files = targetDirectory.listFiles()?.toMutableList() ?: mutableListOf()
            
            if (currentDirectory == null) {
                // Main directory - add voice recordings as a browsable folder
                val voiceDir = File(requireContext().filesDir, "voice_recordings")
                if (voiceDir.exists() && voiceDir.isDirectory) {
                    // Don't add individual voice files to main list, just the folder
                    if (!files.contains(voiceDir)) {
                        files.add(voiceDir)
                    }
                }
                
                // Create sample files if no user files exist
                val userFiles = files.filter { 
                    it.name.endsWith(".txt") || it.name.endsWith(".doc") || it.name.endsWith(".pdf") || 
                    it.name == "voice_recordings"
                }
                if (userFiles.isEmpty()) {
                    createSampleFiles()
                    files = requireContext().filesDir.listFiles()?.toMutableList() ?: mutableListOf()
                    
                    // Re-add voice recordings folder
                    if (voiceDir.exists() && voiceDir.isDirectory && !files.contains(voiceDir)) {
                        files.add(voiceDir)
                    }
                }
            }

            // Add back button if we're in a subdirectory
            if (currentDirectory != null) {
                val backDir = File("..")
                files.add(0, backDir)
            }

            // Sort files: directories first, then by name (except back button)
            val backButton = if (currentDirectory != null) files.removeAt(0) else null
            files.sortWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            if (backButton != null) {
                files.add(0, backButton)
            }

            adapter = FileAdapter(files, { file -> 
                onFileClick(file)
            }, audioPlayer.currentFile)
            recyclerView.adapter = adapter
        } catch (e: Exception) {
            Toast.makeText(context, "Error loading files: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        // Stop any playing audio when leaving this fragment
        audioPlayer.stopAudio()
    }
    
    private fun onFileClick(file: File) {
        when {
            file.name == ".." -> {
                // Go back to parent directory
                currentDirectory = null
                loadFiles()
            }
            file.isDirectory -> {
                // Navigate into directory
                currentDirectory = file
                loadFiles()
            }
            file.name.endsWith(".wav") && file.name.startsWith("voice_") -> {
                // Play/pause audio file
                try {
                    if (audioPlayer.isPlayingFile(file)) {
                        audioPlayer.pauseAudio()
                        Toast.makeText(context, "Audio paused", Toast.LENGTH_SHORT).show()
                    } else {
                        audioPlayer.playAudio(requireContext(), file)
                        // Success/failure feedback is handled by the playback state listener
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error playing audio: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            file.name.endsWith(".txt") && file.name.startsWith("voice_") -> {
                // Show transcript content
                showTranscriptContent(file)
            }
            else -> {
                Toast.makeText(context, "File: ${file.name}\nSize: ${formatFileSize(file.length())}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showTranscriptContent(file: File) {
        try {
            val content = file.readText()
            
            // Parse the transcript file to extract duration, transcription, and translation
            val lines = content.lines()
            var duration = "Unknown"
            var transcription = ""
            var translation = ""
            
            // Find duration line
            lines.forEach { line ->
                if (line.startsWith("Duration:")) {
                    duration = line.substringAfter("Duration: ").trim()
                }
            }
            
            // Find transcription section
            val transcriptionIndex = lines.indexOfFirst { it.trim() == "TRANSCRIPTION:" }
            if (transcriptionIndex != -1 && transcriptionIndex + 1 < lines.size) {
                // Get all lines after "TRANSCRIPTION:" until we hit "TRANSLATION:" or footer
                val transcriptionLines = lines.drop(transcriptionIndex + 1)
                val translationIndex = transcriptionLines.indexOfFirst { it.trim() == "TRANSLATION:" }
                val footerIndex = transcriptionLines.indexOfFirst { it.trim().startsWith("Generated by") }
                
                val endIndex = when {
                    translationIndex != -1 -> translationIndex
                    footerIndex != -1 -> footerIndex
                    else -> transcriptionLines.size
                }
                
                transcription = transcriptionLines.take(endIndex).joinToString("\n").trim()
                
                // Extract translation if present
                if (translationIndex != -1 && translationIndex + 1 < transcriptionLines.size) {
                    val translationLines = transcriptionLines.drop(translationIndex + 1)
                    val translationFooterIndex = translationLines.indexOfFirst { it.trim().startsWith("Generated by") }
                    
                    translation = if (translationFooterIndex != -1) {
                        translationLines.take(translationFooterIndex).joinToString("\n").trim()
                    } else {
                        translationLines.joinToString("\n").trim()
                    }
                }
            }
            
            // Build message with transcription and translation
            val messageBuilder = StringBuilder()
            messageBuilder.append("Duration: $duration\n\n")
            messageBuilder.append("Transcription:\n$transcription")
            
            if (translation.isNotBlank()) {
                messageBuilder.append("\n\nTranslation:\n$translation")
            }
            
            // Show in a clean dialog
            AlertDialog.Builder(requireContext())
                .setTitle("Voice Transcription")
                .setMessage(messageBuilder.toString())
                .setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }
                .show()
                
        } catch (e: Exception) {
            Toast.makeText(context, "Error reading transcript: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    private fun createSampleFiles() {
        try {
            val internalDirectory = requireContext().filesDir
            
            File(internalDirectory, "sample1.txt").writeText(
                "This is a sample file 1\nCreated by our Kotlin app!"
            )
            File(internalDirectory, "sample2.txt").writeText(
                "This is a sample file 2\nStored in app's internal directory"
            )
            File(internalDirectory, "notes.txt").writeText(
                "This is a notes file\nLocation: ${internalDirectory.absolutePath}"
            )
            
            Toast.makeText(context, "Created sample files", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error creating sample files: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }


} 