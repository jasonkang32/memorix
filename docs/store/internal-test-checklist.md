# Memorix Google Play Internal Testing Checklist

Draft date: 2026-07-20

## Goal

Use Google Play internal testing to validate Memorix on JK's real phone before public release.

## Prerequisites

### Google Play Console

- [ ] Create app entry for `Memorix`
- [ ] Package name: `com.mebonsoft.memorix`
- [ ] App category: Productivity
- [ ] Default language: Korean
- [ ] Contact email configured
- [ ] Privacy policy URL published
- [ ] App signing enabled
- [ ] Internal testing track created
- [ ] Tester email list added

### Monetization

- [ ] Create one-time product: `memorix_pro_lifetime`
- [ ] Product name: `Memorix Pro 평생 이용권`
- [ ] Initial price: KRW 14,900
- [ ] License testers added
- [ ] Test purchase flow verified

### Release Build

- [ ] Upload keystore configured locally
- [ ] Signed release AAB generated
- [ ] `versionCode` incremented before each upload
- [ ] `versionName` checked
- [ ] Release notes prepared

## Current Build Info

Current observed debug APK:

- package: `com.mebonsoft.memorix`
- versionCode: `5`
- versionName: `0.1.4`
- minSdk: `26`
- targetSdk: `35`

Current artifacts:

```text
android-native/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
android-native/app/build/outputs/bundle/release/app-release.aab
```

Note:

- Current AAB is buildable but not Play-ready until release signing is configured.

## Phone Smoke Test Script

### Install / Launch

- [ ] Install from Play internal testing link
- [ ] First launch succeeds
- [ ] App icon and name display correctly
- [ ] No immediate permission crash

### Permissions

- [ ] Photo/video picker or permission request is understandable
- [ ] Camera permission denial does not crash app
- [ ] Biometric unavailable/denied case is handled

### Basic Navigation

- [ ] Home opens
- [ ] Work tab opens
- [ ] Personal tab opens
- [ ] Settings tab opens
- [ ] Back navigation behaves normally

### Import / Registration

- [ ] Work registration with 1 photo
- [ ] Work registration with multiple photos/videos
- [ ] Personal registration with 1 photo
- [ ] Personal registration with multiple photos/videos
- [ ] Document registration where intended
- [ ] Shared image from Gallery to Memorix
- [ ] Shared multiple images/videos to Memorix
- [ ] Remove selected media before save
- [ ] Cancel registration without saving

### Counts / Tags

- [ ] Home Work/Personal counts show registration groups, not raw file count
- [ ] Tag top count shows registration group count
- [ ] Tag management lists used tags
- [ ] Deleting a tag removes assignments but not media records

### Detail / Edit

- [ ] Open grouped item detail
- [ ] Swipe/view all grouped media
- [ ] Edit memo
- [ ] Edit tags
- [ ] Edit country/region/date
- [ ] Add media to existing group
- [ ] Hide/unhide record

### Search / Filter

- [ ] Search by memo
- [ ] Search by tag
- [ ] Search by OCR text after OCR implementation check
- [ ] Work type filter
- [ ] Work tag filter
- [ ] Personal type filter

### Security

- [ ] Set PIN
- [ ] Clear PIN
- [ ] Enable/disable biometric
- [ ] Enable/disable Personal lock
- [ ] Hidden vault requires auth where intended

### Backup / Restore / Reset

- [ ] Create backup zip
- [ ] Restore backup zip
- [ ] Invalid zip failure message
- [ ] Reset all data
- [ ] App remains usable after reset

### Language

- [ ] Switch Korean
- [ ] Switch English
- [ ] Switch Japanese
- [ ] Navigation/settings labels update

### Monetization After Billing Integration

- [ ] Free state shows Pro row/card
- [ ] Free limit reached blocks new registration only
- [ ] Existing data remains readable over limit
- [ ] Purchase Pro test product
- [ ] Restore purchase
- [ ] Pending purchase does not unlock Pro until purchased/acknowledged

## Pre-Public Release Criteria

Public release only after:

- [ ] 1–2 weeks JK real-phone use
- [ ] No data-loss bug found
- [ ] Backup/restore verified
- [ ] Privacy policy URL live
- [ ] Data Safety answers finalized
- [ ] Signed AAB uploaded successfully
- [ ] Pro purchase/restore verified with license tester
- [ ] At least 4 store screenshots uploaded
