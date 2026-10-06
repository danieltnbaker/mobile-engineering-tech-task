package com.userhub.presentation

import com.userhub.data.remote.UserDto
import com.userhub.data.repository.FeedUser
import com.userhub.domain.time.TimeProvider
import com.userhub.domain.time.formatRelativeTime

/**
 * Maps feed users to UI models. The "added" label is derived from the real first-seen time supplied
 * by the repository — never synthesised from the list index (MOB-247 Scope 1).
 */
fun List<FeedUser>.toUiModels(): List<UserUiModel> {
    val now = TimeProvider.nowEpochMillis()
    return map { feedUser ->
        UserUiModel(
            user = feedUser.user,
            createdLabel = formatRelativeTime(feedUser.firstSeenMillis, now)
        )
    }
}

fun UserDto.toNewUiModel(): UserUiModel {
    val now = TimeProvider.nowEpochMillis()
    return UserUiModel(user = this, createdLabel = formatRelativeTime(now, now))
}
