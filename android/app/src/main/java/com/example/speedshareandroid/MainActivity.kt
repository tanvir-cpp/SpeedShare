package com.example.speedshareandroid

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.speedshareandroid.theme.SpeedShareAndroidTheme
import com.example.speedshareandroid.ui.OnboardingScreen
import com.example.speedshareandroid.ui.SpeedShareScreen

class MainActivity : ComponentActivity() {
    companion object {
        private const val PREFS_NAME = "speedshare_prefs"
        private const val KEY_ONBOARDING_COMPLETED = "has_completed_onboarding"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        setContent {
            var showOnboarding by remember {
                mutableStateOf(!prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false))
            }

            SpeedShareAndroidTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showOnboarding) {
                        OnboardingScreen(
                            onFinished = {
                                prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
                                showOnboarding = false
                            }
                        )
                    } else {
                        SpeedShareScreen()
                    }
                }
            }
        }
    }
}
