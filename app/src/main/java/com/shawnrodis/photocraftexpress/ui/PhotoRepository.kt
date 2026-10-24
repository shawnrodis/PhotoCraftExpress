package com.shawnrodis.photocraftexpress.ui

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size

data class PhotoItem(val id: Long, val uri: Uri, val name: String)

/** Device photo access: MediaStore grid queries + cached thumbnails. */
object PhotoRepository {

    private const val MAX_PHOTOS = 150
    private val thumbCache = LruCache<Uri, Bitmap>(80)

    fun queryPhotos(context: Context): List<PhotoItem> {
        val items = mutableListOf<PhotoItem>()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        try {
            context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                while (cursor.moveToNext() && items.size < MAX_PHOTOS) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(collection, id)
                    items.add(PhotoItem(id, uri, cursor.getString(nameCol) ?: "Photo"))
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        }
        return items
    }

    suspend fun loadThumbnail(context: Context, uri: Uri, sizePx: Int = 360): Bitmap? {
        thumbCache.get(uri)?.let { return it }
        val bmp = try {
            if (Build.VERSION.SDK_INT >= 29) {
                context.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
            } else {
                BitmapBridge.loadImage(context, uri, sizePx)?.let { BitmapBridge.toBitmap(it) }
            }
        } catch (e: Exception) {
            null
        }
        if (bmp != null) thumbCache.put(uri, bmp)
        return bmp
    }
}
