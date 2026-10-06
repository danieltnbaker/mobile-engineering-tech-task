package com.userhub.domain.time

/**
 * Formats an instant as a relative, human-readable label (e.g. "5 minutes ago").
 *
 * This is a single pure implementation in commonMain: relative times are computed in shared code
 * rather than per platform, so Android and iOS always produce identical output for the same input
 * (MOB-247, Scope 1).
 *
 * @param epochMillis the instant to describe, in epoch milliseconds
 * @param nowMillis the current instant, in epoch milliseconds
 */
fun formatRelativeTime(epochMillis: Long, nowMillis: Long): String {
    val minutes = (nowMillis - epochMillis) / 60_000
    return when {
        minutes < 1 -> "Just now"
        minutes == 1L -> "1 minute ago"
        minutes < 60 -> "$minutes minutes ago"
        minutes < 120 -> "1 hour ago"
        minutes < 1_440 -> "${minutes / 60} hours ago"
        minutes < 2_880 -> "1 day ago"
        else -> "${minutes / 1_440} days ago"
    }
}
