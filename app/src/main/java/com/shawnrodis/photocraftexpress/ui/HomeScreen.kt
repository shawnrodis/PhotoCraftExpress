package com.shawnrodis.photocraftexpress.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Home: app bar with search + account, a prominent Edit entry, an optional
 * "jump back in" card for the last photo, and the device photo grid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    photos: List<PhotoItem>,
    permissionGranted: Boolean,
    lastPhotoUri: Uri?,
    onRequestPermission: () -> Unit,
    onPickPhoto: () -> Unit,
    onOpenPhoto: (Uri) -> Unit,
    onCheckForUpdates: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var showCredits by remember { mutableStateOf(false) }
    val shown = if (query.isBlank()) photos else photos.filter { it.name.contains(query, ignoreCase = true) }

    Column(modifier = modifier.fillMaxSize().background(AppBg)) {
        TopAppBar(
            title = { Text("PhotoCraft Express", fontWeight = FontWeight.Bold) },
            actions = {
                IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) query = "" }) {
                    Icon(if (searchOpen) Icons.Filled.Close else Icons.Filled.Search, contentDescription = "Search photos")
                }
                IconButton(onClick = { showCredits = true }) {
                    Icon(Icons.Filled.AccountCircle, contentDescription = "About and credits")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = AppBg,
                titleContentColor = Color.White,
                actionIconContentColor = Color.White
            )
        )

        if (searchOpen) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search by file name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // Prominent Edit entry.
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clickable { onPickPhoto() }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, tint = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Edit a photo", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text("Looks, crop, adjustments and more", fontSize = 13.sp, color = Color(0xFFBDBDBD))
                }
                Button(
                    onClick = onPickPhoto,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(18.dp)
                ) { Text("Edit") }
            }
        }

        if (lastPhotoUri != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp)
                    .clickable { onOpenPhoto(lastPhotoUri) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp)
                ) {
                    UriThumbnail(lastPhotoUri, Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Jump back in", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text("Continue editing your last photo", fontSize = 12.sp, color = Color(0xFFBDBDBD))
                    }
                }
            }
        }

        if (!permissionGranted) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp)
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = Color(0xFF8A8A8A), modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(10.dp))
                Text(
                    "Allow photo access to browse your library here, or pick a photo with the system picker.",
                    color = Color(0xFFBDBDBD),
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(12.dp))
                Row {
                    Button(onClick = onRequestPermission, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                        Text("Allow access")
                    }
                    Spacer(Modifier.width(10.dp))
                    TextButton(onClick = onPickPhoto) { Text("Use photo picker", color = Accent) }
                }
            }
        } else {
            Text(
                "Your photos",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            if (shown.isEmpty()) {
                Text(
                    if (photos.isEmpty()) "No photos found on this device yet — tap Edit to pick one."
                    else "No photos match \"$query\".",
                    color = Color(0xFF9E9E9E),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(shown, key = { it.id }) { item ->
                        UriThumbnail(
                            item.uri,
                            Modifier
                                .aspectRatio(1f)
                                .clickable { onOpenPhoto(item.uri) }
                        )
                    }
                }
            }
        }
    }

    if (showCredits) {
        AlertDialog(
            onDismissRequest = { showCredits = false },
            title = { Text("PhotoCraft Express 0.2.4") },
            text = {
                Text(
                    "A photo editor with an original codebase and hand-drawn icons. " +
                        "Its adjustment engine's mathematics follows PhotoCraft " +
                        "(github.com/storytold/photocraft), the open-source clean-room " +
                        "photo editor by storytold / ArtCraft, used under the MIT OR " +
                        "Apache-2.0 licenses — see the NOTICE file in the source repo.\n\n" +
                        "The interface recreates Photoshop Express's layout and flow " +
                        "with original artwork. PhotoCraft Express is not affiliated " +
                        "with or endorsed by storytold/ArtCraft or Adobe."
                )
            },
            confirmButton = {
                TextButton(onClick = { showCredits = false }) { Text("Close") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCredits = false; onCheckForUpdates() }
                ) { Text("Check for updates") }
            }
        )
    }
}

@Composable
private fun UriThumbnail(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bmp by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        bmp = withContext(Dispatchers.IO) { PhotoRepository.loadThumbnail(context, uri) }
    }
    Box(modifier = modifier.background(Color(0xFF262626))) {
        bmp?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
