package com.mebonsoft.memorix.core.monetization

data class MemorixPurchase(
    val productId: String,
    val state: PurchaseState,
    val acknowledged: Boolean,
)

enum class PurchaseState {
    Pending,
    Purchased,
}

object PurchaseEntitlementMapper {
    fun map(purchases: List<MemorixPurchase>): ProEntitlement {
        return if (purchases.any { purchase -> purchase.isConfirmedLifetimeProPurchase() }) {
            ProEntitlement.ProLifetime
        } else {
            ProEntitlement.Free
        }
    }

    private fun MemorixPurchase.isConfirmedLifetimeProPurchase(): Boolean =
        productId == ProLifetimeProductId &&
            state == PurchaseState.Purchased &&
            acknowledged
}
