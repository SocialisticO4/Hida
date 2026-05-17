import { useCallback, useEffect, useRef, useState } from "react";
import { View, Text } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import Animated, {
  Easing,
  useAnimatedStyle,
  useSharedValue,
  withSequence,
  withTiming,
} from "react-native-reanimated";
import { Delete, Fingerprint, Lock } from "lucide-react-native";
import * as Haptics from "expo-haptics";
import { useTheme } from "../../lib/theme";
import { NeoIconButton, NeoPressable, NeoSurface } from "./neo";

type KeyLabel =
  | "1" | "2" | "3"
  | "4" | "5" | "6"
  | "7" | "8" | "9"
  | "0" | "del" | "enter";

const KEYPAD_ROWS: ReadonlyArray<ReadonlyArray<KeyLabel>> = [
  ["1", "2", "3"],
  ["4", "5", "6"],
  ["7", "8", "9"],
  ["del", "0", "enter"],
];

// Tuned to fit inside the available area on a 640dp viewport with the title
// section above (smallest Androids we care about). Larger devices simply
// leave a bit more breathing room above the keypad.
const KEY_HEIGHT = 60;
const KEY_GAP = 10;
const SHADOW_OFFSET = 4;
// Hard cap on input. Mirrors the welcome screen's PIN_MAX so the keypad can't
// exceed what setupVault accepts.
const PIN_MAX = 10;

interface PlainKeypadProps {
  /** Called when the user explicitly submits via the Enter key. */
  onSubmit: (pin: string) => Promise<void> | void;
  /** Show biometric button below keypad. */
  biometricAvailable: boolean;
  onRequestBiometric: () => void;
  /** Lockout window in ms (0 = unlocked). Disables keypad while > 0. */
  lockoutRemainingMs: number;
}

/**
 * Plain numeric keypad unlock screen, used when the user opts out of the
 * calculator disguise during welcome. Deliberately:
 * - Does NOT show how many digits have been entered (single pulse indicator
 *   instead of N dots) — entering count would leak PIN length to a shoulder
 *   surfer or anyone who watches the screen unlock once.
 * - Does NOT use the system keyboard (IME/keyboard apps can log digits).
 * - Requires explicit Enter (=) press to submit. This matches the calculator
 *   disguise's "=" gesture and means a casual passer-by who taps digits at
 *   random can't trigger unlock attempts (and burn lockout budget) without
 *   knowing to press the submit key.
 */
