package com.jasonkang.memorix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jasonkang.memorix.app.navigation.MemorixNavHost
import com.jasonkang.memorix.core.designsystem.theme.MemorixTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MemorixTheme {
                MemorixNavHost()
            }
        }
    }
}
