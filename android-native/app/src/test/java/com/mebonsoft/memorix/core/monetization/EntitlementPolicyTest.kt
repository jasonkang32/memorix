package com.mebonsoft.memorix.core.monetization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementPolicyTest {

    @Test
    fun freeTierAllowsRegistrationWithoutLocalCountLimit() {
        val decision = EntitlementPolicy.canRegisterNewItem(
            entitlement = ProEntitlement.Free,
            currentRegistrationCount = 100_000,
        )

        assertEquals(FeatureGateDecision.Allowed, decision)
    }

    @Test
    fun proTierAlsoAllowsRegistrationWithoutLocalCountLimit() {
        val decision = EntitlementPolicy.canRegisterNewItem(
            entitlement = ProEntitlement.ProLifetime,
            currentRegistrationCount = 100_000,
        )

        assertEquals(FeatureGateDecision.Allowed, decision)
    }

    @Test
    fun freeTierKeepsBasicLocalFeaturesOpen() {
        val freeFeatures = listOf(
            ProFeature.BasicSharing,
            ProFeature.BasicHiddenItems,
            ProFeature.DocumentImport,
        )

        freeFeatures.forEach { feature ->
            val decision = EntitlementPolicy.canUseFeature(ProEntitlement.Free, feature)
            assertEquals("$feature should stay available to Free users", FeatureGateDecision.Allowed, decision)
        }
    }

    @Test
    fun proOnlyFeaturesRequireUpgradeForFreeUsers() {
        val proOnly = listOf(
            ProFeature.PrivateVaultProtection,
            ProFeature.BackupRestore,
            ProFeature.OcrSearch,
            ProFeature.AdvancedFilters,
            ProFeature.TagManagement,
            ProFeature.PdfExport,
            ProFeature.BatchShare,
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
