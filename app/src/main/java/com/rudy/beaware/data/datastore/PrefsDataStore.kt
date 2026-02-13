package com.rudy.beaware.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "beaware_prefs")

class PrefsDataStore(private val dataStore: DataStore<Preferences>) {

    companion object {
        private val KEY_SELECTED_APPS = stringSetPreferencesKey("selected_apps")
        private val KEY_TRACKING_ACTIVE = booleanPreferencesKey("tracking_active")
    }

    fun getSelectedApps(): Flow<Set<String>> {
        return dataStore.data.map { prefs ->
            prefs[KEY_SELECTED_APPS] ?: emptySet()
        }
    }

    suspend fun setSelectedApps(packages: Set<String>) {
        dataStore.edit { prefs ->
            prefs[KEY_SELECTED_APPS] = packages
        }
    }

    fun isTrackingActive(): Flow<Boolean> {
        return dataStore.data.map { prefs ->
            prefs[KEY_TRACKING_ACTIVE] ?: false
        }
    }

    suspend fun setTrackingActive(active: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_TRACKING_ACTIVE] = active
        }
    }
}
