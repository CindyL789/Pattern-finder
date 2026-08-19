package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.CrochetSearchScreen
import com.example.ui.CrochetViewModel
import com.example.ui.theme.CrochetTheme

class MainActivity : ComponentActivity() {

    private val viewModel: CrochetViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CrochetTheme {
                CrochetSearchScreen(viewModel = viewModel)
            }
        }
    }
}

