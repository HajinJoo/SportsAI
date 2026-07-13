package com.example.sportsai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.sportsai.ui.PremiumSportsDashboard
import com.example.sportsai.ui.theme.SportsAITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SportsAITheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) { innerPadding ->
                    PremiumSportsDashboard(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

