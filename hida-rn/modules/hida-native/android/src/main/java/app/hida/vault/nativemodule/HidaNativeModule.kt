package app.hida.vault.nativemodule

import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

class HidaNativeModule : Module() {

    private val context
        get() = requireNotNull(appContext.reactContext)

    private val prefs by lazy { PreferencesManager(context) }
    private val repository by lazy { MediaRepository(context) }
    private val cryptoManager by lazy { CryptoManager.getInstance(context) }

    /** Throws if the vault is locked, gated by every media-touching entry point. */
    private fun requireUnlocked(label: String) {
        if (!cryptoManager.isVaultUnlocked()) {
            throw IllegalStateException("Vault is locked ($label)")
        }
    }

    override fun definition() = ModuleDefinition {
        Name("HidaNative")

        // ==================== Native Calculator View ====================

        View(CalculatorView::class) {
            Events("onUnlock", "onRequestBiometric")

            Prop("biometricAvailable") { view: CalculatorView, available: Boolean ->
                view.setBiometricAvailable(available)
            }

            Prop("themeResolved") { view: CalculatorView, mode: String ->
                view.setThemeResolved(mode)
            }
        }

        // ==================== Vault crypto (PIN-bound) ====================

        Function("isVaultInitialized") {
            cryptoManager.isVaultInitialized()
        }

        Function("isVaultUnlocked") {
            cryptoManager.isVaultUnlocked()
        }

        /** True if the on-disk vault was written by a pre-pepper build. The JS app
         *  warns the user and triggers wipeVault before reaching the calculator. */
        Function("isVaultLegacyFormat") {
            cryptoManager.isVaultLegacyFormat()
        }

        /** First-run PIN setup. Generates the file key and wraps it under PBKDF2(PIN).
         *  Also persists pin length so the plain-keypad unlock UI can auto-submit
         *  without leaking length via a visible indicator. */
        Function("setupVault") { pin: String ->
            cryptoManager.setupVault(pin)
            prefs.setPinLength(pin.length)
        }

        /** Verify [pin] by attempting to unwrap the file key. Caches it on success.
         *  Backfills KEY_PIN_LENGTH on success for legacy installs that pre-date
         *  the plain-keypad feature — without this, switching such an install to
         *  keypad in Settings would render a non-responsive unlock screen.
         *
         *  AsyncFunction (not Function) so PBKDF2's 600k iterations run on the
         *  Expo module queue, NOT the JS thread. Without this the plain keypad
         *  freezes for the duration of the KDF (~300 ms on a Pixel 8, several
         *  seconds on low-end Androids) and the press-into-shadow animation
         *  stalls mid-frame before the gallery transition begins. */
        AsyncFunction("unlockVault") { pin: String ->
            val ok = cryptoManager.unlockWithPin(pin)
            if (ok && prefs.getPinLength() == 0) prefs.setPinLength(pin.length)
            ok
        }

        /** Drop the in-memory file key + wipe any plaintext temp files. */
        Function("lockVault") {
            cryptoManager.lockVault()
            repository.cleanupTempFiles()
        }

        /** Re-wrap the in-memory file key under [newPin]. Vault must be unlocked. */
        Function("changePin") { newPin: String ->
            val ok = cryptoManager.rewrapWithNewPin(newPin)
            if (ok) prefs.setPinLength(newPin.length)
            ok
        }

        Function("hasPin") {
            prefs.hasPin()
        }

        // Decoy PIN — UI-level only, not a cryptographic boundary.
        Function("saveFakePin") { pin: String ->
            prefs.saveFakePin(pin)
        }

        Function("getFakePin") {
            prefs.getFakePin()
        }

        /** Wipes PIN/key/biometric state and all encrypted media. */
        Function("wipeVault") {
            cryptoManager.lockVault()
            prefs.wipeVaultCrypto()
            repository.clearAllMedia()
        }

        // ==================== First Launch ====================

        Function("isFirstLaunch") {
            prefs.isFirstLaunch()
        }

        Function("setFirstLaunchComplete") {
            prefs.setFirstLaunchComplete()
        }

        // ==================== Brute Force Protection ====================

        Function("recordFailedAttempt") {
            prefs.recordFailedAttempt()
        }

        Function("getFailedAttempts") {
            prefs.getFailedAttempts()
        }

        Function("getRemainingLockoutTime") {
            prefs.getRemainingLockoutTime()
        }

        Function("isLockedOut") {
            prefs.isLockedOut()
        }

        Function("clearFailedAttempts") {
            prefs.clearFailedAttempts()
        }

        // ==================== Session Management ====================

        Function("setSessionTimeout") { timeoutMs: Long ->
            prefs.setSessionTimeout(timeoutMs)
        }

        Function("getSessionTimeout") {
            prefs.getSessionTimeout()
        }

        Function("markAppPaused") {
            prefs.markAppPaused()
        }

        Function("clearPausedFlag") {
            prefs.clearPausedFlag()
        }

        Function("isSessionExpired") {
            prefs.isSessionExpired()
        }

        // clearSession resets the auto-lock state machine prefs only (WAS_PAUSED,
        // LAST_ACTIVITY, LOCK_PENDING_SCREEN_OFF). It does NOT lock the vault — that
        // is a separate, explicit action via [lockVault]. Mixing the two would
        // re-lock the vault on every successful biometric/PIN unlock that calls
        // clearSession to reset the paused flag.
        Function("clearSession") {
            prefs.clearSession()
        }

        Function("setVaultUnlockedRouteActive") { active: Boolean ->
            prefs.setVaultUnlockedRouteActive(active)
        }

        // ==================== Biometric ====================

        Function("setBiometricEnabled") { enabled: Boolean ->
            prefs.setBiometricEnabled(enabled)
            if (!enabled) cryptoManager.disableBiometricUnlock()
        }

        Function("isBiometricEnabled") {
            prefs.isBiometricEnabled()
        }

        Function("disableBiometricUnlock") {
            cryptoManager.disableBiometricUnlock()
        }

        Function("isBiometricUnlockEnrolled") {
            cryptoManager.isBiometricUnlockEnrolled()
        }

        Function("isAndroidBiometricHardwareAvailable") {
            AndroidBiometricCompat.canAuthenticate(context)
        }

        /**
         * Single bridge crossing for the calculator's biometric-availability gate.
         * Replaces 4 sequential JSI hops the calculator was making per focus event,
         * shaving the back-nav slide-in transition. The JS side combines this with
         * expo-local-authentication's hasHardwareAsync / isEnrolledAsync.
         */
        Function("getBiometricCapability") {
            mapOf(
                "biometricEnabled" to prefs.isBiometricEnabled(),
                "hasPin" to prefs.hasPin(),
                "biometricUnlockEnrolled" to cryptoManager.isBiometricUnlockEnrolled(),
                "androidHardwareAvailable" to AndroidBiometricCompat.canAuthenticate(context),
            )
        }

        /**
         * Re-applies launcher-alias icon to this [android.app.Activity] logo + recents task
         * description before showing a biometric sheet (expo-local-authentication resolves the
         * default application icon unless activity branding catches up — many OEMs also key off logo).
         */
        Function("refreshActivityDisguiseBranding") {
            val act = appContext.currentActivity
            if (act != null) {
                runCatching {
                    LauncherDisguiseBranding.apply(act, PreferencesManager(act))
                }
            }
        }

        AsyncFunction("authenticateVaultBiometric") { promptMessage: String, _: String?, promise: Promise ->
            appContext.mainQueue.launch {
                VaultBiometricHelper.authenticateLikeExpoDefault(appContext, prefs, promptMessage, promise)
            }
        }

        /**
         * Calculator unlock via biometric: gates the prompt with a CryptoObject so the
         * ChaCha20 file key is unwrapped only after successful BiometricPrompt success.
         */
        AsyncFunction("authenticateAndUnlockVault") { promptMessage: String, _: String?, promise: Promise ->
            appContext.mainQueue.launch {
                VaultBiometricHelper.authenticateAndUnlock(
                    appContext, cryptoManager, prefs, promptMessage, promise
                )
            }
        }

        /**
         * Enroll biometric unlock: shows BiometricPrompt with an ENCRYPT-mode
         * CryptoObject, then on success wraps the in-memory ChaCha20 key with the
         * authorised cipher. Vault must already be unlocked.
         */
        AsyncFunction("authenticateAndEnrollBiometric") { promptMessage: String, _: String?, promise: Promise ->
            appContext.mainQueue.launch {
                VaultBiometricHelper.authenticateAndEnroll(
                    appContext, cryptoManager, prefs, promptMessage, promise
                )
            }
        }

        // ==================== Theme preference (persisted) ====================

        Function("getThemeMode") {
            prefs.getThemeMode()
        }

        Function("setThemeMode") { mode: String ->
            prefs.setThemeMode(mode)
        }

        /**
         * Synchronous read of the OS dark/light setting from the Android Configuration.
         * Used by the JS layer at boot before useColorScheme() has populated, so the
         * splash + first-frame UI render in the right palette under "system" mode.
         */
        Function("getSystemNightMode") {
            val nightFlag = context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
            when (nightFlag) {
                android.content.res.Configuration.UI_MODE_NIGHT_YES -> "dark"
                android.content.res.Configuration.UI_MODE_NIGHT_NO -> "light"
                else -> "unknown"
            }
        }

        // ==================== Unlock style (calculator vs plain keypad) ====================

        Function("getUnlockStyle") {
            prefs.getUnlockStyle()
        }

        Function("setUnlockStyle") { style: String ->
            prefs.setUnlockStyle(style)
        }

        /** Pin length, set automatically by setupVault / changePin. 0 if unset. */
        Function("getPinLength") {
            prefs.getPinLength()
        }

        // ==================== App Icon Disguise ====================

        Function("getIconAlias") {
            prefs.getIconAlias()
        }

        Function("getDisguiseDisplayNameForAlias") { alias: String ->
            LauncherDisguiseBranding.displayNameForAlias(alias)
        }

        Function("switchIcon") { newAlias: String ->
            IconAliasSwitcher.applyIconAlias(context, prefs, newAlias)
            appContext.currentActivity?.let { act ->
                runCatching { LauncherDisguiseBranding.apply(act, prefs) }
            }
        }

        // ==================== Sound Preference ====================

        Function("setSoundEnabled") { enabled: Boolean ->
            prefs.setSoundEnabled(enabled)
        }

        Function("isSoundEnabled") {
            prefs.isSoundEnabled()
        }

        // ==================== Media Operations ====================
        //
        // Every entry point that touches encrypted bytes asserts the vault is
        // unlocked. Without this, a stale JS callsite (e.g. surviving navigation
        // through a lock event) or a debug-bridge attacker could invoke decrypt /
        // delete / wipe APIs while the calculator UI shows an unbroken lock.

        AsyncFunction("importMedia") { uriString: String ->
            requireUnlocked("importMedia")
            runBlocking(Dispatchers.IO) {
                val uri = Uri.parse(uriString)
                val result = repository.saveMediaFromUri(uri)

                result.getOrNull()?.let { file ->
                    mapOf(
                        "path" to file.absolutePath,
                        "name" to file.name,
                        "ext" to file.extension,
                        "isVideo" to repository.isVideo(file),
                        "isMotionPhoto" to repository.isMotionPhoto(file)
                    )
                } ?: throw Exception("Import failed")
            }
        }

        AsyncFunction("listMedia") {
            requireUnlocked("listMedia")
            runBlocking {
                val files = repository.getMediaFiles()
                // Parallelise filename-sidecar decrypts. Sequential decryption was the
                // dominant cost of opening the gallery on vaults with many items
                // (~10ms per file × N). async {} dispatches each AES-GCM unwrap
                // concurrently on Dispatchers.IO and awaitAll() rejoins.
                val deferred = files.map { file ->
                    async(Dispatchers.IO) {
                        val originalName = repository.loadOriginalName(file)
                        mapOf(
                            "path" to file.absolutePath,
                            "name" to file.name,
                            "originalName" to originalName,
                            "ext" to file.extension,
                            "isVideo" to repository.isVideo(file),
                            "isAudio" to repository.isAudio(file),
                            "isDocument" to repository.isDocument(file),
                            "isMotionPhoto" to repository.isMotionPhoto(file),
                            "lastModified" to file.lastModified(),
                            "size" to file.length()
                        )
                    }
                }
                deferred.awaitAll()
            }
        }

        AsyncFunction("getOriginalName") { path: String ->
            requireUnlocked("getOriginalName")
            runBlocking {
                repository.loadOriginalName(File(path))
            }
        }

        /**
         * Single-file metadata lookup. Viewer screens (image / video / audio / doc)
         * previously re-ran `listMedia` just to find the one item they're showing —
         * which decrypts every filename sidecar in the vault. This returns the
         * structure for one path with one filename decrypt instead of N.
         */
        AsyncFunction("getMediaInfo") { path: String ->
            requireUnlocked("getMediaInfo")
            runBlocking {
                val file = File(path)
                if (!file.exists() || !file.isFile) {
                    null
                } else {
                    val originalName = repository.loadOriginalName(file)
                    mapOf(
                        "path" to file.absolutePath,
                        "name" to file.name,
                        "originalName" to originalName,
                        "ext" to file.extension,
                        "isVideo" to repository.isVideo(file),
                        "isAudio" to repository.isAudio(file),
                        "isDocument" to repository.isDocument(file),
                        "isMotionPhoto" to repository.isMotionPhoto(file),
                        "lastModified" to file.lastModified(),
                        "size" to file.length()
                    )
                }
            }
        }

        AsyncFunction("deleteMedia") { path: String ->
            requireUnlocked("deleteMedia")
            runBlocking {
                repository.deleteMedia(File(path))
            }
        }

        AsyncFunction("deleteMultipleMedia") { paths: List<String> ->
            requireUnlocked("deleteMultipleMedia")
            runBlocking {
                paths.forEach { path ->
                    repository.deleteMedia(File(path))
                }
            }
        }

        AsyncFunction("exportMedia") { path: String ->
            requireUnlocked("exportMedia")
            runBlocking {
                repository.exportMedia(File(path))
            }
        }

        Function("isVideo") { path: String ->
            repository.isVideo(File(path))
        }

        Function("isAudio") { path: String ->
            repository.isAudio(File(path))
        }

        Function("isDocument") { path: String ->
            repository.isDocument(File(path))
        }

        Function("isMotionPhoto") { path: String ->
            repository.isMotionPhoto(File(path))
        }

        // ==================== Decryption for Display ====================

        AsyncFunction("decryptThumbnail") { path: String ->
            requireUnlocked("decryptThumbnail")
            runBlocking {
                val file = File(path)
                if (repository.isVideo(file)) {
                    val jpeg = repository.generateEncryptedVideoThumbnailJpeg(file)
                        ?: throw Exception("Video thumbnail unavailable")
                    jpeg.absolutePath
                } else {
                    val ext = ".${file.extension}"
                    val tempFile = repository.decryptToTempFile(file, ext)
                        ?: throw Exception("Decryption failed")
                    tempFile.absolutePath
                }
            }
        }

        AsyncFunction("getDecryptedVideoUri") { path: String ->
            requireUnlocked("getDecryptedVideoUri")
            runBlocking {
                val file = File(path)
                val ext = ".${file.extension}"
                val tempFile = repository.decryptToTempFile(file, ext)
                    ?: throw Exception("Decryption failed")
                "file://${tempFile.absolutePath}"
            }
        }

        AsyncFunction("getMotionVideoUri") { path: String ->
            requireUnlocked("getMotionVideoUri")
            runBlocking {
                val file = File(path)
                val tempFile = repository.getMotionPhotoVideoTempFile(file)
                    ?: throw Exception("Motion video extraction failed")
                "file://${tempFile.absolutePath}"
            }
        }

        // ==================== Document Intent ====================

        // Open a decrypted temp file with the system's default viewer via FileProvider.
        // Uses a content:// URI so Android API 24+ won't throw FileUriExposedException.
        AsyncFunction("openDocumentIntent") { path: String ->
            requireUnlocked("openDocumentIntent")
            val file = File(path)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val ext = file.extension.lowercase()
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, file.name).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }

        // ==================== Cleanup ====================

        Function("cleanupTempFiles") {
            repository.cleanupTempFiles()
        }

        /**
         * Delete a single decrypted temp file by absolute path. Restricted to
         * `cacheDir/decrypted/`; any path outside is silently ignored. Called
         * from viewer screens on unmount so plaintext bytes don't survive past
         * the moment the user navigates away.
         */
        Function("deleteTempFile") { path: String ->
            try {
                val target = File(path).canonicalFile
                val allowedRoot = File(context.cacheDir, "decrypted").canonicalFile
                if (target.path.startsWith(allowedRoot.path + File.separator) && target.isFile) {
                    target.delete()
                }
            } catch (_: Exception) {
                // Path traversal attempt or stat failure — drop silently.
            }
        }

        Function("clearAllMedia") {
            requireUnlocked("clearAllMedia")
            repository.clearAllMedia()
        }
    }
}
