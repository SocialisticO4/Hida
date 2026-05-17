import { Modal, View, Text, Pressable } from "react-native";
import Animated, { FadeIn, ZoomIn } from "react-native-reanimated";
import { useTheme } from "../../lib/theme";
import { NeoButton } from "./neo";
import { TIMING } from "../../lib/constants";

interface HidaDialogProps {
  visible: boolean;
  title: string;
  message: string;
  confirmText?: string;
  dismissText?: string;
  confirmDestructive?: boolean;
  onConfirm: () => void;
  onDismiss: () => void;
}

export function HidaDialog({
  visible,
  title,
  message,
  confirmText = "OK",
  dismissText = "Cancel",
  confirmDestructive = false,
  onConfirm,
  onDismiss,
}: HidaDialogProps) {
  const { colors } = useTheme();
  const accentBar = confirmDestructive ? colors.danger : colors.ink;

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
          alignItems: "center",
          justifyContent: "center",
          padding: 24,
        }}
      >
        <Pressable
          accessibilityLabel="Dismiss dialog"
          onPress={onDismiss}
          style={{ position: "absolute", inset: 0 }}
        />
        <Animated.View
          entering={ZoomIn.duration(TIMING.fast)}
          style={{ width: "100%", maxWidth: 360 }}
        >
          <View
            style={{
              backgroundColor: colors.surface,
              borderWidth: 2,
              borderColor: colors.ink,
              borderLeftWidth: 8,
              borderLeftColor: accentBar,
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
                fontFamily: "BricolageGrotesque",
                fontSize: 22,
                fontWeight: "700",
                color: colors.ink,
                marginBottom: 8,
              }}
            >
              {title}
            </Text>
            <Text
              style={{
                fontFamily: "SpaceGrotesk",
                fontSize: 14,
                color: colors.ink2,
                marginBottom: 18,
                lineHeight: 20,
              }}
            >
              {message}
            </Text>
            <View style={{ flexDirection: "row", gap: 10, justifyContent: "flex-end" }}>
              <NeoButton
                label={dismissText}
                variant="secondary"
                onPress={onDismiss}
                containerStyle={{ flexShrink: 1 }}
                style={{ paddingVertical: 10, paddingHorizontal: 16 }}
                textStyle={{ fontSize: 14 }}
              />
              <NeoButton
                label={confirmText}
                variant={confirmDestructive ? "danger" : "primary"}
                onPress={onConfirm}
                containerStyle={{ flexShrink: 1 }}
                style={{ paddingVertical: 10, paddingHorizontal: 18 }}
                textStyle={{ fontSize: 14 }}
              />
            </View>
          </View>
        </Animated.View>
      </Animated.View>
    </Modal>
  );
}
