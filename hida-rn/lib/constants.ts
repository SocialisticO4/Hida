/** Animation timings (ms). Max interactive: 320 (matches design shake). */
export const TIMING = {
  press: 80, // neobrutalist press translate
  fast: 150,
  normal: 200,
  slow: 300,
  shake: 320,
} as const;

/** Spring presets for Reanimated. snappy = no bounce; responsive = light bounce. */
export const SPRING = {
  snappy: { damping: 20, stiffness: 300 },
  responsive: { damping: 15, stiffness: 150 },
} as const;

/**
 * Prefer import-time display name, then vault stored filename (UUID basename),
 * then the path tail. Use for titles, accessibility, and info sheets.
 */
export function displayVaultFileName(opts: {
  originalName?: string | null;
  storedName?: string | null;
  path: string;
}): string {
  const o = opts.originalName?.trim();
  if (o) return o;
  const s = opts.storedName?.trim();
  if (s) return s;
  const tail = opts.path.split(/[/\\]/).pop()?.trim();
  return tail && tail.length > 0 ? tail : "File";
}
