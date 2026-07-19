---
version: alpha
name: Memorix
description: Premium local media vault for fast capture, quiet organization, and confident retrieval on Android.
colors:
  ink: "#0B1220"
  inkSoft: "#263241"
  muted: "#6B7280"
  subtle: "#98A2B3"
  canvas: "#F5F7FA"
  surface: "#FFFFFF"
  surfaceElevated: "#FAFBFD"
  border: "#E5EAF1"
  borderStrong: "#CBD5E1"
  primary: "#005A46"
  primaryBright: "#00C896"
  primarySoft: "#E4F8F1"
  work: "#1A73E8"
  workDeep: "#164EA6"
  workSoft: "#E8F1FF"
  personal: "#C23A70"
  personalBright: "#FF6B9D"
  personalSoft: "#FFF0F6"
  album: "#5142D7"
  albumSoft: "#F0EDFF"
  warning: "#B25E00"
  warningSoft: "#FFF4D6"
  danger: "#B42318"
  dangerSoft: "#FEE4E2"
  success: "#067647"
  successSoft: "#DCFAE6"
  darkCanvas: "#090E17"
  darkSurface: "#111827"
  darkSurfaceElevated: "#172033"
  darkBorder: "#263244"
  darkText: "#F8FAFC"
  darkMuted: "#9CA3AF"
typography:
  display:
    fontFamily: Pretendard, Inter, system-ui, sans-serif
    fontSize: 2.25rem
    fontWeight: 800
    lineHeight: 1.08
    letterSpacing: "-0.035em"
  h1:
    fontFamily: Pretendard, Inter, system-ui, sans-serif
    fontSize: 2rem
    fontWeight: 800
    lineHeight: 1.12
    letterSpacing: "-0.03em"
  h2:
    fontFamily: Pretendard, Inter, system-ui, sans-serif
    fontSize: 1.625rem
    fontWeight: 750
    lineHeight: 1.18
    letterSpacing: "-0.025em"
  h3:
    fontFamily: Pretendard, Inter, system-ui, sans-serif
    fontSize: 1.25rem
    fontWeight: 700
    lineHeight: 1.25
    letterSpacing: "-0.015em"
  body-lg:
    fontFamily: Pretendard, Inter, system-ui, sans-serif
    fontSize: 1rem
    fontWeight: 400
    lineHeight: 1.55
  body-md:
    fontFamily: Pretendard, Inter, system-ui, sans-serif
    fontSize: 0.9375rem
    fontWeight: 400
    lineHeight: 1.5
  body-sm:
    fontFamily: Pretendard, Inter, system-ui, sans-serif
    fontSize: 0.8125rem
    fontWeight: 400
    lineHeight: 1.45
  label:
    fontFamily: Pretendard, Inter, system-ui, sans-serif
    fontSize: 0.8125rem
    fontWeight: 700
    lineHeight: 1.25
    letterSpacing: "-0.005em"
  caption:
    fontFamily: Pretendard, Inter, system-ui, sans-serif
    fontSize: 0.75rem
    fontWeight: 500
    lineHeight: 1.35
rounded:
  xs: 6px
  sm: 10px
  md: 14px
  lg: 18px
  xl: 24px
  full: 999px
spacing:
  xs: 4px
  sm: 8px
  md: 12px
  lg: 16px
  xl: 20px
  2xl: 24px
  3xl: 32px
  4xl: 40px
shadow:
  sm: 0 1px 2px rgba(15, 23, 42, 0.06)
  md: 0 8px 24px rgba(15, 23, 42, 0.08)
  lg: 0 18px 50px rgba(15, 23, 42, 0.12)
