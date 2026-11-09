package com.kambesembi.famobile.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * When a request hits Cloudflare's check, the UI shows it in a WebView and every waiting request
 * resumes together once it passes.
 */
class ChallengeCoordinator {

    private val _pendingUrl = MutableStateFlow<String?>(null)

    /** The page to show in the challenge WebView, or null when no check is waiting. */
    val pendingUrl: StateFlow<String?> = _pendingUrl.asStateFlow()

    private var waiting: CompletableDeferred<Boolean>? = null

    /** Suspends until the user got through (true) or closed the check (false). */
    suspend fun await(url: String): Boolean {
        val deferred = synchronized(this) {
            waiting ?: CompletableDeferred<Boolean>().also {
                waiting = it
                _pendingUrl.value = url
            }
        }
        return deferred.await()
    }

    fun resolve(passed: Boolean) {
        val deferred = synchronized(this) {
            waiting.also {
                waiting = null
                _pendingUrl.value = null
            }
        }
        deferred?.complete(passed)
    }
}
