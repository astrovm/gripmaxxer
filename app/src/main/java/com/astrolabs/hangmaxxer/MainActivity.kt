package com.astrolabs.hangmaxxer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.astrolabs.hangmaxxer.ui.MainScreen
import com.astrolabs.hangmaxxer.ui.MainViewModel
import com.astrolabs.hangmaxxer.ui.theme.HangmaxxerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HangmaxxerTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissionState()
    }
}
