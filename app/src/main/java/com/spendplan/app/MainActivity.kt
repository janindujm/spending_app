package com.spendplan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendplan.app.ui.SpendPlanAppUi
import com.spendplan.app.ui.theme.SpendPlanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settingsFlow = (application as SpendPlanApp).container.settingsRepository.settings
        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = null)
            val s = settings ?: return@setContent // DataStore loads in a few ms; avoids a theme flash
            SpendPlanTheme(themeMode = s.themeMode, dynamicColor = s.dynamicColor) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SpendPlanAppUi()
                }
            }
        }
    }
}
