package com.quillnotes.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

class ProcessedImage(val bytes: ByteArray, val mime: String, val width: Int, val height: Int)

/**
 * Turns a picked or captured photo into something worth storing and syncing: upright (EXIF
 * rotation applied), at most [MAX_EDGE] px on its longest side, and compressed to WebP, which
 * keeps transparency (screenshots, diagrams) and is far smaller than the original.
 */
@Singleton
class ImageImporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun process(uri: Uri): ProcessedImage? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return@withContext null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_EDGE)
            }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return@withContext null

            val rotation = resolver.openInputStream(uri)?.use { rotationOf(it) } ?: 0f
            val scale = MAX_EDGE.toFloat() / maxOf(decoded.width, decoded.height)
            val matrix = Matrix().apply {
                if (rotation != 0f) postRotate(rotation)
                if (scale < 1f) postScale(scale, scale)
            }
            val bitmap = if (matrix.isIdentity) decoded
            else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, QUALITY, out)
            ProcessedImage(out.toByteArray(), "image/webp", bitmap.width, bitmap.height)
                .also { if (bitmap !== decoded) decoded.recycle() }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    private fun rotationOf(stream: java.io.InputStream): Float =
        when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

    companion object {
        const val MAX_EDGE = 2048
        private const val QUALITY = 85

        /** Largest power of two that keeps the decoded image at least [target] px on its long side. */
        fun sampleSizeFor(width: Int, height: Int, target: Int): Int {
            var sample = 1
            while (maxOf(width, height) / (sample * 2) >= target) sample *= 2
            return sample
        }
    }
}
