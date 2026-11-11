package com.kambesembi.famobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Set when the app comes back after a while and loads the tab on screen again: that tab's list goes
 * to the top, where the new entries are, instead of staying deep in a list that changes under it.
 */
class ReloadTop {
    var pending by mutableStateOf(false)
}

val LocalReloadTop = compositionLocalOf<ReloadTop?> { null }

/** In a tab's list: [toTop] when a reload after coming back is asked for, and again once it is in. */
@Composable
fun ScrollTopOnReload(refreshing: Boolean, toTop: suspend () -> Unit) {
    val signal = LocalReloadTop.current ?: return
    val scroll by rememberUpdatedState(toTop)
    val busy by rememberUpdatedState(refreshing)
    LaunchedEffect(signal.pending) {
        if (!signal.pending) return@LaunchedEffect
        scroll()
        // The new entries come in above the first one, which the list would hold on to.
        withTimeoutOrNull(2_000) { snapshotFlow { busy }.first { it } }
        withTimeoutOrNull(40_000) { snapshotFlow { busy }.first { !it } }
        scroll()
        signal.pending = false
    }
}
