package com.userhub.data.local

import com.userhub.data.db.UserDatabase
import com.userhub.data.remote.UserDto

class SqlDelightUserLocalDataSource(database: UserDatabase) : UserLocalDataSource {

    private val queries = database.userCacheQueries

    override fun saveUsers(users: List<UserDto>, timestamp: Long) {
        queries.transaction {
            // Preserve each user's earliest first_seen_at across refreshes. The dialect (SQLite 3.18)
            // has no UPSERT, so we compute the preserved value in Kotlin: existing users keep their
            // original first-seen time; newly observed users get `timestamp`. This keeps the "added"
            // time a real observation time rather than something synthesised from the list position.
            val existingFirstSeen = queries.selectAll().executeAsList()
                .associate { it.id to it.first_seen_at }
            queries.clearAll()
            users.forEach { user ->
                queries.insertUser(
                    id = user.id,
                    name = user.name,
                    email = user.email,
                    gender = user.gender,
                    status = user.status,
                    cached_at = timestamp,
                    first_seen_at = existingFirstSeen[user.id] ?: timestamp
                )
            }
        }
    }

    override fun getUsers(): List<CachedUser> =
        queries.selectAll().executeAsList().map { row ->
            CachedUser(
                user = UserDto(
                    id = row.id,
                    name = row.name,
                    email = row.email,
                    gender = row.gender,
                    status = row.status
                ),
                cachedAt = row.cached_at,
                firstSeenAt = row.first_seen_at
            )
        }
}
