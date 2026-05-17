import { useState, useCallback, useEffect, useMemo, type ReactNode } from "react";
import {
  View,
  Text,
  Pressable,
  ScrollView,
  Modal,
  TextInput,
  InteractionManager,
  KeyboardAvoidingView,
  Platform,
  Alert,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import Animated, { Easing, FadeIn, ZoomIn } from "react-native-reanimated";
import { useRouter, useLocalSearchParams } from "expo-router";
import * as Haptics from "expo-haptics";
import {
  ArrowLeft,
  Lock,
  EyeOff,
  Eye,
  Timer,
  Fingerprint,
  Volume2,
  Palette,
  ChevronRight,
  Check,
  Sun,
  Moon,
  Smartphone,
  Calculator,
  Cloud,
  Phone,
  ImageIcon,
  Mail,
  CirclePlay,
  Folder,
  Globe,
  Clock,
  Calendar,
  MapPin,
  Contact,
  MessageSquare,
  HardDrive,
  FolderOpen,
  Wallet,
  Landmark,
  ShoppingBag,
  Video,
  MessageCircle,
  Images,
  Cog,
  Newspaper,
  Activity,
  Languages,
  LayoutGrid,
  KeyRound,
} from "lucide-react-native";
import { TIMING } from "../lib/constants";
import { useTheme, type ThemeMode } from "../lib/theme";
import {
  NeoButton,
  NeoIconButton,
  NeoPressable,
  NeoSurface,
  NeoToggle,
} from "../components/ui/neo";
import * as HidaNative from "../modules/hida-native";
import type { UnlockStyle } from "../modules/hida-native";

const TIMEOUT_OPTIONS = [
  { label: "30s", ms: 30_000 },
  { label: "1 min", ms: 60_000 },
  { label: "2 min", ms: 120_000 },
  { label: "5 min", ms: 300_000 },
  { label: "10 min", ms: 600_000 },
  { label: "When phone locks", ms: HidaNative.SESSION_TIMEOUT_LOCK_ON_DEVICE },
] as const;

type IconComponent = React.ComponentType<{ size?: number; color?: string; strokeWidth?: number }>;

/** Align native prefs (`HidaDefaultAlias`, legacy `MainActivity`) with picker option aliases. */
function normalizeIconAliasForSettings(alias: string): string {
  const a = alias.trim();
  if (!a || a === "MainActivity") return "HidaDefaultAlias";
  return a;
}

/** Every launcher alias from AndroidManifest — labels via native (manifest android:label). */
const ICON_OPTIONS: ReadonlyArray<{ alias: string; Icon: IconComponent }> = [
  { alias: "HidaDefaultAlias", Icon: Lock },
  { alias: "CalculatorAlias", Icon: Calculator },
  { alias: "CalculatorMaterialAlias", Icon: LayoutGrid },
  { alias: "CalculatorIosAlias", Icon: Smartphone },
  { alias: "WeatherAlias", Icon: Cloud },
  { alias: "NotesAlias", Icon: Folder },
  { alias: "ClockAlias", Icon: Clock },
  { alias: "MusicAlias", Icon: Volume2 },
  { alias: "CalendarAlias", Icon: Calendar },
  { alias: "MailAlias", Icon: Mail },
  { alias: "BrowserAlias", Icon: Globe },
  { alias: "CameraAlias", Icon: ImageIcon },
  { alias: "MapsAlias", Icon: MapPin },
  { alias: "PhoneAlias", Icon: Phone },
  { alias: "ContactsAlias", Icon: Contact },
  { alias: "MessagesAlias", Icon: MessageSquare },
  { alias: "PlayStoreAlias", Icon: CirclePlay },
  { alias: "DriveAlias", Icon: HardDrive },
  { alias: "FilesAlias", Icon: FolderOpen },
  { alias: "WalletAlias", Icon: Wallet },
  { alias: "BankingAlias", Icon: Landmark },
  { alias: "ShoppingAlias", Icon: ShoppingBag },
  { alias: "VideosAlias", Icon: Video },
  { alias: "ChatAlias", Icon: MessageCircle },
  { alias: "PhotosAlias", Icon: Images },
  { alias: "SettingsAlias", Icon: Cog },
  { alias: "NewsAlias", Icon: Newspaper },
  { alias: "FitnessAlias", Icon: Activity },
  { alias: "TranslateAlias", Icon: Languages },
];

interface SectionLabelProps {
  children: ReactNode;
}
function SectionLabel({ children }: SectionLabelProps) {
  const { colors } = useTheme();
  return (
    <Text
      style={{
        fontFamily: "JetBrainsMono",
        fontSize: 10,
        letterSpacing: 1.6,
        textTransform: "uppercase",
        color: colors.ink2,
        paddingHorizontal: 4,
        paddingTop: 8,
        paddingBottom: 4,
      }}
    >
      {children}
    </Text>
  );
}

interface SettingRowProps {
  icon: ReactNode;
  title: string;
  right?: ReactNode;
  onPress?: () => void;
}
function SettingRow({ icon, title, right, onPress }: SettingRowProps) {
  const { colors } = useTheme();
  const content = (
    <View
      style={{
        flexDirection: "row",
        alignItems: "center",
        paddingHorizontal: 12,
        paddingVertical: 10,
        gap: 12,
      }}
    >
      <View
        style={{
          width: 32,
          height: 32,
          borderWidth: 2,
          borderColor: colors.ink,
          backgroundColor: colors.surface2,
          alignItems: "center",
          justifyContent: "center",
        }}
      >
        {icon}
      </View>
      <Text
        style={{
          flex: 1,
          fontFamily: "SpaceGrotesk",
          fontSize: 14,
          fontWeight: "700",
          color: colors.ink,
        }}
      >
        {title}
      </Text>
      <View
        style={{
          flexShrink: 0,
          flexDirection: "row",
          alignItems: "center",
          gap: 6,
        }}
      >
        {right}
      </View>
    </View>
  );
  if (onPress) {
    return (
      <NeoPressable onPress={onPress} shadowOffset={3} pressedShadowOffset={1}>
        {content}
      </NeoPressable>
    );
  }
  return <NeoSurface shadowOffset={3}>{content}</NeoSurface>;
}

function ChevronIcon() {
  const { colors } = useTheme();
  return <ChevronRight size={14} color={colors.ink2} strokeWidth={2.4} />;
}
function MutedText({ children }: { children: ReactNode }) {
  const { colors } = useTheme();
  return (
    <Text
      style={{
        fontFamily: "SpaceGrotesk",
        fontSize: 12,
        fontWeight: "600",
        color: colors.ink2,
      }}
    >
      {children}
    </Text>
  );
}

interface PinModalProps {
  visible: boolean;
  title: string;
  variant: "primary" | "decoy";
  onDismiss: () => void;
  onConfirm: (pin: string) => void;
}
function PinModal({ visible, title, variant, onDismiss, onConfirm }: PinModalProps) {
  const { colors } = useTheme();
  const [pin, setPin] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [showPin, setShowPin] = useState(false);

  useEffect(() => {
    if (!visible) {
      setPin("");
      setError(null);
      setShowPin(false);
    }
  }, [visible]);

  const handleSave = () => {
    // Primary PIN unlocks the cryptographic vault — must be 6+ digits to keep
    // brute-force feasibility above the PBKDF2-600k+pepper hardening floor.
    // Decoy PIN is UI-only (no crypto), so 4 digits is fine for usability.
    const minLen = variant === "primary" ? 6 : 4;
    if (pin.length < minLen) {
      setError(`PIN must be at least ${minLen} digits`);
      return;
    }
    onConfirm(pin);
  };

  return (
    <Modal
      visible={visible}
      transparent
      animationType="none"
      onRequestClose={onDismiss}
    >
      <Animated.View
        entering={FadeIn.duration(TIMING.fast)}
        style={{
          flex: 1,
          backgroundColor: colors.scrim,
        }}
      >
        <Pressable
          accessibilityLabel="Dismiss"
          onPress={onDismiss}
          style={{ position: "absolute", inset: 0 }}
        />
        <KeyboardAvoidingView
          behavior={Platform.OS === "ios" ? "padding" : "height"}
          style={{ flex: 1, alignItems: "center", justifyContent: "center", padding: 24 }}
        >
        <Animated.View
          entering={ZoomIn.duration(TIMING.fast).easing(Easing.out(Easing.cubic))}
          style={{ width: "100%", maxWidth: 360 }}
        >
          <View
            style={{
              backgroundColor: colors.surface,
              borderWidth: 2,
              borderColor: colors.ink,
              borderLeftWidth: 8,
              borderLeftColor: colors.ink,
              padding: 20,
              shadowColor: colors.shadow,
              shadowOffset: { width: 6, height: 6 },
              shadowOpacity: 1,
              shadowRadius: 0,
              elevation: 8,
            }}
          >
            <Text
              style={{
                fontFamily: "JetBrainsMono",
                fontSize: 10,
                letterSpacing: 1.6,
                textTransform: "uppercase",
                color: colors.ink2,
                marginBottom: 6,
              }}
            >
              {variant === "decoy" ? "Decoy" : "Security"}
            </Text>
            <Text
              style={{
                fontFamily: "BricolageGrotesque",
                fontSize: 22,
                color: colors.ink,
                marginBottom: variant === "decoy" ? 6 : 14,
              }}
            >
              {title}
            </Text>
            {variant === "decoy" && (
              <Text
                style={{
                  fontFamily: "SpaceGrotesk",
                  fontSize: 12,
                  color: colors.ink2,
                  marginBottom: 14,
                }}
              >
                Shows an empty vault when entered.
              </Text>
            )}
            <View
              style={{
                flexDirection: "row",
                alignItems: "center",
                borderWidth: 2,
                borderColor: colors.ink,
                backgroundColor: colors.surface,
                paddingHorizontal: 12,
                marginBottom: 16,
              }}
            >
              <TextInput
                value={pin}
                onChangeText={(t) => {
                  if (t.length <= 10 && /^\d*$/.test(t)) {
                    setPin(t);
                    setError(null);
                  }
                }}
                placeholder={variant === "primary" ? "6–10 digits" : "4–10 digits"}
                placeholderTextColor={colors.ink3}
                keyboardType="number-pad"
                maxLength={10}
                secureTextEntry={!showPin}
                cursorColor={colors.ink}
                style={{
                  flex: 1,
                  fontFamily: "SpaceGrotesk",
                  fontSize: 15,
                  color: colors.ink,
                  paddingVertical: 10,
                }}
              />
              <Pressable onPress={() => setShowPin((v) => !v)} style={{ padding: 6 }}>
                {showPin ? (
                  <EyeOff size={18} color={colors.ink2} />
                ) : (
                  <Eye size={18} color={colors.ink2} />
                )}
              </Pressable>
            </View>
            {error && (
              <Text
                style={{
                  fontFamily: "SpaceGrotesk",
                  fontSize: 12,
                  color: colors.danger,
                  marginBottom: 10,
                }}
              >
                {error}
              </Text>
            )}
            <View style={{ flexDirection: "row", gap: 10, justifyContent: "flex-end" }}>
              <NeoButton
                label="Cancel"
                variant="secondary"
                onPress={onDismiss}
                containerStyle={{ flexShrink: 1 }}
                style={{ paddingVertical: 10, paddingHorizontal: 16 }}
                textStyle={{ fontSize: 14 }}
              />
              <NeoButton
                label="Save"
                onPress={handleSave}
                containerStyle={{ flexShrink: 1 }}
                style={{ paddingVertical: 10, paddingHorizontal: 18 }}
                textStyle={{ fontSize: 14 }}
              />
            </View>
          </View>
        </Animated.View>
        </KeyboardAvoidingView>
      </Animated.View>
    </Modal>
  );
}

interface TimeoutModalProps {
  visible: boolean;
  current: number;
  onPick: (ms: number) => void;
  onDismiss: () => void;
}
function TimeoutModal({ visible, current, onPick, onDismiss }: TimeoutModalProps) {
  const { colors } = useTheme();
  return (
    <Modal visible={visible} transparent animationType="none" onRequestClose={onDismiss}>
      <Animated.View
        entering={FadeIn.duration(TIMING.fast)}
        style={{
          flex: 1,
          backgroundColor: colors.scrim,
          alignItems: "center",
          justifyContent: "center",
          padding: 24,
        }}
      >
        <Pressable
          accessibilityLabel="Dismiss"
          onPress={onDismiss}
          style={{ position: "absolute", inset: 0 }}
        />
        <Animated.View
          entering={ZoomIn.duration(TIMING.fast).easing(Easing.out(Easing.cubic))}
          style={{ width: "100%", maxWidth: 360 }}
        >
          <View
            style={{
              backgroundColor: colors.surface,
              borderWidth: 2,
              borderColor: colors.ink,
              borderLeftWidth: 8,
              borderLeftColor: colors.ink,
              padding: 20,
              shadowColor: colors.shadow,
              shadowOffset: { width: 6, height: 6 },
              shadowOpacity: 1,
              shadowRadius: 0,
              elevation: 8,
            }}
          >
          <Text
            style={{
              fontFamily: "JetBrainsMono",
              fontSize: 10,
              letterSpacing: 1.6,
              textTransform: "uppercase",
              color: colors.ink2,
              marginBottom: 6,
            }}
          >
            Auto-Lock
          </Text>
          <Text
            style={{
              fontFamily: "BricolageGrotesque",
              fontSize: 20,
              color: colors.ink,
              marginBottom: 14,
            }}
          >
            Lock after
          </Text>
          <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8 }}>
            {TIMEOUT_OPTIONS.map((o) => {
              const active = o.ms === current;
              return (
                <View key={o.ms} style={{ width: "48%" }}>
                  <NeoPressable
                    onPress={() => onPick(o.ms)}
                    background={active ? colors.ink : colors.surface}
                    shadowOffset={3}
                    pressedShadowOffset={1}
                    style={{
                      paddingHorizontal: 12,
                      paddingVertical: 12,
                      flexDirection: "row",
                      alignItems: "center",
                      justifyContent: "space-between",
                    }}
                  >
                    <Text
                      style={{
                        fontFamily: "SpaceGrotesk",
                        fontSize: 14,
                        fontWeight: "700",
                        color: active ? colors.bg : colors.ink,
                      }}
                    >
                      {o.label}
                    </Text>
                    {active && <Check size={14} color={colors.bg} strokeWidth={3} />}
                  </NeoPressable>
                </View>
              );
            })}
          </View>
          </View>
        </Animated.View>
      </Animated.View>
    </Modal>
  );
}

