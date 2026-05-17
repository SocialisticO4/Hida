# Architecture — Hida

How the React Native shell and the Kotlin native module fit together, how a calculator press becomes a vault unlock, and what to watch out for when changing the lifecycle-sensitive code.

For the overview, see [README.md](./README.md). For the cryptography, see [ENCRYPTION.md](./ENCRYPTION.md). For the design system, see [DESIGN.md](./DESIGN.md).

---

## High-level picture

```
┌─────────────────────────────────────────────────────────────┐
│                  React Native (Expo 54)                     │
│                                                             │
│  expo-router screens (TypeScript)                           │
│  ─────────────────────────────────                          │
│  /welcome  /calculator  /gallery  /image-viewer             │
│  /video-player  /audio-player  /doc-viewer  /settings       │
│                                                             │
│  Styling: NativeWind (Tailwind for RN)                      │
│  Tokens:  tailwind.config.js  +  lib/theme.tsx              │
│  Motion:  react-native-reanimated 4                         │
│  Gestures: react-native-gesture-handler                     │
│  Grid:     @shopify/flash-list                              │
└─────────────────────────────────────────────────────────────┘
                          ▲     │
                          │     │  JSI / TurboModule bridge
                          │     ▼
┌─────────────────────────────────────────────────────────────┐
│           Expo Native Module — modules/hida-native          │
│                                                             │
│  index.ts                                                   │
│    └── public JS API:                                       │
│        crypto, media I/O, biometric, session,               │
│        icon switching, calculator view                      │
│                                                             │
│  Kotlin (android/src/main/java/.../nativemodule/):          │
│    HidaNativeModule.kt       Expo module entry              │
│    CalculatorView.kt         ExpoView host (Compose-aware)  │
│    CalculatorScreen.kt       Jetpack Compose calculator UI  │
│    CalcEngine.kt             calculator math                │
│    CryptoManager.kt          ChaCha20-Poly1305 chunked AEAD │
│    MediaRepository.kt        encrypted file I/O             │
│    PreferencesManager.kt     EncryptedSharedPreferences     │
│    BiometricCryptoStore.kt   biometric wrap                 │
│    VaultBiometricHelper.kt   BiometricPrompt orchestration  │
│    IconAliasSwitcher.kt      launcher disguise              │
│    LauncherDisguiseBranding.kt  recents/task branding       │
│    ScreenOffLockInstaller.kt    BroadcastReceiver for sleep │
│    MotionPhotoParser.kt      Samsung/Google motion split    │
└─────────────────────────────────────────────────────────────┘
                          ▲
                          │  Android KeyStore
                          ▼
┌─────────────────────────────────────────────────────────────┐
│  Android KeyStore — hardware-backed master key (AES/GCM)    │
│  EncryptedSharedPreferences — PIN salt, wrapped keys,       │
│   session prefs, disguise alias, biometric toggle, etc.     │
│  cacheDir/decrypted/ — short-lived plaintext temp files     │
└─────────────────────────────────────────────────────────────┘
```

The boundary between JS and Kotlin is intentionally narrow. **All cryptographic state lives in Kotlin** — the file key, the PIN-derived key, the wrapped blobs — none of them ever cross the bridge. JS only sees booleans, strings, and (for media) `file://` URIs to temp files.

---

## The single native module

`hida-rn/modules/hida-native/` is the only Kotlin code in the project. Everything else is JS/TS.

### Public surface

The JS-facing API is declared in [`hida-rn/modules/hida-native/index.ts`](./hida-rn/modules/hida-native/index.ts). Highlights:

| Area | Functions |
|---|---|
| Vault crypto | `isVaultInitialized`, `isVaultUnlocked`, `setupVault(pin)`, `unlockVault(pin)`, `lockVault`, `changePin`, `wipeVault` |
| Decoy PIN | `saveFakePin(pin)`, `getFakePin` |
| First launch | `isFirstLaunch`, `setFirstLaunchComplete` |
| Brute force | `recordFailedAttempt`, `getFailedAttempts`, `isLockedOut`, `getRemainingLockoutTime`, `clearFailedAttempts` |
| Session | `setSessionTimeout`, `getSessionTimeout`, `markAppPaused`, `isSessionExpired`, `clearSession` |
| Biometric | `isBiometricEnabled`, `setBiometricEnabled`, `authenticateAndUnlockVault`, `authenticateAndEnrollBiometric`, `getBiometricCapability` |
| Theme | `getThemeMode`, `setThemeMode`, `getSystemNightMode` |
| Disguise | `getIconAlias`, `switchIcon(alias)`, `getDisguiseDisplayName` |
| Unlock style | `getUnlockStyle`, `setUnlockStyle` (calculator vs plain keypad) |
| Media | `importMedia(uri)`, `listMedia`, `getMediaInfo(path)`, `deleteMedia(path)`, `exportMedia(path)`, `clearAllMedia` |
| Display | `decryptThumbnail`, `getDecryptedVideoUri`, `getMotionVideoUri` |
| Doc intent | `openDocumentIntent(path)` |
| Cleanup | `cleanupTempFiles`, `deleteTempFile(path)` |
| Calculator view | `<HidaCalculatorView />` — registered via `requireNativeViewManager` |

