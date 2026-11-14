package com.quillnotes.data.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.quillnotes.data.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Reads, decrypts and decodes attachment images for the UI. */
interface ImageLoader {
    /** An image no larger than about [maxEdge] px on its long side, or null if it is not available. */
    suspend fun load(uuid: String, maxEdge: Int): ImageBitmap?
}

/** Used by previews and screenshot tests: nothing to show. */
object NoImageLoader : ImageLoader {
    override suspend fun load(uuid: String, maxEdge: Int): ImageBitmap? = null
}

/** The real loader, with a small in-memory cache so scrolling lists do not decrypt on every frame. */
@Singleton
class AttachmentBitmapLoader @Inject constructor(
    private val repository: NoteRepository
) : ImageLoader {
    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    override suspend fun load(uuid: String, maxEdge: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = "$uuid@$maxEdge"
        cache.get(key)?.let { return@withContext it.asImageBitmap() }
        val bytes = repository.readAttachment(uuid) ?: return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = ImageImporter.sampleSizeFor(bounds.outWidth, bounds.outHeight, maxEdge)
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return@withContext null
        cache.put(key, bitmap)
        bitmap.asImageBitmap()
    }

    private companion object {
        const val CACHE_BYTES = 24 * 1024 * 1024
    }
}
