package com.quillnotes.data.media

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Document scan to text: on-device OCR (ML Kit's bundled models, so it works offline) for Latin
 * and Devanagari script — English, Hindi and Marathi. Nothing leaves the phone.
 */
@Singleton
class TextScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val latin by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val devanagari by lazy { TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build()) }

    /** The recognised text in reading order, or null if nothing legible was found. */
    suspend fun recognize(uri: Uri): String? {
        val image = try {
            InputImage.fromFilePath(context, uri)
        } catch (e: Exception) {
            return null
        }
        val a = run(latin, image)
        val b = run(devanagari, image)
        // Each model reads "foreign" script as noise; the one that found more real text wins.
        return listOfNotNull(a, b).maxByOrNull { score(it) }?.takeIf { it.isNotBlank() }
    }

    private suspend fun run(client: TextRecognizer, image: InputImage): String? =
        suspendCancellableCoroutine { cont ->
            client.process(image)
                .addOnSuccessListener { cont.resume(it.text.trim().ifEmpty { null }) }
                .addOnFailureListener { cont.resume(null) }
        }

    /** Letters count; stray punctuation and symbols (OCR noise) do not. */
    private fun score(text: String): Int = text.count { it.isLetterOrDigit() }
}
