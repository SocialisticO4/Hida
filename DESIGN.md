---
# Hida — Design System
# Neobrutalist "cream paper & ink" vault identity
# Android (API 26+), React Native / Expo 54, NativeWind
#
# Companion docs:
#   • README.md         — project overview, build, publish
#   • ARCHITECTURE.md   — RN/Kotlin module split, screen routing
#   • ENCRYPTION.md     — cipher format, key wrap, threat model

meta:
  name: Hida
  version: "2.0"
  platform: Android
  style: Neobrutalism
  description: >
    Private media vault disguised as a calculator. Hard-edged cream paper
    aesthetic with literal offset shadows, thick ink borders, and
    high-contrast typography. No gradients. No blur. No rounded softness.

# ─────────────────────────────────────────
# COLOR TOKENS
# ─────────────────────────────────────────

color:
  # Light theme (default — "paper mode")
  light:
    bg:        { value: "#f5f1ea", description: "Cream paper — page background" }
    surface:   { value: "#ffffff", description: "Card / sheet face" }
    surface-2: { value: "#ebe5da", description: "Display / secondary surface" }
    surface-3: { value: "#d9d2c4", description: "Function-key / tertiary surface" }
    ink:       { value: "#16140f", description: "Primary text, borders, shadows" }
    ink-2:     { value: "#6b6557", description: "Secondary text / subtitles" }
    ink-3:     { value: "#9a9285", description: "Placeholder / disabled text" }
    danger:    { value: "#c8463a", description: "Error states, destructive actions" }
    shadow:    { value: "#16140f", description: "Hard-offset shadow plate" }
    scrim:     { value: "rgba(22,20,15,0.55)", description: "Modal backdrop" }

  # Dark theme
  dark:
    bg:        { value: "#0a0a0a", description: "Deep black page background" }
    surface:   { value: "#161616", description: "Dark card face" }
    surface-2: { value: "#1f1f1f", description: "Dark secondary surface" }
    surface-3: { value: "#2a2a2a", description: "Dark tertiary surface" }
    ink:       { value: "#f0ece2", description: "Warm white text / borders" }
    ink-2:     { value: "#8a857a", description: "Muted text" }
    ink-3:     { value: "#4a4842", description: "Placeholder / disabled text" }
    danger:    { value: "#ff7a6e", description: "Softened danger for dark bg" }
    shadow:    { value: "#000000", description: "Hard-offset shadow plate" }
    scrim:     { value: "rgba(0,0,0,0.70)", description: "Modal backdrop" }

  # Android platform (native splash + icon)
  android:
    splashBackground: { value: "#F5F1EA", description: "Splash screen — must match bg to avoid cold-start flash" }
    iconBackground:   { value: "#F5F1EA", description: "Adaptive icon tile background" }
    colorPrimary:     { value: "#16140F", description: "Android system primary (status bar tint)" }

# ─────────────────────────────────────────
# TYPOGRAPHY
# ─────────────────────────────────────────

typography:
  fontFamily:
    display: "Bricolage Grotesque"
    sans:    "Space Grotesk"
    mono:    "JetBrains Mono"

  # Roles
  roles:
    display:
      family:  "Bricolage Grotesque"
      weight:  700
      tracking: "-0.02em"
      lineHeight: 1
      usage: "Hero headlines, screen titles, PIN keys"
    title:
      family:  "Bricolage Grotesque"
      weight:  700
      tracking: "-0.01em"
      usage: "Section headers, top-bar titles (22px)"
    body:
      family:  "Space Grotesk"
      weight:  500
      usage: "Body copy, settings rows, gallery captions"
    button:
      family:  "Space Grotesk"
      weight:  700
      size:    "16px"
      usage: "All button labels"
    input:
      family:  "Space Grotesk"
      weight:  500
      size:    "15px"
      usage: "Text field values"
    meta:
      family:  "JetBrains Mono"
      size:    "10px"
      tracking: "0.14em"
      transform: uppercase
      color:   "ink-2"
      usage: "Section labels, tab badges, timestamps"

  scale:
    xs:   { size: "12px", lineHeight: "16px" }
    sm:   { size: "14px", lineHeight: "20px" }
    base: { size: "16px", lineHeight: "24px" }
    lg:   { size: "18px", lineHeight: "26px" }
    xl:   { size: "20px", lineHeight: "28px" }
    2xl:  { size: "24px", lineHeight: "30px" }
    3xl:  { size: "30px", lineHeight: "34px" }

# ─────────────────────────────────────────
# BORDER & RADIUS
# ─────────────────────────────────────────

border:
  width:  "2px"
  color:  "ink (theme-dependent)"
  style:  solid
  note: >
    Every interactive surface carries a 2 px solid ink border.
    This is non-negotiable — it is the defining visual signature.