interface IconPickerModalProps {
  visible: boolean;
  current: string;
  onPick: (alias: string) => void;
  onDismiss: () => void;
}
function IconPickerModal({ visible, current, onPick, onDismiss }: IconPickerModalProps) {
  const { colors } = useTheme();
  return (
    <Modal visible={visible} transparent animationType="none" onRequestClose={onDismiss}>
      <Animated.View
        entering={FadeIn.duration(TIMING.fast)}
        style={{
          flex: 1,
          backgroundColor: colors.scrim,
          alignItems: "center",
          justifyContent: "center",
          padding: 20,
        }}
      >
        <Pressable
          accessibilityLabel="Dismiss"
          onPress={onDismiss}
          style={{ position: "absolute", inset: 0 }}
        />
        <Animated.View
          entering={ZoomIn.duration(TIMING.fast).easing(Easing.out(Easing.cubic))}
          style={{ width: "100%", maxWidth: 380, maxHeight: "85%" }}
        >
          <View
            style={{
              backgroundColor: colors.surface,
              borderWidth: 2,
              borderColor: colors.ink,
              borderLeftWidth: 8,
              borderLeftColor: colors.ink,
              padding: 18,
              shadowColor: colors.shadow,
              shadowOffset: { width: 6, height: 6 },
              shadowOpacity: 1,
              shadowRadius: 0,
              elevation: 8,
            }}
          >
            <Text
              style={{
                fontFamily: "JetBrainsMono",
                fontSize: 10,
                letterSpacing: 1.6,
                textTransform: "uppercase",
                color: colors.ink2,
                marginBottom: 6,
              }}
            >
              Disguise
            </Text>
            <Text
              style={{
                fontFamily: "BricolageGrotesque",
                fontSize: 20,
                color: colors.ink,
                marginBottom: 14,
              }}
            >
              App Icon
            </Text>
            <ScrollView showsVerticalScrollIndicator={false}>
              <View style={{ flexDirection: "row", flexWrap: "wrap" }}>
                {ICON_OPTIONS.map((o) => {
                  const active = normalizeIconAliasForSettings(current) === o.alias;
                  const Icon = o.Icon;
                  return (
                    <View key={o.alias} style={{ width: "25%", padding: 4 }}>
                      <NeoPressable
                        onPress={() => onPick(o.alias)}
                        background={active ? colors.ink : colors.surface}
                        shadowOffset={3}
                        pressedShadowOffset={1}
                        style={{
                          aspectRatio: 1,
                          alignItems: "center",
                          justifyContent: "center",
                          gap: 4,
                          paddingVertical: 8,
                        }}
                      >
                        <Icon
                          size={22}
                          color={active ? colors.bg : colors.ink}
                          strokeWidth={2.2}
                        />
                        <Text
                          numberOfLines={2}
                          style={{
                            fontFamily: "SpaceGrotesk",
                            fontSize: 9,
                            fontWeight: "700",
                            color: active ? colors.bg : colors.ink,
                            textAlign: "center",
                          }}
                        >
                          {HidaNative.getDisguiseDisplayNameForAlias(o.alias)}
                        </Text>
                      </NeoPressable>
                    </View>
                  );
                })}
              </View>
            </ScrollView>
          </View>
        </Animated.View>
      </Animated.View>
    </Modal>
  );
}