components:
  app-background:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
  app-background-dark:
    backgroundColor: "{colors.darkCanvas}"
    textColor: "{colors.darkText}"
  card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.lg}"
    padding: 16px
  card-elevated:
    backgroundColor: "{colors.surfaceElevated}"
    textColor: "{colors.ink}"
    rounded: "{rounded.xl}"
    padding: 20px
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "#FFFFFF"
    rounded: "{rounded.full}"
    padding: 14px
    height: 52px
  button-primary-hover:
    backgroundColor: "{colors.ink}"
    textColor: "#FFFFFF"
    rounded: "{rounded.full}"
    padding: 14px
    height: 52px
  button-secondary:
    backgroundColor: "{colors.primarySoft}"
    textColor: "{colors.primary}"
    rounded: "{rounded.full}"
    padding: 12px
    height: 48px
  tab-selected:
    backgroundColor: "{colors.primarySoft}"
    textColor: "{colors.primary}"
    rounded: "{rounded.full}"
    padding: 10px
  tab-unselected:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.muted}"
    rounded: "{rounded.full}"
    padding: 10px
  work-chip:
    backgroundColor: "{colors.workSoft}"
    textColor: "{colors.workDeep}"
    rounded: "{rounded.full}"
    padding: 8px
  personal-chip:
    backgroundColor: "{colors.personalSoft}"
    textColor: "{colors.personal}"
    rounded: "{rounded.full}"
    padding: 8px
  album-chip:
    backgroundColor: "{colors.albumSoft}"
    textColor: "{colors.album}"
    rounded: "{rounded.full}"
    padding: 8px
  input-field:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.md}"
    padding: 14px
    height: 52px
  danger-button:
    backgroundColor: "{colors.danger}"
    textColor: "#FFFFFF"
    rounded: "{rounded.full}"
    padding: 12px
    height: 48px
---

## Overview

Memorix is a premium Android-native local media vault: fast enough for daily capture, calm enough for long-term storage, and clear enough to find old photos, videos, and documents without stress. The visual identity should feel like a refined productivity product rather than a generic gallery: quiet surfaces, precise spacing, confident typography, rich but restrained color, and highly legible media cards.

The product promise is “기억은 빠르게, 보관은 조용하게.” The UI must support that promise through three principles:

1. **Capture speed:** import actions must be visually obvious and reachable.
2. **Quiet organization:** lists, albums, and detail screens should feel calm, ordered, and low-noise.
3. **Trustworthy retrieval:** search, tags, metadata, and lock states should be visually crisp and reliable.

Android Native is the correct design target. Avoid Flutter-era visual bloat, heavy gradients everywhere, and feature-dense dashboards. Use native-feeling Compose surfaces with commercial polish.

## Colors

