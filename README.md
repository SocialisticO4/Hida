# Hida

---

Note from the developer 
Since I have been working on this app for the past year, I have been using different LLM models. It could include Claude code or Cursor; it could include a n no of things. The thing is, I am not going to publish any more versions of it. There are going to be security updates and specific bug fixes if there is a specific demand to it.

I will be also publishing the same app on Play Store, F-Droid, and other platforms wherever most people could access the app, specifically the application. In the upcoming days, if anyone is interested, now you put PR the repo and add features that will improve the experience for the specific app that is on specific devices. Else it could improve the support for different file systems, and also it could improve the security.

As you know, there is no need for present security to be as secure as possible. I have ensured it in such a way that it is not capable of being cracked by bureaus or agencies, seems to me and the features I have been put through a lot of effort, and these are tested with a lot of problems and thoughts in my mind. A lot of time and energy has been taken to specifically design this thing in the way that no other app exists in the market.

In this strength, I feel the demand for applications is increasing, but the pace of making those applications is falling down. So yea, there is a care for privacy...

---
Hida is an Android-only media vault that hides photos, videos, audio, and documents behind a fully-functional calculator. Type the correct PIN, press `=`, and the vault opens. Type a different PIN and a harmless decoy vault opens instead. All encryption happens on-device in Kotlin — keys never cross the JS bridge, and no data ever leaves your phone.

> Cream paper and ink. Hard borders. No cloud. No accounts.

---

## Table of contents

