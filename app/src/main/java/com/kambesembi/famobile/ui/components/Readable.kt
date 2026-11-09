package com.kambesembi.famobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Text and forms stop growing at this width; on a tablet the rest is margin. */
val ReadableWidth = 720.dp

/** Pictures (the viewer, the comic reader) get more room. */
val MediaWidth = 960.dp

/** Side padding: [base] on a phone; on a wide screen enough to keep the content [max] wide, centred. */
@Composable
fun readableSide(base: Dp, max: Dp = ReadableWidth): Dp {
    val width = LocalConfiguration.current.screenWidthDp.dp
    return maxOf(base, (width - max) / 2)
}

/** Wide enough for the tabs in a rail at the side (tablets, unfolded foldables, phones held sideways). */
@Composable
fun isWide(): Boolean = LocalConfiguration.current.screenWidthDp >= 600
