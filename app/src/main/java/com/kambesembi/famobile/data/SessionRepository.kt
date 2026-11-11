package com.kambesembi.famobile.data

import com.kambesembi.famobile.data.net.ChallengeCancelled
import com.kambesembi.famobile.data.net.SessionEnded
import com.kambesembi.famobile.data.parse.Counts
import com.kambesembi.famobile.data.parse.LoggedInUser
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface SessionState {
    /** Nothing loaded yet. */
    data object Starting : SessionState

    data object LoggedOut : SessionState

    /** The first load failed, so there is nothing to show. */
    data class Unreachable(val error: SessionError) : SessionState

    data class LoggedIn(
        val user: LoggedInUser,
        val counts: Counts,
        val refreshing: Boolean,
        /** The latest refresh failed; the counts are from before. */
        val error: SessionError?,
    ) : SessionState
}

sealed interface SessionError {
    data object ChallengeCancelled : SessionError
    data class Network(val message: String) : SessionError
}

/** Whether we're logged in, as told by the header of whichever page loaded last. */
class SessionRepository(private val fa: FaClient, scope: CoroutineScope) {

    private sealed interface Status {
        data object Idle : Status
        data object Refreshing : Status
        data object LoggedOut : Status
        /** Logging out: FA is being told, the cookies are still there. */
        data object Leaving : Status
        data class Failed(val error: SessionError) : Status
    }

    private val status = MutableStateFlow<Status>(Status.Refreshing)

    val state: StateFlow<SessionState> = combine(status, fa.header) { s, header ->
        when {
            s == Status.LoggedOut -> SessionState.LoggedOut
            s == Status.Leaving -> SessionState.Starting
            header == null -> if (s is Status.Failed) SessionState.Unreachable(s.error) else SessionState.Starting
            header.user == null -> SessionState.LoggedOut
            else -> SessionState.LoggedIn(
                user = header.user,
                counts = header.counts,
                refreshing = s == Status.Refreshing,
                error = (s as? Status.Failed)?.error,
            )
        }
    }.stateIn(scope, SharingStarted.Eagerly, SessionState.Starting)

    /** Reloads the smallest page with the header: checks the login and updates every count. */
    suspend fun refresh() {
        status.value = Status.Refreshing
        status.value = try {
            fa.headerPage(FaUrls.COUNTS_PAGE)
            Status.Idle
        } catch (e: SessionEnded) {
            return // logged out meanwhile: logout() says what the state is
        } catch (e: ChallengeCancelled) {
            Status.Failed(SessionError.ChallengeCancelled)
        } catch (e: IOException) {
            Status.Failed(SessionError.Network(e.message ?: e.javaClass.simpleName))
        }
    }

    suspend fun logout() {
        status.value = Status.Leaving
        fa.endSession()
        status.value = Status.LoggedOut
    }
}
