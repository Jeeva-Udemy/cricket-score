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
