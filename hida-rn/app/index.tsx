import { useEffect } from "react";
import { Alert, View } from "react-native";
import { useRouter, useRootNavigationState } from "expo-router";
import * as HidaNative from "../modules/hida-native";
import { useTheme } from "../lib/theme";

/**
 * Root index — redirects to welcome or calculator based on first launch state.
 * Waits for root layout/navigator to be mounted before navigating.
 */
export default function Index() {
  const { colors } = useTheme();
  const router = useRouter();
  const rootNavigationState = useRootNavigationState();

  useEffect(() => {
    // Defer navigation until the root layout has mounted (Slot/navigator is ready)
    if (!rootNavigationState?.key) return;

    // Pre-pepper vault format: the wrap was written with PBKDF2-120k(plain PIN) and
    // is no longer accepted. Warn the user that resetting wipes their existing
    // photos, then route to /welcome for fresh setup. Without this, the calculator
    // unlock would silently reject the correct PIN.
    if (HidaNative.isVaultLegacyFormat()) {
      Alert.alert(
        "Security upgrade required",
        "Hida's encryption has been upgraded to a Keystore-bound key derivation that prevents offline brute force. Your existing vault uses the older format and cannot be migrated — your previously stored photos will be lost. Tap Continue to set up a new vault.",
        [
          {
            text: "Continue",
            style: "destructive",
            onPress: () => {
              HidaNative.wipeVault();
              router.replace("/welcome");
            },
          },
        ],
        { cancelable: false }
      );
      return;
    }

    const needsSetup = HidaNative.isFirstLaunch() || !HidaNative.hasPin();
    if (needsSetup) {
      router.replace("/welcome");
    } else {
      router.replace("/calculator");
    }
  }, [router, rootNavigationState?.key]);

  return <View style={{ flex: 1, backgroundColor: colors.bg }} />;
}
