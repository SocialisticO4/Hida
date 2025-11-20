package com.example.hida

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.hida.ui.NavigationGraph
import com.example.hida.ui.theme.HidaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Theme is handled inside CalculatorScreen for dynamic switching, 
            // but we can wrap the whole graph in a default theme if needed.
            // For now, let's just call the graph.
            NavigationGraph()
        }
    }
}