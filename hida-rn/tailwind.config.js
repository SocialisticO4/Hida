/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./app/**/*.{js,jsx,ts,tsx}", "./components/**/*.{js,jsx,ts,tsx}"],
  presets: [require("nativewind/preset")],
  theme: {
    extend: {
      colors: {
        // Cream paper neobrutalist palette — single source of truth.
        // Mirrors hida-tokens.css. Light theme only for now (matches calculator).
        bg: "#f5f1ea",
        surface: "#ffffff",
        "surface-2": "#ebe5da",
        "surface-3": "#d9d2c4",
        ink: "#16140f",
        "ink-2": "#6b6557",
        "ink-3": "#9a9285",
        danger: "#c8463a",

        // Aliases kept so existing class names still resolve.
        background: "#f5f1ea",
        foreground: "#16140f",
        card: { DEFAULT: "#ffffff", foreground: "#16140f" },
        popover: { DEFAULT: "#ffffff", foreground: "#16140f" },
        primary: { DEFAULT: "#16140f", foreground: "#f5f1ea" },
        secondary: { DEFAULT: "#ebe5da", foreground: "#16140f" },
        muted: { DEFAULT: "#ebe5da", foreground: "#6b6557" },
        accent: { DEFAULT: "#16140f", foreground: "#f5f1ea" },
        destructive: { DEFAULT: "#c8463a", foreground: "#ffffff" },
        border: "#16140f",
        input: "#16140f",
        ring: "#16140f",
      },
      borderRadius: {
        // Neobrutalist = square. We keep these as 0 so legacy "rounded-*" classes
        // collapse to 0 rather than producing a rounded brutalist hybrid.
        none: "0px",
        sm: "0px",
        md: "0px",
        lg: "0px",
        xl: "0px",
        "2xl": "0px",
        "3xl": "0px",
        full: "9999px",
      },
      fontFamily: {
        // sans = body/buttons; display = headings; mono = meta/labels.
        sans: ["SpaceGrotesk"],
        display: ["BricolageGrotesque"],
        mono: ["JetBrainsMono"],
      },
      fontSize: {
        xs: ["12px", { lineHeight: "16px" }],
        sm: ["14px", { lineHeight: "20px" }],
        base: ["16px", { lineHeight: "24px" }],
        lg: ["18px", { lineHeight: "26px" }],
        xl: ["20px", { lineHeight: "28px" }],
        "2xl": ["24px", { lineHeight: "30px" }],
        "3xl": ["30px", { lineHeight: "34px" }],
      },
    },
  },
  plugins: [],
};
