package com.astrovm.gripmaxxer.testutil

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.runBlocking

/**
 * The settings DataStore is a process-wide singleton, so tests share it. This gives tests direct access
 * to wipe it or to seed raw values the public API cannot write.
 */
object SettingsStore {
    @Suppress("UNCHECKED_CAST")
    fun of(context: Context): DataStore<Preferences> {
        val owner = Class.forName("com.astrovm.gripmaxxer.datastore.SettingsRepositoryKt")
        val getter = owner.getDeclaredMethod("getGripDataStore", Context::class.java)
        getter.isAccessible = true
        return getter.invoke(null, context.applicationContext) as DataStore<Preferences>
    }

    fun clear(context: Context) = runBlocking {
        of(context).edit { it.clear() }
    }

    fun edit(context: Context, block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) = runBlocking {
        of(context).edit { block(it) }
    }
}
