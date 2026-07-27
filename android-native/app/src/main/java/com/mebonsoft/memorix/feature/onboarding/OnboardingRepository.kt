package com.mebonsoft.memorix.feature.onboarding

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.onboardingDataStore by preferencesDataStore(name = "memorix_onboarding")

@Singleton
class OnboardingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val Done = booleanPreferencesKey("onboarding_done")
    }

    val onboardingDone: Flow<Boolean> = context.onboardingDataStore.data.map { preferences ->
        preferences[Keys.Done] == true
    }

    suspend fun markDone() {
        context.onboardingDataStore.edit { preferences ->
            preferences[Keys.Done] = true
        }
    }
}