export function PlainKeypad({
  onSubmit,
  biometricAvailable,
  onRequestBiometric,
  lockoutRemainingMs,
}: PlainKeypadProps) {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const [pin, setPin] = useState("");
  // Pulse: a single dot scales up briefly each time a digit lands.
  const pulse = useSharedValue(0);
  // Shake: full keypad horizontal jitter when an unlock attempt fails.
  const shake = useSharedValue(0);
  // Guards against double-submit if the user mashes Enter while the parent's
  // unlockVault call is in flight.
  const submittingRef = useRef(false);
  // Tracks the previous lockout value so we only react to the unlocked→locked
  // edge — otherwise the parent's 500 ms countdown interval would fire shake
  // and clear pin on every tick.
  const prevLockoutRef = useRef(0);

  const isLocked = lockoutRemainingMs > 0;

  const triggerPulse = useCallback(() => {
    pulse.value = withSequence(
      withTiming(1, { duration: 90, easing: Easing.out(Easing.quad) }),
      withTiming(0, { duration: 160, easing: Easing.in(Easing.quad) })
    );
  }, [pulse]);

  const triggerShake = useCallback(() => {
    shake.value = withSequence(
      withTiming(-10, { duration: 60, easing: Easing.linear }),
      withTiming(10, { duration: 60, easing: Easing.linear }),
      withTiming(-8, { duration: 60, easing: Easing.linear }),
      withTiming(8, { duration: 60, easing: Easing.linear }),
      withTiming(0, { duration: 80, easing: Easing.linear })
    );
  }, [shake]);

  const handleDigit = useCallback(
    (d: string) => {
      if (isLocked || submittingRef.current) return;
      if (pin.length >= PIN_MAX) return;
      Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
      triggerPulse();
      setPin((p) => p + d);
    },
    [isLocked, pin.length, triggerPulse]
  );

  const handleDelete = useCallback(() => {
    if (isLocked || submittingRef.current) return;
    if (pin.length === 0) return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    setPin((p) => p.slice(0, -1));
  }, [isLocked, pin.length]);

  // Explicit submit. The parent decides whether the pin matches the real or
  // fake PIN and either navigates (this component unmounts) or returns. If we
  // stay mounted (wrong pin / lockout) we clear the buffer so the user can
  // start fresh.
  const handleSubmit = useCallback(async () => {
    if (isLocked || submittingRef.current || pin.length === 0) return;
    submittingRef.current = true;
    try {
      await onSubmit(pin);
      setPin("");
    } catch {
      triggerShake();
      setPin("");
    } finally {
      submittingRef.current = false;
    }
  }, [isLocked, pin, onSubmit, triggerShake]);

  // Shake + clear once when the lockout transitions from unlocked → locked.
  // The previous version reacted to every change of lockoutRemainingMs, which
  // — combined with the parent's 500 ms countdown — produced a continuous
  // shake instead of a single one.
  useEffect(() => {
    const was = prevLockoutRef.current;
    prevLockoutRef.current = lockoutRemainingMs;
    if (was <= 0 && lockoutRemainingMs > 0) {
      triggerShake();
      setPin("");
    }
  }, [lockoutRemainingMs, triggerShake]);

  const pulseStyle = useAnimatedStyle(() => {
    const s = 1 + pulse.value * 0.6;
    const o = 0.55 + pulse.value * 0.45;
    return {
      transform: [{ scale: s }],
      opacity: o,
    };
  });

  const shakeStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: shake.value }],
  }));

  const lockoutSeconds = Math.ceil(lockoutRemainingMs / 1000);

  return (
    <View
      style={{
        flex: 1,
        backgroundColor: colors.bg,
        paddingTop: insets.top + 24,
        paddingBottom: insets.bottom + 24,
        paddingHorizontal: 28,
      }}
    >
      <View style={{ flex: 1, alignItems: "center", justifyContent: "center" }}>
        <NeoSurface background={colors.surface} shadowOffset={4}>
          <View
            style={{
              width: 88,
              height: 88,
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <Lock size={40} color={colors.ink} strokeWidth={2.4} />
          </View>
        </NeoSurface>

        <Text
          style={{
            fontFamily: "BricolageGrotesque",
            fontSize: 28,
            color: colors.ink,
            letterSpacing: -0.5,
            marginTop: 22,
          }}
        >
          Enter PIN
        </Text>

        {/* Single fixed-size pulse dot — does NOT reveal how many digits
            were typed. Just animates each time a digit lands. */}
        <View
          style={{
            height: 28,
            marginTop: 14,
            alignItems: "center",
            justifyContent: "center",
          }}
        >
          <Animated.View
            style={[
              {
                width: 14,
                height: 14,
                borderWidth: 2,
                borderColor: colors.ink,
                backgroundColor: pin.length > 0 ? colors.ink : colors.surface,
              },
              pulseStyle,
            ]}
          />
        </View>

        {isLocked ? (
          <Text
            style={{
              fontFamily: "SpaceGrotesk",
              fontSize: 13,
              color: colors.danger,
              marginTop: 10,
            }}
          >
            Locked. Try again in {lockoutSeconds}s
          </Text>
        ) : (
          <View style={{ height: 23, marginTop: 10 }} />
        )}
      </View>

      <Animated.View style={shakeStyle}>
        {KEYPAD_ROWS.map((row, rowIdx) => (
          <View
            key={rowIdx}
            style={{
              flexDirection: "row",
              // marginTop instead of `gap` because flexbox gap on Android
              // (incl. GrapheneOS / older AOSP forks) silently collapses to 0
              // on some surface configs, which made every row stack at y=0.
              marginTop: rowIdx === 0 ? 0 : KEY_GAP,
            }}
          >
            {row.map((label, colIdx) => {
              // Enter is the primary action — filled (ink) background, cream
              // foreground — to match the calculator's "=" button affordance.
              const isEnter = label === "enter";
              // Enter is the only key whose disabled state depends on input;
              // pressing it with no digits is meaningless and would otherwise
              // burn a lockout attempt on an empty PIN. Digit / Del keys stay
              // active so the user can keep typing/erasing during lockout.
              const enterDisabled = isEnter && pin.length === 0;
              return (
                <View
                  key={`${rowIdx}-${colIdx}`}
                  style={{
                    flex: 1,
                    // Same reason as marginTop above — avoid gap.
                    marginLeft: colIdx === 0 ? 0 : KEY_GAP,
                    // Reserve the cell's vertical footprint (face + shadow
                    // plate padding) so the last row's height is predictable
                    // and the row above doesn't bleed through any cell.
                    height: KEY_HEIGHT + SHADOW_OFFSET,
                  }}
                >
                  <NeoPressable
                    onPress={
                      isEnter
                        ? handleSubmit
                        : label === "del"
                          ? handleDelete
                          : () => handleDigit(label)
                    }
                    disabled={isLocked || enterDisabled}
                    background={isEnter ? colors.ink : colors.surface}
                    shadowOffset={SHADOW_OFFSET}
                    pressedShadowOffset={2}
                    style={{
                      height: KEY_HEIGHT,
                      alignItems: "center",
                      justifyContent: "center",
                    }}
                    accessibilityRole={isEnter ? "button" : "keyboardkey"}
                    accessibilityLabel={
                      isEnter
                        ? "Submit PIN"
                        : label === "del"
                          ? "Delete digit"
                          : `Digit ${label}`
                    }
                  >
                    {label === "del" ? (
                      <Delete size={22} color={colors.ink} strokeWidth={2.2} />
                    ) : (
                      <Text
                        style={{
                          fontFamily: "BricolageGrotesque",
                          // "Enter" is wider than a single digit glyph — drop the
                          // size one notch and add weight so the word fits the
                          // cell cleanly on narrow phones without truncating.
                          fontSize: isEnter ? 17 : 24,
                          fontWeight: isEnter ? "700" : "400",
                          letterSpacing: isEnter ? 0.4 : 0,
                          color: isEnter ? colors.bg : colors.ink,
                        }}
                      >
                        {isEnter ? "Enter" : label}
                      </Text>
                    )}
                  </NeoPressable>
                </View>
              );
            })}
          </View>
        ))}

        {biometricAvailable && (
          <View
            style={{
              marginTop: KEY_GAP + 4,
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <NeoIconButton
              ariaLabel="Unlock with biometric"
              onPress={onRequestBiometric}
              size={52}
            >
              <Fingerprint size={26} color={colors.ink} strokeWidth={2.2} />
            </NeoIconButton>
          </View>
        )}
      </Animated.View>
    </View>
  );
}

