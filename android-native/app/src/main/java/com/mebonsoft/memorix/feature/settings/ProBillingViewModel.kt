package com.mebonsoft.memorix.feature.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mebonsoft.memorix.core.monetization.ProBillingRepository
import com.mebonsoft.memorix.core.monetization.ProBillingState
import com.mebonsoft.memorix.core.monetization.ProEntitlement
import com.mebonsoft.memorix.core.monetization.ProEntitlementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ProBillingViewModel @Inject constructor(
    private val billingRepository: ProBillingRepository,
    entitlementRepository: ProEntitlementRepository,
) : ViewModel() {
    val billingState: StateFlow<ProBillingState> = billingRepository.state
    val entitlement: StateFlow<ProEntitlement> = entitlementRepository.entitlement.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProEntitlement.Free,
    )

    init {
        billingRepository.startConnection()
    }

    fun buyPro(activity: Activity) = billingRepository.launchPurchase(activity)
    fun restorePurchases() = billingRepository.restorePurchases()
    fun consumeMessage() = billingRepository.consumeMessage()
}
