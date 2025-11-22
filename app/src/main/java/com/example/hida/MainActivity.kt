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
        
        // Prevent screenshots and recent apps preview
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        enableEdgeToEdge()
        setContent {
            // Theme is handled inside CalculatorScreen for dynamic switching, 
            // but we can wrap the whole graph in a default theme if needed.
            // For now, let's just call the graph.
            NavigationGraph()
        }
    }
}