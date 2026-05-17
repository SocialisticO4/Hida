import { useState, useEffect, useCallback } from "react";
import {
  View,
  Text,
  Pressable,
  ActivityIndicator,
  useWindowDimensions,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import Animated, {
  FadeIn,
  FadeOut,
  runOnJS,
  useAnimatedStyle,
  useSharedValue,
  withTiming,
} from "react-native-reanimated";
import {
  Gesture,
  GestureDetector,
} from "react-native-gesture-handler";
import { useRouter, useLocalSearchParams } from "expo-router";
import * as Haptics from "expo-haptics";
import {
  ArrowLeft,
  ExternalLink,
  Trash2,
  Play,
  Image as ImageIcon,
  Info,
} from "lucide-react-native";
import { TIMING, displayVaultFileName } from "../lib/constants";
import { HidaDialog } from "../components/ui/dialog";
import { InfoSheet, formatBytes, formatDate } from "../components/ui/info-sheet";
import * as HidaNative from "../modules/hida-native";
import type { MediaFile } from "../modules/hida-native";
import { StatusBar } from "expo-status-bar";

export default function ImageViewerScreen() {
  const router = useRouter();
  const { path } = useLocalSearchParams<{ path: string }>();
  const filePath = decodeURIComponent(path ?? "");
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();

  const [imageUri, setImageUri] = useState<string | null>(null);
  const [showControls, setShowControls] = useState(true);
  const [showDeleteDialog, setShowDeleteDialog] = useState(false);
  const [isMotionPhoto, setIsMotionPhoto] = useState(false);
  const [playingVideo, setPlayingVideo] = useState(false);
  const [videoUri, setVideoUri] = useState<string | null>(null);
  const [loadingVideo, setLoadingVideo] = useState(false);
  const [showInfo, setShowInfo] = useState(false);
  const [mediaInfo, setMediaInfo] = useState<MediaFile | null>(null);
  const [peekOriginalName, setPeekOriginalName] = useState<string | null>(null);

  const displayPhotoName = displayVaultFileName({
    originalName: mediaInfo?.originalName ?? peekOriginalName ?? null,
    storedName: mediaInfo?.name ?? null,
    path: filePath,
  });

  // Zoom/pan state
  const scale = useSharedValue(1);
  const savedScale = useSharedValue(1);
  const translateX = useSharedValue(0);
  const translateY = useSharedValue(0);
  const savedTranslateX = useSharedValue(0);
  const savedTranslateY = useSharedValue(0);

  // Load decrypted image. The temp-file path is captured so unmount can delete
  // it — without that, a force-stop or a process kill leaves the plaintext
  // bytes on disk until the next launch's cleanup pass.
  useEffect(() => {
    if (!filePath) return;
    let active = true;
    let mountedTempPath: string | null = null;
    HidaNative.decryptThumbnail(filePath)
      .then((tmp) => {
        if (!active) {
          // Lock fired before the promise settled. Clean up immediately.
          HidaNative.deleteTempFile(tmp);
          return;
        }
        mountedTempPath = tmp;
        setImageUri(tmp);
      })
      .catch(() => {});
    setIsMotionPhoto(HidaNative.isMotionPhoto(filePath));
    HidaNative.getOriginalName(filePath)
      .then((n) => active && setPeekOriginalName(n))
      .catch(() => {});
    // Single-item lookup — avoids decrypting every filename sidecar in the vault.
    HidaNative.getMediaInfo(filePath)
      .then((info) => active && info && setMediaInfo(info))
      .catch(() => {});
    return () => {
      active = false;
      if (mountedTempPath) HidaNative.deleteTempFile(mountedTempPath);
      setImageUri(null);
    };
  }, [filePath]);

  const animatedImageStyle = useAnimatedStyle(() => ({
    transform: [
      { translateX: translateX.value },
      { translateY: translateY.value },
      { scale: scale.value },
    ],
  }));

  // Pinch gesture
  const pinchGesture = Gesture.Pinch()
    .onUpdate((e) => {
      scale.value = Math.min(Math.max(savedScale.value * e.scale, 1), 5);
    })
    .onEnd(() => {
      savedScale.value = scale.value;
      if (scale.value <= 1) {
        translateX.value = withTiming(0, { duration: TIMING.fast });
        translateY.value = withTiming(0, { duration: TIMING.fast });
        savedTranslateX.value = 0;
        savedTranslateY.value = 0;
      }
    });

  const panGesture = Gesture.Pan()
    .averageTouches(false)
    .maxPointers(1)
    .activeOffsetX([-10, 10])
    .activeOffsetY([-10, 10])
    .onUpdate((e) => {
      if (scale.value > 1) {
        translateX.value = savedTranslateX.value + e.translationX;
        translateY.value = savedTranslateY.value + e.translationY;
      }
    })
    .onEnd(() => {
      savedTranslateX.value = translateX.value;
      savedTranslateY.value = translateY.value;
    });

  // Double tap to zoom
  const doubleTapGesture = Gesture.Tap()
    .numberOfTaps(2)
    .onEnd(() => {
      if (scale.value > 1) {
        scale.value = withTiming(1, { duration: TIMING.normal });
        translateX.value = withTiming(0, { duration: TIMING.normal });
        translateY.value = withTiming(0, { duration: TIMING.normal });
        savedScale.value = 1;
        savedTranslateX.value = 0;
        savedTranslateY.value = 0;
      } else {
        scale.value = withTiming(2.5, { duration: TIMING.normal });
        savedScale.value = 2.5;
      }
    });

  const toggleControlsCb = useCallback(() => {
    setShowControls((prev) => !prev);
  }, []);

  const singleTapGesture = Gesture.Tap().numberOfTaps(1).onEnd(() => {
    runOnJS(toggleControlsCb)();
  });

  const composedGesture = Gesture.Simultaneous(
    Gesture.Simultaneous(pinchGesture, panGesture),
    Gesture.Exclusive(doubleTapGesture, singleTapGesture),
  );

  // Export
  const handleExport = useCallback(async () => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
    const ok = await HidaNative.exportMedia(filePath);
    if (ok) router.back();
  }, [filePath, router]);

  // Delete
  const handleDelete = useCallback(async () => {
    await HidaNative.deleteMedia(filePath);
    setShowDeleteDialog(false);
    router.back();
  }, [filePath, router]);

  // Motion photo toggle
  const handleToggleMotion = useCallback(async () => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
    if (playingVideo) {
      setPlayingVideo(false);
    } else {
      setLoadingVideo(true);
      try {
        const uri = await HidaNative.getMotionVideoUri(filePath);
        setVideoUri(uri);
        setPlayingVideo(true);
      } catch {
        // Failed to extract video
      }
      setLoadingVideo(false);
    }
  }, [filePath, playingVideo]);

  return (
    <View className="flex-1 bg-black">
      <StatusBar style="light" backgroundColor="#000000" translucent />
      {/* Image content */}
      <GestureDetector gesture={composedGesture}>
        <Animated.View className="flex-1 items-center justify-center">
          {imageUri ? (
            <Animated.Image
              source={{ uri: `file://${imageUri}` }}
              style={[{ width, height }, animatedImageStyle]}
              resizeMode="contain"
            />
          ) : (
            <ActivityIndicator size="large" color="#ffffff" />
          )}
        </Animated.View>
      </GestureDetector>

      {/* Loading overlay — explicit white because the screen background is black,
          and the primary token is now dark ink (would render invisible). */}
      {loadingVideo && (
        <View className="absolute inset-0 items-center justify-center">
          <ActivityIndicator size="large" color="#ffffff" />
        </View>
      )}

      {/* Top controls — back only */}
      {showControls && (
        <Animated.View
          entering={FadeIn.duration(TIMING.fast)}
          exiting={FadeOut.duration(TIMING.fast)}
          className="absolute top-0 left-0 right-0 flex-row items-center px-4 pb-4"
          style={{ paddingTop: insets.top + 8, backgroundColor: "rgba(0,0,0,0.5)" }}
        >
          <Pressable
            className="w-12 h-12 rounded-full bg-black/40 items-center justify-center"
            accessibilityRole="button"
            accessibilityLabel="Go back"
            onPress={() => {
              Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
              router.back();
            }}
          >
            <ArrowLeft size={24} color="white" />
          </Pressable>
        </Animated.View>
      )}

      {/* Bottom action bar — Samsung Gallery style */}
      {showControls && (
        <Animated.View
          entering={FadeIn.duration(TIMING.fast)}
          exiting={FadeOut.duration(TIMING.fast)}
          className="absolute bottom-0 left-0 right-0 flex-row items-center justify-around px-4 pt-3"
          style={{ paddingBottom: insets.bottom + 12, backgroundColor: "rgba(0,0,0,0.55)" }}
        >
          <Pressable
            className="w-12 h-12 rounded-full bg-black/40 items-center justify-center"
            accessibilityRole="button"
            accessibilityLabel="File info"
            onPress={() => setShowInfo(true)}
          >
            <Info size={22} color="white" />
          </Pressable>
          <Pressable
            className="w-12 h-12 rounded-full bg-black/40 items-center justify-center"
            accessibilityRole="button"
            accessibilityLabel="Export to gallery"
            onPress={handleExport}
          >
            <ExternalLink size={22} color="white" />
          </Pressable>
          <Pressable
            className="w-12 h-12 rounded-full bg-black/40 items-center justify-center"
            accessibilityRole="button"
            accessibilityLabel="Delete"
            onPress={() => setShowDeleteDialog(true)}
          >
            <Trash2 size={22} color="white" />
          </Pressable>
        </Animated.View>
      )}

      {/* Motion photo FAB — sits above bottom action bar */}
      {isMotionPhoto && showControls && (
        <Animated.View
          entering={FadeIn.duration(TIMING.fast)}
          exiting={FadeOut.duration(TIMING.fast)}
          className="absolute self-center"
          style={{ bottom: 96 + insets.bottom }}
        >
          <Pressable
            onPress={handleToggleMotion}
            style={{
              width: 56,
              height: 56,
              backgroundColor: "#ffffff",
              borderWidth: 2,
              borderColor: "#16140f",
              alignItems: "center",
              justifyContent: "center",
              elevation: 6,
            }}
          >
            {playingVideo ? (
              <ImageIcon size={24} color="#16140f" />
            ) : (
              <Play size={24} color="#16140f" fill="#16140f" />
            )}
          </Pressable>
        </Animated.View>
      )}

      {/* Delete dialog */}
      <HidaDialog
        visible={showDeleteDialog}
        title="Delete?"
        message="This will permanently delete the item."
        confirmText="Delete"
        confirmDestructive
        onConfirm={handleDelete}
        onDismiss={() => setShowDeleteDialog(false)}
      />
      <InfoSheet
        visible={showInfo}
        title={mediaInfo?.isMotionPhoto ? "Motion photo" : "Photo"}
        rows={[
          { label: "Name", value: displayPhotoName },
          { label: "Type", value: mediaInfo?.ext ? `.${mediaInfo.ext}` : null },
          { label: "Size", value: formatBytes(mediaInfo?.size) },
          { label: "Modified", value: formatDate(mediaInfo?.lastModified) },
        ]}
        onClose={() => setShowInfo(false)}
      />
    </View>
  );
}
