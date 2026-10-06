package com.userhub.data.local

import com.userhub.data.remote.UserDto

data class CachedUser(
    val user: UserDto,
    val cachedAt: Long,
    val firstSeenAt: Long
)

interface UserLocalDataSource {
    /**
     * Persists the current page. [timestamp] is recorded as the sync time and, for users seen for the
     * first time, as their first-seen time; users already present keep their original first-seen time.
     */
    fun saveUsers(users: List<UserDto>, timestamp: Long)
    fun getUsers(): List<CachedUser>
}