interface UnlockStyleModalProps {
  visible: boolean;
  current: UnlockStyle;
  onPick: (style: UnlockStyle) => void;
  onDismiss: () => void;
}
function UnlockStyleModal({
  visible,
  current,
  onPick,
  onDismiss,
}: UnlockStyleModalProps) {
  const { colors } = useTheme();
  const opts: {
    style: UnlockStyle;
    label: string;
    body: string;
    Icon: typeof Calculator;
  }[] = [
    {
      style: "calculator",
      label: "Calculator disguise",
      body: "Looks like a working calculator. Type PIN, press = to unlock.",
      Icon: Calculator,
    },
    {
      style: "keypad",
      label: "Plain keypad",
      body: "Simple PIN keypad. No system keyboard (keyboards can log digits).",
      Icon: KeyRound,
    },
  ];
  return (
    <Modal visible={visible} transparent animationType="none" onRequestClose={onDismiss}>
      <Animated.View
        entering={FadeIn.duration(TIMING.fast)}
        style={{
          flex: 1,
          backgroundColor: colors.scrim,
          alignItems: "center",
          justifyContent: "center",
          padding: 24,
        }}
      >
        <Pressable
          accessibilityLabel="Dismiss"
          onPress={onDismiss}
          style={{ position: "absolute", inset: 0 }}
        />
        <Animated.View
          entering={ZoomIn.duration(TIMING.fast).easing(Easing.out(Easing.cubic))}
          style={{ width: "100%", maxWidth: 380 }}
        >
          <View
            style={{
              backgroundColor: colors.surface,
              borderWidth: 2,
              borderColor: colors.ink,
              borderLeftWidth: 8,
              borderLeftColor: colors.ink,
              padding: 20,
              shadowColor: colors.shadow,
              shadowOffset: { width: 6, height: 6 },
              shadowOpacity: 1,
              shadowRadius: 0,
              elevation: 8,
            }}
          >
            <Text
              style={{
                fontFamily: "JetBrainsMono",
                fontSize: 10,
                letterSpacing: 1.6,
                textTransform: "uppercase",
                color: colors.ink2,
                marginBottom: 6,
              }}
            >
              Security
            </Text>
            <Text
              style={{
                fontFamily: "BricolageGrotesque",
                fontSize: 20,
                color: colors.ink,
                marginBottom: 14,
              }}
            >
              Unlock style
            </Text>
            <View style={{ gap: 10 }}>
              {opts.map((o) => {
                const active = o.style === current;
                return (
                  <NeoPressable
                    key={o.style}
                    onPress={() => onPick(o.style)}
                    background={active ? colors.ink : colors.surface}
                    shadowOffset={3}
                    pressedShadowOffset={1}
                    style={{
                      paddingHorizontal: 14,
                      paddingVertical: 12,
                      flexDirection: "row",
                      alignItems: "flex-start",
                      gap: 12,
                    }}
                  >
                    <o.Icon
                      size={20}
                      color={active ? colors.bg : colors.ink}
                      strokeWidth={2.2}
                    />
                    <View style={{ flex: 1, gap: 2 }}>
                      <Text
                        style={{
                          fontFamily: "SpaceGrotesk",
                          fontSize: 14,
                          fontWeight: "700",
                          color: active ? colors.bg : colors.ink,
                        }}
                      >
                        {o.label}
                      </Text>
                      <Text
                        style={{
                          fontFamily: "SpaceGrotesk",
                          fontSize: 12,
                          color: active ? colors.bg : colors.ink2,
                          lineHeight: 16,
                        }}
                      >
                        {o.body}
                      </Text>
                    </View>
                    {active && <Check size={14} color={colors.bg} strokeWidth={3} />}
                  </NeoPressable>
                );
              })}
            </View>
          </View>
        </Animated.View>
      </Animated.View>
    </Modal>
  );
}

