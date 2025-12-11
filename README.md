# Hida - Secure Media Vault

<p align="center">
  <a href="https://github.com/SocialisticO4/Hida">
    <img src="app/src/main/res/drawable/lock_6065983.png" alt="Hida Logo" width="120"/>
  </a>
</p>

<p align="center">
  <strong>A secure, encrypted media vault disguised as a calculator</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-14%2B-green?style=flat-square&logo=android" alt="Android 14+"/>
  <img src="https://img.shields.io/badge/Kotlin-1.9-purple?style=flat-square&logo=kotlin" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-blue?style=flat-square" alt="Compose"/>
  <img src="https://img.shields.io/badge/Encryption-ChaCha20--Poly1305-red?style=flat-square" alt="ChaCha20"/>
</p>

---

## 📱 Features

### 🔐 Security
- **Calculator Disguise** - App appears as a fully functional calculator
- **Dual PIN System** - Real PIN unlocks vault, Decoy PIN shows empty vault
- **Military-Grade Encryption** - ChaCha20-Poly1305 AEAD for all files
- **Brute Force Protection** - Exponential lockout after failed attempts
- **Auto-Lock** - Configurable timeout when app goes to background
- **No Cloud** - All data stored locally, never leaves device

### 📸 Media Support
- **Photos** - Full resolution, encrypted thumbnails
- **Videos** - Instant streaming playback with seeking (no decryption wait)
- **Motion Photos** - Samsung/Google motion photo support
- **Large Files** - Streaming encryption handles 4K HDR videos (2GB+)

### 🎨 Design
- **Material 3 Expressive** - M3 Dynamic Color theming
- **Dark Mode** - Full dark theme throughout
- **Smooth Animations** - Spring physics, haptic feedback
- **17+ App Icons** - Disguise as Calculator, Weather, Notes, Clock, etc.

---

## 🏗️ Technical Architecture

### Encryption System

```
┌─────────────────────────────────────────────────────────────┐
│                    ENCRYPTION FLOW                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────┐    ┌──────────────┐    ┌─────────────────┐   │
│  │ Plaintext │───▶│ 256KB Chunks │───▶│ ChaCha20-Poly1305│   │
│  │   File    │    │              │    │   Per Chunk     │   │
│  └──────────┘    └──────────────┘    └─────────────────┘   │
│                                              │              │
│                                              ▼              │
│                    ┌─────────────────────────────────┐      │
│                    │  [12B Nonce][Chunk1+Tag][Chunk2+Tag]│  │
│                    │        Encrypted File Format        │  │
│                    └─────────────────────────────────┘      │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

#### Chunked Encryption Details

| Component | Size | Description |
|-----------|------|-------------|
| **Algorithm** | - | ChaCha20-Poly1305 (AEAD) |
| **Chunk Size** | 256KB | Optimal for high-bitrate video |
| **Auth Tag** | 16 bytes | Poly1305 MAC per chunk |
| **Master Nonce** | 12 bytes | Stored at file start |
| **Chunk Nonce** | 12 bytes | `master_nonce XOR chunk_index` |

#### Key Management

```kotlin
// Master key wrapped in Android KeyStore
KeyStore (AES-256-GCM) 
    └── Wrapped ChaCha20 Key (Base64 in EncryptedSharedPreferences)
            └── Used for all file encryption
```

- **Android KeyStore** hardware-backed (if available)
- **EncryptedSharedPreferences** for secure storage
- **Key generated once** on first launch, persists across app updates

### Video Streaming Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    VIDEO PLAYBACK                           │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ExoPlayer ─────▶ EncryptedDataSource                       │
│      │                    │                                 │
│      │            ┌───────┴───────┐                         │
│      │            │  LRU Cache    │ ◄── 4 chunks = 1MB     │
│      │            │  (4 chunks)   │                         │
│      │            └───────┬───────┘                         │
│      │                    │                                 │
│      ▼                    ▼                                 │
│  Instant Play     RandomAccessFile.seek()                   │
│  + Seeking        + Decrypt chunk on-demand                 │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**Benefits:**
- **Instant playback** - No waiting for full decryption
- **O(1) seeking** - Jump to any position instantly
- **Memory efficient** - Only 1MB cache regardless of file size
- **Authenticated** - Each chunk verified independently

---

## 📁 Project Structure

```
app/src/main/java/com/example/hida/
├── data/
│   ├── CryptoManager.kt        # ChaCha20-Poly1305 encryption
│   ├── EncryptedDataSource.kt  # ExoPlayer streaming decryption
│   ├── EncryptedMediaFetcher.kt# Coil image loader integration
│   ├── MediaRepository.kt      # File management & encryption
│   ├── MotionPhotoParser.kt    # Samsung/Google motion photo support
│   └── PreferencesManager.kt   # Secure settings storage
│
├── ui/
│   ├── CalculatorScreen.kt     # Calculator + PIN entry
│   ├── GalleryScreen.kt        # Photo/Video grid with multi-select
│   ├── ImageViewerScreen.kt    # Zoomable image viewer
│   ├── VideoPlayerScreen.kt    # M3 Expressive video player
│   ├── SettingsScreen.kt       # App settings & icon changer
│   ├── WelcomeScreen.kt        # First-time setup
│   └── Navigation.kt           # Navigation graph
│
└── ui/theme/
    ├── Color.kt                # M3 color tokens
    ├── Theme.kt                # Dynamic theming
    ├── Type.kt                 # Typography
    └── Shape.kt                # Corner shapes
