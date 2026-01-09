package com.example.screensender

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val pager = findViewById<ViewPager2>(R.id.mainPager)
        val tabs = findViewById<TabLayout>(R.id.mainTabs)

        pager.adapter = MainPagerAdapter(this)
        TabLayoutMediator(tabs, pager) { tab, position ->
            tab.text = when (position) {
                0 -> "Devices"
                1 -> "Screen"
                2 -> "Files"
                else -> "Notes"
            }
        }.attach()
    }
}
