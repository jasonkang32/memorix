# Memorix Core Stabilization Checklist — 2026-07-20

## Goal

Before adding Google Play Billing or opening internal testing, Memorix must not expose broken/placeholder UI and must protect local user data.

## Automated Status

Verified from `/home/mebon/project/memorix/android-native`:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

Result: `BUILD SUCCESSFUL`

Static scan:

```text
onClick = {} / TODO / FIXME / NotImplemented / UnsupportedOperationException: 0 matches
```

## Stabilization Fixes Completed

### Removed non-functional report buttons

Removed header `보고서 생성` icon buttons from:

- `feature/work/WorkScreen.kt`
- `feature/personal/PersonalScreen.kt`

Reason:

- They had empty click handlers.
- A release candidate should not show controls that do nothing.
- PDF/report generation can return later as a Pro feature after a complete flow exists.

### Removed non-functional Personal filter header icon

Removed the Personal header tune icon because it had no action. Personal already exposes media-type filters below the header.

## Manual Smoke Test Required on Phone

Run this on a real device before internal testing upload:

### First launch / navigation

- [ ] App launches without crash.
- [ ] Bottom tabs switch: Home, Work, Personal, Settings.
- [ ] Back button does not trap the user.
- [ ] Korean/English/Japanese language selection does not crash.

### Home

- [ ] Home summary counts represent registration groups, not raw media file count.
- [ ] Work/Personal cards open the expected tab.
- [ ] Tag Top 10 appears when tags exist.
- [ ] Tag usage count uses registration-group count.
- [ ] Empty states are understandable.

### Import / registration

- [ ] Work + opens registration.
- [ ] Personal + opens registration.
- [ ] Photos/videos preview as large cards before save.
- [ ] Selected media can be removed before save.
- [ ] Save creates the correct Work/Personal item.
- [ ] Document import works only where intended.
- [ ] Camera launch handles permission denial cleanly.
- [ ] Date-day Home import shows blocking progress.
- [ ] Date-day import cancel stops the operation and clears state.

### Detail / edit

- [ ] A multi-media registration opens as one grouped item.
- [ ] Detail shows all related media in the group.
- [ ] Memo/tag/location/date edits save.
- [ ] Adding media from detail appends to the same group.
- [ ] Removing one media item does not delete the whole group unless intended.
- [ ] Hidden/secret action hides the item from normal lists.

### Search / tags

- [ ] Search matches memo/title/OCR/tag labels.
- [ ] Work filter dialog opens and filters by type/tag.
- [ ] Personal media-type filters work.
- [ ] Settings > Tag management lists tags.
- [ ] Deleting a tag removes tag assignments but not media.

### Security / hidden vault

- [ ] PIN setup works.
- [ ] App lock works after setup.
- [ ] Biometric prompt works if enabled.
- [ ] Settings > hidden vault opens only after auth.
- [ ] Hidden items can be restored/unhidden.

### Backup / restore / reset

- [ ] Backup ZIP can be created through SAF.
- [ ] Restore from the ZIP returns data.
- [ ] Restore handles invalid ZIP safely.
- [ ] Reset clears lists without app restart.
- [ ] Reset does not leave copied files behind.

## Remaining Release Blockers

1. Google Play Billing/Pro entitlement not implemented.
2. Release signing/upload key not configured.
3. Privacy policy and Data Safety docs not drafted.
4. Store screenshots/listing not prepared.
5. Internal testing has not been uploaded/run.

## Deferred Features

These should not block internal testing if hidden or clearly absent:

- PDF/report generation
- Cloud sync
- Team sharing
- Pro+ subscription
