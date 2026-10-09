package com.example.droneservicesapp.ui.compose.foundation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.droneservicesapp.ui.compose.theme.DroneServicesTheme

class ComposeFoundationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DroneServicesTheme {
                ComposeFoundationSurface()
            }
        }
    }
}
