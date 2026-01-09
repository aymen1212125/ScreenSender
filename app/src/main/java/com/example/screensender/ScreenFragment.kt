package com.example.screensender

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class ScreenFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_placeholder, container, false)
        view.findViewById<TextView>(R.id.placeholderTitle).text = "Screen Share"
        view.findViewById<TextView>(R.id.placeholderBody).text =
            "Phase 2: start/stop screen streaming and show status here."
        return view
    }
}
