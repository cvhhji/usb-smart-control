---
version: alpha
name: USB Smart Control
description: Native Android settings for USB debugging, file transfer, and game behavior.
colors:
  primary: "#51625C"
  primary-container: "#D4E7DF"
typography:
  sans:
    fontFamily: "Android system default with OEM CJK fallback"
  technical:
    fontFamily: "monospace"
rounded:
  card: "20px"
  status: "24px"
  icon: "999px"
spacing:
  page-horizontal: "20px"
  card-horizontal: "20px"
  card-vertical: "18px"
components:
  page:
    backgroundColor: "#EFEDEC"
    textColor: "#1B1C1B"
  brand-mark:
    backgroundColor: "#D4E7DF"
    textColor: "#51625C"
    size: "52px"
  status-card:
    backgroundColor: "#D4E7DF"
    textColor: "#0F1E1A"
    rounded: "24px"
    padding: "20px"
    height: "96px"
  restart-card:
    backgroundColor: "#F5E7D2"
    textColor: "#4E361A"
  inactive-card:
    backgroundColor: "#FFDAD6"
    textColor: "#410002"
  settings-card:
    backgroundColor: "#FAF9F7"
    textColor: "#1B1C1B"
    rounded: "20px"
    padding: "18px 20px"
  primary-button:
    backgroundColor: "#51625C"
    textColor: "#FFFFFF"
    height: "56px"
    rounded: "16px"
  secondary-button:
    backgroundColor: "#D4E7DF"
    textColor: "#51625C"
    height: "48px"
---

# USB Smart Control Design System

## Overview

### Creative North Star

The screen should feel like a considered Android settings panel: warm surfaces, clear controls, and a status card that reads at a glance. The interface stays native, quiet, and focused on USB setup.

### Product context and register

- **Audience and primary job:** Android users configuring automatic USB debugging and file transfer behavior in an LSPosed environment.
- **Locale(s) and language policy:** Simplified Chinese copy with Android system font fallback.
- **Usage scene:** A narrow phone screen used occasionally to set options and check module activation.
- **Register:** Direct system utility.
- **Memorable signature:** A compact sage brand mark above a large, color-coded activation card.
- **Restraint:** Each activation state contains only its icon and short label.
- **Anti-references:** Dense diagnostic dashboards, bright blue gradients, heavy shadows, and decorative motion.
- **Token ownership/runtime mapping:** Android color resources are canonical. `values/colors.xml` and `values-night/colors.xml` hold the light and dark palettes; `SettingsActivity` uses them for cards, controls, and status.

## Colors

The light palette pairs a warm gray canvas with near-white cards and a muted sage accent. The night palette uses charcoal page and card surfaces, with lighter sage controls. Activation states use green for active, amber for restart-required, and muted red for inactive. Each state also has an icon and label.

| Token | Light | Dark |
|---|---|---|
| `page` | `#EFEDEC` | `#1D201E` |
| `surface` | `#FAF9F7` | `#111412` |
| `primary` | `#51625C` | `#B8CBC4` |
| `text-primary` | `#1B1C1B` | `#E1E4DF` |
| `text-secondary` | `#464746` | `#C3C8C2` |
| `success-container` | `#D4E7DF` | `#394B44` |
| `warning-container` | `#F5E7D2` | `#493721` |
| `error-container` | `#FFDAD6` | `#5A2826` |

## Typography

Use the Android system typeface and its normal CJK fallback. The title is 28sp, section titles are 18sp, setting names are 16sp, and supporting copy is 13–15sp. Status labels use 22sp bold text. Package names and identifiers use a monospaced face when displayed.

## Layout

The page scrolls as one column. Use 20dp horizontal insets, 20dp card insets, and 14dp gaps. The header pairs a 52dp brand mark with a title and short subtitle. The activation card follows with a 96dp minimum height; its dimensions stay fixed across states. Keep controls at least 48dp high.

## Elevation and shapes

Separate cards with tonal surfaces rather than outlines or shadows. Use 20dp setting-card corners, 24dp activation-card corners, and a circular 52dp status icon. The primary action is 56dp high with a 16dp radius. Avoid nested borders and sharp corners.

## Components

### Activation status

The card has four states: checking uses a neutral surface and a small progress indicator; active uses a sage surface and check; restart-required uses amber and an exclamation mark; inactive uses muted red and an exclamation mark. The card shows only the status symbol and label. It waits up to eight seconds for the module service and retries a missing system target only a small, fixed number of times.

### Settings and actions

Switches and radio controls use the sage accent. The save action is the single filled sage button; game selection is a tinted secondary action. Buttons retain a 48dp or larger touch target and native ripple feedback.

### Navigation and overlays

The settings remain on one scrollable page. Game selection uses the Android multi-choice dialog and keeps platform keyboard and focus behavior.

### Motion and content

Do not animate or resize the status card as state changes. Keep concise Simplified Chinese labels and preserve the device's font settings.

## Do's and don'ts

- **Do:** Pair every semantic color with an icon and visible status label.
- **Do:** Keep the activation card read-only and free of diagnostic detail.
- **Don't:** Flash a false inactive state while the module service is still connecting.
- **Don't:** Add framework details to the active state.
