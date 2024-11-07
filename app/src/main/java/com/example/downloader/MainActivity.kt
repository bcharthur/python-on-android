package com.example.downloader

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.example.downloader.ui.theme.DownloaderTheme
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

class MainActivity : ComponentActivity() {

    // Enregistrement du launcher pour les permissions
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Toast.makeText(this, "Permissions accordées", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                this,
                "Permissions refusées. L'application ne fonctionnera pas correctement.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialiser Python si ce n'est pas déjà fait
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        // Demander les permissions si elles ne sont pas déjà accordées
        if (!hasPermissions()) {
            requestPermissions()
        }

        setContent {
            DownloaderTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DownloaderScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    private fun hasPermissions(): Boolean {
        val readPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val writePermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            readPermission && writePermission
        } else {
            // À partir d'Android 10, WRITE_EXTERNAL_STORAGE n'est plus nécessaire pour MediaStore
            readPermission
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        } else {
            // À partir d'Android 10, seule la lecture est nécessaire
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloaderScreen(modifier: Modifier = Modifier) {
    var videoUrl by remember { mutableStateOf(TextFieldValue("")) }
    var title by remember { mutableStateOf("") }
    var thumbnailPath by remember { mutableStateOf<String?>(null) }
    var isFetching by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var sslTestResult by remember { mutableStateOf("") }

    var expanded by remember { mutableStateOf(false) }
    var selectedFolder by remember { mutableStateOf("Téléchargements") }

    val folders = listOf("Téléchargements", "Pictures", "Movies", "Documents")

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Web Downloader",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        SupportedPlatformsText()

        Spacer(modifier = Modifier.height(24.dp))

        // Champ de saisie pour l'URL
        OutlinedTextField(
            value = videoUrl,
            onValueChange = { videoUrl = it },
            label = { Text("Entrez l'URL de la vidéo YouTube") },
            modifier = Modifier.fillMaxWidth()
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
                if (videoUrl.text.isNotBlank()) {
                    coroutineScope.launch {
                        isFetching = true
                        val (videoTitle, thumbnail) = fetchVideoInfo(videoUrl.text, context)
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
                        val success = downloadVideo(videoUrl.text, context, selectedFolder)
                        if (success) {
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

        // Bouton pour tester la connexion SSL
        Button(
            onClick = {
                coroutineScope.launch {
                    val result = testSSL(context)
                    sslTestResult = result
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Tester la connexion SSL")
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (sslTestResult.isNotBlank()) {
            Text("Résultat du test SSL : $sslTestResult")
        }
    }
}

@Composable
fun SupportedPlatformsText() {
    Text(
        text = "Plateformes supportées : YouTube, Dailymotion",
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
fun VideoInfoCard(title: String, thumbnailPath: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Remplacer l'image par du texte
            Text(
                text = "Miniature de la vidéo",
                style = MaterialTheme.typography.bodyMedium
            )
        }
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
            }

            val result = pyModule.callAttr("get_video_info", url, thumbnailDir.absolutePath)

            // Accéder directement à l'élément "title" et "thumbnail" du dictionnaire Python
            val title = result.callAttr("get", "title").toString()
            val thumbnailUrl = result.callAttr("get", "thumbnail").toString()

            // Télécharger la miniature si l'URL est présente
            val thumbnailPath = if (thumbnailUrl.isNotBlank()) {
                val thumbnailFilename = thumbnailUrl.substringAfterLast("/").takeIf { it.isNotBlank() }
                thumbnailFilename?.let {
                    val thumbnailFile = File(thumbnailDir, it)
                    if (!thumbnailFile.exists()) {
                        // Télécharger la miniature
                        downloadThumbnail(thumbnailUrl, thumbnailFile)
                    }
                    thumbnailFile.absolutePath
                }
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

suspend fun downloadThumbnail(url: String, file: File) {
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
        }
    }
}

suspend fun downloadVideo(url: String, context: Context, folder: String): Boolean {
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
                return@withContext false
            }

            // Ouvrir un flux de sortie vers l'URI
            val outputStream: OutputStream? = resolver.openOutputStream(uri)

            if (outputStream == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Impossible d'ouvrir le flux de sortie.", Toast.LENGTH_LONG).show()
                }
                return@withContext false
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

                true
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Fichier temporaire introuvable.", Toast.LENGTH_LONG).show()
                }
                false
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Erreur: ${e.message}", Toast.LENGTH_LONG).show()
            }
            false
        }
    }
}

fun createDownloadUri(context: Context, filename: String): Uri? {
    val resolver = context.contentResolver
    val contentValues = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, filename)
        put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
        put(MediaStore.Downloads.IS_PENDING, 1)
    }

    return resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
}

suspend fun testSSL(context: Context): String {
    return withContext(Dispatchers.IO) {
        try {
            val py = Python.getInstance()
            val pyModule = py.getModule("test_ssl") // Nom du fichier sans .py
            val result = pyModule.callAttr("test_ssl")
            result.toString()
        } catch (e: Exception) {
            e.message ?: "Unknown error"
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
