import { useState, useCallback } from "react";
import { View, Text, InteractionManager, Platform } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { useRouter } from "expo-router";
import Animated, {
  FadeIn,
  SlideInRight,
  SlideOutLeft,
  useAnimatedStyle,
  useSharedValue,
  withSequence,
  withTiming,
  Easing,
} from "react-native-reanimated";
import { Lock, ArrowLeft, Delete, Fingerprint, Calculator, KeyRound } from "lucide-react-native";
import * as Haptics from "expo-haptics";
import * as LocalAuthentication from "expo-local-authentication";
import { TIMING } from "../lib/constants";
import { useTheme } from "../lib/theme";
import { NeoButton, NeoIconButton, NeoPressable, NeoSurface } from "../components/ui/neo";
import * as HidaNative from "../modules/hida-native";
import { authenticateAndEnrollBiometric } from "../modules/hida-native";

type Step = "info" | "style" | "pin" | "biometric";
type PinPhase = "create" | "confirm";
type UnlockStyleChoice = "calculator" | "keypad";

const PIN_MAX = 10;
// 6 digits = 1M combinations. With 600k PBKDF2 iterations + Keystore-bound HMAC
// pepper, an offline attacker who exfiltrates the encrypted prefs cannot
// brute-force without the device's hardware key. 4 digits was insufficient.
const PIN_MIN = 6;

function StepDots({ step, total }: { step: number; total: number }) {
  const { colors } = useTheme();
  return (
    <View style={{ flexDirection: "row", gap: 6, justifyContent: "center" }}>
      {Array.from({ length: total }).map((_, i) => (
        <View
          key={i}
          style={{
            width: i === step ? 32 : 20,
            height: 4,
            backgroundColor: i === step ? colors.ink : colors.ink3,
          }}
        />
      ))}
    </View>
  );
}

function StyleStep({
  onPick,
}: {
  onPick: (choice: UnlockStyleChoice) => void;
}) {
  const { colors } = useTheme();
  // Each option is a full-width neo card. We deliberately don't pre-select one
  // — picking is the explicit user action that advances the step.
  const Option = ({
    icon: Icon,
    title,
    body,
    onPress,
  }: {
    icon: typeof Calculator;
    title: string;
    body: string;
    onPress: () => void;
  }) => (
    <NeoPressable
      onPress={onPress}
      style={{ padding: 18, gap: 10 }}
      accessibilityLabel={title}
    >
      <View style={{ flexDirection: "row", alignItems: "center", gap: 12 }}>
        <Icon size={24} color={colors.ink} strokeWidth={2.2} />
        <Text
          style={{
            fontFamily: "BricolageGrotesque",
            fontSize: 20,
            color: colors.ink,
          }}
        >
          {title}
        </Text>
      </View>
      <Text
        style={{
          fontFamily: "SpaceGrotesk",
          fontSize: 13,
          color: colors.ink2,
          lineHeight: 18,
        }}
      >
        {body}
      </Text>
    </NeoPressable>
  );

  return (
    <Animated.View
      entering={SlideInRight.duration(TIMING.slow)}
      exiting={SlideOutLeft.duration(TIMING.slow)}
      style={{ flex: 1, paddingHorizontal: 24, paddingTop: 8, paddingBottom: 24 }}
    >
      <View style={{ alignItems: "center", marginBottom: 22, gap: 8 }}>
        <Text
          style={{
            fontFamily: "BricolageGrotesque",
            fontSize: 26,
            color: colors.ink,
            letterSpacing: -0.5,
          }}
        >
          Unlock style
        </Text>
        <Text
          style={{
            fontFamily: "SpaceGrotesk",
            fontSize: 13,
            color: colors.ink2,
            textAlign: "center",
            maxWidth: 280,
          }}
        >
          How should Hida ask for your PIN? You can change this later in Settings.
        </Text>
      </View>

      <View style={{ gap: 14 }}>
        <Option
          icon={Calculator}
          title="Calculator disguise"
          body="Hida looks like a working calculator. Type your PIN and press = to unlock."
          onPress={() => onPick("calculator")}
        />
        <Option
          icon={KeyRound}
          title="Plain keypad"
          body="A simple PIN keypad. No system keyboard (keyboards can log digits)."
          onPress={() => onPick("keypad")}
        />
      </View>
    </Animated.View>
  );
}

