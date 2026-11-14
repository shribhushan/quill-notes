package com.quillnotes

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.quillnotes.ui.QuillNotesMainScreen
import com.quillnotes.ui.components.BiometricGate
import com.quillnotes.ui.theme.QuillNotesTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // A recreated activity (rotation, theme change) must not act on the same request twice.
        val launchRequest = if (savedInstanceState == null) LaunchRequest.from(intent) else LaunchRequest.NONE

        setContent {
            QuillNotesTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BiometricGate {
                        QuillNotesMainScreen(launchRequest = launchRequest)
                    }
                }
            }
        }
    }
}
