package com.example.screensender

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class NotesFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_placeholder, container, false)
        view.findViewById<TextView>(R.id.placeholderTitle).text = "Notes"
        view.findViewById<TextView>(R.id.placeholderBody).text =
            "Phase 4: live synced note pages across devices."
        return view
    }
}
