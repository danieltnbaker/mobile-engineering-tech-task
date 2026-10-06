package com.userhub.data.repository

import com.userhub.data.remote.UserDto
import io.ktor.client.statement.HttpResponse

/** A user as shown in the feed, paired with the real time we first observed them. */
data class FeedUser(
    val user: UserDto,
    val firstSeenMillis: Long
)

sealed interface UsersResult {
    data class Success(val users: List<FeedUser>, val lastSyncMillis: Long?) : UsersResult
    data object NoInternet : UsersResult
}

interface UserRepository {
    suspend fun getUsers(): UsersResult
    suspend fun createUser(name: String, email: String): HttpResponse
    suspend fun deleteUser(id: Long): HttpResponse
}
