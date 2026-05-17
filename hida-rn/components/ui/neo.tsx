import { useCallback, type ReactNode } from "react";
import {
  Pressable,
  TextInput,
  View,
  Text,
  type PressableProps,
  type StyleProp,
  type ViewStyle,
  type TextStyle,
} from "react-native";
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withTiming,
  Easing,
} from "react-native-reanimated";
import * as Haptics from "expo-haptics";
import { useTheme } from "../../lib/theme";
import { TIMING } from "../../lib/constants";

const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

const PRESS_TIMING = { duration: TIMING.press, easing: Easing.out(Easing.quad) };

/**
 * Reserves space for the offset shadow plate so the face doesn't overflow its parent.
 * `offset` is the design's hard-shadow distance (4 for cards/buttons, 3 for icon-btns).
 */
function withShadowSlot(offset: number, style: StyleProp<ViewStyle>): StyleProp<ViewStyle> {
  return [{ paddingRight: offset, paddingBottom: offset }, style];
}

interface SurfaceProps {
  children?: ReactNode;
  style?: StyleProp<ViewStyle>;
  background?: string;
  borderColor?: string;
  shadowOffset?: number;
  borderWidth?: number;
}

/** Static (non-pressable) neo surface — bordered tile with hard offset shadow. */
export function NeoSurface({
  children,
  style,
  background,
  borderColor,
  shadowOffset = 4,
  borderWidth = 2,
}: SurfaceProps) {
  const { colors } = useTheme();
  const bg = background ?? colors.surface;
  const border = borderColor ?? colors.ink;

  return (
    <View style={withShadowSlot(shadowOffset, [{ position: "relative" }])}>
      {/* Shadow plate */}
      <View
        pointerEvents="none"
        style={{
          position: "absolute",
          top: shadowOffset,
          left: shadowOffset,
          right: 0,
          bottom: 0,
          backgroundColor: colors.shadow,
        }}
      />
      {/* Face */}
      <View
        style={[
          {
            backgroundColor: bg,
            borderWidth,
            borderColor: border,
            position: "relative",
          },
          style,
        ]}
      >
        {children}
      </View>
    </View>
  );
}

interface NeoPressableProps extends Omit<PressableProps, "style" | "children"> {
  children?: ReactNode;
  style?: StyleProp<ViewStyle>;
  background?: string;
  borderColor?: string;
  shadowOffset?: number;
  pressedShadowOffset?: number;
  borderWidth?: number;
  haptic?: boolean | "light" | "medium";
  containerStyle?: StyleProp<ViewStyle>;
}

/**
 * Pressable neo surface. On press the face slides into the shadow over 80ms,
 * matching the calculator's button physics.
 */
export function NeoPressable({
  children,
  style,
  background,
  borderColor,
  shadowOffset = 4,
  pressedShadowOffset = 2,
  borderWidth = 2,
  haptic = "light",
  containerStyle,
  onPress,
  onPressIn,
  onPressOut,
  disabled,
  ...rest
}: NeoPressableProps) {
  const { colors } = useTheme();
  const bg = background ?? colors.surface;
  const border = borderColor ?? colors.ink;
  const press = useSharedValue(0); // 0 = rest, 1 = pressed

  const offset = shadowOffset - pressedShadowOffset;

  const faceStyle = useAnimatedStyle(() => ({
    transform: [
      { translateX: press.value * offset },
      { translateY: press.value * offset },
    ],
  }));

  const handlePressIn = useCallback(
    (e: Parameters<NonNullable<PressableProps["onPressIn"]>>[0]) => {
      press.value = withTiming(1, PRESS_TIMING);
      onPressIn?.(e);
    },
    [press, onPressIn]
  );
  const handlePressOut = useCallback(
    (e: Parameters<NonNullable<PressableProps["onPressOut"]>>[0]) => {
      press.value = withTiming(0, PRESS_TIMING);
      onPressOut?.(e);
    },
    [press, onPressOut]
  );
  const handlePress = useCallback(
    (e: Parameters<NonNullable<PressableProps["onPress"]>>[0]) => {
      if (haptic) {
        const style =
          haptic === "medium"
            ? Haptics.ImpactFeedbackStyle.Medium
            : Haptics.ImpactFeedbackStyle.Light;
        Haptics.impactAsync(style).catch(() => {});
      }
      onPress?.(e);
    },
    [haptic, onPress]
  );

  return (
    <View style={[withShadowSlot(shadowOffset, [{ position: "relative" }]), containerStyle]}>
      {!disabled && (
        <View
          pointerEvents="none"
          style={{
            position: "absolute",
            top: shadowOffset,
            left: shadowOffset,
            right: 0,
            bottom: 0,
            backgroundColor: colors.shadow,
          }}
        />
      )}
      <AnimatedPressable
        {...rest}
        disabled={disabled}
        onPress={handlePress}
        onPressIn={handlePressIn}
        onPressOut={handlePressOut}
        style={[
          {
            backgroundColor: disabled ? "transparent" : bg,
            borderWidth,
            borderColor: disabled ? "transparent" : border,
            opacity: disabled ? 0.45 : 1,
            position: "relative",
          },
          faceStyle,
          style,
        ]}
      >
        {children}
      </AnimatedPressable>
    </View>
  );
}