`HidaNativeModule.requireUnlocked(label)` gates every media-touching entry point: if `CryptoManager.isVaultUnlocked()` is false, the call throws.

### Kotlin file responsibilities

- **`HidaNativeModule.kt`** — Expo module registration. Exposes every `Function(...)` and the `View(CalculatorView)`. Owns the gating helper.
- **`CalculatorView.kt`** — `ExpoView` that hosts a `ComposeView` for the calculator UI. Critical for the "blank calculator on second mount" pitfall described below.
- **`CalculatorScreen.kt`** — the Jetpack Compose calculator itself: keypad layout, long-press behavior, biometric button, theme resolution.
- **`CalcEngine.kt`** — pure-math layer for the calculator. Order of operations, parentheses, scientific functions, percent.
- **`CryptoManager.kt`** — ChaCha20-Poly1305 with 256 KB chunks. See [ENCRYPTION.md](./ENCRYPTION.md) for the format.
- **`MediaRepository.kt`** — encrypt-from-URI on import, decrypt-to-temp on view. Owns `cacheDir/decrypted/` cleanup.
- **`PreferencesManager.kt`** — `EncryptedSharedPreferences` wrapper. Keys for PIN, fake PIN, wrapped ChaCha key, session state, biometric toggle, icon alias, theme mode, unlock style.
- **`BiometricCryptoStore.kt`** — separate KeyStore entry for the biometric wrap, marked `setUserAuthenticationRequired(true)` and `setInvalidatedByBiometricEnrollment(true)`.
- **`VaultBiometricHelper.kt`** — orchestrates `BiometricPrompt` with a `CryptoObject` for the encrypt and decrypt flows.
- **`IconAliasSwitcher.kt`** — toggles the 29 manifest activity-aliases via `PackageManager.setComponentEnabledSetting`. Hard invariant: never disables `MainActivity` itself.
- **`LauncherDisguiseBranding.kt`** — syncs the recents/task screen branding to match whichever alias is currently the launcher.
- **`ScreenOffLockInstaller.kt`** — installs a `BroadcastReceiver` for `Intent.ACTION_SCREEN_OFF` when the user picks the "Lock on screen off" auto-lock option.
- **`MotionPhotoParser.kt`** — splits Samsung/Google motion-photo files into image and embedded MP4 components.
- **`AndroidBiometricCompat.kt`** — feature detection across API levels.

---

## Calculator → Vault flow

The most security-sensitive flow in the app. Read this before changing anything in `app/calculator.tsx`, `CalculatorView.kt`, or `CalculatorScreen.kt`.

```
 ┌─────────────────────┐    user types PIN, presses =
 │  /calculator        │  ─────────────────────────────────┐
 │  ────────────────   │                                   │
 │  <HidaCalculatorView│  Native event onUnlock({mode})    │
 │       onUnlock />   │                                   │
 └─────────────────────┘                                   │
            │                                              │
            │  router.replace("/gallery?mode=real|fake")   │
            ▼                                              │
 ┌─────────────────────┐                                   │
 │  /gallery           │  user taps lock button            │
 │  ────────────────   │  ─────────────────────────────────┘
 │  FlashList grid     │
 │  Lock icon button   │  router.replace("/calculator")
 └─────────────────────┘
            │
            ▼
 ┌─────────────────────┐
 │  /calculator        │  NEW <HidaCalculatorView> instance
 │  (second mount!)    │  ←── this is where the blank-screen
 │                     │     pitfall lives, see below
 └─────────────────────┘
```

### Why the second mount is hairy

Every `router.replace("/calculator")` creates a **fresh** `HidaCalculatorView` view. Fabric view recycling does not guarantee a single instance, and the previous `ComposeView`'s composition may or may not be disposed when we come back.

**The pitfall:** the new `ComposeView` inside the new `CalculatorView` does not automatically inherit `ViewTreeLifecycleOwner` or `ViewTreeSavedStateRegistryOwner` from the host Activity. Without those owners attached, Compose's `WindowRecomposer` lookup fails, the recomposer stalls, and the view renders blank at the correct size.

**The fix** (lives in `CalculatorView.onAttachedToWindow`):

