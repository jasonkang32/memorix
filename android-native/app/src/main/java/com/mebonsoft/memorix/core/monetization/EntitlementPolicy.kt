package com.mebonsoft.memorix.core.monetization

const val FreeRegistrationLimit = 300
const val ProLifetimeProductId = "memorix_pro_lifetime"

enum class ProEntitlement {
    Free,
    ProLifetime,
}

enum class ProFeature(
    val displayName: String,
) {
    UnlimitedItems("항목 무제한"),
    DocumentImport("문서/PDF 등록"),
    OcrSearch("OCR 검색"),
    HiddenVault("숨긴 보관함"),
    BackupRestore("백업/복원"),
    TagManagement("태그 관리"),
    AdvancedFilters("고급 필터"),
}

sealed interface FeatureGateDecision {
    data object Allowed : FeatureGateDecision

    data class UpgradeRequired(
        val feature: ProFeature,
        val reason: String,
    ) : FeatureGateDecision
}

object EntitlementPolicy {
    fun canRegisterNewItem(
        entitlement: ProEntitlement,
        currentRegistrationCount: Int,
    ): FeatureGateDecision {
        if (entitlement == ProEntitlement.ProLifetime) return FeatureGateDecision.Allowed
        return if (currentRegistrationCount < FreeRegistrationLimit) {
            FeatureGateDecision.Allowed
        } else {
            FeatureGateDecision.UpgradeRequired(
                feature = ProFeature.UnlimitedItems,
                reason = "무료 버전은 최대 ${FreeRegistrationLimit}개 등록까지 사용할 수 있습니다.",
            )
        }
    }

    fun canUseFeature(
        entitlement: ProEntitlement,
        feature: ProFeature,
    ): FeatureGateDecision {
        return if (entitlement == ProEntitlement.ProLifetime) {
            FeatureGateDecision.Allowed
        } else {
            FeatureGateDecision.UpgradeRequired(
                feature = feature,
                reason = "${feature.displayName} 기능은 Memorix Pro에서 사용할 수 있습니다.",
            )
        }
    }

    fun canReadExistingData(
        entitlement: ProEntitlement,
        currentRegistrationCount: Int,
    ): Boolean = true

    fun shouldDeleteDataBecauseOfEntitlementChange(entitlement: ProEntitlement): Boolean = false
}
