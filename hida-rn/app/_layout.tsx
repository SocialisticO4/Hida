import "../global.css";

import { useEffect, useState, useRef, useCallback } from "react";
import {
  View,
  Text,
  AppState,
  Platform,
  useColorScheme,
  type AppStateStatus,
} from "react-native";
import { Stack, useRouter, usePathname } from "expo-router";
import { StatusBar } from "expo-status-bar";
import * as Font from "expo-font";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { GestureHandlerRootView } from "react-native-gesture-handler";
import * as HidaNative from "../modules/hida-native";
import { ThemeProvider, useTheme, getBootAppearance } from "../lib/theme";

/** Routes where the vault should follow device rotation (landscape / sensor). */
function shouldUnlockOrientationForPathname(pathname: string): boolean {
  const base = pathname.split("?")[0] ?? pathname;
  return (
    base === "/gallery" ||
    base.startsWith("/gallery/") ||
    base === "/image-viewer" ||
    base.startsWith("/image-viewer/") ||
    base === "/video-player" ||
    base.startsWith("/video-player/")
  );
}

async function applyOrientationPolicy(pathname: string): Promise<void> {
  if (Platform.OS !== "android") return;
  let SO: typeof import("expo-screen-orientation") | null = null;
  try {
    SO = require("expo-screen-orientation") as typeof import("expo-screen-orientation");
  } catch {
    return;
  }
  try {
    if (shouldUnlockOrientationForPathname(pathname)) {
      await SO.unlockAsync();
    } else {
      await SO.lockAsync(SO.OrientationLock.PORTRAIT_UP);
    }
  } catch {
    /* TurboModule missing until native rebuild — silent no-op */
  }
}

/**
 * Status bar + Stack contentStyle need to track the active theme so the system
 * bar icons stay legible and the navigator's transition background matches the
 * destination screen. Lives inside ThemeProvider.
 */
function ThemedStack() {
  const { resolved, colors } = useTheme();
  return (
    <SafeAreaProvider>
      <StatusBar style={resolved === "dark" ? "light" : "dark"} />
      <Stack
        screenOptions={{
          headerShown: false,
          contentStyle: { backgroundColor: colors.bg },
          animation: "fade",
          animationDuration: 200,
        }}
      >
        <Stack.Screen name="index" options={{ animation: "none" }} />
        <Stack.Screen name="calculator" options={{ animation: "none" }} />
        <Stack.Screen name="welcome" options={{ animation: "fade" }} />
        <Stack.Screen name="gallery" options={{ animation: "fade" }} />
        <Stack.Screen
          name="image-viewer"
          options={{ animation: "fade", presentation: "fullScreenModal" }}
        />
        <Stack.Screen
          name="video-player"
          options={{ animation: "fade", presentation: "fullScreenModal" }}
        />
        <Stack.Screen
          name="audio-player"
          options={{ animation: "fade", presentation: "fullScreenModal" }}
        />
        <Stack.Screen
          name="doc-viewer"
          options={{ animation: "fade", presentation: "fullScreenModal" }}
        />
        <Stack.Screen
          name="settings"
          options={{ animation: "fade" }}
        />
      </Stack>
    </SafeAreaProvider>
  );
}

// Global error capture — route every JS/RN error to logcat so native + JS both show up
// under a single tag when debugging lock-screen / nav issues.
const g: any = globalThis as any;
if (!g.__HIDA_ERROR_HOOKS_INSTALLED__) {
  g.__HIDA_ERROR_HOOKS_INSTALLED__ = true;
  const origHandler = (g.ErrorUtils && g.ErrorUtils.getGlobalHandler)
    ? g.ErrorUtils.getGlobalHandler()
    : null;
  g.ErrorUtils?.setGlobalHandler?.((err: unknown, isFatal?: boolean) => {
    console.error(`[HIDA][globalError fatal=${!!isFatal}]`, err);
    if (origHandler) try { origHandler(err, isFatal); } catch {}
  });
  if (typeof g.addEventListener === "function") {
    try {
      g.addEventListener("unhandledrejection", (ev: any) => {
        console.error("[HIDA][unhandledRejection]", ev?.reason ?? ev);
      });
    } catch {}
  }
}