interface ThemeModalProps {
  visible: boolean;
  current: ThemeMode;
  onPick: (mode: ThemeMode) => void;
  onDismiss: () => void;
}
function ThemeModal({ visible, current, onPick, onDismiss }: ThemeModalProps) {
  const { colors } = useTheme();
  const opts: { mode: ThemeMode; label: string; Icon: typeof Sun }[] = [
    { mode: "light", label: "Light", Icon: Sun },
    { mode: "dark", label: "Dark", Icon: Moon },
    { mode: "system", label: "System", Icon: Smartphone },
  ];
  return (
    <Modal visible={visible} transparent animationType="none" onRequestClose={onDismiss}>
      <Animated.View
        entering={FadeIn.duration(TIMING.fast)}
        style={{
          flex: 1,
          backgroundColor: colors.scrim,
          alignItems: "center",
          justifyContent: "center",
          padding: 24,
        }}
      >
        <Pressable
          accessibilityLabel="Dismiss"
          onPress={onDismiss}
          style={{ position: "absolute", inset: 0 }}
        />
        <Animated.View
          entering={ZoomIn.duration(TIMING.fast).easing(Easing.out(Easing.cubic))}
          style={{ width: "100%", maxWidth: 360 }}
        >
          <View
            style={{
              backgroundColor: colors.surface,
              borderWidth: 2,
              borderColor: colors.ink,
              borderLeftWidth: 8,
              borderLeftColor: colors.ink,
              padding: 20,
              shadowColor: colors.shadow,
              shadowOffset: { width: 6, height: 6 },
              shadowOpacity: 1,
              shadowRadius: 0,
              elevation: 8,
            }}
          >
          <Text
            style={{
              fontFamily: "JetBrainsMono",
              fontSize: 10,
              letterSpacing: 1.6,
              textTransform: "uppercase",
              color: colors.ink2,
              marginBottom: 6,
            }}
          >
            Appearance
          </Text>
          <Text
            style={{
              fontFamily: "BricolageGrotesque",
              fontSize: 20,
              color: colors.ink,
              marginBottom: 14,
            }}
          >
            Theme
          </Text>
          <View style={{ gap: 10 }}>
            {opts.map((o) => {
              const active = o.mode === current;
              return (
                <NeoPressable
                  key={o.mode}
                  onPress={() => onPick(o.mode)}
                  background={active ? colors.ink : colors.surface}
                  shadowOffset={3}
                  pressedShadowOffset={1}
                  style={{
                    paddingHorizontal: 14,
                    paddingVertical: 12,
                    flexDirection: "row",
                    alignItems: "center",
                    gap: 12,
                  }}
                >
                  <o.Icon
                    size={18}
                    color={active ? colors.bg : colors.ink}
                    strokeWidth={2.2}
                  />
                  <Text
                    style={{
                      flex: 1,
                      fontFamily: "SpaceGrotesk",
                      fontSize: 14,
                      fontWeight: "700",
                      color: active ? colors.bg : colors.ink,
                    }}
                  >
                    {o.label}
                  </Text>
                  {active && <Check size={14} color={colors.bg} strokeWidth={3} />}
                </NeoPressable>
              );
            })}
          </View>
          </View>
        </Animated.View>
      </Animated.View>
    </Modal>
  );
}

