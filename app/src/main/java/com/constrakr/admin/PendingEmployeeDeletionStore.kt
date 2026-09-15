package com.constrakr.admin

import android.content.Context

/** Queues server soft-deletes when an employee is removed on device while offline. */
object PendingEmployeeDeletionStore {
    private const val PREFS = "constrakr.pending_employee_deletions"
    private const val KEY = "server_ids"

    fun pending(context: Context): List<String> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY, emptySet())?.toList()?.sorted() ?: emptyList()
    }

    fun enqueue(context: Context, serverId: String) {
        val normalized = serverId.trim()
        if (normalized.isEmpty()) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ids = prefs.getStringSet(KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        ids.add(normalized)
        prefs.edit().putStringSet(KEY, ids).apply()
    }

    fun remove(context: Context, serverId: String) {
        val normalized = serverId.trim()
        if (normalized.isEmpty()) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ids = prefs.getStringSet(KEY, emptySet())?.toMutableSet() ?: return
        ids.remove(normalized)
        if (ids.isEmpty()) prefs.edit().remove(KEY).apply()
        else prefs.edit().putStringSet(KEY, ids).apply()
    }

    fun hasPending(context: Context): Boolean = pending(context).isNotEmpty()
}
