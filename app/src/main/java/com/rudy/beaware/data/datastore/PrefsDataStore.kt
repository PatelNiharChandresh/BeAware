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
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "beaware_prefs")

class PrefsDataStore(private val dataStore: DataStore<Preferences>) {

    companion object {
        private val KEY_SELECTED_APPS = stringSetPreferencesKey("selected_apps")
        private val KEY_TRACKING_ACTIVE = booleanPreferencesKey("tracking_active")
    }

    fun getSelectedApps(): Flow<Set<String>> {
        Timber.d("getSelectedApps: subscribing to flow")
        return dataStore.data.map { prefs ->
            prefs[KEY_SELECTED_APPS] ?: emptySet()
        }.onEach { apps ->
            Timber.d("getSelectedApps: emitted %d packages: %s", apps.size, apps)
        }
    }

    suspend fun setSelectedApps(packages: Set<String>) {
        Timber.d("setSelectedApps: writing %d packages: %s", packages.size, packages)
        dataStore.edit { prefs ->
            prefs[KEY_SELECTED_APPS] = packages
        }
        Timber.d("setSelectedApps: write complete")
    }

    fun isTrackingActive(): Flow<Boolean> {
        Timber.d("isTrackingActive: subscribing to flow")
        return dataStore.data.map { prefs ->
            prefs[KEY_TRACKING_ACTIVE] ?: false
        }.onEach { active ->
            Timber.d("isTrackingActive: emitted %s", active)
        }
    }

    suspend fun setTrackingActive(active: Boolean) {
        Timber.d("setTrackingActive: writing %s", active)
        dataStore.edit { prefs ->
            prefs[KEY_TRACKING_ACTIVE] = active
        }
        Timber.d("setTrackingActive: write complete")
    }
}