function InfoStep({ onNext }: { onNext: () => void }) {
  const { colors } = useTheme();
  return (
    <Animated.View
      entering={FadeIn.duration(TIMING.slow)}
      exiting={SlideOutLeft.duration(TIMING.slow)}
      style={{ flex: 1, paddingHorizontal: 28, paddingTop: 24, paddingBottom: 24 }}
    >
      <View style={{ flex: 1, alignItems: "center", justifyContent: "center", gap: 18 }}>
        <NeoSurface background={colors.surface} shadowOffset={4}>
          <View
            style={{
              width: 100,
              height: 100,
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <Lock size={48} color={colors.ink} strokeWidth={2.4} />
          </View>
        </NeoSurface>
        <Text
          style={{
            fontFamily: "BricolageGrotesque",
            fontSize: 56,
            color: colors.ink,
            letterSpacing: -1.5,
            marginTop: 6,
          }}
        >
          Hida
        </Text>
        <Text
          style={{
            fontFamily: "SpaceGrotesk",
            fontSize: 14,
            color: colors.ink2,
            textAlign: "center",
          }}
        >
          Private. Hidden. Yours.
        </Text>
      </View>
      <NeoButton label="Continue" onPress={onNext} />
    </Animated.View>
  );
}

function PinDots({ count, max = PIN_MAX }: { count: number; max?: number }) {
  const { colors } = useTheme();
  const slots = Math.min(Math.max(count, PIN_MIN), max);
  // Shrink dot + gap once we go past 6 slots so the row stays one line on
  // narrow phones; without this, 7-10 digits wrap to two rows mid-typing and
  // the keypad below jumps.
  const dense = slots > 6;
  const dotSize = dense ? 11 : 14;
  const gap = dense ? 8 : 12;
  return (
    <View
      style={{
        flexDirection: "row",
        flexWrap: "nowrap",
        justifyContent: "center",
        alignItems: "center",
        width: "100%",
        paddingHorizontal: 12,
        gap,
        height: 18, // fixed: prevents the row from changing height when slots flips
      }}
    >
      {Array.from({ length: slots }).map((_, i) => (
        <View
          key={i}
          style={{
            width: dotSize,
            height: dotSize,
            borderWidth: 2,
            borderColor: colors.ink,
            backgroundColor: i < count ? colors.ink : colors.surface,
          }}
        />
      ))}
    </View>
  );
}

interface PinKeyProps {
  label: string | "del" | "ghost";
  onPress?: () => void;
}

function PinKey({ label, onPress }: PinKeyProps) {
  const { colors } = useTheme();
  if (label === "ghost") {
    return <View style={{ flex: 1, height: 60 }} />;
  }
  return (
    <View style={{ flex: 1 }}>
      <NeoPressable
        onPress={onPress ?? (() => {})}
        style={{ height: 60, alignItems: "center", justifyContent: "center" }}
        accessibilityRole="keyboardkey"
        accessibilityLabel={
          label === "del" ? "Delete digit" : `Digit ${label}`
        }
      >
        {label === "del" ? (
          <Delete size={22} color={colors.ink} strokeWidth={2.2} />
        ) : (
          <Text
            style={{
              fontFamily: "BricolageGrotesque",
              fontSize: 22,
              color: colors.ink,
            }}
          >
            {label}
          </Text>
        )}
      </NeoPressable>
    </View>
  );
}

function PinStep({
  phase,
  onPhaseChange,
  onNext,
}: {
  phase: PinPhase;
  onPhaseChange: (p: PinPhase) => void;
  onNext: () => void;
}) {
  const { colors } = useTheme();
  const [pin, setPin] = useState("");
  const [confirmPin, setConfirmPin] = useState("");
  const [error, setError] = useState<string | null>(null);
  const shake = useSharedValue(0);

  const activeValue = phase === "create" ? pin : confirmPin;
  const setActiveValue = phase === "create" ? setPin : setConfirmPin;

  const triggerShake = () => {
    shake.value = withSequence(
      withTiming(-8, { duration: 60, easing: Easing.linear }),
      withTiming(8, { duration: 60, easing: Easing.linear }),
      withTiming(-6, { duration: 60, easing: Easing.linear }),
      withTiming(6, { duration: 60, easing: Easing.linear }),
      withTiming(0, { duration: 80, easing: Easing.linear })
    );
  };

  const shakeStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: shake.value }],
  }));

  const handleDigit = (d: string) => {
    if (activeValue.length >= PIN_MAX) return;
    setError(null);
    setActiveValue(activeValue + d);
  };

  const handleBackspace = () => {
    if (activeValue.length === 0) return;
    setError(null);
    setActiveValue(activeValue.slice(0, -1));
  };

  const handleNext = () => {
    if (phase === "create") {
      if (pin.length < PIN_MIN) {
        setError(`PIN must be at least ${PIN_MIN} digits`);
        triggerShake();
        return;
      }
      onPhaseChange("confirm");
      return;
    }
    if (confirmPin !== pin) {
      setError("PINs don't match");
      setConfirmPin("");
      triggerShake();
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error).catch(() => {});
      return;
    }
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    try {
      // If onboarding is being re-run (e.g. partial setup or upgrade with stale
      // state), wipe before generating a fresh PIN-bound key. Any leftover
      // ciphertext is unrecoverable under a new key anyway.
      if (HidaNative.isVaultInitialized()) {
        HidaNative.wipeVault();
      }
      // First-run setup: generates the random ChaCha20 file key and wraps it under
      // PBKDF2(pin). After this call, the vault is unlocked in-memory for the rest
      // of onboarding (so the biometric step can wrap a copy of the file key too).
      HidaNative.setupVault(pin);
    } catch (e) {
      console.error("[HIDA] setupVault failed", e);
      setError("Could not set up vault. Try again.");
      setConfirmPin("");
      triggerShake();
      return;
    }
    onNext();
  };

  return (
    <Animated.View
      entering={SlideInRight.duration(TIMING.slow)}
      exiting={SlideOutLeft.duration(TIMING.slow)}
      style={{ flex: 1, paddingHorizontal: 24, paddingTop: 4, paddingBottom: 16 }}
    >
      <Animated.View style={[{ flex: 1 }, shakeStyle]}>
        <View style={{ alignItems: "center", paddingTop: 8, paddingBottom: 12, gap: 12 }}>
          <NeoSurface background={colors.surface} shadowOffset={4}>
            <View style={{ width: 64, height: 64, alignItems: "center", justifyContent: "center" }}>
              <Lock size={28} color={colors.ink} strokeWidth={2.4} />
            </View>
          </NeoSurface>
          <Text
            style={{
              fontFamily: "BricolageGrotesque",
              fontSize: 26,
              color: colors.ink,
              letterSpacing: -0.5,
            }}
          >
            {phase === "create" ? "Create PIN" : "Confirm PIN"}
          </Text>
          <Text
            style={{
              fontFamily: "SpaceGrotesk",
              fontSize: 13,
              color: colors.ink2,
              textAlign: "center",
            }}
          >
            {phase === "create" ? "Choose 6–10 digits." : "Re-enter to confirm."}
          </Text>
        </View>

        <View style={{ marginTop: 4 }}>
          <PinDots count={activeValue.length} />
        </View>

        <Text
          style={{
            fontFamily: "SpaceGrotesk",
            fontSize: 13,
            fontWeight: "600",
            color: colors.danger,
            textAlign: "center",
            minHeight: 18,
            marginTop: 10,
          }}
        >
          {error ?? " "}
        </Text>

        <View style={{ flex: 1 }} />

        <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 10, marginBottom: 12 }}>
          {(["1", "2", "3"] as const).map((d) => (
            <PinKey key={d} label={d} onPress={() => handleDigit(d)} />
          ))}
        </View>
        <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 10, marginBottom: 12 }}>
          {(["4", "5", "6"] as const).map((d) => (
            <PinKey key={d} label={d} onPress={() => handleDigit(d)} />
          ))}
        </View>
        <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 10, marginBottom: 12 }}>
          {(["7", "8", "9"] as const).map((d) => (
            <PinKey key={d} label={d} onPress={() => handleDigit(d)} />
          ))}
        </View>
        <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 10, marginBottom: 12 }}>
          <PinKey label="ghost" />
          <PinKey label="0" onPress={() => handleDigit("0")} />
          <PinKey label="del" onPress={handleBackspace} />
        </View>

        <NeoButton
          label={phase === "create" ? "Next" : "Confirm"}
          onPress={handleNext}
          disabled={activeValue.length < PIN_MIN}
        />
      </Animated.View>
    </Animated.View>
  );
}