```

---

## 🔧 Dependencies

| Library | Version | Purpose | License |
|---------|---------|---------|---------|
| **Jetpack Compose** | BOM 2024 | UI Framework | Apache 2.0 |
| **Material 3** | 1.7.0 | Design System | Apache 2.0 |
| **Navigation Compose** | 2.8.0 | Navigation | Apache 2.0 |
| **ExoPlayer (Media3)** | 1.2.0 | Video Playback | Apache 2.0 |
| **Coil** | 2.5.0 | Image Loading | Apache 2.0 |
| **Security Crypto** | 1.1.0-alpha06 | EncryptedSharedPreferences | Apache 2.0 |
| **AndroidX Core KTX** | Latest | Kotlin Extensions | Apache 2.0 |
| **Lifecycle Runtime** | Latest | Lifecycle Management | Apache 2.0 |

---

## 🔒 Security Considerations

### What's Protected
- ✅ All photos encrypted with ChaCha20-Poly1305
- ✅ All videos encrypted with chunked ChaCha20-Poly1305
- ✅ PIN stored with EncryptedSharedPreferences
- ✅ Master encryption key wrapped in Android KeyStore
- ✅ Brute force protection with exponential backoff

### Attack Resistance
| Attack Vector | Protection |
|---------------|------------|
| **Brute Force** | Exponential lockout (5s → 10s → 20s → ... → 5min max) |
| **Memory Dump** | Keys cleared from memory after use |
| **File System Access** | All files encrypted, no plaintext |
| **Shoulder Surfing** | Calculator disguise, decoy PIN |
| **App Cloning** | KeyStore-bound keys |

### Limitations
- ⚠️ Root access can potentially extract KeyStore keys
- ⚠️ No protection against screen recording while viewing
- ⚠️ Decrypted thumbnails cached by Coil (in-memory only)

---

## 📋 Version History

### v1.0 (Current)
- Initial release
- ChaCha20-Poly1305 chunked encryption
- Calculator disguise with 17 icon options
- Dual PIN (real + decoy)
- Photo/Video support with motion photos
- M3 Expressive video player
- Multi-select with bulk delete
- Date-grouped gallery grid
- Auto-lock with configurable timeout
- Streaming video playback (no decrypt wait)

---

## 📄 Third-Party Libraries & Licenses

All third-party dependencies used in this project are licensed under the **Apache License 2.0**:

| Library | License | Link |
|---------|---------|------|
| **Jetpack Compose** | Apache 2.0 | [developer.android.com](https://developer.android.com/jetpack/compose) |
| **Material 3** | Apache 2.0 | [material.io](https://m3.material.io/) |
| **Navigation Compose** | Apache 2.0 | [AndroidX Navigation](https://developer.android.com/jetpack/compose/navigation) |
| **ExoPlayer/Media3** | Apache 2.0 | [github.com/androidx/media](https://github.com/androidx/media) |
| **Coil** | Apache 2.0 | [github.com/coil-kt/coil](https://github.com/coil-kt/coil) |
| **Security Crypto** | Apache 2.0 | [AndroidX Security](https://developer.android.com/jetpack/androidx/releases/security) |
| **AndroidX Core KTX** | Apache 2.0 | [AndroidX](https://developer.android.com/jetpack/androidx) |
| **Lifecycle Runtime** | Apache 2.0 | [AndroidX Lifecycle](https://developer.android.com/jetpack/androidx/releases/lifecycle) |

### Apache License 2.0 Summary
- ✅ Commercial use allowed
- ✅ Modification allowed
- ✅ Distribution allowed
- ✅ Private use allowed
- ⚠️ Must include license and copyright notice
- ⚠️ Must state changes made

Full Apache 2.0 license text: [apache.org/licenses/LICENSE-2.0](https://www.apache.org/licenses/LICENSE-2.0)

---

## 📞 Contact

For questions or suggestions, please open an issue on GitHub.

---

<p align="center">
  Made with ❤️ for privacy
</p>

