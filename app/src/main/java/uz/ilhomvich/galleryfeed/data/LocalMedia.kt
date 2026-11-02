package uz.ilhomvich.galleryfeed.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A read-only description of one media item already stored on the device. */
data class LocalMedia(
    val key: String,
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val dateAddedSeconds: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val relativePath: String,
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
}

object LocalMediaScanner {
    suspend fun scan(context: Context): List<LocalMedia> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri("external")
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
            }
            add(MediaStore.Video.VideoColumns.DURATION)
        }.toTypedArray()

        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )

        val result = ArrayList<LocalMedia>()
        resolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val pathCol = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            val durationCol = cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val mime = cursor.getString(mimeCol) ?: continue
                val isVideo = mime.startsWith("video/")
                val baseUri = if (isVideo) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                val uri = ContentUris.withAppendedId(baseUri, id)
                val name = cursor.getString(nameCol) ?: "Media $id"
                result += LocalMedia(
                    key = uri.toString(),
                    uri = uri,
                    displayName = name,
                    mimeType = mime,
                    dateAddedSeconds = cursor.getLong(dateCol),
                    sizeBytes = cursor.getLong(sizeCol),
                    width = cursor.getInt(widthCol),
                    height = cursor.getInt(heightCol),
                    durationMs = if (isVideo && durationCol >= 0) cursor.getLong(durationCol) else 0L,
                    relativePath = if (pathCol >= 0) cursor.getString(pathCol).orEmpty() else "",
                )
            }
        }
        result
    }
}

fun hasAnyMediaPermission(context: Context): Boolean {
    fun granted(permission: String): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            permission,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    return when {
        Build.VERSION.SDK_INT >= 34 ->
            granted(android.Manifest.permission.READ_MEDIA_IMAGES) ||
                granted(android.Manifest.permission.READ_MEDIA_VIDEO) ||
                granted("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
        Build.VERSION.SDK_INT >= 33 ->
            granted(android.Manifest.permission.READ_MEDIA_IMAGES) ||
                granted(android.Manifest.permission.READ_MEDIA_VIDEO)
        else -> granted(android.Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

fun mediaPermissionRequest(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        android.Manifest.permission.READ_MEDIA_IMAGES,
        android.Manifest.permission.READ_MEDIA_VIDEO,
        "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(
        android.Manifest.permission.READ_MEDIA_IMAGES,
        android.Manifest.permission.READ_MEDIA_VIDEO,
    )
    else -> arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

fun formatDate(dateAddedSeconds: Long): String {
    if (dateAddedSeconds <= 0L) return "Sana noma'lum"
    return java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale("uz", "UZ"))
        .format(java.util.Date(dateAddedSeconds * 1000L))
}
