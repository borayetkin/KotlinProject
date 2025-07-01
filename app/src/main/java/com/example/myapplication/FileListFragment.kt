package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class FileListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FileAdapter
    private val PERMISSION_REQUEST_CODE = 100

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d("FileListFragment", "onCreateView called")
        val root = inflater.inflate(R.layout.fragment_file_list, container, false)
        recyclerView = root.findViewById(R.id.recyclerView)
        
        recyclerView.layoutManager = LinearLayoutManager(context)
        Log.d("FileListFragment", "RecyclerView layout manager set")
        
        // Check for permission and load files
        checkPermissionAndLoadFiles()
        
        return root
    }

    private fun checkPermissionAndLoadFiles() {
        // Always load files first from internal storage (no permission needed)
        // Then try external storage if permission is available
        loadFiles()
    }

    private fun loadFiles() {
        try {
            // First try internal files directory
            val internalDirectory = requireContext().filesDir
            var files = internalDirectory.listFiles()?.toList() ?: emptyList()
            
            Log.d("FileListFragment", "Internal directory path: ${internalDirectory.absolutePath}")
            Log.d("FileListFragment", "Internal directory files count: ${files.size}")
            
            // Check if we have any user-created text files (not system files)
            val userFiles = files.filter { it.name.endsWith(".txt") || it.name.endsWith(".doc") || it.name.endsWith(".pdf") }
            
            if (userFiles.isEmpty()) {
                Log.d("FileListFragment", "No user files found, creating sample files...")
                createSampleFiles()
                // Reload files after creating samples
                files = internalDirectory.listFiles()?.toList() ?: emptyList()
                Log.d("FileListFragment", "After creating samples, files count: ${files.size}")
            }

            // OPTION: Filter out system files to show only user files
            // Uncomment the next line if you want to hide system files like 'profileInstalled'
            // files = files.filter { !it.name.startsWith("profile") && !it.name.startsWith(".") }

            Log.d("FileListFragment", "Final files list size: ${files.size}")
            files.forEach { file ->
                val fileType = if (file.name == "profileInstalled") "🤖 System" else "👤 User"
                Log.d("FileListFragment", "$fileType File: ${file.name}, Size: ${file.length()}, IsDirectory: ${file.isDirectory}")
            }

            adapter = FileAdapter(files)
            recyclerView.adapter = adapter
            Log.d("FileListFragment", "Adapter set with ${files.size} files")
        } catch (e: Exception) {
            Log.e("FileListFragment", "Exception in loadFiles: ${e.message}", e)
            Toast.makeText(context, "Error loading files: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun createSampleFiles() {
        try {
            val internalDirectory = requireContext().filesDir
            Log.d("FileListFragment", "Creating sample files in: ${internalDirectory.absolutePath}")
            
            // Create some sample files for demonstration
            val file1 = File(internalDirectory, "sample1.txt").apply {
                writeText("This is a sample file 1\nCreated by our Kotlin app!")
            }
            val file2 = File(internalDirectory, "sample2.txt").apply {
                writeText("This is a sample file 2\nStored in app's internal directory")
            }
            val file3 = File(internalDirectory, "notes.txt").apply {
                writeText("This is a notes file\nLocation: ${internalDirectory.absolutePath}")
            }
            
            Log.d("FileListFragment", "✅ Created file1: ${file1.exists()}, size: ${file1.length()}")
            Log.d("FileListFragment", "✅ Created file2: ${file2.exists()}, size: ${file2.length()}")
            Log.d("FileListFragment", "✅ Created file3: ${file3.exists()}, size: ${file3.length()}")
            
            Toast.makeText(context, "Created sample files in internal storage", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("FileListFragment", "Exception in createSampleFiles: ${e.message}", e)
            Toast.makeText(context, "Error creating sample files: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadFiles()
            } else {
                // Permission denied, load internal files only
                Toast.makeText(context, "Permission denied. Loading internal files only.", Toast.LENGTH_LONG).show()
                loadFiles()
            }
        }
    }
} 