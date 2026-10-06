package com.userhub.data

import com.userhub.data.local.CachedUser
import com.userhub.data.remote.ApiConfig
import com.userhub.data.remote.GoRestApi
import com.userhub.data.remote.UserDto
import com.userhub.data.remote.createHttpClient
import com.userhub.data.repository.UserRepositoryImpl
import com.userhub.data.repository.UsersResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UserRepositoryImplTest {

    private val usersJson = """
        [
          {"id":1,"name":"Ada Lovelace","email":"ada@example.com","gender":"female","status":"active"},
          {"id":2,"name":"Alan Turing","email":"alan@example.com","gender":"male","status":"active"}
        ]
    """.trimIndent()

    private fun successEngine() = MockEngine {
        respond(
            content = usersJson,
            status = HttpStatusCode.OK,
            headers = headersOf(
                HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                "x-pagination-pages" to listOf("3")
            )
        )
    }

    @Test
    fun `returns users from the last page`() = runTest {
        val repository = UserRepositoryImpl(
            GoRestApi(createHttpClient(successEngine(), authToken = TEST_TOKEN)),
            FakeLocalDataSource()
        )

        val result = repository.getUsers()

        assertIs<UsersResult.Success>(result)
        assertEquals(2, result.users.size)
    }

    @Test
    fun `caches users after a successful fetch`() = runTest {
        val local = FakeLocalDataSource()
        val repository = UserRepositoryImpl(GoRestApi(createHttpClient(successEngine(), authToken = TEST_TOKEN)), local)

        repository.getUsers()

        assertEquals(2, local.savedUsers.size)
        assertTrue(local.savedUsers.any { it.email == "ada@example.com" })
    }

    @Test
    fun `falls back to cached users when the network fails`() = runTest {
        val cached = listOf(
            CachedUser(
                UserDto(7, "Cached User", "cached@example.com", "male", "active"),
                cachedAt = 1_700_000_000_000L,
                firstSeenAt = 1_699_000_000_000L
            )
        )
        val engine = MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }
        val repository = UserRepositoryImpl(
            GoRestApi(createHttpClient(engine, authToken = TEST_TOKEN)),
            FakeLocalDataSource(cached)
        )

        val result = repository.getUsers()

        assertIs<UsersResult.Success>(result)
        assertEquals("cached@example.com", result.users.single().user.email)
        assertEquals(1_700_000_000_000L, result.lastSyncMillis)
    }

    @Test
    fun `added time comes from the persisted first-seen value, not the list index`() = runTest {
        // Pre-seed a real first-seen time for user 2 that differs from any index-based guess.
        val realFirstSeen = 1_650_000_000_000L
        val local = FakeLocalDataSource(
            listOf(
                CachedUser(
                    UserDto(2, "Alan Turing", "alan@example.com", "male", "active"),
                    cachedAt = realFirstSeen,
                    firstSeenAt = realFirstSeen
                )
            )
        )
        val repository = UserRepositoryImpl(
            GoRestApi(createHttpClient(successEngine(), authToken = TEST_TOKEN)),
            local
        )

        val result = repository.getUsers()

        assertIs<UsersResult.Success>(result)
        // User 2 keeps its real first-seen time; it is not re-derived from its position in the list.
        val alan = result.users.single { it.user.id == 2L }
        assertEquals(realFirstSeen, alan.firstSeenMillis)
        // A newly observed user (id 1) gets a real "now-ish" first-seen, not an index offset.
        val ada = result.users.single { it.user.id == 1L }
        assertTrue(ada.firstSeenMillis >= realFirstSeen)
    }

    @Test
    fun `uses the public GoRest base url`() {
        assertTrue(ApiConfig.BASE_URL.startsWith("https://"))
    }

    private companion object {
        const val TEST_TOKEN = "test-token"
    }
}