function BiometricStep({ onNext }: { onNext: () => void }) {
  const { colors } = useTheme();
  const [unavailable, setUnavailable] = useState(false);

  const handleEnable = useCallback(async () => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
    try {
      const hasHardware = await LocalAuthentication.hasHardwareAsync();
      const isEnrolled = await LocalAuthentication.isEnrolledAsync();
      if (!hasHardware || !isEnrolled) {
        setUnavailable(true);
        return;
      }
      if (Platform.OS === "android") {
        // Single CryptoObject-bound prompt: the user's fingerprint authorises the
        // very cipher we use to wrap the in-memory ChaCha key. A two-step flow
        // (plain prompt + separate Cipher.doFinal) cannot work because the
        // Keystore key is built with setUserAuthenticationRequired(true) — the
        // doFinal would throw UserNotAuthenticatedException, which used to be
        // swallowed and surface as the misleading "Not available on this device".
        const r = await authenticateAndEnrollBiometric("Verify your fingerprint", "Cancel");
        if (r.success) {
          HidaNative.setBiometricEnabled(true);
          InteractionManager.runAfterInteractions(() => {
            onNext();
          });
          return;
        }
        const err = r.error;
        if (err === "user_cancel" || err === "system_cancel" || err === "app_cancel") {
          return;
        }
        setUnavailable(true);
        return;
      }

      const result = await LocalAuthentication.authenticateAsync({
        promptMessage: "Verify your fingerprint",
        cancelLabel: "Cancel",
      });
      if (result.success) {
        // iOS path: setUserAuthenticationRequired isn't a thing on iOS, but if/when
        // an iOS build ships it should mirror the Android CryptoObject pattern.
        HidaNative.setBiometricEnabled(true);
        InteractionManager.runAfterInteractions(() => {
          onNext();
        });
        return;
      }
      const err = "error" in result ? result.error : undefined;
      if (
        err === "user_cancel" ||
        err === "system_cancel" ||
        err === "app_cancel"
      ) {
        return;
      }
      setUnavailable(true);
    } catch {
      setUnavailable(true);
    }
  }, [onNext]);

  return (
    <Animated.View
      entering={SlideInRight.duration(TIMING.slow)}
      style={{ flex: 1, paddingHorizontal: 28, paddingTop: 8, paddingBottom: 24 }}
    >
      <View style={{ flex: 1, alignItems: "center", justifyContent: "center", gap: 18 }}>
        <NeoSurface background={colors.surface} shadowOffset={4}>
          <View style={{ width: 96, height: 96, alignItems: "center", justifyContent: "center" }}>
            <Fingerprint size={44} color={colors.ink} strokeWidth={1.8} />
          </View>
        </NeoSurface>
        <Text
          style={{
            fontFamily: "BricolageGrotesque",
            fontSize: 26,
            color: colors.ink,
            textAlign: "center",
          }}
        >
          Biometric
        </Text>
        <Text
          style={{
            fontFamily: "SpaceGrotesk",
            fontSize: 13,
            color: colors.ink2,
            textAlign: "center",
            maxWidth: 240,
          }}
        >
          Use fingerprint to unlock faster.
        </Text>
        {unavailable && (
          <Text
            style={{
              fontFamily: "SpaceGrotesk",
              fontSize: 12,
              color: colors.danger,
              textAlign: "center",
              maxWidth: 260,
            }}
          >
            Not available on this device. You can enable it later in Settings.
          </Text>
        )}
      </View>
      <View style={{ gap: 10 }}>
        <NeoButton label="Enable" onPress={handleEnable} />
        <NeoButton label="Skip" variant="secondary" onPress={onNext} />
      </View>
    </Animated.View>
  );
}

