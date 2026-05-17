import { useState, useEffect, useCallback, useRef } from "react";
import { View, Text, Pressable, ActivityIndicator } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { useAudioPlayer, useAudioPlayerStatus } from "expo-audio";
import { useRouter, useLocalSearchParams } from "expo-router";
import * as Haptics from "expo-haptics";
import {
  ArrowLeft,
  ExternalLink,
  Trash2,
  Play,
  Pause,
  RotateCcw,
  SkipBack,
  SkipForward,
  Music,
  Info,
} from "lucide-react-native";
import { useTheme } from "../lib/theme";
import { displayVaultFileName } from "../lib/constants";
import { HidaDialog } from "../components/ui/dialog";
import { NeoButton, NeoIconButton, NeoPressable, NeoSurface } from "../components/ui/neo";
import { InfoSheet, formatBytes, formatDate, formatDuration } from "../components/ui/info-sheet";
import * as HidaNative from "../modules/hida-native";
import type { MediaFile } from "../modules/hida-native";

function formatTime(ms: number): string {
  const totalSec = Math.floor(ms / 1000);
  const min = Math.floor(totalSec / 60);
  const sec = totalSec % 60;
  return `${min}:${sec.toString().padStart(2, "0")}`;
}

export default function AudioPlayerScreen() {
  const router = useRouter();
  const { path } = useLocalSearchParams<{ path: string }>();
  const filePath = decodeURIComponent(path ?? "");
  const insets = useSafeAreaInsets();
  const { colors } = useTheme();

  const [showDeleteDialog, setShowDeleteDialog] = useState(false);
  const [isEnded, setIsEnded] = useState(false);
  const progressBarWidth = useRef<number>(0);
  const [audioUri, setAudioUri] = useState<string | null>(null);
  const [originalName, setOriginalName] = useState<string | null>(null);
  const [showInfo, setShowInfo] = useState(false);
  const [mediaInfo, setMediaInfo] = useState<MediaFile | null>(null);
  // editlock §4: useAudioPlayer is initialised once with a stable null and the
  // URI is swapped via player.replace inside an effect. Do not collapse this back
  // into useAudioPlayer(audioUri ?? "") — that pattern caused
  // "shared object already released" crashes.
  const player = useAudioPlayer(null, { updateInterval: 250 });
  const status = useAudioPlayerStatus(player);

  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!filePath) return;
    let active = true;
    let mountedTempPath: string | null = null;
    HidaNative.getOriginalName(filePath)
      .then((n) => active && setOriginalName(n))
      .catch(() => {});
    HidaNative.getMediaInfo(filePath)
      .then((info) => active && info && setMediaInfo(info))
      .catch(() => {});
    (async () => {
      try {
        const uri = await HidaNative.getDecryptedVideoUri(filePath);
        if (!active) {
          HidaNative.deleteTempFile(uri.replace(/^file:\/\//, ""));
          return;
        }
        mountedTempPath = uri.replace(/^file:\/\//, "");
        setAudioUri(uri);
      } catch (e: any) {
        if (!active) return;
        setError(e?.message ?? "Decryption failed");
      }
    })();
    return () => {
      active = false;
      if (mountedTempPath) HidaNative.deleteTempFile(mountedTempPath);
      setAudioUri(null);
    };
  }, [filePath]);

  useEffect(() => {
    if (!audioUri) return;
    player.replace({ uri: audioUri });
  }, [audioUri, player]);

  const isPlaying = status.playing;
  const positionMs = status.currentTime * 1000;
  const durationMs = status.duration * 1000;

  // Detect track end: playing just stopped and position is near the end.
  useEffect(() => {
    if (!isPlaying && durationMs > 0 && positionMs >= durationMs - 250) {
      setIsEnded(true);
    }
  }, [isPlaying, positionMs, durationMs]);

  const togglePlay = useCallback(async () => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    try {
      if (isEnded) {
        await player.seekTo(0);
        setIsEnded(false);
        player.play();
      } else if (status.playing) {
        player.pause();
      } else {
        player.play();
      }
    } catch (e) {
      console.error("Audio play toggle failed:", e);
    }
  }, [player, status, isEnded]);

  const skip = useCallback(
    async (ms: number) => {
      Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
      const newPos = Math.max(0, Math.min(positionMs + ms, durationMs));
      try {
        await player.seekTo(newPos / 1000);
      } catch (e) {
        console.error("Audio seek failed:", e);
      }
    },
    [positionMs, durationMs, player]
  );

  const handleExport = useCallback(async () => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
    await HidaNative.exportMedia(filePath);
    router.back();
  }, [filePath, router]);

  const handleDelete = useCallback(async () => {
    try {
      player.pause();
    } catch {}
    await HidaNative.deleteMedia(filePath);
    router.back();
  }, [filePath, router, player]);

  const progress = durationMs > 0 ? positionMs / durationMs : 0;

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
        <NeoSurface background={colors.surface} shadowOffset={4}>
          <View style={{ width: 80, height: 80, alignItems: "center", justifyContent: "center" }}>
            <Music size={36} color={colors.ink} strokeWidth={2.2} />
          </View>
        </NeoSurface>
        <Text style={{ fontFamily: "BricolageGrotesque", fontSize: 18, color: colors.ink }}>
          Unable to play audio
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

  if (!audioUri) {
    return (
      <View style={{ flex: 1, backgroundColor: colors.bg, alignItems: "center", justifyContent: "center" }}>
        <ActivityIndicator size="large" color={colors.ink} />
        <Text style={{ fontFamily: "JetBrainsMono", fontSize: 11, color: colors.ink2, marginTop: 14, letterSpacing: 1.4, textTransform: "uppercase" }}>
          Decrypting audio
        </Text>
      </View>
    );
  }

  const displayAudioName = displayVaultFileName({
    originalName: originalName ?? mediaInfo?.originalName ?? null,
    storedName: mediaInfo?.name ?? null,
    path: filePath,
  });

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      {/* Header */}
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
        <View style={{ flex: 1 }} />
        <NeoIconButton ariaLabel="Audio info" onPress={() => setShowInfo(true)}>
          <Info size={20} color={colors.ink} strokeWidth={2.2} />
        </NeoIconButton>
        <NeoIconButton ariaLabel="Export audio" onPress={handleExport}>
          <ExternalLink size={20} color={colors.ink} strokeWidth={2.2} />
        </NeoIconButton>
        <NeoIconButton
          ariaLabel="Delete audio"
          background={colors.danger}
          onPress={() => setShowDeleteDialog(true)}
        >
          <Trash2 size={20} color="#ffffff" strokeWidth={2.2} />
        </NeoIconButton>
      </View>

      {/* Center art tile + title */}
      <View style={{ flex: 1, alignItems: "center", justifyContent: "center", paddingHorizontal: 32, gap: 18 }}>
        <NeoSurface background={colors.surface2} shadowOffset={6}>
          <View
            style={{
              width: 200,
              height: 200,
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <Music size={72} color={colors.ink} strokeWidth={1.8} />
          </View>
        </NeoSurface>
        <Text
          numberOfLines={2}
          style={{
            fontFamily: "BricolageGrotesque",
            fontSize: 22,
            color: colors.ink,
            textAlign: "center",
            letterSpacing: -0.4,
          }}
        >
          {displayAudioName}
        </Text>
      </View>

      {/* Controls */}
      <View style={{ paddingHorizontal: 24, paddingBottom: insets.bottom + 24, gap: 14 }}>
        {/* Progress bar — square, ink-bordered, tap to seek */}
        <Pressable
          style={{ height: 20, justifyContent: "center" }}
          onLayout={(e) => { progressBarWidth.current = e.nativeEvent.layout.width; }}
          onPress={(e) => {
            const barWidth = progressBarWidth.current || 1;
            const newProgress = Math.max(0, Math.min(e.nativeEvent.locationX / barWidth, 1));
            const newPos = newProgress * (durationMs / 1000);
            player.seekTo(newPos).catch(() => {});
            setIsEnded(false);
          }}
          accessibilityLabel="Seek audio"
        >
          <View
            style={{
              height: 8,
              borderWidth: 2,
              borderColor: colors.ink,
              backgroundColor: colors.bg,
            }}
          >
            <View style={{ height: "100%", width: `${progress * 100}%`, backgroundColor: colors.ink }} />
          </View>
        </Pressable>
        <View style={{ flexDirection: "row", justifyContent: "space-between" }}>
          <Text style={{ fontFamily: "JetBrainsMono", fontSize: 11, color: colors.ink2, letterSpacing: 1 }}>
            {formatTime(positionMs)}
          </Text>
          <Text style={{ fontFamily: "JetBrainsMono", fontSize: 11, color: colors.ink2, letterSpacing: 1 }}>
            {formatTime(durationMs)}
          </Text>
        </View>

        {/* Transport */}
        <View style={{ flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 16, marginTop: 6 }}>
          <NeoIconButton ariaLabel="Skip back 10 seconds" size={56} onPress={() => skip(-10_000)}>
            <SkipBack size={24} color={colors.ink} strokeWidth={2.2} />
          </NeoIconButton>

          <View style={{ width: 80 }}>
            <NeoPressable
              onPress={togglePlay}
              background={colors.ink}
              shadowOffset={5}
              pressedShadowOffset={2}
              style={{
                height: 80,
                alignItems: "center",
                justifyContent: "center",
              }}
              accessibilityLabel={isEnded ? "Replay" : isPlaying ? "Pause" : "Play"}
            >
              {isEnded ? (
                <RotateCcw size={32} color={colors.bg} />
              ) : isPlaying ? (
                <Pause size={32} color={colors.bg} fill={colors.bg} />
              ) : (
                <Play size={32} color={colors.bg} fill={colors.bg} style={{ marginLeft: 3 }} />
              )}
            </NeoPressable>
          </View>

          <NeoIconButton ariaLabel="Skip forward 10 seconds" size={56} onPress={() => skip(10_000)}>
            <SkipForward size={24} color={colors.ink} strokeWidth={2.2} />
          </NeoIconButton>
        </View>
      </View>

      <InfoSheet
        visible={showInfo}
        title="Audio"
        rows={[
          { label: "Name", value: displayAudioName },
          { label: "Type", value: mediaInfo?.ext ? `.${mediaInfo.ext}` : null },
          { label: "Size", value: formatBytes(mediaInfo?.size) },
          { label: "Duration", value: formatDuration(durationMs) },
          { label: "Modified", value: formatDate(mediaInfo?.lastModified) },
        ]}
        onClose={() => setShowInfo(false)}
      />

      <HidaDialog
        visible={showDeleteDialog}
        title="Delete audio?"
        message="This will permanently remove the audio file from your vault."
        confirmText="Delete"
        dismissText="Cancel"
        confirmDestructive
        onConfirm={handleDelete}
        onDismiss={() => setShowDeleteDialog(false)}
      />

    </View>
  );
}
