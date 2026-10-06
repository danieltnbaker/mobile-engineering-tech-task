package com.userhub.presentation

import com.userhub.data.remote.UserDto
import com.userhub.domain.usecase.AddUserUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class AddUserViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `submit sends the entered values to the repository`() = runTest {
        val repository = FakeUserRepository()
        val viewModel = AddUserViewModel(AddUserUseCase(repository))
        // Await the callback deterministically instead of sleeping, so the real HttpClient work the
        // fake performs is complete before we assert.
        val done = CompletableDeferred<UserDto>()

        viewModel.submit("Grace Hopper", "grace@example.com") { done.complete(it) }
        done.await()

        assertEquals("Grace Hopper", repository.createdName)
        assertEquals("grace@example.com", repository.createdEmail)
    }

    @Test
    fun `successful creation notifies the caller and clears the error`() = runTest {
        val repository = FakeUserRepository()
        val viewModel = AddUserViewModel(AddUserUseCase(repository))
        val done = CompletableDeferred<UserDto>()

        viewModel.submit("Grace Hopper", "grace@example.com") { done.complete(it) }
        val created = done.await()

        assertEquals("grace@example.com", created.email)
        assertNull(viewModel.state.value.errorMessage)
    }
}
