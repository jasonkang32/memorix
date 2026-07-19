package com.mebonsoft.memorix.core.locale

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.localeDataStore by preferencesDataStore(name = "memorix_locale")

@Singleton
class LocaleRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val language: Flow<AppLanguage> = context.localeDataStore.data.map { preferences ->
        AppLanguage.fromCode(preferences[Keys.LanguageCode])
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.localeDataStore.edit { preferences ->
            preferences[Keys.LanguageCode] = language.code
        }
    }

    private object Keys {
        val LanguageCode = stringPreferencesKey("language_code")
    }
}
