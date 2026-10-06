package com.userhub.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.userhub.data.remote.UserDto
import com.userhub.data.repository.UsersResult
import com.userhub.domain.time.TimeProvider
import com.userhub.domain.time.formatRelativeTime
import com.userhub.domain.usecase.DeleteUserUseCase
import com.userhub.domain.usecase.GetUsersUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Default undo window; kept in step with the "Undo" snackbar duration shown by the UI. */
private const val DEFAULT_UNDO_WINDOW_MILLIS = 4_000L

class UserFeedViewModel(
    private val getUsers: GetUsersUseCase,
    private val deleteUser: DeleteUserUseCase,
    private val undoWindowMillis: Long = DEFAULT_UNDO_WINDOW_MILLIS
) : ViewModel() {

    private val _state = MutableStateFlow<UserFeedUiState>(UserFeedUiState.Loading)
    val state: StateFlow<UserFeedUiState> = _state.asStateFlow()

    private var pendingUndo: Pair<Int, UserUiModel>? = null
    private var pendingDeleteJob: Job? = null

    init {
        load()
    }

    fun load() {
        _state.value = UserFeedUiState.Loading
        viewModelScope.launch {
            when (val result = getUsers()) {
                is UsersResult.Success -> {
                    val lastSyncLabel = result.lastSyncMillis?.let {
                        "Offline — last updated ${formatRelativeTime(it, TimeProvider.nowEpochMillis())}"
                    }
                    _state.value = UserFeedUiState.Content(result.users.toUiModels(), lastSyncLabel)
                }

                UsersResult.NoInternet -> _state.value = UserFeedUiState.NoInternet
            }
        }
    }

    fun onUserCreated(user: UserDto) {
        val content = _state.value as? UserFeedUiState.Content ?: return
        _state.value = content.copy(users = listOf(user.toNewUiModel()) + content.users)
    }

    /**
     * Removes the row from the feed immediately but defers the server delete until the undo window
     * has elapsed (MOB-247 Scope 3: "the deletion is not final until the undo window has elapsed").
     * The network call runs in viewModelScope so it is cancellable and tied to the screen lifecycle.
     */
    fun onDeleteConfirmed(user: UserUiModel) {
        val content = _state.value as? UserFeedUiState.Content ?: return
        val index = content.users.indexOf(user)
        if (index < 0) return
        pendingUndo = index to user
        _state.value = content.copy(users = content.users - user)
        pendingDeleteJob = viewModelScope.launch {
            delay(undoWindowMillis)
            runCatching { deleteUser(user.id) }
                .onSuccess { pendingUndo = null }
                .onFailure {
                    // Delete failed after the window: restore the row so the UI reflects reality.
                    restorePending()
                }
        }
    }

    /**
     * Taking Undo cancels the pending server delete before it fires, so no API call is ever made and
     * the record is left intact, then restores the row to its original position.
     */
    fun onUndo() {
        pendingDeleteJob?.cancel()
        pendingDeleteJob = null
        restorePending()
    }

    private fun restorePending() {
        val (index, user) = pendingUndo ?: return
        val content = _state.value as? UserFeedUiState.Content ?: return
        val users = content.users.toMutableList()
        if (users.none { it.id == user.id }) {
            users.add(index.coerceAtMost(users.size), user)
            _state.value = content.copy(users = users)
        }
        pendingUndo = null
    }
}
