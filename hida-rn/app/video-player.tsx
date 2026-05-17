import { useState, useEffect, useCallback, useRef, useMemo } from "react";
import {
  View,
  Text,
  Pressable,
  ActivityIndicator,
  PanResponder,
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
import { Gesture, GestureDetector } from "react-native-gesture-handler";
import { useVideoPlayer, VideoView, type VideoPlayer as ExpoVideoPlayer } from "expo-video";
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
  Film,
  Info,
} from "lucide-react-native";
import { TIMING, displayVaultFileName } from "../lib/constants";
import { HidaDialog } from "../components/ui/dialog";
import { InfoSheet, formatBytes, formatDate, formatDuration } from "../components/ui/info-sheet";
import * as HidaNative from "../modules/hida-native";
import type { MediaFile } from "../modules/hida-native";
import { StatusBar } from "expo-status-bar";

function formatTime(ms: number): string {
  if (ms <= 0 || !Number.isFinite(ms)) return "0:00";
  const totalSeconds = Math.floor(ms / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  if (hours > 0) {
    return `${hours}:${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
  }
  return `${minutes}:${String(seconds).padStart(2, "0")}`;
}

function clampTranslateWorklet(
  s: number,
  tx: number,
  ty: number,
  w: number,
  h: number,
): { tx: number; ty: number } {
  "worklet";
  if (w <= 0 || h <= 0 || s <= 1) return { tx: 0, ty: 0 };
  const maxX = (w * (s - 1)) / 2;
  const maxY = (h * (s - 1)) / 2;
  return {
    tx: Math.min(Math.max(tx, -maxX), maxX),
    ty: Math.min(Math.max(ty, -maxY), maxY),
  };
}

export default function VideoPlayerScreen() {
  const router = useRouter();
  const { path } = useLocalSearchParams<{ path: string }>();
  const filePath = decodeURIComponent(path ?? "");
  const insets = useSafeAreaInsets();

  const [videoUri, setVideoUri] = useState<string | null>(null);
  const [showChrome, setShowChrome] = useState(true);
  const [showDeleteDialog, setShowDeleteDialog] = useState(false);
  const [isPlaying, setIsPlaying] = useState(false);
  const [isEnded, setIsEnded] = useState(false);
  const [currentTimeMs, setCurrentTimeMs] = useState(0);
  const [durationMs, setDurationMs] = useState(0);
  const [decrypting, setDecrypting] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showInfo, setShowInfo] = useState(false);
  const [mediaInfo, setMediaInfo] = useState<MediaFile | null>(null);
  const [peekOriginalName, setPeekOriginalName] = useState<string | null>(null);
  /** Fraction 0–1 during bar scrub only; playback uses player + currentTimeMs. */
  const [scrubFrac, setScrubFrac] = useState<number | null>(null);

  const hideControlsTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const progressBarWidth = useRef(1);
  const durationMsRef = useRef(0);
  const isScrubbingRef = useRef(false);
  const scrubPendingFracRef = useRef<number | null>(null);
  const playerRef = useRef<ExpoVideoPlayer | null>(null);

  const scale = useSharedValue(1);
  const savedScale = useSharedValue(1);
  const translateX = useSharedValue(0);
  const translateY = useSharedValue(0);
  const savedTranslateX = useSharedValue(0);
  const savedTranslateY = useSharedValue(0);
  const containerWidth = useSharedValue(1);
  const containerHeight = useSharedValue(1);

  const animatedVideoStyle = useAnimatedStyle(() => ({
    transform: [
      { translateX: translateX.value },
      { translateY: translateY.value },
      { scale: scale.value },
    ],
  }));

  const displayName = displayVaultFileName({
    originalName: mediaInfo?.originalName ?? peekOriginalName ?? null,
    storedName: mediaInfo?.name ?? null,
    path: filePath,
  });

  useEffect(() => {
    durationMsRef.current = durationMs;
  }, [durationMs]);

  useEffect(() => {
    isScrubbingRef.current = scrubFrac != null;
  }, [scrubFrac]);

  useEffect(() => {
    if (!filePath) return;
    let active = true;
    let mountedTempPath: string | null = null;
    HidaNative.getOriginalName(filePath)
      .then((n) => active && setPeekOriginalName(n))
      .catch(() => {});
    setDecrypting(true);
    setError(null);
    setIsEnded(false);
    setCurrentTimeMs(0);
    setScrubFrac(null);
    scrubPendingFracRef.current = null;
    HidaNative.getDecryptedVideoUri(filePath)
      .then((uri) => {
        if (!active) {
          // Strip leading "file://" before handing back to the native delete API.
          HidaNative.deleteTempFile(uri.replace(/^file:\/\//, ""));
          return;
        }
        mountedTempPath = uri.replace(/^file:\/\//, "");
        setVideoUri(uri);
        setDecrypting(false);
      })
      .catch((e) => {
        if (!active) return;
        setDecrypting(false);
        setError(e?.message ?? "Decryption failed");
      });
    HidaNative.getMediaInfo(filePath)
      .then((info) => active && info && setMediaInfo(info))
      .catch(() => {});
    return () => {
      active = false;
      // The temp video file is held by expo-video while the player is alive;
      // unmounting the screen tears the player down (release runs in a separate
      // effect), and this delete runs after — by which point the file handle
      // is closed.
      if (mountedTempPath) HidaNative.deleteTempFile(mountedTempPath);
      setVideoUri(null);
    };
  }, [filePath]);

  // Mirror the audio-player pattern (editlock §4): instantiate once with null,
  // then `replace` when the decrypted URI arrives. Passing the URI inline meant
  // the hook re-ran on the same render and disposed the prior player mid-frame.
  const player = useVideoPlayer(null, (p) => {
    p.loop = false;
  });
  playerRef.current = player;

  useEffect(() => {
    if (!videoUri || !player) return;
    try {
      player.replace({ uri: videoUri });
      player.play();
    } catch {
      /* player may be torn down during navigation; ignore */
    }
  }, [videoUri, player]);

  useEffect(() => {
    if (!player) return;

    const statusSub = player.addListener("statusChange", (payload) => {
      setIsPlaying(player.playing);
      if (payload.status === "readyToPlay") {
        const d = player.duration;
        if (typeof d === "number" && Number.isFinite(d) && d > 0) {
          const ms = d * 1000;
          setDurationMs(ms);
          durationMsRef.current = ms;
        }
      }
    });

    const playingSub = player.addListener("playingChange", (payload) => {
      setIsPlaying(payload.isPlaying);
    });

    return () => {
      statusSub.remove();
      playingSub.remove();
    };
  }, [player]);

  /** Keep UI time synced while paused or playing; paused when scrubbing. */
  useEffect(() => {
    if (!player || !videoUri) return;
    const id = setInterval(() => {
      if (isScrubbingRef.current) return;
      const dSec = player.duration;
      if (typeof dSec === "number" && Number.isFinite(dSec) && dSec > 0) {
        const ms = dSec * 1000;
        durationMsRef.current = ms;
        setDurationMs((prev) => (Math.abs(prev - ms) > 350 ? ms : prev));
      }
      setCurrentTimeMs(player.currentTime * 1000);
      if (
        typeof dSec === "number" &&
        Number.isFinite(dSec) &&
        dSec > 0 &&
        player.currentTime >= dSec - 0.08
      ) {
        setIsEnded(true);
      }
    }, 250);
    return () => clearInterval(id);
  }, [player, videoUri]);

  const resetHideTimer = useCallback(() => {
    if (hideControlsTimer.current) clearTimeout(hideControlsTimer.current);
    if (isPlaying && showChrome && !showInfo && !isScrubbingRef.current) {
      hideControlsTimer.current = setTimeout(() => setShowChrome(false), 4000);
    }
  }, [isPlaying, showChrome, showInfo]);

  useEffect(() => {
    resetHideTimer();
    return () => {
      if (hideControlsTimer.current) clearTimeout(hideControlsTimer.current);
    };
  }, [showChrome, isPlaying, resetHideTimer, showInfo]);

  const toggleChromeCb = useCallback(() => {
    setShowChrome((prev) => !prev);
  }, []);

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
      } else {
        const w = containerWidth.value;
        const h = containerHeight.value;
        const c = clampTranslateWorklet(scale.value, translateX.value, translateY.value, w, h);
        translateX.value = c.tx;
        translateY.value = c.ty;
        savedTranslateX.value = c.tx;
        savedTranslateY.value = c.ty;
      }
    });

  const panGesture = Gesture.Pan()
    .averageTouches(false)
    .maxPointers(1)
    .activeOffsetX([-10, 10])
    .activeOffsetY([-10, 10])
    .onUpdate((e) => {
      if (scale.value <= 1) return;
      const w = containerWidth.value;
      const h = containerHeight.value;
      const nx = savedTranslateX.value + e.translationX;
      const ny = savedTranslateY.value + e.translationY;
      const c = clampTranslateWorklet(scale.value, nx, ny, w, h);
      translateX.value = c.tx;
      translateY.value = c.ty;
    })
    .onEnd(() => {
      savedTranslateX.value = translateX.value;
      savedTranslateY.value = translateY.value;
    });

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

  const singleTapGesture = Gesture.Tap()
    .numberOfTaps(1)
    .onEnd(() => {
      runOnJS(toggleChromeCb)();
    });

  const composedGesture = Gesture.Simultaneous(
    Gesture.Simultaneous(pinchGesture, panGesture),
    Gesture.Exclusive(doubleTapGesture, singleTapGesture),
  );

  const togglePlayPause = () => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    if (isEnded) {
      player.currentTime = 0;
      player.play();
      setIsEnded(false);
    } else if (isPlaying) {
      player.pause();
    } else {
      player.play();
    }
    resetHideTimer();
  };

  const seekBy = (deltaMs: number) => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    const dSec = player.duration;
    const durMs =
      typeof dSec === "number" && Number.isFinite(dSec) && dSec > 0 ? dSec * 1000 : durationMsRef.current;
    if (durMs <= 0) return;
    const nextSec = Math.max(0, Math.min(player.currentTime + deltaMs / 1000, durMs / 1000));
    player.currentTime = nextSec;
    setCurrentTimeMs(nextSec * 1000);
    resetHideTimer();
  };

  const handleExport = useCallback(async () => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
    const ok = await HidaNative.exportMedia(filePath);
    if (ok) router.back();
  }, [filePath, router]);

  const handleDelete = useCallback(async () => {
    player.pause();
    await HidaNative.deleteMedia(filePath);
    setShowDeleteDialog(false);
    router.back();
  }, [filePath, router, player]);

  const dur = durationMs > 0 ? durationMs : durationMsRef.current;
  const playbackFrac = dur > 0 ? Math.min(1, Math.max(0, currentTimeMs / dur)) : 0;
  const displayFrac = scrubFrac ?? playbackFrac;

  const finalizeScrub = useCallback(() => {
    const frac = scrubPendingFracRef.current;
    scrubPendingFracRef.current = null;
    setScrubFrac(null);
    isScrubbingRef.current = false;
    const dm = durationMsRef.current;
    const p = playerRef.current;
    if (frac != null && dm > 0 && p != null) {
      p.currentTime = (frac * dm) / 1000;
      setCurrentTimeMs(frac * dm);
      setIsEnded(false);
      Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    }
    resetHideTimer();
  }, [resetHideTimer]);

  const seekBarPan = useMemo(
    () =>
      PanResponder.create({
        onStartShouldSetPanResponder: () => durationMsRef.current > 0,
        onMoveShouldSetPanResponder: (_, g) =>
          Math.abs(g.dx) > 5 && Math.abs(g.dx) > Math.abs(g.dy) * 1.1,
        onPanResponderGrant: (e) => {
          const w = progressBarWidth.current || 1;
          const x = e.nativeEvent.locationX;
          const frac = Math.min(1, Math.max(0, x / w));
          isScrubbingRef.current = true;
          scrubPendingFracRef.current = frac;
          setScrubFrac(frac);
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
        },
        onPanResponderMove: (e) => {
          const w = progressBarWidth.current || 1;
          const x = e.nativeEvent.locationX;
          const frac = Math.min(1, Math.max(0, x / w));
          scrubPendingFracRef.current = frac;
          setScrubFrac(frac);
        },
        onPanResponderRelease: finalizeScrub,
        onPanResponderTerminate: finalizeScrub,
      }),
    [finalizeScrub],
  );

  if (decrypting) {
    return (
      <>
        <StatusBar style="light" backgroundColor="#000000" translucent />
        <View style={{ flex: 1, backgroundColor: "#000", alignItems: "center", justifyContent: "center" }}>
        <ActivityIndicator size="large" color="#ffffff" />
        <Text
          style={{
            fontFamily: "JetBrainsMono",
            fontSize: 11,
            color: "#ccc",
            marginTop: 14,
            letterSpacing: 1.4,
            textTransform: "uppercase",
          }}
        >
          Decrypting video
        </Text>
      </View>
      </>
    );
  }

  if (error) {
    return (
      <>
        <StatusBar style="light" backgroundColor="#000000" translucent />
        <View style={{ flex: 1, backgroundColor: "#000", alignItems: "center", justifyContent: "center", paddingHorizontal: 32 }}>
        <Film size={48} color="#ffffff" />
        <Text style={{ fontFamily: "BricolageGrotesque", fontSize: 18, color: "#ffffff", marginTop: 16 }}>
          Unable to play video
        </Text>
        <Text style={{ fontFamily: "SpaceGrotesk", fontSize: 13, color: "#aaa", marginTop: 6, textAlign: "center" }}>
          {error}
        </Text>
        <Pressable
          style={{
            marginTop: 24,
            paddingHorizontal: 20,
            paddingVertical: 12,
            backgroundColor: "#ffffff",
            borderWidth: 2,
            borderColor: "#ffffff",
          }}
          onPress={() => router.back()}
        >
          <Text style={{ fontFamily: "SpaceGrotesk", fontSize: 14, fontWeight: "700", color: "#000" }}>
            Go back
          </Text>
        </Pressable>
      </View>
      </>
    );
  }

  const displayTimeMs =
    scrubFrac != null && dur > 0 ? scrubFrac * dur : currentTimeMs;

  return (
    <View className="flex-1 bg-black">
      <StatusBar style="light" backgroundColor="#000000" translucent />
      {/*
        Immersive chrome: no full-screen overlay (that blocked reliable tap-to-hide).
        Root cause: native VideoView consumed touches, so Pressable onPress rarely fired;
        a full inset-0 layer also made hit-testing fragile vs Reanimated.
        Top/bottom bars only; video area uses RNGH tap + pinch/pan like image-viewer.
      */}
      <GestureDetector gesture={composedGesture}>
        <Animated.View
          className="flex-1"
          onLayout={(e) => {
            const { width: w, height: h } = e.nativeEvent.layout;
            containerWidth.value = Math.max(1, w);
            containerHeight.value = Math.max(1, h);
          }}
        >
          {videoUri ? (
            <Animated.View className="flex-1" style={animatedVideoStyle}>
              <VideoView
                player={player}
                style={{ flex: 1 }}
                contentFit="contain"
                nativeControls={false}
                pointerEvents="none"
              />
            </Animated.View>
          ) : null}
        </Animated.View>
      </GestureDetector>

      {showChrome && (
        <Animated.View
          entering={FadeIn.duration(TIMING.fast)}
          exiting={FadeOut.duration(TIMING.fast)}
          className="absolute top-0 left-0 right-0 flex-row items-center px-4 pb-3"
          style={{ paddingTop: insets.top + 8, backgroundColor: "rgba(0,0,0,0.5)" }}
          pointerEvents="box-none"
        >
          <Pressable
            className="w-12 h-12 rounded-full bg-black/40 items-center justify-center"
            accessibilityRole="button"
            accessibilityLabel="Go back"
            onPress={() => {
              player.pause();
              router.back();
            }}
          >
            <ArrowLeft size={24} color="white" />
          </Pressable>
        </Animated.View>
      )}

      {showChrome && (
        <Animated.View
          entering={FadeIn.duration(TIMING.fast)}
          exiting={FadeOut.duration(TIMING.fast)}
          className="absolute left-4 right-4"
          style={{ bottom: 24 + insets.bottom }}
          pointerEvents="box-none"
        >
          <View className="bg-black/60 border border-white/10 rounded-3xl px-4 py-4">
            <View className="flex-row items-center justify-around mb-3">
              <Pressable
                className="w-11 h-11 rounded-full bg-white/10 items-center justify-center"
                accessibilityRole="button"
                accessibilityLabel="Video info"
                onPress={() => setShowInfo(true)}
              >
                <Info size={20} color="white" />
              </Pressable>
              <Pressable
                className="w-11 h-11 rounded-full bg-white/10 items-center justify-center"
                accessibilityRole="button"
                accessibilityLabel="Export to gallery"
                onPress={handleExport}
              >
                <ExternalLink size={20} color="white" />
              </Pressable>
              <Pressable
                className="w-11 h-11 rounded-full bg-white/10 items-center justify-center"
                accessibilityRole="button"
                accessibilityLabel="Delete video"
                onPress={() => setShowDeleteDialog(true)}
              >
                <Trash2 size={20} color="white" />
              </Pressable>
            </View>

            <View className="items-center mb-3">
              <View className="bg-black/80 rounded-2xl px-4 py-2">
                <Text style={{ fontFamily: "JetBrainsMono", fontSize: 14, color: "white" }}>
                  {formatTime(displayTimeMs)} / {formatTime(durationMs || durationMsRef.current)}
                </Text>
              </View>
            </View>

            {/* Seek track: drag + tap commit on release via PanResponder (stable vs Fabric). */}
            <View
              className="h-10 justify-center mb-3 px-1"
              onLayout={(e) => {
                progressBarWidth.current = Math.max(1, e.nativeEvent.layout.width);
              }}
              {...seekBarPan.panHandlers}
            >
              <View pointerEvents="none" className="h-2 bg-white/20 rounded-full overflow-hidden mx-1">
                <View
                  className="h-full rounded-full"
                  style={{
                    width: `${displayFrac * 100}%`,
                    backgroundColor: "#ffffff",
                  }}
                />
              </View>
            </View>

            <View className="flex-row items-center justify-center gap-6">
              <Pressable
                className="w-14 h-14 rounded-full bg-white/15 items-center justify-center"
                accessibilityRole="button"
                accessibilityLabel="Skip back 10 seconds"
                onPress={() => seekBy(-10000)}
              >
                <SkipBack size={24} color="white" />
              </Pressable>

              <Pressable
                accessibilityRole="button"
                accessibilityLabel={isEnded ? "Replay" : isPlaying ? "Pause" : "Play"}
                onPress={togglePlayPause}
                style={{
                  width: 80,
                  height: 80,
                  backgroundColor: "#ffffff",
                  borderWidth: 2,
                  borderColor: "#000",
                  alignItems: "center",
                  justifyContent: "center",
                }}
              >
                {isEnded ? (
                  <RotateCcw size={36} color="#000" />
                ) : isPlaying ? (
                  <Pause size={36} color="#000" fill="#000" />
                ) : (
                  <Play size={36} color="#000" fill="#000" style={{ marginLeft: 3 }} />
                )}
              </Pressable>

              <Pressable
                className="w-14 h-14 rounded-full bg-white/15 items-center justify-center"
                accessibilityRole="button"
                accessibilityLabel="Skip forward 10 seconds"
                onPress={() => seekBy(10000)}
              >
                <SkipForward size={24} color="white" />
              </Pressable>
            </View>
          </View>
        </Animated.View>
      )}

      <InfoSheet
        visible={showInfo}
        title="Video"
        rows={[
          { label: "Name", value: displayName },
          { label: "Type", value: mediaInfo?.ext ? `.${mediaInfo.ext}` : null },
          { label: "Size", value: formatBytes(mediaInfo?.size) },
          { label: "Duration", value: formatDuration(durationMs || durationMsRef.current || null) },
          { label: "Modified", value: formatDate(mediaInfo?.lastModified) },
        ]}
        onClose={() => setShowInfo(false)}
      />

      <HidaDialog
        visible={showDeleteDialog}
        title="Delete Video?"
        message="This will permanently delete this video from the vault."
        confirmText="Delete"
        confirmDestructive
        onConfirm={handleDelete}
        onDismiss={() => setShowDeleteDialog(false)}
      />
    </View>
  );
}
