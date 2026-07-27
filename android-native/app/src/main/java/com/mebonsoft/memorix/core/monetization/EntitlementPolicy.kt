package com.mebonsoft.memorix.core.monetization

const val ProLifetimeProductId = "memorix_pro_lifetime"

enum class ProEntitlement {
    Free,
    ProLifetime,
}

enum class ProFeature(
    val displayName: String,
    val requiresPro: Boolean,
) {
    BasicSharing("기본 사진 공유", false),
    BasicHiddenItems("기본 숨김", false),
    DocumentImport("문서/PDF 등록", false),
    PrivateVaultProtection("프라이빗 보관함 보호", true),
    BackupRestore("백업/복구", true),
    OcrSearch("OCR 검색", true),
    AdvancedFilters("고급 필터", true),
    TagManagement("태그 관리", true),
    PdfExport("PDF 내보내기", true),
    BatchShare("묶음 공유", true),
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
    ): FeatureGateDecision = FeatureGateDecision.Allowed

    fun canUseFeature(
        entitlement: ProEntitlement,
        feature: ProFeature,
    ): FeatureGateDecision {
        if (!feature.requiresPro || entitlement == ProEntitlement.ProLifetime) {
            return FeatureGateDecision.Allowed
        }
        return FeatureGateDecision.UpgradeRequired(
            feature = feature,
            reason = "${feature.displayName} 기능은 Memorix Pro에서 사용할 수 있습니다.",
        )
    }

    fun canReadExistingData(
        entitlement: ProEntitlement,
        currentRegistrationCount: Int,
    ): Boolean = true

    fun shouldDeleteDataBecauseOfEntitlementChange(entitlement: ProEntitlement): Boolean = false
}