export default function RootLayout() {
  const [fontsLoaded, setFontsLoaded] = useState(false);
  const router = useRouter();
  const pathname = usePathname();
  const appState = useRef(AppState.currentState);
  const systemScheme = useColorScheme();
  const boot = getBootAppearance(systemScheme);

  const isVaultPath =
    pathname.startsWith("/gallery") ||
    pathname.startsWith("/settings") ||
    pathname.startsWith("/image-viewer") ||
    pathname.startsWith("/video-player") ||
    pathname.startsWith("/audio-player") ||
    pathname.startsWith("/doc-viewer");

  // Calculator / welcome / settings stay portrait; gallery + fullscreen viewers follow rotation.
  useEffect(() => {
    void applyOrientationPolicy(pathname);
  }, [pathname]);

  // Native ACTION_SCREEN_OFF locks only when this flag is true and session mode is "when device locks".
  useEffect(() => {
    try {
      HidaNative.setVaultUnlockedRouteActive(isVaultPath);
    } catch {
      /* no-op */
    }
    return () => {
      try {
        HidaNative.setVaultUnlockedRouteActive(false);
      } catch {
        /* no-op */
      }
    };
  }, [isVaultPath]);
  // Load fonts. Bricolage / Space Grotesk / JetBrains Mono are the design-system trio.
  // Geist is kept loaded as a fallback so any straggler `font-sans` consumer still has it.
  useEffect(() => {
    Font.loadAsync({
      BricolageGrotesque: require("../assets/fonts/BricolageGrotesque.ttf"),
      SpaceGrotesk: require("../assets/fonts/SpaceGrotesk.ttf"),
      JetBrainsMono: require("../assets/fonts/JetBrainsMono.ttf"),
      Geist: require("../assets/fonts/Geist-Regular.ttf"),
      "Geist-SemiBold": require("../assets/fonts/Geist-SemiBold.ttf"),
    }).then(() => setFontsLoaded(true));
  }, []);

  // Session timeout: lock vault when returning from background (finite timeouts), or after
  // device lock when session mode is "when device locks" (native SCREEN_OFF → pending flag).
  useEffect(() => {
    const subscription = AppState.addEventListener(
      "change",
      (nextState: AppStateStatus) => {
        // Going to background → mark paused only for finite idle timeouts (not "when phone locks").
        if (
          appState.current === "active" &&
          (nextState === "background" || nextState === "inactive")
        ) {
          let timeoutMs = 60000;
          try {
            timeoutMs = HidaNative.getSessionTimeout();
          } catch {
            /* use default */
          }
          if (isVaultPath && timeoutMs > 0) {
            HidaNative.markAppPaused();
          }
        }

        // Returning to foreground → check if session expired
        if (
          nextState === "active" &&
          (appState.current === "background" ||
            appState.current === "inactive")
        ) {
          void applyOrientationPolicy(pathname);
          const sessionExpired = HidaNative.isSessionExpired();
          if (sessionExpired) {
            // lockVault wipes plaintext temps + the in-memory ChaCha key.
            // clearSession resets the auto-lock pref state machine.
            HidaNative.lockVault();
            HidaNative.clearSession();
            router.replace("/calculator");
          } else {
            HidaNative.clearPausedFlag();
          }
        }

        appState.current = nextState;
      }
    );

    return () => subscription.remove();
  }, [isVaultPath, pathname, router]);

  // Foreground idle auto-lock: if the user sits inside the vault without any
  // touch for `sessionTimeout` ms, lock back to the calculator.
  const idleTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Idle countdown runs only when timeout > 0; "when phone locks" (−1) uses SCREEN_OFF instead.
  const armIdleTimer = useCallback(() => {
    if (idleTimerRef.current) {
      clearTimeout(idleTimerRef.current);
      idleTimerRef.current = null;
    }
    if (!isVaultPath) return;
    const timeoutMs = HidaNative.getSessionTimeout();
    if (timeoutMs <= 0) return;
    idleTimerRef.current = setTimeout(() => {
      HidaNative.lockVault();
      HidaNative.clearSession();
      router.replace("/calculator");
    }, timeoutMs);
  }, [isVaultPath, pathname, router]);

  useEffect(() => {
    armIdleTimer();
    return () => {
      if (idleTimerRef.current) {
        clearTimeout(idleTimerRef.current);
        idleTimerRef.current = null;
      }
    };
  }, [armIdleTimer]);

  const handleUserActivity = useCallback(() => {
    if (isVaultPath && idleTimerRef.current) {
      armIdleTimer();
    }
    return false;
  }, [armIdleTimer, isVaultPath]);

  if (!fontsLoaded) {
    return (
      <>
        <StatusBar style={boot.resolved === "dark" ? "light" : "dark"} />
        <View
          style={{
            flex: 1,
            backgroundColor: boot.colors.bg,
            alignItems: "center",
            justifyContent: "center",
          }}
        >
          <Text style={{ color: boot.colors.ink2, fontSize: 15 }}>Loading…</Text>
        </View>
      </>
    );
  }

  return (
    <ThemeProvider>
      <GestureHandlerRootView style={{ flex: 1 }}>
        <View
          style={{ flex: 1 }}
          onStartShouldSetResponderCapture={handleUserActivity}
          onMoveShouldSetResponderCapture={handleUserActivity}
        >
          <ThemedStack />
        </View>
      </GestureHandlerRootView>
    </ThemeProvider>
  );
}
