package com.astrovm.gripmaxxer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.astrovm.gripmaxxer.feedback.FloatingTimer
import com.astrovm.gripmaxxer.media.MediaRemote
import com.astrovm.gripmaxxer.ui.Access
import com.astrovm.gripmaxxer.ui.GripmaxxerApp
import com.astrovm.gripmaxxer.ui.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        viewModelFactory { initializer { MainViewModel(container, ::readAccess) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent { GripmaxxerApp(viewModel) }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshAccess()
    }

    private fun readAccess() = Access(
        camera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED,
        notifications = MediaRemote.hasAccess(this),
        overlay = FloatingTimer.canShow(this),
    )
}
