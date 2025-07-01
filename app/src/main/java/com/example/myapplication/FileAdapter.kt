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

class FileAdapter(private val files: List<File>) :
    RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val fileIcon: ImageView = itemView.findViewById(R.id.file_icon)
        val fileName: TextView = itemView.findViewById(R.id.file_name)
        val fileSize: TextView = itemView.findViewById(R.id.file_size)
        val fileDate: TextView = itemView.findViewById(R.id.file_date)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = files[position]
        holder.fileName.text = file.name
        
        val isSystemFile = file.name == "profileInstalled" || file.name.startsWith(".") || file.name.startsWith("profile")
        
        if (file.isDirectory) {
            holder.fileSize.text = "Folder"
            holder.fileIcon.setImageResource(R.drawable.ic_folder)
        } else {
            val fileType = if (isSystemFile) "🤖 System" else "👤 User"
            holder.fileSize.text = "${formatFileSize(file.length())} • $fileType"
            holder.fileIcon.setImageResource(R.drawable.ic_file)
        }
        
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val explanation = if (isSystemFile) " • Created by Android" else " • Created by app"
        holder.fileDate.text = dateFormat.format(Date(file.lastModified())) + explanation
    }

    override fun getItemCount(): Int = files.size

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
} 