package com.kambesembi.famobile.ui.components

import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Submissions per row in portrait, from the settings (1–5). The app's root provides it. */
val LocalGridColumns = staticCompositionLocalOf { 2 }

/** The columns for the screen as it is: the setting on a phone held upright, as many of that width on wider screens. */
@Composable
fun gridColumns(): Int {
    val chosen = LocalGridColumns.current.coerceIn(1, 5)
    val width = LocalConfiguration.current.screenWidthDp
    return (chosen * width.toFloat() / PHONE_WIDTH_DP).roundToInt().coerceAtLeast(chosen)
}

/** About a phone's width in portrait (411 dp on many). */
private const val PHONE_WIDTH_DP = 411f

/** Every submission grid uses this, so the setting applies everywhere. */
@Composable
fun submissionGridCells(): StaggeredGridCells = StaggeredGridCells.Fixed(gridColumns())

/** The FA thumbnail size closest above a column's width in pixels (the app asks for 300 to 800). */
@Composable
fun gridThumbSize(): Int {
    val columnPx = with(LocalDensity.current) { (LocalConfiguration.current.screenWidthDp.dp / gridColumns()).toPx() }
    return listOf(300, 400, 600, 800).firstOrNull { it >= columnPx } ?: 800
}