export default function WelcomeScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { colors } = useTheme();
  const [step, setStep] = useState<Step>("info");
  const [pinPhase, setPinPhase] = useState<PinPhase>("create");

  const handleComplete = useCallback(() => {
    HidaNative.setFirstLaunchComplete();
    // setupVault() during onboarding leaves the in-memory ChaCha key loaded so the
    // biometric step can wrap a copy of it. Before handing off to the calculator,
    // explicitly lock so the user must enter their PIN to enter the gallery — same
    // path every cold launch follows.
    HidaNative.lockVault();
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    router.replace("/calculator");
  }, [router]);

  const handleBack = useCallback(() => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    if (step === "pin" && pinPhase === "confirm") {
      setPinPhase("create");
      return;
    }
    if (step === "style") setStep("info");
    else if (step === "pin") setStep("style");
    else if (step === "biometric") setStep("pin");
  }, [step, pinPhase]);

  const handlePickStyle = useCallback((choice: UnlockStyleChoice) => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    try {
      HidaNative.setUnlockStyle(choice);
    } catch (e) {
      console.error("[HIDA] setUnlockStyle failed", e);
    }
    setStep("pin");
  }, []);

  const canGoBack = step !== "info";
  const stepIndex =
    step === "info" ? 0 : step === "style" ? 1 : step === "pin" ? 2 : 3;

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <View
        style={{
          flexDirection: "row",
          alignItems: "center",
          paddingTop: insets.top + 10,
          paddingHorizontal: 16,
          paddingBottom: 8,
          gap: 12,
        }}
      >
        <View style={{ width: 44 }}>
          {canGoBack && (
            <NeoIconButton onPress={handleBack} ariaLabel="Back">
              <ArrowLeft size={20} color={colors.ink} strokeWidth={2.2} />
            </NeoIconButton>
          )}
        </View>
        <View style={{ flex: 1 }}>
          <StepDots step={stepIndex} total={4} />
        </View>
        <View style={{ width: 44 }} />
      </View>

      {step === "info" && <InfoStep onNext={() => setStep("style")} />}
      {step === "style" && <StyleStep onPick={handlePickStyle} />}
      {step === "pin" && (
        <PinStep
          phase={pinPhase}
          onPhaseChange={setPinPhase}
          onNext={() => setStep("biometric")}
        />
      )}
      {step === "biometric" && <BiometricStep onNext={handleComplete} />}
    </View>
  );
}
