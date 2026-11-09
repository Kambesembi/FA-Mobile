package com.kambesembi.famobile.ui.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.ImageResult
import coil.request.SuccessResult

/**
 * Demo mode has no network, so every image is drawn here instead: a soft two-colour gradient with a
 * few circles, different for each URL, so screenshots show a believable grid.
 */
class DemoImageInterceptor : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        // Files on the phone (an upload's pictures) are real: show them.
        val data = chain.request.data
        if (data is java.io.File || data.toString().startsWith("/")) return chain.proceed(chain.request)
        val key = data.toString()
        val seed = key.hashCode()
        val avatar = "a.furaffinity.net" in key
        val width = if (avatar) 200 else 600
        val height = if (avatar) 200 else 400 + ((seed ushr 3) and 0xFF)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val hue = ((seed and 0xFFFF) % 360).toFloat()
        val from = Color.HSVToColor(floatArrayOf(hue, 0.45f, 0.75f))
        val to = Color.HSVToColor(floatArrayOf((hue + 50f) % 360f, 0.55f, 0.45f))
        canvas.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), from, to, Shader.TileMode.CLAMP) })
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 255, 255, 255) }
        for (i in 0 until 4) {
            val r = (width / 8f) * (1 + ((seed ushr (i * 4)) and 3))
            canvas.drawCircle(width * (0.2f + 0.2f * i), height * (0.3f + 0.15f * (i % 3)), r, dot)
        }
        return SuccessResult(
            drawable = BitmapDrawable(chain.request.context.resources, bitmap),
            request = chain.request,
            dataSource = DataSource.MEMORY,
        )
    }
}
