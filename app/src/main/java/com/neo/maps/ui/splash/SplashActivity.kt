package com.neo.maps.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.neo.maps.core.settings.SettingsStore
import com.neo.maps.databinding.ActivitySplashBinding
import com.neo.maps.ui.directions.DirectionsActivity
import com.neo.maps.ui.onboarding.OnboardingActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            delay(1500L)
            val next = if (SettingsStore.isOnboardingComplete(this@SplashActivity)) {
                DirectionsActivity::class.java
            } else {
                OnboardingActivity::class.java
            }
            startActivity(Intent(this@SplashActivity, next))
            finish()
        }
    }
}
