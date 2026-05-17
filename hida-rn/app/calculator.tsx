import { useState, useEffect, useCallback, useRef } from "react";
import { Alert, View, Platform } from "react-native";
import { useRouter, useFocusEffect } from "expo-router";
import * as LocalAuthentication from "expo-local-authentication";
import {
  authenticateAndUnlockVault,
  HidaCalculatorView,
} from "../modules/hida-native";
import * as HidaNative from "../modules/hida-native";
import { useTheme } from "../lib/theme";
import { PlainKeypad } from "../components/ui/PlainKeypad";
import {
  playSoundEffect,
  primeSoundEffects,
  unloadSoundEffects,
} from "../lib/soundEffects";

type UnlockMode = "real" | "fake";

function isUnlockMode(mode: unknown): mode is UnlockMode {
  return mode === "real" || mode === "fake";
}

export default function CalculatorScreen() {
  const { resolved } = useTheme();
  const router = useRouter();
  const [biometricAvailable, setBiometricAvailable] = useState(false);
  const isAuthenticatingRef = useRef(false);
  const isNavigatingRef = useRef(false);

  // Read once per mount. Settings can change this and bounces the user back to
  // /calculator on save, so this mount-time read is sufficient.
  const [unlockStyle] = useState(() => HidaNative.getUnlockStyle());
  const [lockoutMs, setLockoutMs] = useState(0);

  useEffect(() => {
    isNavigatingRef.current = false;
  }, []);

  // Seed lockout once at mount so the keypad reflects any prior failed-attempt
  // state from a previous unlock screen.
  useEffect(() => {
    if (unlockStyle !== "keypad") return;
    setLockoutMs(HidaNative.getRemainingLockoutTime());
  }, [unlockStyle]);

  // Tick the lockout timer down for the plain keypad. Restarts whenever a new
  // lockout begins (handleKeypadSubmit pushes a non-zero value into lockoutMs).
  // Without this, a freshly-recorded lockout would freeze the countdown on
  // screen until the user tapped a key.
  const isInLockout = lockoutMs > 0;
  useEffect(() => {
    if (unlockStyle !== "keypad" || !isInLockout) return;
    const id = setInterval(() => {
      const remaining = HidaNative.getRemainingLockoutTime();
      setLockoutMs(remaining);
      if (remaining <= 0) clearInterval(id);
    }, 500);
    return () => clearInterval(id);
  }, [unlockStyle, isInLockout]);

  useFocusEffect(
    useCallback(() => {
      let cancelled = false;
      (async () => {
        try {
          // One bridge call instead of four — meaningfully cheaper during the
          // back-nav slide-in animation that focuses the calculator.
          const cap = HidaNative.getBiometricCapability();
          // Biometric unlock requires the biometric-bound Keystore wrap to exist.
          // If the user toggled biometric on without enrolling (or the wrap was
          // invalidated by a fingerprint change), fall back to PIN.
          if (!cap.biometricEnabled || !cap.hasPin || !cap.biometricUnlockEnrolled) {
            if (!cancelled) setBiometricAvailable(false);
            return;
          }
          const enrolled = await LocalAuthentication.isEnrolledAsync();
          let hardwareOk = await LocalAuthentication.hasHardwareAsync();
          if (Platform.OS === "android" && cap.androidHardwareAvailable) {
            hardwareOk = true;
          }
          if (!cancelled) setBiometricAvailable(hardwareOk && enrolled);
        } catch {
          if (!cancelled) setBiometricAvailable(false);
        }
      })();
      return () => {
        cancelled = true;
      };
    }, [])
  );

  useEffect(() => {
    void primeSoundEffects();
    return () => {
      void unloadSoundEffects();
    };
  }, []);

  const navigateToGallery = useCallback(
    (mode: UnlockMode) => {
      if (isNavigatingRef.current) return;
      isNavigatingRef.current = true;
      router.replace(`/gallery?mode=${mode}`);
    },
    [router]
  );

  const handleUnlock = useCallback(
    (e: { nativeEvent?: { mode?: unknown } }) => {
      const mode = e.nativeEvent?.mode;
      if (!isUnlockMode(mode)) {
        void playSoundEffect("error");
        Alert.alert("Unlock Error", "Unexpected unlock mode. Please try again.");
        return;
      }
      void playSoundEffect("unlock");
      navigateToGallery(mode);
    },
    [navigateToGallery]
  );

  // Plain-keypad submit (Enter key). The user explicitly chose to attempt this
  // PIN, so unlike auto-submit there's no need to second-guess the length:
  // lockout check → fake PIN match → real PIN unlock → record failed attempt.
  // Keep the order in lockstep with CalculatorScreen.kt so both unlock modes
  // count failures the same way.
  const handleKeypadSubmit = useCallback(
    async (pin: string) => {
      if (HidaNative.isLockedOut()) {
        setLockoutMs(HidaNative.getRemainingLockoutTime());
        void playSoundEffect("error");
        return;
      }
      const fake = HidaNative.getFakePin();
      if (fake && fake.length > 0 && pin === fake) {
        void playSoundEffect("unlock");
        navigateToGallery("fake");
        return;
      }
      let ok = false;
      try {
        ok = await HidaNative.unlockVault(pin);
      } catch {
        ok = false;
      }
      if (ok) {
        HidaNative.clearFailedAttempts();
        HidaNative.clearSession();
        void playSoundEffect("unlock");
        navigateToGallery("real");
        return;
      }
      HidaNative.recordFailedAttempt();
      const remaining = HidaNative.getRemainingLockoutTime();
      setLockoutMs(remaining);
      void playSoundEffect("error");
    },
    [navigateToGallery]
  );

  const handleBiometricRequest = useCallback(async () => {
    if (isAuthenticatingRef.current) return;
    isAuthenticatingRef.current = true;

    try {
      const hasHw = await LocalAuthentication.hasHardwareAsync();
      const enrolled = await LocalAuthentication.isEnrolledAsync();
      if (!hasHw || !enrolled) {
        setBiometricAvailable(false);
        return;
      }

      if (Platform.OS === "android") {
        // CryptoObject-backed unlock: the ChaCha20 file key is unwrapped only after
        // the BiometricPrompt succeeds. r.success means the vault is now unlocked.
        const r = await authenticateAndUnlockVault("Unlock Vault", "Cancel");
        if (r.success) {
          HidaNative.clearFailedAttempts();
          HidaNative.clearSession();
          void playSoundEffect("unlock");
          navigateToGallery("real");
          return;
        }
        if (
          r.error === "user_cancel" ||
          r.error === "system_cancel" ||
          r.error === "app_cancel"
        ) {
          return;
        }
        if (r.error === "key_invalidated" || r.error === "not_enrolled") {
          // Biometric wrap is gone (e.g. user added a new fingerprint, or never
          // enrolled biometric unlock). Fall back to PIN entry.
          setBiometricAvailable(false);
          void playSoundEffect("error");
          Alert.alert(
            "Biometric Unlock",
            "Use your PIN to unlock. Re-enable biometric in Settings.",
          );
          return;
        }
        void playSoundEffect("error");
        Alert.alert("Biometric Unlock", "Authentication failed. Please try again.");
        return;
      }

      const result = await LocalAuthentication.authenticateAsync({
        promptMessage: "Unlock Vault",
        cancelLabel: "Cancel",
      });

      if (result.success) {
        // iOS path is not currently shipped; if it ever is, this branch needs to
        // be replaced with a CryptoObject-backed iOS flow.
        HidaNative.clearFailedAttempts();
        HidaNative.clearSession();
        void playSoundEffect("unlock");
        navigateToGallery("real");
        return;
      }

      if (
        result.error === "user_cancel" ||
        result.error === "system_cancel" ||
        result.error === "app_cancel"
      ) {
        return;
      } else {
        void playSoundEffect("error");
        Alert.alert("Biometric Unlock", "Authentication failed. Please try again.");
      }
    } catch {
      void playSoundEffect("error");
      Alert.alert(
        "Biometric Unlock",
        "Unable to authenticate right now. Please try again."
      );
    } finally {
      isAuthenticatingRef.current = false;
    }
  }, [navigateToGallery]);

  if (unlockStyle === "keypad") {
    return (
      <PlainKeypad
        onSubmit={handleKeypadSubmit}
        biometricAvailable={biometricAvailable}
        onRequestBiometric={handleBiometricRequest}
        lockoutRemainingMs={lockoutMs}
      />
    );
  }

  return (
    <View style={{ flex: 1 }}>
      <HidaCalculatorView
        style={{ flex: 1 }}
        biometricAvailable={biometricAvailable}
        themeResolved={resolved}
        onUnlock={handleUnlock}
        onRequestBiometric={handleBiometricRequest}
      />
    </View>
  );
}
