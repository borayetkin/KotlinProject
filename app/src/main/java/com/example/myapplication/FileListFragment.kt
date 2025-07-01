package com.example.myapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class FileListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FileAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_file_list, container, false)
        recyclerView = root.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        
        loadFiles()
        return root
    }

    private fun loadFiles() {
        try {
            val internalDirectory = requireContext().filesDir
            var files = internalDirectory.listFiles()?.toList() ?: emptyList()
            
            // Create sample files if no user files exist
            val userFiles = files.filter { it.name.endsWith(".txt") || it.name.endsWith(".doc") || it.name.endsWith(".pdf") }
            if (userFiles.isEmpty()) {
                createSampleFiles()
                files = internalDirectory.listFiles()?.toList() ?: emptyList()
            }

            adapter = FileAdapter(files)
            recyclerView.adapter = adapter
        } catch (e: Exception) {
            Toast.makeText(context, "Error loading files: ${e.message}", Toast.LENGTH_LONG).show()
        }
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