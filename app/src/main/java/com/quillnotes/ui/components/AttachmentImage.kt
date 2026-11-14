package com.quillnotes.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.quillnotes.R
import com.quillnotes.data.media.ImageLoader
import com.quillnotes.data.media.NoImageLoader

/** Where attachment pictures come from. The activity provides the real loader; previews show placeholders. */
val LocalImageLoader = staticCompositionLocalOf<ImageLoader> { NoImageLoader }

/**
 * A picture attached to a note. [pending] marks one announced by sync whose file has not arrived
 * yet, so it shows as "downloading" rather than broken.
 */
@Composable
fun AttachmentImage(
    uuid: String,
    maxEdge: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    pending: Boolean = false
) {
    val loader = LocalImageLoader.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, uuid, maxEdge, pending) {
        value = loader.load(uuid, maxEdge)
    }
    val scheme = MaterialTheme.colorScheme
    Box(modifier.background(scheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                if (pending) Icons.Rounded.CloudSync else Icons.Rounded.BrokenImage,
                contentDescription = null,
                tint = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/** Full-screen view of one picture; tap anywhere or the close button to leave. */
@Composable
fun ImageViewerDialog(uuid: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss)
        ) {
            AttachmentImage(
                uuid = uuid,
                maxEdge = 2048,
                contentScale = ContentScale.Fit,
                contentDescription = stringResource(R.string.attachment_image),
                modifier = Modifier.fillMaxSize()
            )
            CircleIconButton(
                icon = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.close),
                onClick = onDismiss,
                containerColor = Color.Black.copy(alpha = 0.5f),
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            )
        }
    }
}
