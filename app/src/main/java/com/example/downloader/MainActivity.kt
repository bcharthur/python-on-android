// MainActivity.kt
package com.example.downloader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.downloader.ui.MainScreen
import com.example.downloader.ui.theme.DownloaderTheme
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialiser Python si ce n'est pas déjà fait
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        setContent {
            DownloaderTheme {
                MainScreen()
            }
        }
    }
}
