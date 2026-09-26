---
version: alpha
name: USB Smart Control
description: Android system utility for configuring USB debugging and file transfer behavior.
colors:
  primary: "#51625C"
  surface: "#FAF9F7"
typography:
  sans:
    fontFamily: "sans, system-ui, sans-serif"
    lineHeight: "1.4"
  technical:
    fontFamily: "monospace"
rounded:
  DEFAULT: "22px"
  status: "24px"
  badge: "18px"
spacing:
  page-horizontal: "18px"
  card-horizontal: "20px"
  card-vertical: "18px"
components:
  page:
    backgroundColor: "#F5F3F2"
    textColor: "#1B1C1B"
  supporting-copy:
    textColor: "#464746"
  status-card:
    backgroundColor: "#D4E7DF"
    textColor: "#0F1E1A"
    rounded: "24px"
    padding: "22px"
    size: "58px"
  settings-card:
    backgroundColor: "#FAF9F7"
    textColor: "#1B1C1B"
    rounded: "22px"
    padding: "18px 20px"
  primary-button:
    backgroundColor: "#51625C"
    textColor: "#FFFFFF"
    height: "52px"
    rounded: "28px"
  secondary-button:
    backgroundColor: "#DBE5E0"
    textColor: "#51625C"
    height: "48px"
    rounded: "26px"
---

# USB Smart Control Design System

## Overview

### Creative North Star

The screen should feel like a compact Android system panel: quiet surfaces, clear controls, and a status card that is readable at a glance. The provided reference informs the muted green palette, rounded cards, and generous status area. The app remains a native Chinese-language settings screen.

### Product context and register

- **Audience and primary job:** Android users who configure automatic USB debugging and file transfer behavior in an LSPosed environment.
- **Target market(s) and evidence:** Chinese-language Android installation; no region-specific workflow is present.
- **Locale(s) and language policy:** Simplified Chinese copy with Android system font fallbacks.
- **Usage scene:** A narrow phone screen used occasionally to set options and check module injection.
- **Register:** Product utility.
- **Memorable signature:** A large, tinted activation card with a clear check or warning mark.
- **Restraint:** Keep every activation state to its icon and label. The card does not expand into diagnostic copy.
- **Anti-references:** Dense diagnostic dashboards, bright blue gradients, heavy shadows, and decorative status animations.
- **Token ownership/runtime mapping:** Android resource XML is the runtime source. `app/src/main/res/values/colors.xml` and `app/src/main/res/values-night/colors.xml` define light and dark semantic colors; `app/src/main/res/values/styles.xml` and its night variant set system bars. `SettingsActivity` consumes these resources for cards, controls, and status. This document mirrors those values.

## Colors

The light palette uses a warm near-white canvas and muted sage primary. The dark palette maps the same roles to charcoal surfaces and lighter sage. Success remains green and attention remains amber in both themes; each state also has a symbol and label so color is never the only signal.

| Design token | Light runtime resource | Dark runtime resource |
|---|---|---|
| `primary` | `accent` `#51625C` | `accent` `#B8CBC4` |
| `surface` | `card_background` `#FAF9F7` | `card_background` `#1D201E` |
| `page` | `page_background` `#F5F3F2` | `page_background` `#111412` |
| `outline` | `card_border` `#D8DEDA` | `card_border` `#424943` |
| `text-primary` | `text_primary` `#1B1C1B` | `text_primary` `#E1E4DF` |
| `text-secondary` | `text_secondary` `#464746` | `text_secondary` `#C3C8C2` |
| `success-container` | `status_active_background` `#D4E7DF` | `status_active_background` `#344B43` |
| `warning-container` | `status_attention_background` `#FFE8CC` | `status_attention_background` `#3B2D1E` |

## Typography

Use Android's `sans` family for Chinese text and native control metrics. The page title is 30sp, section titles are 18sp, setting names are 16sp, and supporting copy is 13–15sp. Status labels use 23sp bold text. Keep package names and identifiers in a monospaced face when displayed.

## Layout

The page scrolls naturally as one column. Use 18dp horizontal page insets, 22dp standard card padding, and a 14dp gap between cards. Keep controls at least 48dp high. The activation card leads the page and keeps the same footprint across its states.

## Elevation & Depth

Separate cards with surface color and a fine outline. Do not add prominent shadows. Status cards use a semantic tinted surface; normal cards remain neutral. System bars follow the active light or dark theme.

## Shapes

Use rounded 22dp setting cards, a 24dp activation card, and rounded 18dp status icon tiles. Primary actions are pill-shaped. Avoid nested decorative borders and sharp corners.

## Components

### Foundational visual states

The activation card has four states: checking uses a neutral surface and ellipsis; active uses a sage surface with a check; inactive and restart-required use an amber surface with an exclamation mark. The card shows only its status label and symbol. Switches and radio controls use the sage accent. Light and dark themes preserve these semantic roles.

### Buttons and actions

The save action is the single filled sage button. Game selection is a tinted secondary button. Buttons retain a 48dp or larger touch height and use the system ripple for press feedback.

### Navigation and data display

The settings screen is a single scrollable page. Keep each configuration area grouped in a rounded card with its title above its controls.

### Forms and overlays

Game selection remains an Android multi-choice dialog. Preserve system keyboard and focus behavior for native controls.

### Iconography

Use simple, high-contrast symbols for status: a check mark for active, an exclamation mark for inactive or restart-required, and an ellipsis while checking. The text label always remains visible.

### Motion

Do not animate the status card. Status updates should not flash or move the rest of the settings page.

### Content and data visualization

Use short, direct Simplified Chinese labels. Keep status copy to “已激活”, “未激活”, or “需重启”.

## Do's and Don'ts

- **Do:** Keep the active card recognizable with a large symbol and a short label.
- **Do:** Use semantic colors consistently in light and dark themes.
- **Don't:** Show framework diagnostics in the active state.
- **Don't:** Use color alone to distinguish activation states.
