package com.shawnrodis.photocraftexpress

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.shawnrodis.photocraftexpress.ui.HomeScreen
import com.shawnrodis.photocraftexpress.ui.PhotoItem
import com.shawnrodis.photocraftexpress.ui.PhotoCraftExpressTheme
import com.shawnrodis.photocraftexpress.ui.PhotoRepository
import com.shawnrodis.photocraftexpress.ui.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app chrome is dark at every API level, so keep the system-bar
        // icons light, and draw edge-to-edge: the root layout in AppRoot
        // applies the safe-drawing insets itself (see below).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            PhotoCraftExpressTheme {
                // One inset gate for the whole app (field report, v0.1.0:
                // the editor's top bar rendered underneath the status bar
                // and camera cutout). The window is edge-to-edge, so every
                // screen lives inside this padded box: nothing interactive
                // can render under the status bar, the display cutout or
                // the gesture/navigation bar, on Home, in the Editor, or on
                // the loading screen. The box paints AppBg full-bleed so
                // the strips behind the system bars match the app chrome.
                // (The export ModalBottomSheet is a separate window and
                // handles its own navigation-bar padding.)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppBg)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    AppRoot()
                }
            }
        }
    }
}

private fun mediaPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
    else Manifest.permission.READ_EXTERNAL_STORAGE

private fun hasMediaPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, mediaPermission()) == PackageManager.PERMISSION_GRANTED

// v0.2.7: GPS EXIF is redacted from content:// reads on API 29+ unless
// the app holds ACCESS_MEDIA_LOCATION (a dangerous permission, so it
// needs a runtime request like the media permission).
private fun locationPermission(): String? =
    if (Build.VERSION.SDK_INT >= 29) Manifest.permission.ACCESS_MEDIA_LOCATION else null

private fun hasLocationPermission(context: Context): Boolean =
    locationPermission()?.let {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    } ?: true

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("photocraft_express", Context.MODE_PRIVATE) }

    // In-app updates: automatic check on launch, throttled to once per 24h,
    // plus a manual check from the About dialog. Never silent: the user
    // always taps Download, then Install in the system prompt.
    var updateInfo by remember { mutableStateOf<UpdateChecker.ReleaseInfo?>(null) }
    var downloadingUpdate by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (UpdateChecker.shouldCheck(prefs)) {
            UpdateChecker.markChecked(prefs)
            val latest = UpdateChecker.fetchLatest()
            if (latest != null && UpdateChecker.isNewer(latest.tag, BuildConfig.VERSION_NAME)) {
                updateInfo = latest
            }
        }
    }

    fun manualUpdateCheck() {
        scope.launch {
            val latest = UpdateChecker.fetchLatest()
            when {
                latest == null -> Toast.makeText(
                    context, "Couldn't check for updates.", Toast.LENGTH_SHORT
                ).show()
                UpdateChecker.isNewer(latest.tag, BuildConfig.VERSION_NAME) -> updateInfo = latest
                else -> Toast.makeText(
                    context, "You're up to date.", Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    var permissionGranted by remember { mutableStateOf(hasMediaPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }
    LaunchedEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(mediaPermission())
    }

    // v0.2.7: ask for the location permission once at startup, same
    // pattern as the media permission. The result is intentionally not
    // stored in state: exports check it at read time, and a denial
    // simply means GPS is left out of the export (everything else
    // still copies). A denial re-asks on the next cold start unless
    // the user chose "don't ask again", which the system then honors.
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no state: read at export time */ }
    LaunchedEffect(Unit) {
        val loc = locationPermission()
        if (loc != null && !hasLocationPermission(context)) locationPermissionLauncher.launch(loc)
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
                sourceUri = uri,
                onBack = { editingUri = null },
                onCheckForUpdates = { manualUpdateCheck() }
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
            onOpenPhoto = { openPhoto(it) },
            onCheckForUpdates = { manualUpdateCheck() }
        )
    }

    // Update available dialog (from the launch check or the About button).
    val info = updateInfo
    if (info != null) {
        AlertDialog(
            onDismissRequest = { if (!downloadingUpdate) updateInfo = null },
            title = { Text("Update available: ${info.versionName}") },
            text = {
                Text(
                    if (info.notes.isNotBlank()) info.notes
                    else "A newer version of PhotoCraft Express is available."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !downloadingUpdate,
                    onClick = {
                        val apkUrl = info.apkUrl
                        if (apkUrl == null) {
                            Toast.makeText(
                                context,
                                "This release has no APK attached.",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@TextButton
                        }
                        downloadingUpdate = true
                        scope.launch {
                            val file = UpdateChecker.downloadApk(
                                context, apkUrl,
                                "PhotoCraftExpress_${info.versionName}.apk"
                            )
                            downloadingUpdate = false
                            if (file == null) {
                                Toast.makeText(
                                    context, "Download failed.", Toast.LENGTH_SHORT
                                ).show()
                            } else if (!UpdateChecker.installApk(context, file)) {
                                Toast.makeText(
                                    context,
                                    "Allow installing unknown apps, then tap Download again.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            updateInfo = null
                        }
                    }
                ) { Text(if (downloadingUpdate) "Downloading…" else "Download") }
            },
            dismissButton = {
                TextButton(
                    enabled = !downloadingUpdate,
                    onClick = { updateInfo = null }
                ) { Text("Later") }
            }
        )
    }
}
