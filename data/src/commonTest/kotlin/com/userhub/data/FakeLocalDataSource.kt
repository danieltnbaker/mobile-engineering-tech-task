package com.userhub.data

import com.userhub.data.local.CachedUser
import com.userhub.data.local.UserLocalDataSource
import com.userhub.data.remote.UserDto

class FakeLocalDataSource(initial: List<CachedUser> = emptyList()) : UserLocalDataSource {

    private var stored = initial.toMutableList()

    var savedUsers: List<UserDto> = emptyList()
        private set

    override fun saveUsers(users: List<UserDto>, timestamp: Long) {
        savedUsers = users
        val existingFirstSeen = stored.associate { it.user.id to it.firstSeenAt }
        stored = users.map { user ->
            CachedUser(
                user = user,
                cachedAt = timestamp,
                firstSeenAt = existingFirstSeen[user.id] ?: timestamp
            )
        }.toMutableList()
    }

    override fun getUsers(): List<CachedUser> = stored
}
