package com.mebonsoft.memorix.core.monetization

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.proEntitlementDataStore by preferencesDataStore(name = "memorix_pro_entitlement")

data class StoredProEntitlement(
    val entitlement: ProEntitlement = ProEntitlement.Free,
    val purchaseToken: String? = null,
)

interface ProEntitlementRepository {
    val entitlement: Flow<ProEntitlement>
    suspend fun setEntitlement(entitlement: ProEntitlement, purchaseToken: String? = null)
}

@Singleton
class DataStoreProEntitlementRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : ProEntitlementRepository {
    private object Keys {
        val IsPro = booleanPreferencesKey("is_pro")
        val PurchaseToken = stringPreferencesKey("purchase_token")
    }

    val stored: Flow<StoredProEntitlement> = context.proEntitlementDataStore.data.map { preferences ->
        StoredProEntitlement(
            entitlement = if (preferences[Keys.IsPro] == true) ProEntitlement.ProLifetime else ProEntitlement.Free,
            purchaseToken = preferences[Keys.PurchaseToken],
        )
    }

    override val entitlement: Flow<ProEntitlement> = stored.map { it.entitlement }

    override suspend fun setEntitlement(entitlement: ProEntitlement, purchaseToken: String?) {
        context.proEntitlementDataStore.edit { preferences ->
            preferences[Keys.IsPro] = entitlement == ProEntitlement.ProLifetime
            if (purchaseToken.isNullOrBlank()) {
                preferences.remove(Keys.PurchaseToken)
            } else {
                preferences[Keys.PurchaseToken] = purchaseToken
            }
        }
    }
}
