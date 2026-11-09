package com.kambesembi.famobile.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/** Shows the loader full screen; the app's root provides it. */
val LocalPullSurprise = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Pull to refresh, with a little secret: a pull from the very top of the screen to the very bottom
 * shows the loading animation full screen until a tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    val surprise by rememberUpdatedState(LocalPullSurprise.current)
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = state,
        modifier = modifier.pointerInput(Unit) {
            // Only watches the finger (nothing is consumed), so scrolling and refreshing work as always.
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var shown = false
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val travel = change.position.y - down.position.y
                    if (!shown && travel > size.height * SURPRISE_PULL && state.distanceFraction >= 1f) {
                        shown = true
                        surprise()
                    }
                }
            }
        },
        content = content,
    )
}

/** How much of the screen's height the finger has to travel. */
private const val SURPRISE_PULL = 0.7f

/** The full-screen loader of the secret, gone on a tap (or Back). */
@Composable
fun PullSurprise(onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) { FurryLoader() }
}
