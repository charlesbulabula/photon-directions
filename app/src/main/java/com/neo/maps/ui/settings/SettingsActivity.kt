package com.neo.maps.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.neo.maps.core.metrics.UploadHistoryStore
import com.neo.maps.core.settings.SettingsStore
import com.neo.maps.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = binding.settingsTitle.text

        // Wi‑Fi only toggle
        binding.switchWifiOnly.isChecked = SettingsStore.isWifiOnlyUploads(this)
        binding.switchWifiOnly.setOnCheckedChangeListener { _, isChecked ->
            SettingsStore.setWifiOnlyUploads(this, isChecked)
        }

        // Basic upload statistics
        refreshUploadStats()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun refreshUploadStats() {
        val entries = UploadHistoryStore.load(this)
        if (entries.isEmpty()) {
            binding.uploadStats.text = "No uploads yet."
        } else {
            val total = entries.size
            val successes = entries.count { it.success }
            val last = entries.first()
            binding.uploadStats.text =
                "Uploads: $successes / $total successful\nLast code: ${last.code}"
        }
    }

    companion object {
        fun newIntent(context: Context): Intent =
            Intent(context, SettingsActivity::class.java)
    }
}


