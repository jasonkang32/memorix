package com.mebonsoft.memorix.core.monetization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementPolicyTest {

    @Test
    fun freeTierAllowsRegistrationWhenCurrentCountIsBelowLimit() {
        val decision = EntitlementPolicy.canRegisterNewItem(
            entitlement = ProEntitlement.Free,
            currentRegistrationCount = 299,
        )

        assertEquals(FeatureGateDecision.Allowed, decision)
    }

    @Test
    fun freeTierBlocksRegistrationWhenLimitHasBeenReached() {
        val decision = EntitlementPolicy.canRegisterNewItem(
            entitlement = ProEntitlement.Free,
            currentRegistrationCount = 300,
        )

        assertEquals(
            FeatureGateDecision.UpgradeRequired(
                feature = ProFeature.UnlimitedItems,
                reason = "무료 버전은 최대 300개 등록까지 사용할 수 있습니다.",
            ),
            decision,
        )
    }

    @Test
    fun proTierAllowsRegistrationBeyondFreeLimit() {
        val decision = EntitlementPolicy.canRegisterNewItem(
            entitlement = ProEntitlement.ProLifetime,
            currentRegistrationCount = 10_000,
        )

        assertEquals(FeatureGateDecision.Allowed, decision)
    }

    @Test
    fun proOnlyFeaturesRequireUpgradeForFreeUsers() {
        val proOnly = listOf(
            ProFeature.DocumentImport,
            ProFeature.OcrSearch,
            ProFeature.HiddenVault,
            ProFeature.BackupRestore,
            ProFeature.TagManagement,
            ProFeature.AdvancedFilters,
        )

        proOnly.forEach { feature ->
            val decision = EntitlementPolicy.canUseFeature(ProEntitlement.Free, feature)
            assertTrue("$feature should require upgrade", decision is FeatureGateDecision.UpgradeRequired)
        }
    }

    @Test
    fun proOnlyFeaturesAreAllowedForLifetimePurchasers() {
        ProFeature.entries.forEach { feature ->
            assertEquals(
                "$feature should be allowed for Pro users",
                FeatureGateDecision.Allowed,
                EntitlementPolicy.canUseFeature(ProEntitlement.ProLifetime, feature),
            )
        }
    }

    @Test
    fun existingDataShouldRemainReadableWhenFreeUserIsOverLimit() {
        assertTrue(EntitlementPolicy.canReadExistingData(ProEntitlement.Free, currentRegistrationCount = 999))
        assertFalse(EntitlementPolicy.shouldDeleteDataBecauseOfEntitlementChange(ProEntitlement.Free))
    }
}
