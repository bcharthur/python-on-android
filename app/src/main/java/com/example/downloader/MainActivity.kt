package com.example.downloader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.example.downloader.ui.theme.DownloaderTheme
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialiser Python si ce n'est pas déjà fait
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        setContent {
            DownloaderTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(modifier: Modifier = Modifier) {
    var message by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = {
            try {
                // Appeler le script Python
                val py = Python.getInstance()
                val pyModule = py.getModule("my_script") // Nom du fichier sans .py
                val result = pyModule.callAttr("get_message")

                // Accéder directement à l'élément "message" du dictionnaire Python
                val messagePyObj = result.callAttr("get", "message")
                message = messagePyObj.toString()
            } catch (e: Exception) {
                message = "Erreur : ${e.message}"
            }
        }) {
            Text("Exécuter le script Python")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
