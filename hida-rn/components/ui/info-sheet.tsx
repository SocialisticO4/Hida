import { Modal, View, Text, Pressable } from "react-native";
import { useTheme } from "../../lib/theme";
import { NeoSurface, NeoButton } from "./neo";

interface InfoRow {
  label: string;
  value: string | null | undefined;
}

interface InfoSheetProps {
  visible: boolean;
  title: string;
  rows: InfoRow[];
  onClose: () => void;
}

export function InfoSheet({ visible, title, rows, onClose }: InfoSheetProps) {
  const { colors } = useTheme();
  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <Pressable
        onPress={onClose}
        style={{
          flex: 1,
          backgroundColor: colors.scrim,
          alignItems: "center",
          justifyContent: "center",
          padding: 24,
        }}
      >
        <Pressable onPress={() => {}} style={{ width: "100%", maxWidth: 380 }}>
          <NeoSurface background={colors.surface} shadowOffset={4}>
            <View style={{ paddingHorizontal: 20, paddingVertical: 18, gap: 14 }}>
              <Text
                style={{
                  fontFamily: "BricolageGrotesque",
                  fontSize: 18,
                  color: colors.ink,
                  letterSpacing: -0.3,
                }}
              >
                {title}
              </Text>
              <View style={{ gap: 10 }}>
                {rows.map((row) =>
                  row.value == null || row.value === "" ? null : (
                    <View key={row.label} style={{ gap: 2 }}>
                      <Text
                        style={{
                          fontFamily: "JetBrainsMono",
                          fontSize: 10,
                          letterSpacing: 1.4,
                          textTransform: "uppercase",
                          color: colors.ink2,
                        }}
                      >
                        {row.label}
                      </Text>
                      <Text
                        style={{
                          fontFamily: "SpaceGrotesk",
                          fontSize: 14,
                          color: colors.ink,
                        }}
                      >
                        {row.value}
                      </Text>
                    </View>
                  )
                )}
              </View>
              <View style={{ alignItems: "flex-end", marginTop: 4 }}>
                <NeoButton label="Close" variant="secondary" onPress={onClose} />
              </View>
            </View>
          </NeoSurface>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

/** Format a byte count as KB/MB/GB. */
export function formatBytes(bytes: number | null | undefined): string {
  if (bytes == null || bytes <= 0) return "—";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`;
}

/** Format an epoch ms as a friendly local date string. */
export function formatDate(epochMs: number | null | undefined): string {
  if (!epochMs) return "—";
  return new Date(epochMs).toLocaleString();
}

/** Format a millisecond duration as "M:SS" or "H:MM:SS". */
export function formatDuration(ms: number | null | undefined): string {
  if (ms == null || ms <= 0) return "—";
  const totalSec = Math.floor(ms / 1000);
  const h = Math.floor(totalSec / 3600);
  const m = Math.floor((totalSec % 3600) / 60);
  const s = totalSec % 60;
  if (h > 0) return `${h}:${m.toString().padStart(2, "0")}:${s.toString().padStart(2, "0")}`;
  return `${m}:${s.toString().padStart(2, "0")}`;
}
