package com.mebonsoft.memorix.core.monetization

data class ProUpgradeCopy(
    val title: String,
    val body: String,
    val primaryActionLabel: String = "Pro 준비 중",
    val secondaryActionLabel: String = "나중에",
)

object ProUpgradeContent {
    fun forFeature(feature: ProFeature): ProUpgradeCopy = when (feature) {
        ProFeature.BackupRestore -> ProUpgradeCopy(
            title = "백업/복구는 Memorix Pro에서",
            body = "무료 버전은 등록 수량 제한 없이 내 폰에 기록을 보관할 수 있습니다. Pro에서는 휴대폰 변경, 재설치, 실수로 인한 데이터 손실에 대비해 DB와 사진·영상·문서 파일을 함께 백업하고 복구할 수 있습니다.",
        )
        ProFeature.CloudSync -> ProUpgradeCopy(
            title = "Google Drive 동기화는 Memorix Pro에서",
            body = "Pro에서는 내 Google Drive 앱 전용 공간에 Memorix 백업을 저장하고, 새 폰이나 재설치 후 최신 백업으로 복구할 수 있습니다. 비밀 보관함 복구 키도 백업에 포함되어 숨긴 파일까지 이어집니다.",
        )
        ProFeature.TagManagement -> ProUpgradeCopy(
            title = "태그 관리는 Memorix Pro에서",
            body = "기본 태그 입력과 검색은 무료로 사용할 수 있습니다. Pro에서는 태그 이름 변경, 삭제, 정리, 자주 쓰는 태그 관리로 기록이 많아져도 깔끔하게 유지할 수 있습니다.",
        )
        ProFeature.OcrSearch -> ProUpgradeCopy(
            title = "OCR 검색은 Memorix Pro에서",
            body = "기본 메모·태그 검색은 무료입니다. Pro에서는 사진 속 글자를 인식하고 저장해 문서 사진, 영수증, 회의자료까지 검색할 수 있습니다.",
        )
        ProFeature.PrivateVaultProtection -> ProUpgradeCopy(
            title = "프라이빗 보관함은 Memorix Pro에서",
            body = "기본 숨김은 무료로 사용할 수 있습니다. Pro에서는 숨긴 보관함 접근을 PIN/생체인증으로 한 번 더 보호하고 자동 재잠금 같은 고급 보호 기능을 제공합니다.",
        )
        ProFeature.AdvancedFilters -> ProUpgradeCopy(
            title = "고급 필터는 Memorix Pro에서",
            body = "기본 검색은 무료입니다. Pro에서는 날짜 범위, 태그 조합, 미디어 타입, OCR 텍스트를 함께 활용해 오래된 기록도 빠르게 찾을 수 있습니다.",
        )
        ProFeature.PdfExport -> ProUpgradeCopy(
            title = "PDF 내보내기는 Memorix Pro에서",
            body = "사진과 메모는 무료로 보관하고 공유할 수 있습니다. Pro에서는 여러 장의 사진과 기록 정보를 하나의 PDF로 묶어 업무자료나 개인 기록으로 깔끔하게 내보낼 수 있습니다.",
        )
        ProFeature.BatchShare -> ProUpgradeCopy(
            title = "묶음 공유는 Memorix Pro에서",
            body = "현재 사진 1장 공유는 무료입니다. Pro에서는 여러 장 묶음 공유, 기록 전체 공유, ZIP/PDF 내보내기처럼 정리된 공유 기능을 사용할 수 있습니다.",
        )
        ProFeature.BasicSharing,
        ProFeature.BasicHiddenItems,
        ProFeature.DocumentImport -> ProUpgradeCopy(
            title = "무료로 사용할 수 있는 기능입니다",
            body = "이 기능은 Memorix Free에서도 사용할 수 있습니다.",
            primaryActionLabel = "확인",
        )
    }
}
