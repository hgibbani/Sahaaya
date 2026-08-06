package com.sahaaya.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sahaaya.domain.model.User
import com.sahaaya.domain.usecase.auth.ObserveSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Where the whole app is, at the top level.
 *
 * [Resolving] is a real state, not a loading flag: at cold start Firebase has
 * not yet restored the cached session, and showing the welcome screen for those
 * few hundred milliseconds would flash the sign-in page at a user who is already
 * signed in.
 */
sealed interface SessionState {
    data object Resolving : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val user: User) : SessionState
}

@HiltViewModel
class SessionViewModel @Inject constructor(
    observeSession: ObserveSessionUseCase,
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = observeSession()
        .map { user ->
            if (user == null) SessionState.SignedOut else SessionState.SignedIn(user)
        }
        .stateIn(
            scope = viewModelScope,
            // Eagerly: the session must keep resolving while the splash screen
            // is on display, before anything subscribes.
            started = SharingStarted.Eagerly,
            initialValue = SessionState.Resolving,
        )
}
