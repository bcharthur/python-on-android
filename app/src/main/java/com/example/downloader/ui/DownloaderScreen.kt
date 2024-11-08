// ui/DownloaderScreen.kt
package com.example.downloader.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberImagePainter
import com.example.downloader.utils.PermissionUtils.hasPermissions
import com.example.downloader.utils.PermissionUtils.requestPermissions
import com.example.downloader.utils.PermissionUtils.shouldShowRationale
import com.example.downloader.utils.downloadVideo
import com.example.downloader.utils.fetchVideoInfo
import com.example.downloader.utils.openVideo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloaderScreen(modifier: Modifier = Modifier) {
    var videoUrl by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var thumbnailPath by remember { mutableStateOf<String?>(null) }
    var isFetching by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadedVideoUri by remember { mutableStateOf<Uri?>(null) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Gestion des permissions avec explication préalable
    var showPermissionRationale by remember { mutableStateOf(false) }

    // Launcher pour demander les permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                Toast.makeText(context, "Permissions accordées", Toast.LENGTH_SHORT).show()
            }
        }
    )

    // Vérification des permissions au lancement
    LaunchedEffect(key1 = Unit) {
        if (!hasPermissions(context)) {
            if (shouldShowRationale(context)) {
                showPermissionRationale = true
            } else {
                requestPermissions(permissionLauncher, context)
            }
        }
    }

    // Dialogue d'explication des permissions
    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text("Permissions nécessaires") },
            text = { Text("Cette application a besoin d'accéder à votre stockage pour télécharger des vidéos.") },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionRationale = false
                    requestPermissions(permissionLauncher, context)
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationale = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Web Downloader",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        SupportedPlatformsText()

        Spacer(modifier = Modifier.height(24.dp))

        // Champ de saisie pour l'URL et Bouton Rechercher côte à côte
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = videoUrl,
                onValueChange = { videoUrl = it },
                label = { Text("Entrez l'URL de la vidéo YouTube") },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                singleLine = true
            )

            // Bouton Rechercher avec icône de loupe agrandie
            Button(
                onClick = {
                    if (videoUrl.isNotBlank()) {
                        coroutineScope.launch {
                            isFetching = true
                            val (videoTitle, thumbnail) = fetchVideoInfo(videoUrl, context)
                            title = videoTitle
                            thumbnailPath = thumbnail
                            isFetching = false
                        }
                    } else {
                        Toast.makeText(context, "Veuillez entrer une URL de vidéo.", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = !isFetching && !isDownloading,
                modifier = Modifier
                    .size(64.dp) // Taille agrandie pour le bouton
            ) {
                if (isFetching) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Rechercher",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp) // Taille agrandie de l'icône
                    )
                }
            }

        }

        Spacer(modifier = Modifier.height(24.dp))

        // Affichage des informations de la vidéo
        if (title.isNotBlank() && thumbnailPath != null) {
            VideoInfoCard(title, thumbnailPath!!)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Boutons pour télécharger la vidéo et accéder à la vidéo téléchargée
        if (title.isNotBlank() && thumbnailPath != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bouton Télécharger
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isDownloading = true
                            val successUri = downloadVideo(url = videoUrl, context = context)
                            if (successUri != null) {
                                downloadedVideoUri = successUri
                                Toast.makeText(context, "Vidéo téléchargée avec succès.", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Erreur lors du téléchargement de la vidéo.", Toast.LENGTH_SHORT).show()
                            }
                            isDownloading = false
                        }
                    },
                    enabled = !isDownloading && !isFetching,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = "Télécharger",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Bouton Accéder à la vidéo
                if (downloadedVideoUri != null) {
                    Button(
                        onClick = { openVideo(context, downloadedVideoUri!!) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Green),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Accéder à la vidéo")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
