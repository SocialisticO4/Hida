import { requireNativeModule, requireNativeViewManager } from "expo-modules-core";

// ==================== Types ====================

export interface MediaFile {
  path: string;
  name: string;
  /** Display name from the source URI when imported (null for files imported before this was tracked). */
  originalName: string | null;
  ext: string;
  isVideo: boolean;
  isAudio: boolean;
  isDocument: boolean;
  isMotionPhoto: boolean;
  lastModified: number;
  size: number;
}

export interface ImportResult {
  path: string;
  name: string;
  ext: string;
  isVideo: boolean;
  isMotionPhoto: boolean;
}

// ==================== Native Module ====================

const HidaNative = requireNativeModule("HidaNative");

// ==================== Vault crypto (PIN-bound) ====================

/** True after the user has completed first-run PIN setup. */
export function isVaultInitialized(): boolean {
  return HidaNative.isVaultInitialized();
}

/** True only when the in-memory ChaCha20 key is loaded (PIN entered this session). */
export function isVaultUnlocked(): boolean {
  return HidaNative.isVaultUnlocked();
}

/** True if the on-disk vault was written by a pre-pepper build. JS shows a warning
 *  and calls wipeVault() before re-onboarding the user — there is no in-place migration. */
export function isVaultLegacyFormat(): boolean {
  return HidaNative.isVaultLegacyFormat();
}

/** First-run setup. Generates the file key and wraps it under PBKDF2(PIN). */
export function setupVault(pin: string): void {
  return HidaNative.setupVault(pin);
}

/** Returns true on correct PIN (file key unwrapped + cached); false otherwise.
 *  Async because PBKDF2 runs on the native module queue, not the JS thread. */
export async function unlockVault(pin: string): Promise<boolean> {
  return HidaNative.unlockVault(pin);
}

export function lockVault(): void {
  return HidaNative.lockVault();
}

/** Re-wraps the in-memory file key under [newPin]. Vault must be unlocked. */
export function changePin(newPin: string): boolean {
  return HidaNative.changePin(newPin);
}

export function hasPin(): boolean {
  return HidaNative.hasPin();
}

/**
 * Wipes PIN, salt, wrapped key, biometric wrap, and all encrypted media files.
 * Used by first-launch onboarding rerun, and by Settings → "Reset vault".
 */
export function wipeVault(): void {
  return HidaNative.wipeVault();
}

// Decoy PIN — UI-only, not a cryptographic boundary (see app/gallery.tsx).
export function saveFakePin(pin: string): void {
  return HidaNative.saveFakePin(pin);
}

export function getFakePin(): string {
  return HidaNative.getFakePin();
}

// ==================== First Launch ====================

export function isFirstLaunch(): boolean {
  return HidaNative.isFirstLaunch();
}

export function setFirstLaunchComplete(): void {
  return HidaNative.setFirstLaunchComplete();
}

// ==================== Brute Force Protection ====================

export function recordFailedAttempt(): number {
  return HidaNative.recordFailedAttempt();
}

export function getFailedAttempts(): number {
  return HidaNative.getFailedAttempts();
}

export function getRemainingLockoutTime(): number {
  return HidaNative.getRemainingLockoutTime();
}

export function isLockedOut(): boolean {
  return HidaNative.isLockedOut();
}

export function clearFailedAttempts(): void {
  return HidaNative.clearFailedAttempts();
}

// ==================== Session Management ====================

/** Persisted sentinel for auto-lock timing: expire when screen turns off (device sleep / keyguard). */
export const SESSION_TIMEOUT_LOCK_ON_DEVICE = -1;

export function setSessionTimeout(timeoutMs: number): void {
  return HidaNative.setSessionTimeout(timeoutMs);
}

export function getSessionTimeout(): number {
  return HidaNative.getSessionTimeout();
}

export function markAppPaused(): void {
  return HidaNative.markAppPaused();
}

export function clearPausedFlag(): void {
  return HidaNative.clearPausedFlag();
}

export function isSessionExpired(): boolean {
  return HidaNative.isSessionExpired();
}

export function clearSession(): void {
  return HidaNative.clearSession();
}

export function setVaultUnlockedRouteActive(active: boolean): void {
  return HidaNative.setVaultUnlockedRouteActive(active);
}