interface NeoButtonProps {
  label: string;
  onPress: () => void;
  variant?: "primary" | "secondary" | "danger";
  disabled?: boolean;
  haptic?: boolean | "light" | "medium";
  style?: StyleProp<ViewStyle>;
  containerStyle?: StyleProp<ViewStyle>;
  textStyle?: StyleProp<TextStyle>;
}

export function NeoButton({
  label,
  onPress,
  variant = "primary",
  disabled,
  haptic = "light",
  style,
  containerStyle,
  textStyle,
}: NeoButtonProps) {
  const { colors } = useTheme();
  let bg: string;
  let fg: string;
  if (variant === "primary") {
    bg = colors.ink;
    fg = colors.bg;
  } else if (variant === "danger") {
    bg = colors.danger;
    fg = "#ffffff";
  } else {
    bg = colors.surface;
    fg = colors.ink;
  }

  return (
    <NeoPressable
      background={bg}
      onPress={onPress}
      disabled={disabled}
      haptic={haptic}
      containerStyle={containerStyle}
      style={[
        {
          paddingVertical: 14,
          paddingHorizontal: 18,
          alignItems: "center",
          justifyContent: "center",
        },
        style,
      ]}
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ disabled: !!disabled }}
    >
      <Text
        style={[
          { fontFamily: "SpaceGrotesk", fontSize: 16, fontWeight: "700", color: fg },
          textStyle,
        ]}
      >
        {label}
      </Text>
    </NeoPressable>
  );
}

interface NeoIconButtonProps {
  onPress: () => void;
  children: ReactNode;
  ariaLabel: string;
  disabled?: boolean;
  size?: number;
  background?: string;
  containerStyle?: StyleProp<ViewStyle>;
}

export function NeoIconButton({
  onPress,
  children,
  ariaLabel,
  disabled,
  size = 44,
  background,
  containerStyle,
}: NeoIconButtonProps) {
  return (
    <NeoPressable
      background={background}
      onPress={onPress}
      disabled={disabled}
      shadowOffset={3}
      pressedShadowOffset={1}
      containerStyle={containerStyle}
      style={{
        width: size,
        height: size,
        alignItems: "center",
        justifyContent: "center",
      }}
      accessibilityRole="button"
      accessibilityLabel={ariaLabel}
      accessibilityState={{ disabled: !!disabled }}
    >
      {children}
    </NeoPressable>
  );
}

interface NeoToggleProps {
  value: boolean;
  onValueChange: (next: boolean) => void;
  ariaLabel?: string;
}

export function NeoToggle({ value, onValueChange, ariaLabel }: NeoToggleProps) {
  const { colors } = useTheme();
  const trackBg = value ? colors.ink : colors.bg;
  const knobBg = value ? colors.bg : colors.ink;

  return (
    <Pressable
      accessibilityRole="switch"
      accessibilityState={{ checked: value }}
      accessibilityLabel={ariaLabel ?? "Toggle"}
      onPress={() => {
        Haptics.selectionAsync().catch(() => {});
        onValueChange(!value);
      }}
      style={{
        width: 46,
        height: 26,
        borderWidth: 2,
        borderColor: colors.ink,
        backgroundColor: trackBg,
        position: "relative",
        // 2px hard shadow done with translucent wrapper would conflict with toggle motion,
        // so we draw the shadow inline via a sibling absolute View underneath the track.
      }}
    >
      <View
        pointerEvents="none"
        style={{
          position: "absolute",
          top: 2,
          left: value ? 22 : 2,
          width: 18,
          height: 18,
          backgroundColor: knobBg,
        }}
      />
    </Pressable>
  );
}

interface NeoTextFieldProps {
  value: string;
  onChangeText: (next: string) => void;
  placeholder?: string;
  isPassword?: boolean;
  keyboardType?: "default" | "numeric" | "number-pad";
  maxLength?: number;
  trailing?: ReactNode;
}

export function NeoTextField({
  value,
  onChangeText,
  placeholder,
  isPassword,
  keyboardType = "default",
  maxLength,
  trailing,
}: NeoTextFieldProps) {
  const { colors } = useTheme();

  return (
    <NeoSurface shadowOffset={3} background={colors.surface}>
      <View
        style={{
          paddingHorizontal: 14,
          paddingVertical: 10,
          flexDirection: "row",
          alignItems: "center",
          gap: 10,
        }}
      >
        <TextInput
          value={value}
          onChangeText={onChangeText}
          placeholder={placeholder}
          placeholderTextColor={colors.ink3}
          secureTextEntry={isPassword}
          keyboardType={isPassword ? "number-pad" : keyboardType}
          maxLength={maxLength}
          cursorColor={colors.ink}
          style={{
            flex: 1,
            fontFamily: "SpaceGrotesk",
            fontSize: 15,
            color: colors.ink,
            paddingVertical: 4,
          }}
        />
        {trailing}
      </View>
    </NeoSurface>
  );
}
