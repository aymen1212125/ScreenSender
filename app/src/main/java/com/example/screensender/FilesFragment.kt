package com.example.screensender

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class FilesFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_placeholder, container, false)
        view.findViewById<TextView>(R.id.placeholderTitle).text = "File Transfer"
        view.findViewById<TextView>(R.id.placeholderBody).text =
            "Phase 3: send and receive large files with progress."
        return view
    }
}