// ==================== Biometric ====================

export function setBiometricEnabled(enabled: boolean): void {
  return HidaNative.setBiometricEnabled(enabled);
}

export function isBiometricEnabled(): boolean {
  return HidaNative.isBiometricEnabled();
}

export function disableBiometricUnlock(): void {
  return HidaNative.disableBiometricUnlock();
}

export function isBiometricUnlockEnrolled(): boolean {
  return HidaNative.isBiometricUnlockEnrolled();
}

function isStoredThemeMode(m: string): m is "light" | "dark" | "system" {
  return m === "light" || m === "dark" || m === "system";
}

export function getThemeMode(): "light" | "dark" | "system" {
  try {
    const m = HidaNative.getThemeMode() as string;
    if (isStoredThemeMode(m)) return m;
  } catch {
    /* no-op */
  }
  return "system";
}

export function setThemeMode(mode: "light" | "dark" | "system"): void {
  return HidaNative.setThemeMode(mode);
}

// ==================== Unlock style (calculator vs plain keypad) ====================

export type UnlockStyle = "calculator" | "keypad";

function isUnlockStyle(s: string): s is UnlockStyle {
  return s === "calculator" || s === "keypad";
}

export function getUnlockStyle(): UnlockStyle {
  try {
    const s = HidaNative.getUnlockStyle() as string;
    if (isUnlockStyle(s)) return s;
  } catch {
    /* no-op */
  }
  return "calculator";
}

export function setUnlockStyle(style: UnlockStyle): void {
  return HidaNative.setUnlockStyle(style);
}

/** PIN length persisted by setupVault / changePin. 0 if not yet set. */
export function getPinLength(): number {
  try {
    const n = HidaNative.getPinLength() as number;
    return typeof n === "number" && n > 0 ? n : 0;
  } catch {
    return 0;
  }
}

/** Synchronous read of Android Configuration.UI_MODE_NIGHT_*; "unknown" on legacy / pre-init. */
export function getSystemNightMode(): "light" | "dark" | "unknown" {
  try {
    const v = HidaNative.getSystemNightMode() as string;
    if (v === "light" || v === "dark") return v;
  } catch {
    /* native not ready */
  }
  return "unknown";
}

/** Android: whether class 2/3 biometrics can be used (OEM-accurate). */
export function isAndroidBiometricHardwareAvailable(): boolean {
  return HidaNative.isAndroidBiometricHardwareAvailable();
}

export interface BiometricCapability {
  biometricEnabled: boolean;
  hasPin: boolean;
  biometricUnlockEnrolled: boolean;
  androidHardwareAvailable: boolean;
}

/** Single bridge crossing returning every flag the calculator needs to decide whether
 *  to show the biometric button. Replaces 4 sequential JSI hops on every focus event. */
export function getBiometricCapability(): BiometricCapability {
  return HidaNative.getBiometricCapability() as BiometricCapability;
}

export type VaultBiometricResult =
  | { success: true }
  | { success: false; error: string; warning?: string };

function vaultBiometricResultFromNative(bundle: Record<string, unknown>): VaultBiometricResult {
  if (bundle.success === true) {
    return { success: true };
  }
  return {
    success: false,
    error: typeof bundle.error === "string" ? bundle.error : "unknown",
    warning: typeof bundle.warning === "string" ? bundle.warning : undefined,
  };
}

/** Android: BiometricPrompt with disguise icon (best-effort) + subtitle aligned to launcher disguise.
 *  Plain "did the user pass biometric" check; does NOT unwrap the vault key. */
export async function authenticateVaultBiometric(
  promptMessage: string,
  cancelLabel?: string | null,
): Promise<VaultBiometricResult> {
  const bundle = await HidaNative.authenticateVaultBiometric(
    promptMessage,
    cancelLabel ?? null,
  );
  return vaultBiometricResultFromNative(bundle as Record<string, unknown>);
}

/** Calculator-screen biometric unlock: gates the prompt with a CryptoObject so the
 *  ChaCha20 file key is unwrapped only after BiometricPrompt success. */
export async function authenticateAndUnlockVault(
  promptMessage: string,
  cancelLabel?: string | null,
): Promise<VaultBiometricResult> {
  const bundle = await HidaNative.authenticateAndUnlockVault(
    promptMessage,
    cancelLabel ?? null,
  );
  return vaultBiometricResultFromNative(bundle as Record<string, unknown>);
}

