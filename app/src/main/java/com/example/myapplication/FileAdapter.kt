package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class FileAdapter(
    private val files: List<File>,
    private val onFileClick: (File) -> Unit = {}
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val fileIcon: ImageView = itemView.findViewById(R.id.file_icon)
        val fileName: TextView = itemView.findViewById(R.id.file_name)
        val fileSize: TextView = itemView.findViewById(R.id.file_size)
        val fileDate: TextView = itemView.findViewById(R.id.file_date)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        val holder = FileViewHolder(view)
        
        // Add click listener
        view.setOnClickListener {
            val position = holder.adapterPosition
            if (position != RecyclerView.NO_POSITION) {
                onFileClick(files[position])
            }
        }
        
        return holder
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = files[position]
        holder.fileName.text = file.name
        
        val isSystemFile = file.name == "profileInstalled" || file.name.startsWith(".") || file.name.startsWith("profile")
        val isVoiceFile = file.name.startsWith("voice_")
        val isBackButton = file.name == ".."
        
        when {
            isBackButton -> {
                holder.fileName.text = "Back to Main"
                holder.fileSize.text = "Return to file list"
                holder.fileIcon.setImageResource(R.drawable.ic_back_arrow)
                holder.fileDate.text = "Tap to go back to main directory"
            }
            file.isDirectory && file.name == "voice_recordings" -> {
                val fileCount = file.listFiles()?.size ?: 0
                holder.fileSize.text = "Voice Recordings ($fileCount files)"
                holder.fileIcon.setImageResource(R.drawable.ic_folder)
            }
            file.isDirectory -> {
                holder.fileSize.text = "Folder"
                holder.fileIcon.setImageResource(R.drawable.ic_folder)
            }
            isVoiceFile && file.name.endsWith(".wav") -> {
                holder.fileSize.text = "${formatFileSize(file.length())} • Audio Recording"
                holder.fileIcon.setImageResource(R.drawable.ic_mic)
            }
            isVoiceFile && file.name.endsWith(".txt") -> {
                holder.fileSize.text = "${formatFileSize(file.length())} • Transcription"
                holder.fileIcon.setImageResource(R.drawable.ic_file)
            }
            isSystemFile -> {
                holder.fileSize.text = "${formatFileSize(file.length())} • System"
                holder.fileIcon.setImageResource(R.drawable.ic_file)
            }
            else -> {
                holder.fileSize.text = "${formatFileSize(file.length())} • User"
                holder.fileIcon.setImageResource(R.drawable.ic_file)
            }
        }
        
        if (!isBackButton) {
            val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            val explanation = when {
                file.name == "voice_recordings" -> " • Tap to browse recordings"
                isVoiceFile -> " • Voice Recorder"
                isSystemFile -> " • Android System"
                else -> " • User Created"
            }
            holder.fileDate.text = dateFormat.format(Date(file.lastModified())) + explanation
        }
    }

    override fun getItemCount(): Int = files.size

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
} 