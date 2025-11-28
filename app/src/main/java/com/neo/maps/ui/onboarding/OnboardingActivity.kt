package com.neo.maps.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.neo.maps.core.settings.SettingsStore
import com.neo.maps.databinding.ActivityOnboardingBinding
import com.neo.maps.ui.directions.DirectionsActivity

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGetStarted.setOnClickListener {
            SettingsStore.setOnboardingComplete(this, true)
            startActivity(Intent(this, DirectionsActivity::class.java))
            finish()
        }
    }
}


