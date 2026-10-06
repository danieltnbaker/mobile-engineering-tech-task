package com.userhub.data.repository

import com.userhub.data.currentTimeMillis
import com.userhub.data.local.UserLocalDataSource
import com.userhub.data.remote.CreateUserRequest
import com.userhub.data.remote.GoRestApi
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserRepositoryImpl(
    private val api: GoRestApi,
    private val localDataSource: UserLocalDataSource
) : UserRepository {

    override suspend fun getUsers(): UsersResult = withContext(Dispatchers.Default) {
        try {
            val users = api.fetchLastPageUsers().reversed()
            // Persist for offline support; the local store assigns/preserves each user's first-seen
            // time, which we then read back as the authoritative "added" time (never synthesised).
            localDataSource.saveUsers(users, currentTimeMillis())
            val firstSeenById = localDataSource.getUsers().associate { it.user.id to it.firstSeenAt }
            val feed = users.map { user ->
                FeedUser(user = user, firstSeenMillis = firstSeenById[user.id] ?: currentTimeMillis())
            }
            UsersResult.Success(feed, lastSyncMillis = null)
        } catch (e: Exception) {
            val cached = localDataSource.getUsers()
            if (cached.isEmpty()) {
                UsersResult.NoInternet
            } else {
                val lastSync = cached.maxOf { it.cachedAt }
                val feed = cached.map { FeedUser(user = it.user, firstSeenMillis = it.firstSeenAt) }
                UsersResult.Success(feed, lastSyncMillis = lastSync)
            }
        }
    }

    override suspend fun createUser(name: String, email: String): HttpResponse =
        withContext(Dispatchers.Default) {
            api.createUser(
                CreateUserRequest(name = name, email = email, gender = "male", status = "active")
            )
        }

    override suspend fun deleteUser(id: Long): HttpResponse =
        withContext(Dispatchers.Default) {
            api.deleteUser(id)
        }
}
