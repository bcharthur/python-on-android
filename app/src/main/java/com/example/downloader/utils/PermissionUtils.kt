// utils/PermissionUtils.kt
package com.example.downloader.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat

object PermissionUtils {

    // Fonction pour vérifier si les permissions sont accordées
    fun hasPermissions(context: Context): Boolean {
        val readPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        return if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
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
            return if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
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
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
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
}
