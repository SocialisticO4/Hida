# Encryption — Hida

This document is the canonical reference for how Hida encrypts media on disk, how it derives and stores its keys, and what it does and does not protect against.

For the project overview and build instructions, see [README.md](./README.md). For the architectural context (where the cipher lives in the module graph), see [ARCHITECTURE.md](./ARCHITECTURE.md).

---

## TL;DR

- **Cipher:** ChaCha20-Poly1305 (AEAD), 256-bit key, 96-bit nonce.
- **Chunking:** plaintext split into 256 KB chunks; each chunk encrypted independently with a 16-byte Poly1305 tag.
- **Master nonce** written once at the head of every file; per-chunk nonce derived deterministically (XOR with chunk index in the last 8 bytes).
- **File key** (ChaCha20 key) is randomly generated per install, then wrapped under an **Android KeyStore** master key (AES/GCM, hardware-backed when available).
- **PIN binding:** the wrapped key blob is itself encrypted with a PBKDF2-derived key from the user's PIN. The KeyStore wrap is the outer envelope; the PIN wrap is the inner envelope. Both must be unwrapped to use the cipher.
- **Biometric unlock** (optional) gates the unwrap behind a `BiometricPrompt` + `CryptoObject` — the cipher is only authorized after the user passes a fingerprint check.
- **No network.** The app's manifest does not request `android.permission.INTERNET`.

---

## On-disk format

Every encrypted file in the vault has the following layout:

```
┌──────────────────────────────────────────────────────────┐
│  Bytes  0   ..  11    │  master nonce  (12 bytes)         │
├───────────────────────┴──────────────────────────────────┤
│  Bytes 12   ..  12+N₀ │  chunk 0 ciphertext + 16-byte tag │
├──────────────────────────────────────────────────────────┤
│           ...           │  chunk 1, 2, ...                │
├──────────────────────────────────────────────────────────┤
│           ...           │  chunk k-1 (last, may be short) │
└──────────────────────────────────────────────────────────┘
```

- **Master nonce** — 12 random bytes generated when the file is first written. Written once. Never reused for another file.
- **Plaintext chunk size** — exactly **256 KB** (`262 144 bytes`), except the final chunk which may be shorter (down to 1 byte).
- **Ciphertext chunk size** — 256 KB + 16-byte Poly1305 tag (`262 160 bytes`), except the final chunk which is `(remaining plaintext) + 16`.
- **Per-chunk nonce** — derived from the master nonce and chunk index `i`:

  ```
  perChunkNonce[0..3]  = masterNonce[0..3]
  perChunkNonce[4..11] = masterNonce[4..11] XOR bigEndianInt64(i)
  ```

  This guarantees a unique 96-bit nonce per (file, chunk) pair without writing it to disk.

### Why these choices

- **ChaCha20-Poly1305 over AES-GCM:** constant-time on devices without AES hardware (older / low-end Android), nonce-misuse failure mode is identical (must not repeat). Native to Android since API 28; polyfilled by Tink for API 26–27.
- **256 KB chunks:** big enough to amortize the AEAD overhead (16 bytes / 256 KB ≈ 0.006 %), small enough to seek into a video without decrypting hundreds of MB.
- **Per-chunk authentication:** any corruption (truncation, bit flip, swapped chunks) is detected at the chunk where it happens. Decryption fails fast instead of producing garbled plaintext.
- **Nonce-by-XOR:** simpler than tracking a counter on disk, and just as safe — the master nonce is random and used only once per file, so the per-chunk nonces are unique across the entire vault.

### Format invariants

These must not be changed without a migration path:

- `CHUNK_SIZE = 256 * 1024`
- `NONCE_SIZE = 12`
- `AUTH_TAG_SIZE = 16`
- `KEY_SIZE = 32` (256-bit)
- Header layout (master nonce at byte 0).

Changing any of these silently would corrupt every existing vault.

### Streaming write correctness

`MediaRepository.encryptFromStream` reads source bytes into a 256 KB buffer **with a fill loop**, not a single `InputStream.read(buffer)`. A single `read` can short-read on slow sources (network providers, USB-OTG, SAF documents) and would produce truncated chunks. The fill loop is part of the format contract.

---

## Keys

There are three keys in play:

```
                ┌─────────────────────────────────────────┐
                │  PIN (4–10 digits)                      │
                └─────────────────────────────────────────┘
                              │  PBKDF2-HMAC-SHA256
                              │  100 000 iterations
                              │  per-install salt (16 B)
                              ▼
                ┌─────────────────────────────────────────┐
                │  PIN-derived key (32 B)                 │
                └─────────────────────────────────────────┘
                              │  AES/GCM wraps the next layer
                              ▼
                ┌─────────────────────────────────────────┐
                │  KeyStore master key (AES, 256 B)       │
                │  (hardware-backed where available)      │
                └─────────────────────────────────────────┘
                              │  AES/GCM wraps the next layer
                              ▼
                ┌─────────────────────────────────────────┐
                │  File key — ChaCha20 (32 B)             │
                │  Encrypts every media file.             │
                └─────────────────────────────────────────┘
```

