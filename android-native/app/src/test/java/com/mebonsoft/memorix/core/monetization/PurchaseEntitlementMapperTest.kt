package com.mebonsoft.memorix.core.monetization

import org.junit.Assert.assertEquals
import org.junit.Test

class PurchaseEntitlementMapperTest {

    @Test
    fun mapsActiveLifetimePurchaseToProEntitlement() {
        val purchases = listOf(
            MemorixPurchase(
                productId = ProLifetimeProductId,
                state = PurchaseState.Purchased,
                acknowledged = true,
            ),
        )

        assertEquals(ProEntitlement.ProLifetime, PurchaseEntitlementMapper.map(purchases))
    }

    @Test
    fun ignoresPendingPurchaseUntilGooglePlayConfirmsIt() {
        val purchases = listOf(
            MemorixPurchase(
                productId = ProLifetimeProductId,
                state = PurchaseState.Pending,
                acknowledged = false,
            ),
        )

        assertEquals(ProEntitlement.Free, PurchaseEntitlementMapper.map(purchases))
    }

    @Test
    fun ignoresUnknownProducts() {
        val purchases = listOf(
            MemorixPurchase(
                productId = "some_other_product",
                state = PurchaseState.Purchased,
                acknowledged = true,
            ),
        )

        assertEquals(ProEntitlement.Free, PurchaseEntitlementMapper.map(purchases))
    }
}
