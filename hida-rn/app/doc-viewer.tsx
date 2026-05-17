import { useState, useEffect, useCallback } from "react";
import {
  View,
  Text,
  ActivityIndicator,
  ScrollView,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { useRouter, useLocalSearchParams } from "expo-router";
import * as Haptics from "expo-haptics";
import {
  ArrowLeft,
  ExternalLink,
  Trash2,
  FileText,
  Info,
} from "lucide-react-native";
import { useTheme } from "../lib/theme";
import { displayVaultFileName } from "../lib/constants";
import { HidaDialog } from "../components/ui/dialog";
import {
  NeoButton,
  NeoIconButton,
  NeoSurface,
} from "../components/ui/neo";
import { InfoSheet, formatBytes, formatDate } from "../components/ui/info-sheet";
import * as HidaNative from "../modules/hida-native";
import type { MediaFile } from "../modules/hida-native";

export default function DocViewerScreen() {
  const router = useRouter();
  const { path } = useLocalSearchParams<{ path: string }>();
  const filePath = decodeURIComponent(path ?? "");
  const insets = useSafeAreaInsets();
  const { colors } = useTheme();

  const ext = filePath.split(".").pop()?.toLowerCase() ?? "";
  const isTextFile = ["txt", "csv", "md", "log", "json", "xml"].includes(ext);

  const [decryptedPath, setDecryptedPath] = useState<string | null>(null);
  const [textContent, setTextContent] = useState<string | null>(null);
  const [showDeleteDialog, setShowDeleteDialog] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [originalName, setOriginalName] = useState<string | null>(null);
  const [showInfo, setShowInfo] = useState(false);
  const [mediaInfo, setMediaInfo] = useState<MediaFile | null>(null);

  useEffect(() => {
    if (!filePath) return;
    let active = true;
    let mountedTempPath: string | null = null;
    (async () => {
      try {
        HidaNative.getOriginalName(filePath)
          .then((n) => active && setOriginalName(n))
          .catch(() => {});
        HidaNative.getMediaInfo(filePath)
          .then((info) => active && info && setMediaInfo(info))
          .catch(() => {});
        const uri = await HidaNative.getDecryptedVideoUri(filePath);
        const cleanPath = uri.replace(/^file:\/\//, "");
        if (!active) {
          HidaNative.deleteTempFile(cleanPath);
          return;
        }
        mountedTempPath = cleanPath;
        setDecryptedPath(cleanPath);
        if (isTextFile) {
          const response = await fetch(uri);
          const text = await response.text();
          if (active) setTextContent(text);
        }
      } catch (e: any) {
        if (!active) return;
        setError(e?.message ?? "Decryption failed");
      }
    })();
    return () => {
      active = false;
      // Clear plaintext text content out of JS state and delete the temp file.
      setTextContent(null);
      setDecryptedPath(null);
      if (mountedTempPath) HidaNative.deleteTempFile(mountedTempPath);
    };
  }, [filePath, isTextFile]);

  const handleOpenExternal = useCallback(async () => {
    if (!decryptedPath) return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
    try {
      await HidaNative.openDocumentIntent(decryptedPath);
    } catch (e) {
      console.error("openDocumentIntent failed:", e);
    }
  }, [decryptedPath]);

  const handleExport = useCallback(async () => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
    await HidaNative.exportMedia(filePath);
    router.back();
  }, [filePath, router]);

  const handleDelete = useCallback(async () => {
    await HidaNative.deleteMedia(filePath);
    router.back();
  }, [filePath, router]);

  const fileName = displayVaultFileName({
    originalName: originalName ?? mediaInfo?.originalName ?? null,
    storedName: mediaInfo?.name ?? null,
    path: filePath,
  });

  function HeaderBar() {
    return (
      <View
        style={{
          paddingTop: insets.top + 10,
          paddingHorizontal: 16,
          paddingBottom: 8,
          flexDirection: "row",
          alignItems: "center",
          gap: 10,
        }}
      >
        <NeoIconButton
          ariaLabel="Go back"
          onPress={() => {
            Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
            router.back();
          }}
        >
          <ArrowLeft size={20} color={colors.ink} strokeWidth={2.2} />
        </NeoIconButton>
        <View style={{ flex: 1, minWidth: 0 }}>
          <Text
            numberOfLines={1}
            style={{
              fontFamily: "BricolageGrotesque",
              fontSize: 18,
              color: colors.ink,
              letterSpacing: -0.3,
            }}
          >
            {fileName}
          </Text>
          <Text
            style={{
              fontFamily: "JetBrainsMono",
              fontSize: 10,
              letterSpacing: 1.2,
              textTransform: "uppercase",
              color: colors.ink2,
              marginTop: 2,
            }}
          >
            .{ext}
          </Text>
        </View>
        <NeoIconButton ariaLabel="Document info" onPress={() => setShowInfo(true)}>
          <Info size={20} color={colors.ink} strokeWidth={2.2} />
        </NeoIconButton>
        <NeoIconButton ariaLabel="Export document" onPress={handleExport}>
          <ExternalLink size={20} color={colors.ink} strokeWidth={2.2} />
        </NeoIconButton>
        <NeoIconButton
          ariaLabel="Delete document"
          background={colors.danger}
          onPress={() => setShowDeleteDialog(true)}
        >
          <Trash2 size={20} color="#ffffff" strokeWidth={2.2} />
        </NeoIconButton>
      </View>
    );
  }

  if (error) {
    return (
      <View
        style={{
          flex: 1,
          backgroundColor: colors.bg,
          alignItems: "center",
          justifyContent: "center",
          padding: 32,
          gap: 14,
        }}
      >
        <NeoSurface background={colors.surface}>
          <View style={{ width: 80, height: 80, alignItems: "center", justifyContent: "center" }}>
            <FileText size={36} color={colors.ink} strokeWidth={2.2} />
          </View>
        </NeoSurface>
        <Text style={{ fontFamily: "BricolageGrotesque", fontSize: 18, color: colors.ink }}>
          Unable to open document
        </Text>
        <Text
          style={{
            fontFamily: "SpaceGrotesk",
            fontSize: 13,
            color: colors.ink2,
            textAlign: "center",
          }}
        >
          {error}
        </Text>
        <NeoButton label="Go back" variant="secondary" onPress={() => router.back()} />
      </View>
    );
  }

  if (!decryptedPath) {
    return (
      <View style={{ flex: 1, backgroundColor: colors.bg, alignItems: "center", justifyContent: "center" }}>
        <ActivityIndicator size="large" color={colors.ink} />
        <Text
          style={{
            fontFamily: "JetBrainsMono",
            fontSize: 11,
            color: colors.ink2,
            marginTop: 14,
            letterSpacing: 1.4,
            textTransform: "uppercase",
          }}
        >
          Decrypting document
        </Text>
      </View>
    );
  }

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <HeaderBar />

      {isTextFile && textContent !== null ? (
        <ScrollView
          style={{ flex: 1, paddingHorizontal: 16 }}
          contentContainerStyle={{ paddingBottom: insets.bottom + 24 }}
        >
          <NeoSurface background={colors.surface}>
            <View style={{ padding: 16 }}>
              <Text
                selectable
                style={{
                  fontFamily: "JetBrainsMono",
                  fontSize: 13,
                  color: colors.ink,
                  lineHeight: 20,
                }}
              >
                {textContent}
              </Text>
            </View>
          </NeoSurface>
        </ScrollView>
      ) : (
        <View style={{ flex: 1, alignItems: "center", justifyContent: "center", padding: 32, gap: 18 }}>
          <NeoSurface background={colors.surface2} shadowOffset={6}>
            <View style={{ width: 140, height: 140, alignItems: "center", justifyContent: "center" }}>
              <FileText size={56} color={colors.ink} strokeWidth={2} />
            </View>
          </NeoSurface>
          <Text
            numberOfLines={2}
            style={{
              fontFamily: "BricolageGrotesque",
              fontSize: 18,
              color: colors.ink,
              textAlign: "center",
            }}
          >
            {fileName}
          </Text>
          <Text
            style={{
              fontFamily: "JetBrainsMono",
              fontSize: 10,
              letterSpacing: 1.4,
              textTransform: "uppercase",
              color: colors.ink2,
            }}
          >
            .{ext} document
          </Text>
          <View style={{ flexDirection: "row", gap: 10, marginTop: 12 }}>
            <NeoButton
              label="Open"
              variant="secondary"
              onPress={handleOpenExternal}
              containerStyle={{ minWidth: 120 }}
            />
            <NeoButton
              label="Export"
              onPress={handleExport}
              containerStyle={{ minWidth: 120 }}
            />
          </View>
        </View>
      )}

      <InfoSheet
        visible={showInfo}
        title="Document"
        rows={[
          { label: "Name", value: fileName },
          { label: "Type", value: `.${ext}` },
          { label: "Size", value: formatBytes(mediaInfo?.size) },
          { label: "Modified", value: formatDate(mediaInfo?.lastModified) },
        ]}
        onClose={() => setShowInfo(false)}
      />

      <HidaDialog
        visible={showDeleteDialog}
        title="Delete document?"
        message="This will permanently remove the document from your vault."
        confirmText="Delete"
        dismissText="Cancel"
        confirmDestructive
        onConfirm={handleDelete}
        onDismiss={() => setShowDeleteDialog(false)}
      />
    </View>
  );
}
