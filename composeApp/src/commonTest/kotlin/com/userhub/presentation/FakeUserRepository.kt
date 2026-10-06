package com.userhub.presentation

import com.userhub.data.remote.UserDto
import com.userhub.data.repository.UserRepository
import com.userhub.data.repository.UsersResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json

class FakeUserRepository(
    private val users: List<UserDto> = emptyList(),
    private val createStatus: HttpStatusCode = HttpStatusCode.Created
) : UserRepository {

    var getUsersCalls = 0
        private set
    var createdName: String? = null
        private set
    var createdEmail: String? = null
        private set
    val deletedIds = mutableListOf<Long>()

    override suspend fun getUsers(): UsersResult {
        getUsersCalls++
        return UsersResult.Success(users, lastSyncMillis = null)
    }

    override suspend fun createUser(name: String, email: String): HttpResponse {
        createdName = name
        createdEmail = email
        return respondWith(
            createStatus,
            """{"id":99,"name":"$name","email":"$email","gender":"male","status":"active"}"""
        )
    }

    override suspend fun deleteUser(id: Long): HttpResponse {
        deletedIds.add(id)
        return respondWith(HttpStatusCode.NoContent, "")
    }

    private suspend fun respondWith(status: HttpStatusCode, body: String): HttpResponse {
        val engine = MockEngine {
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json() }
        }
        return client.get("https://test.local/")
    }
}