1. Walk up the parent view chain with `findViewTreeLifecycleOwner` and `findViewTreeSavedStateRegistryOwner`.
2. Call `setViewTreeLifecycleOwner(...)` and `setViewTreeSavedStateRegistryOwner(...)` on **both** the `ExpoView` and the inner `ComposeView`.
3. Re-call `setContent { CalculatorScreen(...) }` as a belt-and-braces measure.
4. Guard `composeView.measure(...)` in `onMeasure` with `composeView.isAttachedToWindow`.
5. Cache `lastLayoutWidth` / `lastLayoutHeight` so a delayed measure has the right dimensions.
6. Add an `OnAttachStateChangeListener` to the inner `ComposeView` that re-triggers `measureAndLayoutComposeChild(...)` when the child finally attaches.

The fix is verified by these log lines:

```
HidaCalcView: init complete view=…
HidaCalcView: onAttachedToWindow view=… state=RESUMED
HidaCalcView: composeView attached view=… size=1440x3120
HidaCalcView: measureAndLayoutComposeChild view=… size=1440x3120
HidaCalcScreen: composition active view=…
HidaCalcScreen: content positioned size=1440x3120
```

---

## Screen routing

`hida-rn/app/` uses Expo Router (file-based). Each `.tsx` file is a route.

| Route | File | Purpose |
|---|---|---|
| `/` | `index.tsx` | Routes to `/welcome` on first launch, else `/calculator`. |
| `/welcome` | `welcome.tsx` | Hero + PIN-setup (4–10 digits) + optional biometric enrollment. |
| `/calculator` | `calculator.tsx` | Hosts `<HidaCalculatorView>`. Bridges `onUnlock` and `onRequestBiometric` events. |
| `/gallery` | `gallery.tsx` | The vault. FlashList grid, four tabs (Photos / Videos / Audio / Docs), import FAB, lock button. |
| `/image-viewer` | `image-viewer.tsx` | Black background. Pinch / pan / motion-photo playback. |
| `/video-player` | `video-player.tsx` | Black background. `expo-video` over a decrypted temp URI. |
| `/audio-player` | `audio-player.tsx` | Neo-themed audio chrome with play / pause / seek. |
| `/doc-viewer` | `doc-viewer.tsx` | Inline text viewer; system intent export for binary docs. |
| `/settings` | `settings.tsx` | Security + Appearance sections; PIN change, decoy PIN, auto-lock, theme, biometric, icon disguise, vault wipe. |

### Layout wrapper order

`app/_layout.tsx` builds the root tree in a specific order — gestures need `GestureHandlerRootView` at the top, the touch-capture wrapper needs to sit *between* it and `SafeAreaProvider`, and `ThemeProvider` wraps the JS surface to expose the theme to every screen:

```
GestureHandlerRootView
  └── View (touch capture for foreground idle timer)
        └── SafeAreaProvider
              └── ThemeProvider
                    └── Stack (expo-router)
```

The touch-capture `<View>` uses `onStartShouldSetResponderCapture` and `onMoveShouldSetResponderCapture` to observe input without claiming the responder — every touch resets the foreground idle timer. The captures always return `false` so they don't break gestures inside screens.

### Foreground idle timer

`_layout.tsx` arms a `setTimeout` on vault routes (`/gallery`, `/settings`, viewers) that re-arms on every user touch. On fire it runs:

```
cleanupTempFiles() → clearSession() → router.replace("/calculator")
```

Skipped when `getSessionTimeout()` ≤ 0 (the "Never" case).

---

## Theme system

`hida-rn/lib/theme.tsx` exposes a `ThemeProvider` with light and dark palettes. The mode is:

1. Read natively before the first JS frame via `getThemeMode()` / `getSystemNightMode()` so the status bar and Stack backgrounds show the correct palette immediately (no light flash on a dark-themed device).
2. Persisted in `EncryptedSharedPreferences` via `setThemeMode(mode)`.
3. Available to every component via `useTheme()`.

Settings exposes Light / Dark / System. Tokens live in [`hida-rn/tailwind.config.js`](./hida-rn/tailwind.config.js) and [`hida-rn/lib/constants.ts`](./hida-rn/lib/constants.ts).

The native calculator (`CalculatorScreen.kt`) gets the resolved mode via the `themeResolved` prop on `<HidaCalculatorView>`.

---

## Launcher disguise

The Android manifest declares one always-enabled default alias (`HidaDefaultAlias`) and 28 disabled aliases that look like other apps:

```
Calculator (3 variants: stock / Material / iOS), Weather, Notes,
Clock, Music, Calendar, Mail, Browser, Camera, Maps, Phone,
Contacts, Messages, Play Store, Drive, Files, Wallet, Banking,
Shopping, Videos, Chat, Photos, Settings, News, Fitness, Translate
```

The full list is enumerated as `LAUNCHER_ALIASES` in [`IconAliasSwitcher.kt`](./hida-rn/modules/hida-native/android/src/main/java/app/hida/vault/nativemodule/IconAliasSwitcher.kt).

