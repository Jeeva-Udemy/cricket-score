package com.example.cricketscorer.data

import android.content.Context

/**
 * req #1: "it should show the batsman/bowler name in the dropdown just like we have for Squad
 * ... keep a field to enter the name manually as well." The Squad-based dropdown (see
 * ScoringViewModel.existingBowlers / availableIncomingBatsmen) only has names to offer when a
 * saved squad was actually linked to the match — a match started by typing team/player names
 * by hand has no roster at all to suggest from. This is a small local memory of every player
 * name ever typed anywhere in the app (any match, squad-linked or not), so the very next time a
 * name is needed it's already a tap away instead of being retyped from scratch — the same
 * "dropdown + manual entry" convenience Squad gets, without requiring a saved squad.
 *
 * Deliberately a flat, app-wide set (not per-team) — cricket team rosters change match to
 * match and a name like "Ravi" only needs to be remembered once, not re-learned per team.
 */
object RecentPlayersStore {
    private const val PREFS_NAME = "recent_players"
    private const val KEY_NAMES = "names"

    fun getAll(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_NAMES, emptySet()) ?: emptySet()
    }

    /** Adds [names] to the remembered set (blank/duplicate entries are ignored). */
    fun addNames(context: Context, names: Collection<String>) {
        val cleaned = names.map { it.trim() }.filter { it.isNotBlank() }
        if (cleaned.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet(KEY_NAMES, emptySet()) ?: emptySet()
        val merged = existing + cleaned
        if (merged.size != existing.size) {
            // getStringSet's returned set must never be mutated in place (SharedPreferences
            // contract) — always write back a fresh copy.
            prefs.edit().putStringSet(KEY_NAMES, merged).apply()
        }
    }

    fun addName(context: Context, name: String) = addNames(context, listOf(name))
}