### File key

A single 32-byte random key per install. Created on first-run PIN setup. All vault files are encrypted with this key. Wrapped on disk; never written in plaintext to any storage Hida controls.

### KeyStore master key

A 256-bit AES key created with `KeyGenParameterSpec` using `BLOCK_MODE_GCM`, `ENCRYPTION_PADDING_NONE`, and (on supported devices) `setIsStrongBoxBacked(true)` falling back to Trusted Execution Environment. Marked `setUserAuthenticationRequired(true)` only when the user enables biometric unlock — otherwise the key is unconditionally usable once the app process has it.

### PIN-derived key

PBKDF2-HMAC-SHA256, 100 000 iterations, 16-byte per-install salt. The salt is stored in `EncryptedSharedPreferences` (which is itself encrypted under the KeyStore master key). PBKDF2 is the inner envelope so that *even if* the KeyStore wrap is compromised (e.g. KeyStore data extracted from a rooted device), the attacker still has to brute-force the PIN — and ten-digit PINs with 100k iterations are not trivial.

### Wrap order

```
wrappedBlob = KeyStoreAES_GCM_Encrypt(
                PinDerivedAES_GCM_Encrypt(fileKey, pinDerivedKey),
                keyStoreMasterKey,
              )
```

To unlock the vault we must:
1. Authenticate with the KeyStore master key (passive — biometric only if enabled).
2. Unwrap the outer envelope.
3. Derive the PIN key with PBKDF2.
4. Unwrap the inner envelope to recover the file key.

Steps 3–4 happen on the native module's queue, not the JS thread, which is why `unlockVault(pin)` returns a `Promise<boolean>`.

---

## Biometric unlock

Optional, off by default. When enabled:

- A second wrap of the file key is created using a `KeyStore` key marked `setUserAuthenticationRequired(true)` and `setInvalidatedByBiometricEnrollment(true)`.
- The biometric wrap is created via a `BiometricPrompt` in **ENCRYPT** mode using a `CryptoObject(cipher)`.
- To unlock by biometric, Hida shows a `BiometricPrompt` in **DECRYPT** mode with the matching `CryptoObject`. The `Cipher` is only authorized by the OS *after* the user passes the fingerprint check.
- If the user re-enrolls fingerprints, `setInvalidatedByBiometricEnrollment(true)` invalidates the biometric key and the user must use their PIN to re-enable biometric unlock.

The PIN wrap and the biometric wrap are independent — disabling biometric does not affect PIN unlock.

---

## Brute-force protection

Tracked entirely in the native module (`PreferencesManager` + `CryptoManager`):

| Failed attempts | Lockout |
|---|---|
| 1–4 | none |
| 5 | 30 seconds |
| 6 | 1 minute |
| 7 | 5 minutes |
| 8 | 15 minutes |
| 9+ | 1 hour, increasing |

A successful unlock clears the counter. The lockout state is persisted, so killing and re-launching the app does not reset it. The counter and lockout-until timestamp live in `EncryptedSharedPreferences`, so even with root access the values can't be flipped to zero without the KeyStore master key.

The decoy PIN is **not** a cryptographic boundary — it is a UI-only signal that opens an empty vault view. Cryptographically, only the real PIN unwraps the file key. The decoy is plausible deniability, not security.

---

## Session / auto-lock

- The session timeout is set in Settings (off / 30 s / 1 min / 5 min / 15 min / lock on screen off).
- Background → foreground transitions check `isSessionExpired()` and route to `/calculator` if expired. The in-memory file key is wiped (`lockVault()`) before navigation.
- Foreground idle timer: `_layout.tsx` arms a `setTimeout` while on vault routes and resets it on user touch. On fire, it runs `cleanupTempFiles()` → `clearSession()` → `router.replace("/calculator")`.
- "Never" (timeout = 0) disables both background-expiry and foreground-idle locking. The vault stays unlocked until the user explicitly locks or kills the app.
- "Lock on screen off" (-1) installs a `BroadcastReceiver` for `Intent.ACTION_SCREEN_OFF` which locks immediately when the device sleeps.

---

## Plaintext temp files (decrypted media)

Photos, videos, audio, and documents need plaintext to play. Hida decrypts on demand into `context.cacheDir/decrypted/` and returns a `file://` URI to the viewer. Cleanup happens at three points:

1. **On viewer unmount** — viewer screens call `deleteTempFile(path)` in their cleanup effect.
2. **On lock** — `cleanupTempFiles()` wipes the entire `decrypted/` directory.
3. **On `MediaRepository.init`** — defensive cleanup in case the process was killed before step 1 or 2.

Temp files have `deleteOnExit()` set. The Android `cacheDir` is excluded from `adb backup` and from cloud backup unless the user has explicitly enabled `android:allowBackup`. Hida sets `android:allowBackup="false"` and provides `secure_store_backup_rules.xml` + `secure_store_data_extraction_rules.xml` (referenced by the manifest's `android:fullBackupContent` and `android:dataExtractionRules`) to deny `cloud-backup` and `device-transfer`.

`FLAG_SECURE` is set on the Activity in `MainActivity.onCreate`, so neither the recents-task screenshot nor `adb screencap` capture the screen contents — both return solid black.

---

## Threat model

### What Hida defends against

- **Stolen device, no PIN, no biometric.** The wrapped file key is bound to the KeyStore master key (and PIN); a `dd` of the data partition is opaque without the PIN.
- **Casual phone borrowing.** The launcher icon disguises ("Weather", "Notes", "Phone", etc.) plus the calculator UI mean a casual observer doesn't know there's a vault. Real calculator math works.
- **Screenshot / screen-record exfiltration of an unlocked session.** `FLAG_SECURE` neuters both.
- **Cloud backup / device transfer.** Disabled in the manifest.
- **PIN guessing.** Exponential lockout after 5 wrong attempts. 100 000 PBKDF2 iterations.

### What Hida does *not* defend against

- **Unlocked device, unlocked vault.** If you hand the phone to someone with the vault open, they can scroll the gallery. Set Auto-Lock short.
- **Rooted device, attacker with full FS access, *and* knowledge of your PIN.** This is the threshold above which any phone-side encryption is meaningless.
- **Coercion.** The decoy vault is plausible deniability, not a defense against a determined adversary who can see process state, network traffic (none, but still), or compel you to type the real PIN.
- **Side channels.** Hida does not specifically defend against timing attacks on PIN entry, RAM-cold-boot key extraction, or hardware-fault injection against the KeyStore TEE.
- **Existence of the app.** A user inspecting `pm list packages` (with USB debugging) sees `app.hida.vault`. The disguise is on the *launcher icon*, not at the OS level.
- **Targeted malware running as another app.** Hida assumes the OS sandbox is honest.

### Out of scope

- Sharing files between devices.
- Server-side anything.
- Identity. Hida has no notion of "user account" — there is only the on-device PIN.

---

## Vault wipe

`wipeVault()` is destructive and irrecoverable. It:

1. Deletes every encrypted file in the vault directory.
2. Zeros and removes the PIN salt, wrapped key blob, biometric wrap, fake PIN, failed-attempt counter, session prefs.
3. Removes the KeyStore master key entry.
4. Marks `isFirstLaunch = true` so the next launch re-onboards.

There is no "soft delete" or recoverable state. By design, an attacker who triggers `wipeVault()` (via Settings → Reset Vault, which requires the PIN to reach) destroys all data immediately.

---

## Auditing

The full cipher implementation lives in:

- [`hida-rn/modules/hida-native/android/src/main/java/app/hida/vault/nativemodule/CryptoManager.kt`](./hida-rn/modules/hida-native/android/src/main/java/app/hida/vault/nativemodule/CryptoManager.kt) — chunked AEAD, key derivation, key wrapping.
- [`hida-rn/modules/hida-native/android/src/main/java/app/hida/vault/nativemodule/MediaRepository.kt`](./hida-rn/modules/hida-native/android/src/main/java/app/hida/vault/nativemodule/MediaRepository.kt) — streaming encrypt/decrypt, temp-file lifecycle.
- [`hida-rn/modules/hida-native/android/src/main/java/app/hida/vault/nativemodule/PreferencesManager.kt`](./hida-rn/modules/hida-native/android/src/main/java/app/hida/vault/nativemodule/PreferencesManager.kt) — `EncryptedSharedPreferences`, session state.
- [`hida-rn/modules/hida-native/android/src/main/java/app/hida/vault/nativemodule/BiometricCryptoStore.kt`](./hida-rn/modules/hida-native/android/src/main/java/app/hida/vault/nativemodule/BiometricCryptoStore.kt) — biometric wrap.

Issues and questions: please open a GitHub issue.

---

[← Back to README](./README.md) · [Architecture →](./ARCHITECTURE.md) · [Design System →](./DESIGN.md)
