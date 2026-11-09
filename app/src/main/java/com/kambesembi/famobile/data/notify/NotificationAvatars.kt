package com.kambesembi.famobile.data.notify

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.transform.CircleCropTransformation
import com.kambesembi.famobile.data.FaUrls

/** The avatar shown on a notification about one person: round, small, loaded the way the app loads images. */
object NotificationAvatars {

    /** The one user every line of [news] is about, or null when there are several (or none). */
    fun userOf(news: News): String? = news.lines.mapNotNull { it.user }.distinct().singleOrNull()
        ?.takeIf { news.lines.all { line -> line.user != null } }

    /** Avatars for the notifications about a single user, by user name; any that fail to load are left out. */
    suspend fun load(context: Context, images: ImageLoader, news: List<News>): Map<String, Bitmap> =
        news.mapNotNull(::userOf).distinct().mapNotNull { name ->
            val request = ImageRequest.Builder(context)
                .data(FaUrls.avatar(name))
                .size(SIZE_PX)
                .allowHardware(false)
                .transformations(CircleCropTransformation())
                .build()
            val result = images.execute(request) as? SuccessResult ?: return@mapNotNull null
            name to result.drawable.toBitmap(SIZE_PX, SIZE_PX)
        }.toMap()

    private const val SIZE_PX = 192
}
