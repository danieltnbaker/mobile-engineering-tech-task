package com.userhub.presentation

import com.userhub.data.remote.UserDto
import com.userhub.domain.usecase.DeleteUserUseCase
import com.userhub.domain.usecase.GetUsersUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UserFeedViewModelTest {

    private val users = listOf(
        UserDto(1, "Ada Lovelace", "ada@example.com", "female", "active"),
        UserDto(2, "Alan Turing", "alan@example.com", "male", "active"),
        UserDto(3, "Grace Hopper", "grace@example.com", "female", "active")
    )

    // A real scheduler so delay()-based undo timing is deterministic and controllable.
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(repository: FakeUserRepository, undoWindowMillis: Long = UNDO_WINDOW) =
        UserFeedViewModel(
            GetUsersUseCase(repository),
            DeleteUserUseCase(repository),
            undoWindowMillis = undoWindowMillis
        )

    @Test
    fun `loads users from the repository on init`() = runTest {
        val repository = FakeUserRepository(users)

        val viewModel = viewModel(repository)
        advanceUntilIdle()

        assertEquals(1, repository.getUsersCalls)
        assertIs<UserFeedUiState.Content>(viewModel.state.value)
    }

    @Test
    fun `content state exposes every user returned by the repository`() = runTest {
        val viewModel = viewModel(FakeUserRepository(users))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertIs<UserFeedUiState.Content>(state)
        assertEquals(3, state.users.size)
        assertTrue(state.users.any { it.email == "grace@example.com" })
    }

    @Test
    fun `delete removes the user from the list immediately`() = runTest {
        val viewModel = viewModel(FakeUserRepository(users))
        advanceUntilIdle()
        val target = (viewModel.state.value as UserFeedUiState.Content).users.first()

        viewModel.onDeleteConfirmed(target)

        val state = viewModel.state.value
        assertIs<UserFeedUiState.Content>(state)
        assertEquals(2, state.users.size)
        assertTrue(state.users.none { it.id == target.id })
    }

    @Test
    fun `delete is not sent to the server until the undo window elapses`() = runTest {
        val repository = FakeUserRepository(users)
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        val target = (viewModel.state.value as UserFeedUiState.Content).users.first()

        viewModel.onDeleteConfirmed(target)

        // Before the window closes, no API call has been made (Scope 3: not final yet).
        advanceTimeBy(UNDO_WINDOW - 1)
        assertEquals(emptyList(), repository.deletedIds)

        // After the window, exactly one delete is sent for the right user.
        advanceUntilIdle()
        assertEquals(listOf(target.id), repository.deletedIds)
    }

    @Test
    fun `undo cancels the pending delete so no API call is made`() = runTest {
        val repository = FakeUserRepository(users)
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        val target = (viewModel.state.value as UserFeedUiState.Content).users.first()
        viewModel.onDeleteConfirmed(target)

        // Undo within the window.
        advanceTimeBy(UNDO_WINDOW / 2)
        viewModel.onUndo()

        // Let any (incorrectly) scheduled work run; there must be none.
        advanceUntilIdle()
        assertEquals(emptyList(), repository.deletedIds)

        // And the record is restored to the list.
        val state = viewModel.state.value
        assertIs<UserFeedUiState.Content>(state)
        assertEquals(3, state.users.size)
        assertEquals(target.id, state.users.first().id)
    }

    @Test
    fun `onUserCreated puts the new user at the top of the feed`() = runTest {
        val viewModel = viewModel(FakeUserRepository(users))
        advanceUntilIdle()
        val created = UserDto(99, "New Person", "new@example.com", "male", "active")

        viewModel.onUserCreated(created)

        val state = viewModel.state.value
        assertIs<UserFeedUiState.Content>(state)
        assertEquals(4, state.users.size)
        assertEquals(99, state.users.first().id)
    }

    private companion object {
        const val UNDO_WINDOW = 4_000L
    }
}
