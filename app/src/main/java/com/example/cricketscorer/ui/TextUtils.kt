package com.example.cricketscorer.ui

/**
 * Forces the first letter of manually-typed text to uppercase (req #2), regardless of what
 * the on-screen keyboard's auto-capitalization setting does. Only the first character is
 * touched — the rest of what the user typed is left exactly as they typed it.
 */
internal fun capitalizeFirstLetter(input: String): String {
    if (input.isEmpty()) return input
    val first = input[0]
    if (first.isLowerCase()) {
        return first.uppercaseChar() + input.substring(1)
    }
    return input
}

/**
 * req #2: human-friendly "Last backed up ..." status text for the Backup & Resync dialog,
 * instead of a raw timestamp — e.g. "just now" / "5 min ago" / "3 hr ago" / "2 days ago", and
 * a plain date once it's more than a week old.
 */
internal fun formatRelativeTime(timestampMillis: Long): String {
    val diffMs = (System.currentTimeMillis() - timestampMillis).coerceAtLeast(0)
    val minutes = diffMs / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> "$hours hr ago"
        days < 7 -> "$days day${if (days == 1L) "" else "s"} ago"
        else -> java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
            .format(java.util.Date(timestampMillis))
    }
}
