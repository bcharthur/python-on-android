// MainActivity.kt
package com.example.downloader

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.rememberImagePainter
import com.example.downloader.ui.theme.DownloaderTheme
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialiser Python si ce n'est pas déjà fait
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        setContent {
            DownloaderTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DownloaderScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloaderScreen(modifier: Modifier = Modifier) {
    var videoUrl by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var thumbnailPath by remember { mutableStateOf<String?>(null) }
    var isFetching by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadedVideoUri by remember { mutableStateOf<Uri?>(null) }

    var expanded by remember { mutableStateOf(false) }
    var selectedFolder by remember { mutableStateOf("Téléchargements") }

    val folders = listOf("Téléchargements", "Pictures", "Movies", "Documents")

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

        // Champ de saisie pour l'URL
        OutlinedTextField(
            value = videoUrl,
            onValueChange = { videoUrl = it },
            label = { Text("Entrez l'URL de la vidéo YouTube") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Sélecteur de dossier
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedFolder,
                onValueChange = {},
                readOnly = true,
                label = { Text("Sélectionnez le dossier de téléchargement") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier.fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                folders.forEach { folder ->
                    DropdownMenuItem(
                        text = { Text(folder) },
                        onClick = {
                            selectedFolder = folder
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Bouton pour récupérer les informations de la vidéo
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
            modifier = Modifier.fillMaxWidth(),
            enabled = !isFetching && !isDownloading
        ) {
            if (isFetching) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(end = 8.dp),
                    strokeWidth = 2.dp
                )
            }
            Text("Rechercher")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Affichage des informations de la vidéo
        if (title.isNotBlank() && thumbnailPath != null) {
            VideoInfoCard(title, thumbnailPath!!)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Bouton pour télécharger la vidéo
        if (title.isNotBlank() && thumbnailPath != null) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        isDownloading = true
                        val successUri = downloadVideo(videoUrl, context, selectedFolder)
                        if (successUri != null) {
                            downloadedVideoUri = successUri
                            Toast.makeText(context, "Vidéo téléchargée avec succès.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Erreur lors du téléchargement de la vidéo.", Toast.LENGTH_SHORT).show()
                        }
                        isDownloading = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isDownloading && !isFetching
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
                Text("Télécharger")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Bouton pour accéder à la vidéo téléchargée
        if (downloadedVideoUri != null) {
            Button(
                onClick = {
                    openVideo(context, downloadedVideoUri!!)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Green)
            ) {
                Text("Accéder à la vidéo")
            }
        }
    }
}

@Composable
fun SupportedPlatformsText() {
    Text(
        text = "Plateformes supportées : YouTube, Dailymotion",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun VideoInfoCard(title: String, thumbnailPath: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.LightGray.copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Affichage de la miniature de la vidéo
            Image(
                painter = rememberImagePainter(thumbnailPath),
                contentDescription = "Miniature de la vidéo",
                modifier = Modifier
                    .height(200.dp)
                    .fillMaxWidth(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

// Fonction pour vérifier si les permissions sont accordées
fun hasPermissions(context: Context): Boolean {
    val readPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED

    return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        val writePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        readPermission && writePermission
    } else {
        // À partir d'Android 10, WRITE_EXTERNAL_STORAGE n'est plus nécessaire pour MediaStore
        readPermission
    }
}

// Fonction pour déterminer si une explication des permissions doit être montrée
fun shouldShowRationale(context: Context): Boolean {
    if (context is ComponentActivity) {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            context.shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE) ||
                    context.shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            context.shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    return false
}

// Fonction pour demander les permissions
fun requestPermissions(
    permissionLauncher: ActivityResultLauncher<Array<String>>,
    context: Context
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        )
    } else {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        )
    }
}

// Fonction pour ouvrir la vidéo téléchargée
fun openVideo(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/*")
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
    // Vérifier s'il existe une application pour gérer l'intent
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        Toast.makeText(context, "Aucune application trouvée pour ouvrir cette vidéo.", Toast.LENGTH_SHORT).show()
    }
}

suspend fun fetchVideoInfo(url: String, context: Context): Pair<String, String?> {
    return withContext(Dispatchers.IO) {
        try {
            val py = Python.getInstance()
            val pyModule = py.getModule("downloader") // Nom du fichier sans .py

            // Utiliser le répertoire de cache de l'application pour les miniatures
            val thumbnailDir = File(context.cacheDir, "thumbnails")
            if (!thumbnailDir.exists()) {
                thumbnailDir.mkdirs()
            } else {
                // Supprimer toutes les miniatures existantes
                thumbnailDir.listFiles()?.forEach { it.delete() }
            }

            val result = pyModule.callAttr("get_video_info", url, thumbnailDir.absolutePath)

            // Accéder directement à l'élément "title" et "thumbnail" du dictionnaire Python
            val title = result.callAttr("get", "title").toString()
            val thumbnailUrl = result.callAttr("get", "thumbnail").toString()

            // Télécharger la miniature si l'URL est présente
            val thumbnailPath = if (thumbnailUrl.isNotBlank()) {
                val thumbnailFilename = "current_thumbnail.jpg" // Nom fixe pour la miniature
                val thumbnailFile = File(thumbnailDir, thumbnailFilename)
                // Télécharger la miniature, écrasant l'ancienne si elle existe
                downloadThumbnail(thumbnailUrl, thumbnailFile, context)
                thumbnailFile.absolutePath
            } else {
                null
            }

            Pair(title, thumbnailPath)
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Erreur: ${e.message}", Toast.LENGTH_LONG).show()
            }
            Pair("", null)
        }
    }
}

suspend fun downloadThumbnail(url: String, file: File, context: Context) {
    withContext(Dispatchers.IO) {
        try {
            val connection = java.net.URL(url).openConnection()
            connection.connect()
            val input = connection.getInputStream()
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        } catch (e: Exception) {
            // Gérer l'erreur de téléchargement de la miniature
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Erreur de téléchargement de la miniature: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}


@SuppressLint("NewApi")
suspend fun downloadVideo(url: String, context: Context, folder: String): Uri? {
    return withContext(Dispatchers.IO) {
        try {
            val py = Python.getInstance()
            val pyModule = py.getModule("downloader") // Nom du fichier sans .py

            // Définir le nom de fichier
            val videoFilename = "video_${System.currentTimeMillis()}.mp4"

            // Déterminer l'URI de MediaStore en fonction du dossier sélectionné
            val collection: Uri = when (folder) {
                "Pictures" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                "Movies" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                "Documents" -> MediaStore.Files.getContentUri("external")
                else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
            }

            // Créer le ContentValues en fonction du dossier
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, videoFilename)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, when (folder) {
                    "Pictures" -> "Pictures/Downloader/"
                    "Movies" -> "Movies/Downloader/"
                    "Documents" -> "Documents/Downloader/"
                    else -> "Download/Downloader/"
                })
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            // Insérer dans MediaStore
            val resolver = context.contentResolver
            val uri: Uri? = resolver.insert(collection, contentValues)

            if (uri == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Impossible de créer l'URI de téléchargement.", Toast.LENGTH_LONG).show()
                }
                return@withContext null
            }

            // Ouvrir un flux de sortie vers l'URI
            val outputStream: OutputStream? = resolver.openOutputStream(uri)

            if (outputStream == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Impossible d'ouvrir le flux de sortie.", Toast.LENGTH_LONG).show()
                }
                return@withContext null
            }

            // Appeler la fonction de téléchargement en passant le chemin temporaire
            // Télécharger dans un fichier temporaire dans le cache
            val tempDir = File(context.cacheDir, "temp")
            if (!tempDir.exists()) tempDir.mkdirs()
            val tempPath = File(tempDir, videoFilename).absolutePath

            pyModule.callAttr("download_video", url, tempPath)

            // Lire le fichier temporaire et écrire dans l'URI de MediaStore
            val tempFile = File(tempPath)
            if (tempFile.exists()) {
                tempFile.inputStream().use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }

                // Mettre à jour le statut dans MediaStore
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }

                // Supprimer le fichier temporaire
                tempFile.delete()

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Vidéo téléchargée dans $folder.", Toast.LENGTH_LONG).show()
                }

                uri
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Fichier temporaire introuvable.", Toast.LENGTH_LONG).show()
                }
                null
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Erreur: ${e.message}", Toast.LENGTH_LONG).show()
            }
            null
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DownloaderScreenPreview() {
    DownloaderTheme {
        DownloaderScreen()
    }
}
