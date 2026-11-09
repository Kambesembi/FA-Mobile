package com.kambesembi.famobile.ui.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.imageLoader
import coil.memory.MemoryCache
import coil.request.ImageRequest

private val thumbSize = Regex("""@\d+-""")

/** The sizes FA serves thumbnails in, largest first. */
private val thumbSizes = listOf(800, 600, 400, 300, 250, 200, 150)

/**
 * A thumbnail of this submission that is already in memory (a grid showed it), so the viewer has
 * something to draw at once; the largest one there, or null.
 */
@Composable
internal fun rememberCachedThumbnail(previewUrl: String?): String? {
    val loader = LocalContext.current.imageLoader
    return remember(previewUrl) {
        val url = previewUrl?.takeIf { thumbSize.containsMatchIn(it) } ?: return@remember null
        thumbSizes.asSequence()
            .map { thumbSize.replaceFirst(url, "@$it-") }
            .firstOrNull { loader.memoryCache?.get(MemoryCache.Key(it)) != null }
    }
}

/**
 * Like FA's own page: whatever thumbnail is at hand right away, FA's 600 px preview over it, and the
 * full image drawn over that the moment it is decoded. Nothing fades, so the picture only gets sharper,
 * and if the full image is slow or fails the preview simply stays.
 *
 * [sized]: the box already has the image's shape (from FA's "Resolution"), so the layers fill it and
 * the preview can go once the full image is in. Otherwise each layer takes its own height.
 */
@Composable
internal fun ProgressiveImage(
    fullUrl: String,
    previewUrl: String?,
    contentDescription: String?,
    sized: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val thumb = rememberCachedThumbnail(previewUrl)
    var fullShown by remember(fullUrl) { mutableStateOf(false) }
    val layer = if (sized) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
    val hasPreview = previewUrl != null && previewUrl != fullUrl
    Box(modifier) {
        if (hasPreview && !(sized && fullShown)) {
            AsyncImage(
                model = remember(previewUrl, thumb) {
                    ImageRequest.Builder(context).data(previewUrl).placeholderMemoryCacheKey(thumb).crossfade(false).build()
                },
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = layer,
            )
        }
        AsyncImage(
            model = remember(fullUrl, thumb) {
                ImageRequest.Builder(context)
                    .data(fullUrl)
                    // Without a preview of its own (a story's cover), the cached thumbnail stands in.
                    .placeholderMemoryCacheKey(if (hasPreview) null else thumb)
                    .crossfade(false)
                    .build()
            },
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            onSuccess = { fullShown = true },
            modifier = layer,
        )
    }
}