- **Ink (#0B1220):** primary text and brand depth. Use for high-confidence labels, screen titles, and important metadata.
- **Primary (#005A46):** deep premium green for primary actions. This replaces bright mint as the main button color because it passes contrast with white text.
- **Primary Bright (#00C896):** Memorix signature mint. Use as glow, small active accent, progress highlight, or gradient endpoint — not as white-text button background.
- **Canvas (#F5F7FA):** default app background. It keeps media cards elevated without turning the app into a dark vault.
- **Surface (#FFFFFF):** card and sheet background. Pair with subtle borders, not heavy shadows.
- **Work Blue (#1A73E8 / #164EA6):** Work space identity. Use deep blue for text/icons, soft blue for chips and panels.
- **Personal Rose (#C23A70 / #FF6B9D):** Personal space identity. Use deep rose for accessible text and bright rose only for accents.
- **Album Violet (#6D5DF6):** albums and grouped memories. Keep it secondary to Work/Personal identity.
- **Warning/Danger/Success:** semantic states only. Do not use these as decorative brand colors.
- **Dark palette:** available for system dark mode, but the default product impression should remain bright, premium, and clean.

Color hierarchy:

1. White/silver surfaces dominate.
2. Ink and muted gray carry structure.
3. Deep green marks primary commitment.
4. Blue/rose/violet identify content domains.
5. Bright mint appears sparingly as a memorable signature accent.

## Typography

Use Pretendard first for Korean readability, with Inter/system fallback. Memorix should feel premium through confident weight and tight but readable rhythm.

- **Display/H1:** use only on Home hero, empty states, and major onboarding moments. Keep letter spacing tight.
- **H2/H3:** section titles, album names, detail titles, and timeline group headings.
- **Body:** use 15–16px equivalent for normal reading. Avoid tiny metadata in media-heavy screens; metadata is only useful if visible on a phone outdoors.
- **Labels:** use strong weight for chips, filter pills, and action labels.
- **Caption:** use sparingly for timestamps, file size, duration, and helper text.

Do not mix many font sizes on one card. A commercial media-card rhythm should usually be: title/metadata header, image area, one body preview, one compact metadata row.

## Layout

The app should feel spatially generous without wasting screen real estate.

- Screen horizontal padding: **16px** on normal screens, **12px** for dense media grids, **20px** for hero/onboarding surfaces.
- Section spacing: **20–24px** between major groups.
- Card internal padding: **14–20px**, depending on density.
- Media grid gap: **6–8px** for dense grids; timeline image collage gap can be **2–4px**.
- Bottom navigation height should remain compact and stable. Avoid oversized nav chrome that steals media space.
- Important actions should be thumb-reachable: bottom sheets, top-right add buttons, and floating compose surfaces are acceptable, but avoid hiding capture behind multiple menus.

Home should show a premium first impression: a concise header, a primary quick-import action, recent media, and meaningful shortcuts. Avoid dashboard clutter.

Work/Personal screens should prioritize content retrieval: filters, timeline/grid, search, and import. Keep instructional copy minimal.

## Elevation & Depth

Use depth like a professional mobile app, not like a mockup.

- Default cards: subtle border plus tiny shadow, or border only.
- Important cards: medium shadow with large soft radius.
- Bottom sheets/dialogs: elevated surface with clear separation from canvas.
- Image cards: depth should come from the media itself; keep chrome minimal.
- Dark mode: reduce shadow reliance; use layered surfaces and borders.

Avoid heavy shadows under every card. If everything floats, nothing feels important.

## Shapes

Memorix should use modern rounded geometry, but not toy-like bubbles.

- Small controls: **10–14px** radius.
- Cards: **18px** radius by default.
- Hero/import cards: **24px** radius.
- Pills/chips/buttons: full radius.
- Media thumbnails: **12–16px** radius, with slightly tighter corners inside collage grids.

For Work timeline cards, use rectangular premium cards with controlled rounding. Do not over-round image grids; it makes the app feel casual rather than professional.

## Components

### Primary Button

Use `button-primary` for high-commitment actions: 저장, 가져오기, PIN 설정, 선택 완료. It must use deep green with white text. Do not use bright mint as the button background with white text because contrast is weak.

### Secondary Button

Use `button-secondary` for supportive actions: 태그 추가, 앨범 이동, 다시 선택, 필터 초기화. It should feel available but not compete with the primary action.

### Media Card

A media card should have:

1. A strong visual image/video/document preview.
2. A short title or inferred filename.
3. A compact metadata row: date, type, count, location/album if useful.
4. Optional chips for Work/Personal/Album/Tags.
5. A subtle favorite/locked/trash state indicator.

Do not fill media cards with long explanatory text. The user wants to recognize and retrieve, not read a report.

### Work Timeline Card

Work cards may use a more information-dense timeline layout:

- Header: location/tag/date.
- Body: image collage or document preview.
- Footer: memo preview and media count.
- Deep blue Work chip for identity.

The card should feel like a polished field record, not social media clone. Use Instagram-like image composition but productivity-app typography.

### Personal Card

Personal cards should feel warmer and simpler:

- Rose/personal accent.
- Album/event title first.
- Fewer metadata lines.
- More emphasis on memory and preview.

### Search Field

Search is a first-class retrieval tool. Use a stable full-width field with rounded shape, clear icon, and explicit filter chips. Empty state should guide the user to search by title, memo, tag, date, album, document type, or location.

### Input / Compose Screen

The media registration screen must feel like a dedicated capture desk:

- App bar: back, title, save.
- First block: selected media thumbnails and add more.
- Then: date, memo, tags, location.
- Save remains visible and unambiguous.
- Errors should appear inline, not as vague toasts only.

The user should understand that files are not stored until pressing 저장.

## Do's and Don'ts

### Do

- Use Android Native components, but polish them with consistent tokens.
- Preserve fast import as the main commercial differentiator.
- Keep bright mint as a signature accent, not a universal fill color.
- Make media thumbnails large enough to recognize.
- Use Work/Personal color identity only where it improves orientation.
- Keep settings/security screens calm and trustworthy.
- Validate button contrast whenever colors change.

### Don't

- Do not revive heavy Flutter-era scope such as Drive, subscription, team, or report-heavy UI in the MVP design.
- Do not make Home a statistics dashboard.
- Do not use bright mint with white text for primary buttons.
- Do not overuse gradients; one premium header or small accent is enough.
- Do not make cards overly rounded or cartoonish.
- Do not bury capture/import behind multiple taps.
- Do not use tiny gray metadata that cannot be read outdoors.
