// utils/VideoUtils.kt
package com.example.downloader.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

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

suspend fun downloadVideo(url: String, context: Context): Uri? {
    return withContext(Dispatchers.IO) {
        try {
            val py = Python.getInstance()
            val pyModule = py.getModule("downloader") // Nom du fichier sans .py

            // Définir le nom de fichier
            val videoFilename = "video_${System.currentTimeMillis()}.mp4"

            // Déterminer l'URI de MediaStore en imposant le dossier Téléchargements
            val collection: Uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI

            // Créer le ContentValues en fonction du dossier
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, videoFilename)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/Downloader/")
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
                    Toast.makeText(context, "Vidéo téléchargée dans Téléchargements.", Toast.LENGTH_LONG).show()
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
