package com.shawnrodis.photocraftexpress

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.shawnrodis.photocraftexpress.engine.PixelImage
import com.shawnrodis.photocraftexpress.ui.Accent
import com.shawnrodis.photocraftexpress.ui.AppBg
import com.shawnrodis.photocraftexpress.ui.BitmapBridge
import com.shawnrodis.photocraftexpress.ui.EditorScreen
import com.shawnrodis.photocraftexpress.ui.Exporter
import com.shawnrodis.photocraftexpress.ui.HomeScreen
import com.shawnrodis.photocraftexpress.ui.PhotoItem
import com.shawnrodis.photocraftexpress.ui.PhotoCraftExpressTheme
import com.shawnrodis.photocraftexpress.ui.PhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhotoCraftExpressTheme {
                AppRoot()
            }
        }
    }
}

private fun mediaPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
    else Manifest.permission.READ_EXTERNAL_STORAGE

private fun hasMediaPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, mediaPermission()) == PackageManager.PERMISSION_GRANTED

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("photocraft_express", Context.MODE_PRIVATE) }

    var permissionGranted by remember { mutableStateOf(hasMediaPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }
    LaunchedEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(mediaPermission())
    }

    var photos by remember { mutableStateOf<List<PhotoItem>>(emptyList()) }
    LaunchedEffect(permissionGranted) {
        if (permissionGranted) {
            photos = withContext(Dispatchers.IO) { PhotoRepository.queryPhotos(context) }
        }
    }

    var lastPhotoUri by remember {
        mutableStateOf(prefs.getString("last_photo_uri", null)?.let { Uri.parse(it) })
    }

    var editingUri by remember { mutableStateOf<Uri?>(null) }
    var editorSource by remember { mutableStateOf<PixelImage?>(null) }

    fun openPhoto(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            // MediaStore URIs don't need (or allow) a persisted grant; the
            // media permission covers them.
        }
        prefs.edit().putString("last_photo_uri", uri.toString()).apply()
        lastPhotoUri = uri
        editorSource = null
        editingUri = uri
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) openPhoto(uri) }

    LaunchedEffect(editingUri) {
        val uri = editingUri ?: return@LaunchedEffect
        editorSource = withContext(Dispatchers.IO) {
            BitmapBridge.loadImage(context, uri, maxLongEdge = 1600)
        }
    }

    when {
        editingUri != null && editorSource != null -> {
            val uri = editingUri!!
            EditorScreen(
                source = editorSource!!,
                onBack = { editingUri = null },
                onExport = { editState, share -> Exporter.export(context, uri, editState, share) }
            )
        }

        editingUri != null -> {
            Box(
                modifier = Modifier.fillMaxSize().background(AppBg),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Accent)
                    Spacer(Modifier.height(12.dp))
                    Text("Loading photo…", color = Color(0xFFBDBDBD))
                }
            }
        }

        else -> HomeScreen(
            photos = photos,
            permissionGranted = permissionGranted,
            lastPhotoUri = lastPhotoUri,
            onRequestPermission = { permissionLauncher.launch(mediaPermission()) },
            onPickPhoto = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onOpenPhoto = { openPhoto(it) }
        )
    }
}