/** Enroll biometric unlock: BiometricPrompt with ENCRYPT-mode CryptoObject, then wrap the
 *  in-memory ChaCha20 key with the authorised cipher. Vault must already be unlocked. */
export async function authenticateAndEnrollBiometric(
  promptMessage: string,
  cancelLabel?: string | null,
): Promise<VaultBiometricResult> {
  const bundle = await HidaNative.authenticateAndEnrollBiometric(
    promptMessage,
    cancelLabel ?? null,
  );
  return vaultBiometricResultFromNative(bundle as Record<string, unknown>);
}

/** Android: sync recents/task branding from persisted disguise (also called internally before vault biometric). */
export function refreshActivityDisguiseBranding(): void {
  return HidaNative.refreshActivityDisguiseBranding();
}

// ==================== App Icon Disguise ====================

export function getIconAlias(): string {
  return HidaNative.getIconAlias();
}

/** Launcher-alias label matching AndroidManifest activity-alias android:label (via native map). */
export function getDisguiseDisplayNameForAlias(alias: string): string {
  return HidaNative.getDisguiseDisplayNameForAlias(alias);
}

export function getDisguiseDisplayName(): string {
  return getDisguiseDisplayNameForAlias(getIconAlias());
}

export function switchIcon(newAlias: string): void {
  return HidaNative.switchIcon(newAlias);
}

// ==================== Sound ====================

export function setSoundEnabled(enabled: boolean): void {
  return HidaNative.setSoundEnabled(enabled);
}

export function isSoundEnabled(): boolean {
  return HidaNative.isSoundEnabled();
}

// ==================== Media Operations ====================

export async function importMedia(uri: string): Promise<ImportResult> {
  return HidaNative.importMedia(uri);
}

export async function listMedia(): Promise<MediaFile[]> {
  return HidaNative.listMedia();
}

export async function getOriginalName(path: string): Promise<string | null> {
  return HidaNative.getOriginalName(path);
}

/** Single-item metadata lookup. Returns the full MediaFile struct for one path
 *  without enumerating the rest of the vault — used by viewer screens. */
export async function getMediaInfo(path: string): Promise<MediaFile | null> {
  return HidaNative.getMediaInfo(path);
}

export async function deleteMedia(path: string): Promise<void> {
  return HidaNative.deleteMedia(path);
}

export async function deleteMultipleMedia(paths: string[]): Promise<void> {
  return HidaNative.deleteMultipleMedia(paths);
}

export async function exportMedia(path: string): Promise<boolean> {
  return HidaNative.exportMedia(path);
}

export function isVideo(path: string): boolean {
  return HidaNative.isVideo(path);
}

export function isAudio(path: string): boolean {
  return HidaNative.isAudio(path);
}

export function isDocument(path: string): boolean {
  return HidaNative.isDocument(path);
}

export function isMotionPhoto(path: string): boolean {
  return HidaNative.isMotionPhoto(path);
}

// ==================== Decryption for Display ====================

export async function decryptThumbnail(path: string): Promise<string> {
  return HidaNative.decryptThumbnail(path);
}

export async function getDecryptedVideoUri(path: string): Promise<string> {
  return HidaNative.getDecryptedVideoUri(path);
}

export async function getMotionVideoUri(path: string): Promise<string> {
  return HidaNative.getMotionVideoUri(path);
}

// ==================== Document Intent ====================

export async function openDocumentIntent(path: string): Promise<void> {
  return HidaNative.openDocumentIntent(path);
}

// ==================== Cleanup ====================

export function cleanupTempFiles(): void {
  return HidaNative.cleanupTempFiles();
}

/** Delete a single decrypted temp file (restricted to cacheDir/decrypted/ on native side).
 *  Call from viewer screens on unmount so plaintext bytes don't survive past navigation. */
export function deleteTempFile(path: string): void {
  return HidaNative.deleteTempFile(path);
}

export function clearAllMedia(): void {
  return HidaNative.clearAllMedia();
}

// ==================== Native Calculator View ====================

export const HidaCalculatorView = requireNativeViewManager("HidaNative");
