package com.example.cricketscorer.data

import android.content.Context
import java.util.UUID

/**
 * Cloud Sync: a single, stable identifier for THIS physical device/install, persisted so it
 * never changes across ViewModel recreations, screen navigation, or app restarts.
 *
 * Every write this device makes to Firestore (see [com.example.cricketscorer.sync.CloudSync])
 * must be tagged with the SAME id every time, because the listener's self-echo filter
 * ("is this update mine, or did it come from the other phone?") only works by comparing the
 * incoming doc's `updatedBy` against this value. Previously this was generated with a fresh
 * `UUID.randomUUID()` in each place a push happened (match creation vs. live scoring), so a
 * device could never recognize its own earlier writes as "self" — causing its own initial
 * snapshot to be replayed back through the listener as if it were a remote update from the
 * other phone, silently reverting whatever had just been scored locally. See MatchSetupViewModel
 * / ScoringViewModel for where this is used.
 */
object CloudDeviceIdStore {
    private const val PREFS_NAME = "cloud_device_id"
    private const val KEY_DEVICE_ID = "device_id"

    fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
        val newId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
        return newId
    }
}
