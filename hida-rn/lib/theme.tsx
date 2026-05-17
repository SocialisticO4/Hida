import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { useColorScheme, type ColorSchemeName } from "react-native";
import * as HidaNative from "../modules/hida-native";

export type ThemeMode = "light" | "dark" | "system";
export type ResolvedMode = "light" | "dark";

export interface Palette {
  bg: string;
  surface: string;
  surface2: string;
  surface3: string;
  ink: string;
  ink2: string;
  ink3: string;
  danger: string;
  shadow: string;
  scrim: string;
}

const lightPalette: Palette = {
  bg: "#f5f1ea",
  surface: "#ffffff",
  surface2: "#ebe5da",
  surface3: "#d9d2c4",
  ink: "#16140f",
  ink2: "#6b6557",
  ink3: "#9a9285",
  danger: "#c8463a",
  shadow: "#16140f",
  scrim: "rgba(22,20,15,0.55)",
};

// Designer-quality dark palette. Not an inversion of cream/ink:
// the bg is a warm deep charcoal that echoes the cream's warmth
// (same hue family, opposite luminance), surfaces step UP for elevation,
// and the shadow plate is pure black so the offset reads as depth into a
// void rather than a cream "stamp". Borders use ink (warm off-white) — high
// contrast cream-on-charcoal is the neobrutalist signature, not a defect.
const darkPalette: Palette = {
  bg: "#14110d",
  // surface steps deliberately widened from the previous values: on AMOLED panels
  // the prior bg→surface gap (#14110d → #1d1913, ΔL ~0.7) was barely visible, so
  // the neo "elevation by surface step" read as flat. The new step is closer to
  // ~1.5 ΔL while keeping the warm-charcoal hue.
  surface: "#231e16",
  surface2: "#2d2719",
  surface3: "#3a321f",
  // Warm cream foreground (slightly desaturated vs pure white so it doesn't glare).
  ink: "#ece6d8",
  // Mid-tone warm gray — readable secondary text on bg and surfaces.
  ink2: "#a39884",
  // Tertiary / disabled. Dim but still legible against bg.
  ink3: "#6b6453",
  // Slightly desaturated red so it reads as urgent without neon glow.
  danger: "#d9655a",
  // Pure void: face sits on warm-tinted surface, shadow leaks pure black —
  // the chromatic gap between warm bg (#14110d) and neutral black makes the
  // offset visible as elevation rather than a competing colored stamp.
  shadow: "#000000",
  scrim: "rgba(0,0,0,0.78)",
};

interface ThemeContextValue {
  mode: ThemeMode;
  resolved: ResolvedMode;
  colors: Palette;
  setMode: (m: ThemeMode) => void;
  toggle: () => void;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

function readStoredThemeMode(): ThemeMode {
  try {
    const m = HidaNative.getThemeMode() as string;
    if (m === "light" || m === "dark" || m === "system") return m;
  } catch {
    /* native not ready */
  }
  return "system";
}

/** Resolve light/dark from stored mode + current system appearance.
 *  When in "system" mode and RN's useColorScheme() hasn't populated yet (null/undefined),
 *  fall back to the synchronous native read of Android Configuration.UI_MODE_NIGHT_MASK
 *  so the first frame doesn't always default to light. */
export function resolveAppearance(
  mode: ThemeMode,
  system: ColorSchemeName | null | undefined
): ResolvedMode {
  if (mode !== "system") return mode;
  if (system === "dark") return "dark";
  if (system === "light") return "light";
  // RN scheme not ready yet — ask Android directly.
  const nm = HidaNative.getSystemNightMode();
  if (nm === "dark") return "dark";
  return "light";
}

/** For the pre-font loading gate (outside ThemeProvider). */
export function getBootAppearance(system: ColorSchemeName | null | undefined): {
  resolved: ResolvedMode;
  colors: Palette;
} {
  const mode = readStoredThemeMode();
  const resolved = resolveAppearance(mode, system);
  return {
    resolved,
    colors: resolved === "dark" ? darkPalette : lightPalette,
  };
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const system = useColorScheme();
  const [mode, setModeState] = useState<ThemeMode>(readStoredThemeMode);

  const resolved: ResolvedMode = useMemo(
    () => resolveAppearance(mode, system),
    [mode, system]
  );

  const colors = resolved === "dark" ? darkPalette : lightPalette;

  const setMode = useCallback((m: ThemeMode) => {
    setModeState(m);
    try {
      HidaNative.setThemeMode(m);
    } catch {
      /* no-op */
    }
  }, []);
  const toggle = useCallback(() => {
    setMode(resolved === "dark" ? "light" : "dark");
  }, [resolved, setMode]);

  const value = useMemo(
    () => ({ mode, resolved, colors, setMode, toggle }),
    [mode, resolved, colors, setMode, toggle]
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext);
  if (!ctx) {
    // Safe fallback so non-wrapped surfaces (calculator screen, splash) don't crash.
    // Resolve synchronously via the native night-mode read so the fallback honours
    // the user's system pref instead of always being light.
    const resolved: ResolvedMode = HidaNative.getSystemNightMode() === "dark" ? "dark" : "light";
    return {
      mode: "system",
      resolved,
      colors: resolved === "dark" ? darkPalette : lightPalette,
      setMode: () => {},
      toggle: () => {},
    };
  }
  return ctx;
}

export const palettes = { light: lightPalette, dark: darkPalette };
