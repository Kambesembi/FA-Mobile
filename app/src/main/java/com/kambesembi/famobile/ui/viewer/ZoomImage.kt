package com.kambesembi.famobile.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.kambesembi.famobile.R

/** Full-screen image: pinch to zoom, drag to pan, double-tap to zoom in at that spot or back out. */
@Composable
fun ZoomImage(url: String, previewUrl: String?, description: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        var size by remember { mutableStateOf(IntSize.Zero) }
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }

        fun clamp(value: Offset, s: Float): Offset {
            val maxX = size.width * (s - 1f) / 2f
            val maxY = size.height * (s - 1f) / 2f
            return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged {
                    // Turned or resized: the picture fits the new screen again.
                    if (size != IntSize.Zero && it != size) {
                        scale = 1f
                        offset = Offset.Zero
                    }
                    size = it
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { tap ->
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            val target = 2.5f
                            val center = Offset(size.width / 2f, size.height / 2f)
                            // Keep the tapped point where it is: center + (tap - center) * s + offset = tap.
                            offset = clamp((tap - center) * (1f - target), target)
                            scale = target
                        }
                    })
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val next = (scale * zoom).coerceIn(1f, 6f)
                        scale = next
                        offset = if (next <= 1f) Offset.Zero else clamp(offset + pan, next)
                    }
                },
        ) {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = description,
                contentScale = ContentScale.Fit,
                loading = {
                    if (previewUrl != null) {
                        AsyncImage(previewUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .safeDrawingPadding()
                    .padding(8.dp)
                    .background(Color(0x66000000), CircleShape),
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close), tint = Color.White)
            }
        }
    }
}