`switchIcon(newAlias)` toggles them via `PackageManager.setComponentEnabledSetting`. The **invariant**: `MainActivity` itself must never be disabled — it is the underlying Activity that every alias resolves to. A previous bug accidentally disabled `MainActivity` as the "previous icon component", which broke `ActivityInfo` lookups used by `BiometricPrompt` and Compose and manifested as a blank calculator on lock/biometric re-mount. The current `switchIcon` protects `MainActivity` explicitly, and `MainActivity.onCreate` re-enables itself defensively on every launch as a self-heal for users who hit the old bug.

The adaptive icon (`mipmap-anydpi-v26/ic_launcher.xml`) points at a cream-tile background and an ink-glyph foreground. Cold launch should flash **cream**, not dark, because `splashscreen_background` in `colors.xml` is `#F5F1EA`.

---

## Native lifecycle pitfalls

### 1. Blank calculator on second mount

Fixed; see [Calculator → Vault flow](#calculator--vault-flow). The fix is in `CalculatorView.onAttachedToWindow`. Do not remove the explicit `setViewTreeLifecycleOwner` / `setViewTreeSavedStateRegistryOwner` calls.

### 2. `FLAG_SECURE` and `adb screencap`

Every vault Activity sets `FLAG_SECURE` in `MainActivity.onCreate`, so `adb screencap` returns solid black. A black screencap is **not** evidence of a blank UI — get a phone photo or trust the logs.

### 3. Fabric view recycling

Never assume a `CalculatorView` is single-instance. Every `router.replace("/calculator")` creates a new one. The detach side may or may not dispose the previous Compose composition.

### 4. `useAudioPlayer` lifecycle

`useAudioPlayer(audioUri ?? "")` called with a string that flips from `""` → real URI on the same render creates a new player instance and releases the previous one mid-frame, producing `"Cannot use shared object that was already released"`. The fix in `app/audio-player.tsx`:

```ts
const player = useAudioPlayer(null);
useEffect(() => {
  if (audioUri) player.replace({ uri: audioUri });
}, [audioUri, player]);
const status = useAudioPlayerStatus(player);
```

Do not call `useAudioPlayer(audioUri)` directly.

### 5. Auto-Lock "Never" was locking immediately

Fixed; `PreferencesManager.kt::isSessionExpired()` returns `false` when `timeout == 0L`.

### 6. expo-video picture-in-picture warning

Silenced by `["expo-video", { "supportsPictureInPicture": false }]` in `app.json`. Leave it.

### 7. Metro must be running for any dev-client launch

The debug APK ships a dev-client (not a stock Expo Go). For any launch — first or otherwise — Metro must be reachable. If users see "Unable to load script": start Metro, then `adb reverse tcp:8081 tcp:8081`. Release builds embed the JS bundle and do not need Metro.

---

## Encryption invariants

Repeating the load-bearing ones here for visibility — the full spec is in [ENCRYPTION.md](./ENCRYPTION.md):

- `CHUNK_SIZE = 256 * 1024`, `NONCE_SIZE = 12`, `AUTH_TAG_SIZE = 16`, `KEY_SIZE = 32`. Changing these breaks every existing vault.
- `MediaRepository.encryptFromStream` reads with a **fill loop**, not a single `InputStream.read(buffer)`.
- Keys never cross the JS bridge.
- `EncryptedSharedPreferences` is the only persistent KV store. No plain `SharedPreferences` for secrets.

---

## Where to start reading the code

If you're new to the codebase, read in this order:

1. [`hida-rn/modules/hida-native/index.ts`](./hida-rn/modules/hida-native/index.ts) — the JS API surface.
2. [`hida-rn/modules/hida-native/android/src/main/java/app/hida/vault/nativemodule/HidaNativeModule.kt`](./hida-rn/modules/hida-native/android/src/main/java/app/hida/vault/nativemodule/HidaNativeModule.kt) — Kotlin entry.
3. [`hida-rn/app/_layout.tsx`](./hida-rn/app/_layout.tsx) — root wrapper, session timer, theme.
4. [`hida-rn/app/calculator.tsx`](./hida-rn/app/calculator.tsx) — unlock entry.
5. [`hida-rn/app/gallery.tsx`](./hida-rn/app/gallery.tsx) — main vault UI.
6. [`CryptoManager.kt`](./hida-rn/modules/hida-native/android/src/main/java/app/hida/vault/nativemodule/CryptoManager.kt) — the cipher.
7. [`CalculatorView.kt`](./hida-rn/modules/hida-native/android/src/main/java/app/hida/vault/nativemodule/CalculatorView.kt) — the lifecycle dance.

---

[← Back to README](./README.md) · [Encryption →](./ENCRYPTION.md) · [Design System →](./DESIGN.md)
