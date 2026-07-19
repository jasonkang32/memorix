# Memorix Free/Pro Monetization Design — 2026-07-20

## Decision

Initial Google Play monetization model:

- No ads
- Free tier for trial/real use
- One-time lifetime Pro purchase
- Product ID: `memorix_pro_lifetime`
- Initial KRW price target: `14,900원`

Subscription is deferred until Memorix adds ongoing server-cost features such as cloud backup, multi-device sync, or AI automation.

## Product Positioning

Memorix should not be sold as a gallery replacement. It should be positioned as:

> Work와 Personal 기록을 나눠 보관하고, 태그·메모·날짜·OCR 맥락으로 다시 찾는 광고 없는 로컬 기록함.

## Free Tier

Free must be useful enough to validate value but limited enough to create an upgrade path.

Free includes:

- Work / Personal spaces
- Photo/video import
- Basic memo and tags
- Basic search
- Basic viewing/detail/editing
- Read access to all existing data, even if over the free limit

Free limits:

- Maximum registration groups: `300`
- Count basis: registration group, not raw media file count
  - Use `batchGroupId` as the registration key
  - Fall back to `legacy-{mediaItem.id}` for older data

Important rule:

- Entitlement changes must never delete local user data.
- If a free user is over the limit, existing records remain readable/editable; only new registration is blocked until Pro.

## Pro Lifetime

Product ID:

```text
memorix_pro_lifetime
```

Pro unlocks:

- Unlimited registration groups
- Document/PDF import
- OCR search
- Hidden vault
- Backup/restore
- Tag management
- Advanced filters

Deferred possible Pro additions:

- PDF/report generation
- Date-based bulk import beyond daily quick import
- Batch export
- Advanced organization views

## Implemented Internal Model

Implemented pure Kotlin monetization model:

- `core/monetization/EntitlementPolicy.kt`
- `core/monetization/PurchaseEntitlementMapper.kt`

Constants:

```kotlin
const val FreeRegistrationLimit = 300
const val ProLifetimeProductId = "memorix_pro_lifetime"
```

Entitlements:

```kotlin
enum class ProEntitlement {
    Free,
    ProLifetime,
}
```

Pro features:

```kotlin
enum class ProFeature {
    UnlimitedItems,
    DocumentImport,
    OcrSearch,
    HiddenVault,
    BackupRestore,
    TagManagement,
    AdvancedFilters,
}
```

Gate decision:

```kotlin
sealed interface FeatureGateDecision {
    data object Allowed
    data class UpgradeRequired(feature: ProFeature, reason: String)
}
```

Purchase mapping:

- confirmed `memorix_pro_lifetime` purchase + acknowledged = `ProLifetime`
- pending purchase = `Free`
- unknown product = `Free`

## Tests Added

```text
EntitlementPolicyTest
PurchaseEntitlementMapperTest
```

Covered behavior:

- Free allows registration below 300 groups
- Free blocks registration at 300 groups
- Pro allows registration beyond 300 groups
- Pro-only features require upgrade for Free
- Pro-only features are allowed for ProLifetime
- Existing data remains readable even when over limit
- Entitlement changes never delete data
- Active acknowledged lifetime purchase maps to Pro
- Pending/unknown purchases do not unlock Pro

## Next Implementation Tasks

### 1. Add registration group count source

Add DAO/repository support:

```kotlin
suspend fun countRegistrationGroups(): Int
fun observeRegistrationGroupCount(): Flow<Int>
```

SQL concept:

```sql
COUNT(DISTINCT CASE
  WHEN batchGroupId != '' THEN batchGroupId
  ELSE 'legacy-' || id
END)
```

### 2. Gate new registrations

Before saving/importing new grouped records:

- Home quick import
- Work compose save
- Personal compose save
- Detail append media only if it creates new group? Existing group append can remain allowed.

Behavior:

- If Free and count >= 300: show upgrade prompt
- Do not start media copy/import

### 3. Gate Pro features in UI

Gate entry points:

- Document import
- OCR search UI/filter
- Hidden vault
- Backup/restore
- Tag management
- Advanced filters

Behavior:

- Show feature card/row as visible but locked
- On click, open Pro upgrade sheet
- Do not silently hide value; use locked UI to explain why Pro matters

### 4. Add local entitlement repository

Use DataStore, similar to auth/locale:

- store latest known entitlement
- expose `Flow<ProEntitlement>`
- allow debug/test override only in debug builds if needed

### 5. Add Google Play Billing client

Dependency target:

```kotlin
implementation("com.android.billingclient:billing-ktx:<current stable>")
```

Required operations:

- connect BillingClient
- query product details for `memorix_pro_lifetime`
- launch purchase flow
- handle purchase updates
- acknowledge purchases
- restore/query existing purchases
- map purchases through `PurchaseEntitlementMapper`
- update local entitlement cache

### 6. Store setup prerequisite

In Google Play Console:

- create app package: `com.mebonsoft.memorix`
- enable Play App Signing
- create one-time product: `memorix_pro_lifetime`
- price: KRW 14,900 initially
- add license testers/internal testers

## UX Copy Draft

Upgrade title:

```text
Memorix Pro로 계속 정리하세요
```

Upgrade body:

```text
무료 버전은 300개 기록까지 사용할 수 있습니다. Pro 평생 이용권을 구매하면 항목 제한 없이 문서, OCR 검색, 숨긴 보관함, 백업/복원, 태그 관리를 사용할 수 있습니다.
```

CTA:

```text
Pro 평생 이용권 구매
```

Restore CTA:

```text
구매 복원
```

Limit reached message:

```text
무료 버전은 최대 300개 등록까지 사용할 수 있습니다.
```