radius:
  none: "0px"
  sm:   "0px"
  md:   "0px"
  lg:   "0px"
  xl:   "0px"
  2xl:  "0px"
  3xl:  "0px"
  full: "9999px"
  note: >
    All radii collapse to 0 except `full` (pill shapes for gesture
    indicator only). The neobrutalist contract is hard corners everywhere.

# ─────────────────────────────────────────
# SHADOW SYSTEM
# ─────────────────────────────────────────

shadow:
  note: >
    Shadows are literal offset plates — a solid rectangle placed behind the
    face element. No blur. No spread. Zero softness. The offset creates the
    illusion of depth through displacement alone.
  card:     { offset: "4px 4px", blur: "0", spread: "0", color: "shadow token" }
  iconBtn:  { offset: "3px 3px", blur: "0", spread: "0", color: "shadow token" }
  modal:    { offset: "6px 6px", blur: "0", spread: "0", color: "shadow token" }
  toggle:   { offset: "2px 2px", blur: "0", spread: "0", color: "shadow token" }
  pressed:
    card:    { offset: "2px 2px", blur: "0", spread: "0", color: "shadow token" }
    iconBtn: { offset: "1px 1px", blur: "0", spread: "0", color: "shadow token" }
  implementation: >
    In React Native, the shadow plate is a sibling `View` absolutely positioned
    behind the face, shifted right+down by the offset amount. The face then
    animates (translate) on press to close the gap — making it appear to "sink"
    into the shadow. The container reserves padding-right + padding-bottom equal
    to the shadow offset so the plate never clips parent bounds.

# ─────────────────────────────────────────
# SPACING
# ─────────────────────────────────────────

spacing:
  touchTarget:    "44px"   # Android minimum tap target
  pagePadding:    "16px"   # horizontal page gutter
  sectionLabel:   "16px 18px 8px"  # padding on JetBrains Mono section headers
  cardPadding:    "16px"
  modalPadding:   "20px"
  topBar:         "10px 16px 6px"
  buttonPadding:  "14px 18px"
  inputPadding:   "12px 14px"
  fabSize:        "56px"
  emptyIconSize:  "72px"

# ─────────────────────────────────────────
# MOTION & ANIMATION
# ─────────────────────────────────────────

motion:
  timing:
    press:  { duration: "80ms",  easing: "ease-out quad", usage: "Button press translate" }
    fast:   { duration: "150ms", easing: "ease",          usage: "Toggle knob, quick transitions" }
    normal: { duration: "200ms", easing: "ease",          usage: "Theme color transitions, modals" }
    slow:   { duration: "300ms", easing: "ease",          usage: "Slower reveals" }
    shake:  { duration: "320ms", easing: "ease",          usage: "PIN error shake" }

  keyframes:
    fade:
      from: { opacity: 0 }
      to:   { opacity: 1 }
      usage: "Scrim / overlay entrance (160ms)"
    pop:
      from: { transform: "scale(0.9)", opacity: 0 }
      to:   { transform: "scale(1)",   opacity: 1 }
      easing: "cubic-bezier(0.34, 1.56, 0.64, 1)"
      duration: "200ms"
      usage: "Modal entrance (elastic overshoot)"
    shake:
      keypoints:
        "0%,100%": { transform: "translateX(0)" }
        "25%":     { transform: "translateX(-8px)" }
        "75%":     { transform: "translateX(8px)" }
      duration: "320ms"
      easing: "ease"
      usage: "PIN mismatch error"

  spring:
    snappy:
      damping: 20
      stiffness: 300
      note: "No bounce — navigation transitions"
    responsive:
      damping: 15
      stiffness: 150
      note: "Light bounce — FAB, selection mode"

  press:
    translate: "offset - pressedOffset px in both axes"
    shadowShrink: "4→2 px (cards/buttons) | 3→1 px (icon buttons)"
    duration: "80ms"
    note: >
      On pressIn the face slides into the shadow; on pressOut it springs back.
      The shadow plate is static — only the face moves.

# ─────────────────────────────────────────
# COMPONENT TOKENS
# ─────────────────────────────────────────