export default function SettingsScreen() {
  const router = useRouter();
  const { mode: routeMode } = useLocalSearchParams<{ mode?: string }>();
  const isFake = routeMode === "fake";
  const insets = useSafeAreaInsets();
  const { colors, mode: themeMode, setMode: setThemeMode } = useTheme();

  // Decoy mode: a stub "saving" toast that completes instantly, used to make
  // every non-Lock action feel responsive without persisting anything.
  const fakeStub = useCallback((message?: string) => {
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    if (message) {
      Alert.alert("Saved", message);
    }
  }, []);

  const [showPinDialog, setShowPinDialog] = useState(false);
  const [showFakePinDialog, setShowFakePinDialog] = useState(false);
  const [showTimeoutPicker, setShowTimeoutPicker] = useState(false);
  const [showIconPicker, setShowIconPicker] = useState(false);
  const [showThemePicker, setShowThemePicker] = useState(false);
  const [showUnlockStylePicker, setShowUnlockStylePicker] = useState(false);
  const [unlockStyle, setUnlockStyleState] = useState<UnlockStyle>(() => {
    try {
      return HidaNative.getUnlockStyle();
    } catch {
      return "calculator";
    }
  });
  const [currentTimeout, setCurrentTimeout] = useState(() => {
    try {
      return HidaNative.getSessionTimeout();
    } catch {
      return 60_000;
    }
  });
  const [biometricOn, setBiometricOn] = useState(() => {
    // The toggle is only "on" when both sides agree: the JS pref AND the actual
    // Keystore-bound biometric wrap exist. A stale pref (e.g. wrap invalidated
    // by a fingerprint enrollment) would otherwise show ON while biometric
    // unlock silently does nothing.
    try {
      return HidaNative.isBiometricEnabled() && HidaNative.isBiometricUnlockEnrolled();
    } catch {
      return false;
    }
  });

  useEffect(() => {
    try {
      const real = HidaNative.isBiometricEnabled() && HidaNative.isBiometricUnlockEnrolled();
      setBiometricOn(real);
      if (HidaNative.isBiometricEnabled() && !HidaNative.isBiometricUnlockEnrolled()) {
        // Pref disagrees with the wrap. Reconcile by clearing the pref so the
        // calculator screen doesn't show a fingerprint button that can't unlock.
        HidaNative.setBiometricEnabled(false);
      }
    } catch {
      /* native not ready */
    }
  }, []);
  const [currentIconAlias, setCurrentIconAlias] = useState(() => {
    try {
      return normalizeIconAliasForSettings(HidaNative.getIconAlias());
    } catch {
      return "HidaDefaultAlias";
    }
  });

  const currentTimeoutLabel =
    TIMEOUT_OPTIONS.find((o) => o.ms === currentTimeout)?.label ?? "1 min";
  const currentIconLabel = useMemo(() => {
    try {
      return HidaNative.getDisguiseDisplayNameForAlias(currentIconAlias);
    } catch {
      return "Hida";
    }
  }, [currentIconAlias]);
  const settingsFooterWordmark = useMemo(() => {
    try {
      return `${HidaNative.getDisguiseDisplayNameForAlias(currentIconAlias).toUpperCase()} · v2`;
    } catch {
      return "HIDA · v2";
    }
  }, [currentIconAlias]);
  const themeLabel =
    themeMode === "light" ? "Light" : themeMode === "dark" ? "Dark" : "System";
  const unlockStyleLabel = unlockStyle === "keypad" ? "Keypad" : "Calculator";

  const handleUnlockStylePick = useCallback(
    (style: UnlockStyle) => {
      setShowUnlockStylePicker(false);
      if (isFake) {
        // Decoy mode: change the toggle visually but don't persist — the real
        // user's unlock screen must stay the way they configured it.
        setUnlockStyleState(style);
        fakeStub();
        return;
      }
      try {
        HidaNative.setUnlockStyle(style);
        setUnlockStyleState(style);
      } catch {
        Alert.alert("Unlock Style", "Couldn't save unlock style.");
        return;
      }
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    },
    [isFake, fakeStub]
  );

  const handleChangePin = useCallback((newPin: string) => {
    // Vault is already unlocked because the user is inside Settings (a vault route).
    // changePin re-wraps the in-memory ChaCha20 file key under PBKDF2(newPin) and
    // invalidates any biometric wrap (user must re-enroll biometric).
    const ok = HidaNative.changePin(newPin);
    if (!ok) {
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error).catch(() => {});
      Alert.alert("Change PIN", "Couldn't change PIN. Please try again.");
      return;
    }
    setShowPinDialog(false);
    if (biometricOn) {
      // Old biometric wrap was just invalidated. Tell the user to re-enroll.
      setBiometricOn(false);
      HidaNative.setBiometricEnabled(false);
      Alert.alert(
        "Biometric Unlock Reset",
        "Re-enable biometric unlock in Settings to use it with the new PIN.",
      );
    }
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  }, [biometricOn]);

  const handleChangeFakePin = useCallback((newPin: string) => {
    HidaNative.saveFakePin(newPin);
    setShowFakePinDialog(false);
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  }, []);

  const handleTimeoutChange = useCallback((ms: number) => {
    if (!isFake) HidaNative.setSessionTimeout(ms);
    setCurrentTimeout(ms);
    setShowTimeoutPicker(false);
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  }, [isFake]);

  const handleIconPick = useCallback((alias: string) => {
    setCurrentIconAlias(alias);
    setShowIconPicker(false);
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    if (isFake) return; // Switching the launcher tile from decoy mode would expose the real config.
    InteractionManager.runAfterInteractions(() => {
      HidaNative.switchIcon(alias);
    });
  }, [isFake]);

  const handleBiometricToggle = useCallback(async (next: boolean) => {
    if (next) {
      // Single CryptoObject-bound prompt does both: authenticates the user AND
      // authorises the cipher used to wrap the in-memory ChaCha key. Required
      // because the Keystore key has setUserAuthenticationRequired(true).
      try {
        const r = await HidaNative.authenticateAndEnrollBiometric(
          "Verify to enable biometric unlock",
          "Cancel",
        );
        if (!r.success) {
          if (r.error !== "user_cancel" && r.error !== "system_cancel" && r.error !== "app_cancel") {
            Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error).catch(() => {});
            Alert.alert(
              "Biometric Unlock",
              "Make sure a fingerprint/face is enrolled in system settings.",
            );
          }
          return;
        }
      } catch {
        Alert.alert("Biometric Unlock", "Couldn't show the biometric prompt.");
        return;
      }
      HidaNative.setBiometricEnabled(true);
      setBiometricOn(true);
    } else {
      HidaNative.disableBiometricUnlock();
      HidaNative.setBiometricEnabled(false);
      setBiometricOn(false);
    }
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  }, []);

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      {/* Header */}
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
        <NeoIconButton
          onPress={() => {
            Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
            router.back();
          }}
          ariaLabel="Back"
        >
          <ArrowLeft size={20} color={colors.ink} strokeWidth={2.2} />
        </NeoIconButton>
        <Text
          style={{
            flex: 1,
            fontFamily: "BricolageGrotesque",
            fontSize: 22,
            color: colors.ink,
            letterSpacing: -0.5,
          }}
        >
          Settings
        </Text>
      </View>

      {/* Single-page; if the device is short, ScrollView gracefully handles it. */}
      <ScrollView
        style={{ flex: 1 }}
        contentContainerStyle={{
          paddingHorizontal: 16,
          paddingBottom: 16,
          gap: 8,
        }}
        showsVerticalScrollIndicator={false}
      >
        {/* Decoy mode never exposes the real PIN flows. The Access PIN and Decoy
            PIN rows are removed entirely so an observer scrolling Settings doesn't
            see "Decoy PIN" — which would give away the dual-vault design. The
            "Security" section label is also dropped in decoy mode so Auto-Lock /
            Biometric appear under the next available label, preserving rhythm. */}
        {!isFake && (
          <>
            <SectionLabel>Security</SectionLabel>
            <SettingRow
              icon={<Lock size={16} color={colors.ink} strokeWidth={2.2} />}
              title="Access PIN"
              right={<ChevronIcon />}
              onPress={() => setShowPinDialog(true)}
            />
            <SettingRow
              icon={<EyeOff size={16} color={colors.ink} strokeWidth={2.2} />}
              title="Decoy PIN"
              right={<ChevronIcon />}
              onPress={() => setShowFakePinDialog(true)}
            />
          </>
        )}
        {isFake && <SectionLabel>Security</SectionLabel>}
        <SettingRow
          icon={
            unlockStyle === "keypad" ? (
              <KeyRound size={16} color={colors.ink} strokeWidth={2.2} />
            ) : (
              <Calculator size={16} color={colors.ink} strokeWidth={2.2} />
            )
          }
          title="Unlock Style"
          right={
            <>
              <MutedText>{unlockStyleLabel}</MutedText>
              <ChevronIcon />
            </>
          }
          onPress={() => setShowUnlockStylePicker(true)}
        />
        <SettingRow
          icon={<Timer size={16} color={colors.ink} strokeWidth={2.2} />}
          title="Auto-Lock"
          right={
            <>
              <MutedText>{currentTimeoutLabel}</MutedText>
              <ChevronIcon />
            </>
          }
          onPress={() => (isFake ? fakeStub() : setShowTimeoutPicker(true))}
        />
        <SettingRow
          icon={<Fingerprint size={16} color={colors.ink} strokeWidth={2.2} />}
          title="Biometric"
          right={
            <NeoToggle
              value={biometricOn}
              onValueChange={(next) => {
                if (isFake) {
                  // Don't persist; just animate the toggle for plausibility.
                  setBiometricOn(next);
                  fakeStub();
                  return;
                }
                handleBiometricToggle(next);
              }}
              ariaLabel="Toggle biometric"
            />
          }
        />

        <SectionLabel>Appearance</SectionLabel>
        <SettingRow
          icon={
            themeMode === "dark" ? (
              <Moon size={16} color={colors.ink} strokeWidth={2.2} />
            ) : themeMode === "system" ? (
              <Smartphone size={16} color={colors.ink} strokeWidth={2.2} />
            ) : (
              <Sun size={16} color={colors.ink} strokeWidth={2.2} />
            )
          }
          title="Theme"
          right={
            <>
              <MutedText>{themeLabel}</MutedText>
              <ChevronIcon />
            </>
          }
          onPress={() => setShowThemePicker(true)}
        />
        <SettingRow
          icon={<Palette size={16} color={colors.ink} strokeWidth={2.2} />}
          title="App Icon"
          right={
            <>
              <MutedText>{currentIconLabel}</MutedText>
              <ChevronIcon />
            </>
          }
          onPress={() => setShowIconPicker(true)}
        />
      </ScrollView>
      {/* Footer wordmark — fixed at bottom */}
      <View
        style={{
          alignItems: "center",
          paddingVertical: 10,
          paddingBottom: insets.bottom + 10,
          backgroundColor: colors.bg,
        }}
      >
        <Text
          style={{
            fontFamily: "JetBrainsMono",
            fontSize: 10,
            letterSpacing: 1.6,
            textTransform: "uppercase",
            color: colors.ink3,
          }}
        >
          {settingsFooterWordmark}
        </Text>
      </View>

      {/* Modals */}
      <PinModal
        visible={showPinDialog}
        title="Change PIN"
        variant="primary"
        onDismiss={() => setShowPinDialog(false)}
        onConfirm={handleChangePin}
      />
      <PinModal
        visible={showFakePinDialog}
        title="Decoy PIN"
        variant="decoy"
        onDismiss={() => setShowFakePinDialog(false)}
        onConfirm={handleChangeFakePin}
      />
      <TimeoutModal
        visible={showTimeoutPicker}
        current={currentTimeout}
        onPick={handleTimeoutChange}
        onDismiss={() => setShowTimeoutPicker(false)}
      />
      <IconPickerModal
        visible={showIconPicker}
        current={currentIconAlias}
        onPick={handleIconPick}
        onDismiss={() => setShowIconPicker(false)}
      />
      <ThemeModal
        visible={showThemePicker}
        current={themeMode}
        onPick={(m) => {
          setThemeMode(m);
          setShowThemePicker(false);
          Haptics.selectionAsync().catch(() => {});
        }}
        onDismiss={() => setShowThemePicker(false)}
      />
      <UnlockStyleModal
        visible={showUnlockStylePicker}
        current={unlockStyle}
        onPick={handleUnlockStylePick}
        onDismiss={() => setShowUnlockStylePicker(false)}
      />
    </View>
  );
}