- [Why Hida](#why-hida)
- [Features](#features)
- [Documentation](#documentation)
- [Project layout](#project-layout)
- [Tech stack](#tech-stack)
- [Security model](#security-model)
- [Contributing](#contributing)
- [License](#license)

---

## Why Hida

Most "vault" apps on Android are obvious: a padlock icon, an obvious name, an obvious entry screen. Anyone who borrows your phone for thirty seconds knows exactly what's inside. Hida flips the threat model — the entry point is a real calculator that real calculation works on. The vault only exists if you know the PIN, and even then there's a decoy vault for plausible deniability.

The cryptography is conservative on purpose: **ChaCha20-Poly1305** in 256 KB AEAD chunks, master key wrapped under the Android KeyStore, optional biometric unlock via `BiometricPrompt` with a `CryptoObject`. See [ENCRYPTION.md](./ENCRYPTION.md) for the full spec.

---

## Features

### Security

- **Calculator disguise** — fully functional scientific calculator at the lock screen
- **Dual PIN** — real PIN unlocks the vault; decoy PIN opens an empty-looking decoy
- **ChaCha20-Poly1305 AEAD** — 256 KB chunked streaming, 16-byte auth tag per chunk
- **KeyStore-backed master key** — hardware-backed where the device supports it
- **Optional biometric unlock** — `BiometricPrompt` + `CryptoObject` gates the key unwrap
- **Brute-force lockout** — exponential backoff after consecutive failed attempts
- **Auto-lock** — configurable session timeout when the app is backgrounded or idle
- **`FLAG_SECURE`** — screenshots and screen recording return solid black
- **28 launcher disguises** — the app icon can pose as Calculator (three variants), Weather, Notes, Clock, Music, Calendar, Mail, Browser, Camera, Maps, Phone, Contacts, Messages, Play Store, Drive, Files, Wallet, Banking, Shopping, Videos, Chat, Photos, Settings, News, Fitness, or Translate.
- **No network permission** — Hida is fully offline; nothing leaves the device

### Media

- **Photos** — full resolution with encrypted thumbnails
- **Videos** — instant streaming playback; no full-file decrypt wait
- **Audio** — play / pause / seek / skip ±10s
- **Documents** — inline viewer for plain text, system-intent export for PDFs/DOCX
- **Motion photos** — Samsung and Google motion-photo containers supported
- **Large files** — streaming encryption handles 4K HDR videos and multi-hour audio

### Design

- **Neobrutalist "cream paper & ink"** — see [DESIGN.md](./DESIGN.md)
- **Light, Dark, and System** themes — persisted natively so there's no flash on launch
- **Hard offset shadows, square corners, 2 px ink borders** everywhere
- **Bricolage Grotesque / Space Grotesk / JetBrains Mono** typography
- **Native Jetpack Compose calculator** + NativeWind-styled React Native for the vault

---

## Documentation

| Doc                                  | What's in it                                                                                            |
| ------------------------------------ | ------------------------------------------------------------------------------------------------------- |
| [README.md](./README.md)             | This file — overview, features, layout, tech stack, security summary.                                   |
| [ARCHITECTURE.md](./ARCHITECTURE.md) | How the RN + Kotlin native module fit together; calculator-to-vault flow; screen routing; theme system. |
| [ENCRYPTION.md](./ENCRYPTION.md)     | Chunked ChaCha20-Poly1305 format, master-key wrapping, KeyStore, threat model, what's *not* protected.  |
| [DESIGN.md](./DESIGN.md)             | Neobrutalist design system — tokens, components, motion, typography.                                    |
| [LICENSE](./LICENSE)                 | Apache 2.0.                                                                                             |

---

## Project layout

The exact tree of what's in this repository:

```
Hida/
├── README.md
├── ARCHITECTURE.md
├── ENCRYPTION.md
├── DESIGN.md
├── LICENSE
└── hida-rn/
    ├── app.json
    ├── babel.config.js
    ├── metro.config.js
    ├── tailwind.config.js
    ├── tsconfig.json
    ├── global.css
    ├── nativewind-env.d.ts
    ├── package.json
    ├── package-lock.json
    ├── .npmrc
    │
    ├── app/                               # expo-router screens
    │   ├── _layout.tsx                    # root stack + session/idle wiring
    │   ├── index.tsx                      # routes to /welcome or /calculator
    │   ├── welcome.tsx                    # first-run hero + PIN setup
    │   ├── calculator.tsx                 # hosts <HidaCalculatorView>
    │   ├── gallery.tsx                    # vault grid (Photos/Videos/Audio/Docs)
    │   ├── image-viewer.tsx
    │   ├── video-player.tsx
    │   ├── audio-player.tsx
    │   ├── doc-viewer.tsx
    │   └── settings.tsx
    │
    ├── components/ui/                     # neo design primitives
    │   ├── neo.tsx                        # NeoSurface, NeoButton, NeoToggle, etc.
    │   ├── dialog.tsx
    │   ├── info-sheet.tsx
    │   └── PlainKeypad.tsx
    │
    ├── lib/
    │   ├── theme.tsx                      # ThemeProvider, useTheme, palettes
    │   ├── constants.ts
    │   ├── soundEffects.ts
    │   └── useGallerySelection.ts
    │
    ├── assets/
    │   ├── icon.png                       # app icon
    │   ├── adaptive-icon.png              # Android adaptive icon foreground
    │   ├── favicon.png
    │   ├── splash-icon.png                # cold-start splash glyph
    │   ├── fonts/
    │   │   ├── BricolageGrotesque.ttf
    │   │   ├── SpaceGrotesk.ttf
    │   │   ├── JetBrainsMono.ttf
    │   │   ├── Geist-Regular.ttf
    │   │   └── Geist-SemiBold.ttf
    │   └── sounds/
    │       ├── tap.wav
    │       ├── unlock.wav
    │       └── error.wav
    │
    ├── patches/                           # patch-package patches applied on `npm install`
    │   └── expo-keep-awake+15.0.8.patch
    │
    ├── modules/hida-native/               # the single Expo native module (Kotlin)
    │   ├── index.ts                       # public JS surface
    │   ├── expo-module.config.json
    │   └── android/
    │       ├── build.gradle
    │       └── src/main/
    │           ├── AndroidManifest.xml
    │           ├── res/font/              # bricolage / jetbrains_mono / space_grotesk TTFs
    │           └── java/app/hida/vault/nativemodule/
    │               ├── HidaNativeModule.kt     # Expo module registration
    │               ├── CalculatorView.kt       # ExpoView host (Compose-aware)
    │               ├── CalculatorScreen.kt     # Jetpack Compose calculator UI
    │               ├── CalcEngine.kt           # calculator math
    │               ├── CryptoManager.kt        # ChaCha20-Poly1305 chunked AEAD
    │               ├── MediaRepository.kt      # encrypted file I/O
    │               ├── PreferencesManager.kt   # EncryptedSharedPreferences wrapper
    │               ├── BiometricCryptoStore.kt # biometric key wrap
    │               ├── VaultBiometricHelper.kt # BiometricPrompt orchestration
    │               ├── AndroidBiometricCompat.kt
    │               ├── IconAliasSwitcher.kt        # launcher disguise toggling
    │               ├── LauncherDisguiseBranding.kt # recents/task branding
    │               ├── ScreenOffLockInstaller.kt   # ACTION_SCREEN_OFF receiver
    │               └── MotionPhotoParser.kt
    │
    └── android/                           # generated Android project (expo prebuild output)
        ├── build.gradle
        ├── settings.gradle
        ├── gradle.properties
        ├── gradlew, gradlew.bat
        ├── gradle/wrapper/
        └── app/
            ├── build.gradle
            ├── proguard-rules.pro
            ├── debug.keystore              # ships with default android/android creds
            └── src/main/
                ├── AndroidManifest.xml
                ├── java/app/hida/vault/
                │   ├── MainActivity.kt
                │   └── MainApplication.kt
                └── res/
                    ├── values/                 # colors.xml, strings.xml, themes.xml
                    ├── values-night/           # dark-theme color overrides
                    ├── mipmap-*/               # adaptive launcher icons + 28 disguise icons
                    ├── drawable/               # 28 adaptive-icon foreground XMLs + bitmaps
                    ├── drawable-*/             # density-bucketed splash + legacy icons
                    └── xml/                    # backup + extraction + file-provider rules
```

The Java/Kotlin package is `app.hida.vault` (`applicationId` and `namespace` both). Details on every module live in [ARCHITECTURE.md](./ARCHITECTURE.md). Crypto details live in [ENCRYPTION.md](./ENCRYPTION.md).

---

## Tech stack

- **React Native 0.81** with the New Architecture (Fabric + TurboModules) enabled
- **Expo 54** + **expo-router 6** (file-based routing)
- **NativeWind** (Tailwind CSS for React Native) — token-driven theming
- **Reanimated 4** + **react-native-gesture-handler** for motion and gestures
- **@shopify/flash-list** for the gallery grid
- **Lucide** icons in JS; vector launcher glyph in Android resources
- **Kotlin** + **Jetpack Compose** for the native calculator UI
- **`javax.crypto.Cipher`** with ChaCha20-Poly1305 in the native module
- **Android KeyStore** for hardware-backed key wrapping
- **`BiometricPrompt`** + **`CryptoObject`** for biometric-gated unlock

---

## Security model

- **What Hida protects:** confidentiality and integrity of media files on disk against an attacker who has the device but not the PIN or a valid biometric.
- **What Hida does *not* protect:** a running, unlocked vault. If the device is unlocked and Hida is unlocked, an attacker with physical access can browse the vault like any other app. Set Auto-Lock short.
- **What Hida does *not* hide:** the APK itself. The launcher alias may say "Weather" but the package id is still `app.hida.vault` and any user inspecting `pm list packages` sees it.
- **No network permission.** The `AndroidManifest.xml` does not declare `INTERNET`. Hida cannot phone home — by design.

Full threat model in [ENCRYPTION.md](./ENCRYPTION.md#threat-model).

---

## Contributing

Hida is open to contributions under the Apache 2.0 license. The project is small and opinionated — changes are easiest to land when they fit the existing patterns.

**Where to start.** Open [ARCHITECTURE.md](./ARCHITECTURE.md) before touching the native module, the calculator, or the session/auto-lock flow — it documents lifecycle pitfalls that are easy to regress. For crypto changes, read [ENCRYPTION.md](./ENCRYPTION.md) — the on-disk chunk format is a hard invariant and there is no in-place migration path. For UI work, [DESIGN.md](./DESIGN.md) defines the neobrutalist tokens and the eight neo primitives in [`hida-rn/components/ui/neo.tsx`](./hida-rn/components/ui/neo.tsx).

**Ways to help.**

- **Bug reports.** Open a GitHub issue with the device model, Android version, repro steps, and an `adb logcat` snippet filtered on `HidaCalcView:V ReactNativeJS:V AndroidRuntime:E *:S`. Note that `FLAG_SECURE` makes `adb screencap` return solid black — phone photos beat screenshots.
- **New launcher disguises.** Add a `.<Name>Alias` entry to the manifest, drop a 432×432 adaptive icon foreground at `mipmap-anydpi-v26/ic_disguise_<name>.xml`, and add the alias name to `LAUNCHER_ALIASES` in [`IconAliasSwitcher.kt`](./hida-rn/modules/hida-native/android/src/main/java/app/hida/vault/nativemodule/IconAliasSwitcher.kt).
- **Document viewers.** The current `doc-viewer.tsx` handles plain text inline and delegates everything else via system intent. Inline support for PDF / DOCX / EPUB would be welcome — but must not introduce a network dependency.
- **Translations.** Strings are in [`android/app/src/main/res/values/strings.xml`](./hida-rn/android/app/src/main/res/values/strings.xml) and inline in JS. There's no `values-<locale>/` infrastructure yet — adding one is a contribution by itself.
- **Performance.** The gallery uses FlashList. Large vaults (10k+ items) have not been profiled. PRs with measurements welcome.

**Pull request expectations.**

- **One concern per PR.** A neo-component refactor doesn't share a PR with a crypto change. Reviewers should be able to reason about one boundary at a time.
- **Match existing style.** TypeScript strict, no `any` on the JS API surface; Kotlin idiomatic, no Java-style getters. NativeWind classes preferred over inline `style` for static values.
- **Don't break the AEAD format.** `CHUNK_SIZE`, `NONCE_SIZE`, `AUTH_TAG_SIZE`, the header layout — any of these changing silently corrupts every existing vault. If a format change is genuinely needed, it needs a migration path and a migration PR.
- **Tests.** Unit tests for `CalcEngine.kt` and `CryptoManager.kt` are welcome. JS-side: typecheck must pass (`npx tsc --noEmit`).
- **Privacy-respecting commits.** Don't add analytics, crash reporting, or any code that performs network I/O. The `INTERNET` permission is deliberately absent from the manifest — keep it that way.

**Things that will be rejected.**

- iOS scaffolding — Hida is Android-only by design.
- Cloud backup, sync, or any account system.
- "Improvements" to the design tokens without a corresponding update to [DESIGN.md](./DESIGN.md).
- Edits to `CalculatorView.kt` that remove the `setViewTreeLifecycleOwner` / `setViewTreeSavedStateRegistryOwner` calls — see *Native lifecycle pitfalls* in [ARCHITECTURE.md](./ARCHITECTURE.md).
- Tracking, telemetry, ads, or any third-party SDK that opens an outbound connection.

---

## License

[Apache License 2.0](./LICENSE). See the LICENSE file for the full text.