components:
  NeoSurface:
    border:       "2px solid ink"
    shadowOffset: 4
    background:   "surface"
    usage: "Static bordered tile — cards, PIN hero, empty-state icons"

  NeoPressable:
    border:              "2px solid ink"
    shadowOffset:        4
    pressedShadowOffset: 2
    pressTranslate:      "2px"
    haptic:              "light (default)"
    disabledOpacity:     0.45
    usage: "Base for all interactive neo surfaces"

  NeoButton:
    height:    "auto (paddingVertical 14px)"
    width:     "100%"
    font:      "SpaceGrotesk 16px 700"
    variants:
      primary:   { bg: "ink",     fg: "bg" }
      secondary: { bg: "surface", fg: "ink" }
      danger:    { bg: "danger",  fg: "#ffffff" }

  NeoIconButton:
    size:                "44×44px"
    shadowOffset:        3
    pressedShadowOffset: 1
    pressTranslate:      "2px"
    background:          "surface (default)"

  NeoToggle:
    size:        "46×26px"
    border:      "2px solid ink"
    knobSize:    "18×18px"
    knobTravel:  "20px (left: 2→22)"
    animation:   "150ms ease"
    states:
      off: { track: "bg",  knob: "ink" }
      on:  { track: "ink", knob: "bg"  }

  NeoTextField:
    border:      "2px solid ink"
    shadowOffset: 3
    padding:     "10px 14px"
    font:        "SpaceGrotesk 15px 500"
    placeholder: "ink-3"

  Tab:
    border:       "2px solid ink"
    shadowOffset: "3px"
    padding:      "10px 8px"
    font:         "SpaceGrotesk 12px 700 uppercase, 0.04em tracking"
    shadowActive: "3px"
    states:
      default: { bg: "surface", fg: "ink" }
      active:  { bg: "ink",    fg: "bg"  }
    pressTranslate: "2px | shadow → 1px"

  BottomNav:
    border:    "2px solid ink (top rule)"
    background: "bg"
    items:
      border:      "2px solid ink"
      shadowOffset: "3px"
      padding:     "8px 4px 6px"
      font:        "SpaceGrotesk 11px 700 uppercase 0.04em"
      states:
        default: { bg: "surface", fg: "ink" }
        active:  { bg: "ink",    fg: "bg"  }

  FAB:
    size:         "56×56px"
    border:       "2px solid ink"
    background:   "ink"
    color:        "bg"
    shadowOffset: "4px"
    position:     "absolute, right 18px, bottom 88px"
    pressTranslate: "2px | shadow → 2px"

  Modal:
    border:       "2px solid ink"
    shadowOffset: "6px"
    background:   "surface"
    padding:      "20px"
    animation:    "pop 200ms cubic-bezier(0.34,1.56,0.64,1)"
    scrim:
      light: "rgba(22,20,15,0.55)"
      dark:  "rgba(0,0,0,0.70)"
    scrimAnimation: "fade 160ms ease"

  SectionLabel:
    font:      "JetBrains Mono 10px uppercase"
    tracking:  "0.18em"
    color:     "ink-2"
    padding:   "16px 18px 8px"

  TopBar:
    padding:     "10px 16px 6px"
    titleFont:   "Bricolage Grotesque 22px 700 -0.01em"
    gap:         "12px"

  PINKeypad:
    keySize:      "height 60px"
    keyFont:      "Bricolage Grotesque 22px 700"
    keyShadow:    "4px"
    keyGrid:      "3 columns, 10px gap"
    dotSize:      "14×14px"
    dotBorder:    "2px solid ink"
    dotFilled:    "bg: ink"
    dotEmpty:     "bg: bg"
    dotAnimation: "120ms ease"
    errorColor:   "danger"
    shakeAnimation: "shake 320ms ease"

  EmptyState:
    iconBox:   "72×72px, 2px border, 4px shadow"
    titleFont: "Bricolage Grotesque 18px 700"
    subFont:   "13px 500, color: ink-2"
    gap:       "12px"

# ─────────────────────────────────────────
# APP ICON
# ─────────────────────────────────────────

appIcon:
  foreground:
    glyph:    "Lock (closed padlock)"
    fill:     "#16140F"
    viewbox:  "108×108dp"
    note: "Standard lock SVG scaled 2× and centered in the 108dp adaptive icon viewport"
  background:
    color:    "#F5F1EA"
    note: "Cream tile — matches splash background for seamless cold-start"
  disguiseAliases:
    count: 28
    examples: ["Calculator", "Weather", "Phone", "Clock", "Notes", "Music", "Calendar", "Mail", "Browser", "Camera", "Maps", "Contacts", "Messages", "Play Store", "Drive", "Files", "Wallet", "Banking", "Shopping", "Videos", "Chat", "Photos", "Settings", "News", "Fitness", "Translate"]
    default: "HidaDefaultAlias (lock glyph)"

---

## Visual Identity

Hida presents itself as an unremarkable calculator. Its visual language is built on a single metaphor: **ink on cream paper**. Every surface, border, and shadow is derived from that palette. There are no gradients, no blur effects, no transparency beyond scrim overlays, and no border radius except for the gesture-bar pill.

### The Neobrutalist Contract

The design system enforces three invariants with no exceptions:

1. **Every interactive surface has a 2 px solid ink border.** Cards, buttons, inputs, tabs, toggles, the FAB — all carry this border. It is the connective tissue of the design.
2. **Shadows are literal offset plates.** A shadow is a solid rectangle the same color as the ink, placed exactly 4 px (or 3 px, or 6 px) to the bottom-right of the face element. No blur. No diffusion. The offset is the shadow.
3. **All corners are square.** `border-radius: 0` everywhere except `full` (9999 px) for the single gesture indicator pill.

### Press Interaction

When the user taps a button, the face element physically slides into its shadow over 80 ms (`Easing.out(Easing.quad)`). The shadow plate is static — only the face moves. On release, it slides back. This creates the sensation of pressing a physical rubber stamp. The animation is fast enough to feel instantaneous on a keystroke, but slow enough to register tactility.

Shadow compression on press:
- Cards and primary buttons: 4 px → 2 px (2 px travel)
- Icon buttons: 3 px → 1 px (2 px travel)

### Color Philosophy

The cream paper (`#f5f1ea`) is not white. It is warm, slightly yellow-gray — the color of an aged index card. Paired with the near-black ink (`#16140f`), the contrast is high without being harsh. Secondary text (`ink-2: #6b6557`) is a warm brown-gray, not a neutral gray, to stay within the paper metaphor. Danger (`#c8463a`) is a brick red — present but not screaming.

In dark mode, the palette inverts to a deep black base (`#0a0a0a`) with warm off-white ink (`#f0ece2`). The warmth is preserved — this is not a cold blue-gray dark mode. Shadow becomes pure black (`#000000`); the scrim deepens from 55 % to 70 %.

### Typography Hierarchy

Three fonts, three roles:

- **Bricolage Grotesque** (display weight 700) anchors every headline, screen title, and PIN numeral. Tightly tracked (`-0.02em`), compressed line-height (`1`). It has presence without shouting.
- **Space Grotesk** (weights 500–700) handles all body copy, button labels, and field values. It is geometric but slightly humanist — practical and legible at small sizes.
- **JetBrains Mono** appears only on section labels and metadata. Always uppercase, always 10 px, always `0.14–0.18 em` tracking. It signals "system" — configuration, not content.

### Screen Roles

**Calculator (lock screen):** Native Jetpack Compose, not subject to the JS design system. Its visual character (cream, ink borders, hard buttons) is the original from which the JS system was derived — they must stay visually consistent even though they are separate implementations.

**Welcome / onboarding:** Minimal single-screen hero. "Hida." in Bricolage Grotesque display size. The PIN creation step shows dots that grow in count as the user sets a longer PIN (4–10 digits). Error shake is the `shake` keyframe — 320 ms horizontal oscillation ±8 px.

**Gallery (vault):** Top bar with Settings and Lock icon buttons (44 px NeoIconButton). Four segmented tabs (Photos / Videos / Audio / Docs), each a NeoTab that inverts ink/bg when active. Square-framed grid via FlashList. FAB at bottom-right for import. Empty state uses a 72 px icon box with a 4 px shadow.

**Settings:** Single-page, no scroll. Sections divided by JetBrains Mono section labels with 0.18 em tracking. Rows alternate NeoSurface and plain backgrounds. Modals slide in with the `pop` animation (elastic overshoot via `cubic-bezier(0.34,1.56,0.64,1)`).

**Media viewers (image, video):** Deliberately black — ink-dark background for immersive viewing. Chrome elements (motion FAB, control bar) use neo tokens for borders and shadows but float over the dark canvas.

**Audio player:** Full neo reskin — cream background, ink borders, waveform placeholder, neo controls.

**Doc viewer:** Cream background for text docs; large neo card surface for unsupported binary types.

### Spacing Rationale

The design uses a loose 8 px grid with key exceptions. Page gutter is 16 px. Card padding is 16 px. Modal padding is 20 px. Section labels carry asymmetric padding (16 px top, 18 px sides, 8 px bottom) to optically balance the uppercase JetBrains Mono against the section below. Touch targets are anchored at 44 px (Android accessibility minimum).

### Android Integration

The splash screen background is `#F5F1EA` — cream, not white, not black. This prevents the dark-flash artifact that appears when a white or black splash transitions to a cream app background. The adaptive icon uses the same cream tile so the launcher icon and the app feel continuous.

`FLAG_SECURE` is set on the Activity, so the OS screenshot API returns a solid black image. This is intentional — the vault must not leak thumbnails to the recents screen or screenshot tools.

### Theme Switching

The theme is persisted natively (EncryptedSharedPreferences) and resolved before the first JS frame via `getThemeMode()` / `getSystemNightMode()`. This ensures the status bar, Stack screen backgrounds, and all neo surfaces show the correct palette immediately — no flash of the wrong theme on launch. The user can choose Light, Dark, or System in Settings.

---

[← Back to README](./README.md) · [Architecture →](./ARCHITECTURE.md) · [Encryption →](./ENCRYPTION.md)
