import { useState, useEffect, useCallback, useMemo, useRef } from "react";
import {
  View,
  Text,
  Pressable,
  Image,
  ActivityIndicator,
  BackHandler,
  useWindowDimensions,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { FlashList } from "@shopify/flash-list";
import Animated, {
  FadeIn,
  FadeOut,
  useAnimatedStyle,
  useSharedValue,
  withSpring,
  runOnJS,
} from "react-native-reanimated";
import { Gesture, GestureDetector } from "react-native-gesture-handler";
import { useRouter, useLocalSearchParams, useFocusEffect } from "expo-router";
import * as Haptics from "expo-haptics";
import * as DocumentPicker from "expo-document-picker";
import {
  Lock,
  Settings,
  Plus,
  Image as ImageIcon,
  Film,
  Music,
  FileText,
  Trash2,
  X,
  CheckSquare,
  Square,
  Play,
  Check,
  Search,
  ExternalLink,
  LayoutGrid,
  List,
} from "lucide-react-native";
import { TextInput } from "react-native";
import { SPRING, TIMING, displayVaultFileName } from "../lib/constants";
import { useTheme } from "../lib/theme";
import { useGallerySelection } from "../lib/useGallerySelection";
import { HidaDialog } from "../components/ui/dialog";
import { NeoIconButton, NeoPressable, NeoSurface } from "../components/ui/neo";
import { formatBytes } from "../components/ui/info-sheet";
import * as HidaNative from "../modules/hida-native";
import type { MediaFile } from "../modules/hida-native";

const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

const TABS = [
  { key: "gallery", label: "GALLERY", icon: ImageIcon },
  { key: "audio", label: "AUDIO", icon: Music },
  { key: "docs", label: "DOCS", icon: FileText },
  // Settings is a navigation tab, not a content tab — tapping routes to /settings
  // rather than swapping the gallery contents. Kept last so it sits at the right
  // edge of the bottom nav like Samsung Gallery's "More" tab.
  { key: "settings", label: "SETTINGS", icon: Settings },
] as const;

type TabKey = (typeof TABS)[number]["key"];
type ContentTabKey = Exclude<TabKey, "settings">;

const TAB_BAR_HEIGHT = 64;

function BottomNav({
  activeTab,
  onTabChange,
  onOpenSettings,
  bottomInset,
}: {
  activeTab: ContentTabKey;
  onTabChange: (tab: ContentTabKey) => void;
  onOpenSettings: () => void;
  bottomInset: number;
}) {
  const { colors } = useTheme();
  return (
    <View
      style={{
        flexDirection: "row",
        gap: 8,
        paddingHorizontal: 12,
        paddingTop: 10,
        paddingBottom: bottomInset + 10,
        borderTopWidth: 2,
        borderTopColor: colors.ink,
        backgroundColor: colors.bg,
      }}
    >
      {TABS.map((tab) => {
        const isSettingsTab = tab.key === "settings";
        const isActive = !isSettingsTab && activeTab === tab.key;
        const Icon = tab.icon;
        return (
          <View key={tab.key} style={{ flex: 1 }}>
            <NeoPressable
              shadowOffset={isActive ? 0 : 3}
              pressedShadowOffset={isActive ? 0 : 1}
              background={isActive ? colors.ink : colors.surface}
              onPress={() => {
                if (isSettingsTab) onOpenSettings();
                else onTabChange(tab.key as ContentTabKey);
              }}
              style={{
                paddingVertical: 8,
                alignItems: "center",
                gap: 3,
              }}
              accessibilityRole="tab"
              accessibilityState={{ selected: isActive }}
              accessibilityLabel={tab.label}
            >
              <Icon size={18} color={isActive ? colors.bg : colors.ink} strokeWidth={2.2} />
              <Text
                style={{
                  fontFamily: "SpaceGrotesk",
                  fontSize: 11,
                  fontWeight: "700",
                  letterSpacing: 0.6,
                  color: isActive ? colors.bg : colors.ink,
                }}
              >
                {tab.label}
              </Text>
            </NeoPressable>
          </View>
        );
      })}
    </View>
  );
}

const TAB_TITLES: Record<ContentTabKey, string> = {
  gallery: "Gallery",
  audio: "Audio",
  docs: "Documents",
};

/**
 * Gallery tab import MIME list — broaden beyond wildcard strings only; OEM DocumentPickers vary.
 * Playback still depends on device Stagefright/ExoPlayer (same as VLC on Android minus bundled codecs).
 */
const GALLERY_IMPORT_CONTENT_TYPES = [
  "image/*",
  "video/*",
  "image/heic",
  "image/heif",
  "video/mp4",
  "video/mpeg",
  "video/quicktime",
  "video/webm",
  "video/x-matroska",
  "video/x-msvideo",
  "video/x-ms-wmv",
  "video/x-flv",
];

type ViewMode = "grid" | "list";

interface HeaderProps {
  selection: ReturnType<typeof useGallerySelection>;
  activeTab: ContentTabKey;
  filteredMedia: MediaFile[];
  isFake: boolean;
  topInset: number;
  searchQuery: string;
  searchOpen: boolean;
  onSearchOpen: () => void;
  onSearchClose: () => void;
  onSearchChange: (q: string) => void;
  onLock: () => void;
  onRequestDelete: () => void;
  onRequestExport: () => void;
  viewMode: ViewMode;
  onToggleViewMode: () => void;
}

function Header({
  selection,
  activeTab,
  filteredMedia,
  isFake,
  topInset,
  searchQuery,
  searchOpen,
  onSearchOpen,
  onSearchClose,
  onSearchChange,
  onLock,
  onRequestDelete,
  onRequestExport,
  viewMode,
  onToggleViewMode,
}: HeaderProps) {
  const { colors } = useTheme();
  const inSelection = selection.isSelectionMode;
  const total = filteredMedia.length;
  const selectedCount = selection.selectedCount;
  const allSelected = selectedCount > 0 && selectedCount === total;

  const title = inSelection
    ? selectedCount === 0
      ? "Select"
      : `${selectedCount} selected`
    : TAB_TITLES[activeTab];

  return (
    <View
      style={{
        paddingTop: topInset + 10,
        paddingHorizontal: 16,
        paddingBottom: 10,
        flexDirection: "row",
        alignItems: "center",
        gap: 10,
      }}
    >
      {inSelection ? (
        <NeoIconButton onPress={selection.clearSelection} ariaLabel="Cancel selection">
          <X size={20} color={colors.ink} strokeWidth={2.2} />
        </NeoIconButton>
      ) : null}

      {!(searchOpen && !inSelection) ? (
        <View style={{ flex: 1, minWidth: 0 }}>
          <View
            style={{
              flexDirection: "row",
              alignItems: "center",
              gap: 10,
              minWidth: 0,
            }}
          >
            <Text
              numberOfLines={inSelection ? 2 : 1}
              adjustsFontSizeToFit={inSelection}
              minimumFontScale={inSelection ? 0.75 : 1}
              style={{
                fontFamily: "BricolageGrotesque",
                fontSize: inSelection ? 20 : 22,
                color: colors.ink,
                letterSpacing: -0.5,
                flexShrink: 1,
                flexGrow: 1,
                minWidth: 0,
              }}
            >
              {title}
            </Text>
          </View>
        </View>
      ) : null}

      {inSelection ? (
        <>
          <NeoIconButton
            onPress={() => selection.selectAll(filteredMedia.map((f) => f.path))}
            ariaLabel={allSelected ? "Deselect all" : "Select all"}
            disabled={total === 0}
          >
            {allSelected ? (
              <CheckSquare size={20} color={colors.ink} strokeWidth={2.2} />
            ) : (
              <Square size={20} color={colors.ink} strokeWidth={2.2} />
            )}
          </NeoIconButton>
          <NeoIconButton
            onPress={onRequestExport}
            disabled={selectedCount === 0}
            ariaLabel="Export selected"
          >
            <ExternalLink
              size={20}
              color={selectedCount > 0 ? colors.ink : colors.ink2}
              strokeWidth={2.2}
            />
          </NeoIconButton>
          <NeoIconButton
            onPress={onRequestDelete}
            disabled={selectedCount === 0}
            ariaLabel="Delete selected"
            background={selectedCount > 0 ? colors.danger : undefined}
          >
            <Trash2
              size={20}
              color={selectedCount > 0 ? "#ffffff" : colors.ink2}
              strokeWidth={2.2}
            />
          </NeoIconButton>
        </>
      ) : searchOpen ? (
        <>
          <View style={{ flex: 1, minWidth: 0 }}>
            <NeoSurface background={colors.surface} shadowOffset={3}>
              <View
                style={{
                  flexDirection: "row",
                  alignItems: "center",
                  paddingHorizontal: 12,
                  paddingVertical: 6,
                  gap: 8,
                  minWidth: 0,
                }}
              >
                <Search size={16} color={colors.ink2} strokeWidth={2.2} />
                <TextInput
                  autoFocus
                  value={searchQuery}
                  onChangeText={onSearchChange}
                  placeholder="Search by name"
                  placeholderTextColor={colors.ink3}
                  cursorColor={colors.ink}
                  accessibilityLabel="Search media by name"
                  style={{
                    flex: 1,
                    minWidth: 0,
                    fontFamily: "SpaceGrotesk",
                    fontSize: 14,
                    color: colors.ink,
                    paddingVertical: 4,
                  }}
                />
              </View>
            </NeoSurface>
          </View>
          <NeoIconButton onPress={onSearchClose} ariaLabel="Close search">
            <X size={20} color={colors.ink} strokeWidth={2.2} />
          </NeoIconButton>
        </>
      ) : (
        <>
          {!isFake && (
            <NeoIconButton onPress={onSearchOpen} ariaLabel="Search">
              <Search size={20} color={colors.ink} strokeWidth={2.2} />
            </NeoIconButton>
          )}
          {(activeTab === "audio" || activeTab === "docs") && !isFake ? (
            <NeoIconButton
              onPress={onToggleViewMode}
              ariaLabel={viewMode === "grid" ? "List view" : "Grid view"}
            >
              {viewMode === "list" ? (
                <LayoutGrid size={20} color={colors.ink} strokeWidth={2.2} />
              ) : (
                <List size={20} color={colors.ink} strokeWidth={2.2} />
              )}
            </NeoIconButton>
          ) : null}
          <NeoIconButton onPress={onLock} ariaLabel="Lock vault">
            <Lock size={20} color={colors.ink} strokeWidth={2.2} />
          </NeoIconButton>
        </>
      )}
    </View>
  );
}

interface GridItemProps {
  item: MediaFile;
  isSelected: boolean;
  isSelectionMode: boolean;
  onPress: () => void;
  onLongPress: () => void;
  size: number;
  density: number;
}

function MediaGridItem({
  item,
  isSelected,
  isSelectionMode,
  onPress,
  onLongPress,
  size,
  density,
}: GridItemProps) {
  const { colors } = useTheme();
  const scale = useSharedValue(1);
  const [thumbUri, setThumbUri] = useState<string | null>(null);
  const [thumbError, setThumbError] = useState(false);
  const loadsThumbnail = !item.isAudio && !item.isDocument;

  useEffect(() => {
    if (!loadsThumbnail) return;
    let cancelled = false;
    setThumbUri(null);
    setThumbError(false);
    HidaNative.decryptThumbnail(item.path)
      .then((uri) => {
        if (!cancelled) setThumbUri(uri);
      })
      .catch(() => {
        if (!cancelled) setThumbError(true);
      });
    return () => {
      cancelled = true;
    };
  }, [item.path, loadsThumbnail]);

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.value }],
  }));

  useEffect(() => {
    scale.value = withSpring(isSelected ? 0.92 : 1, SPRING.responsive);
  }, [isSelected, scale]);

  const mediaType = item.isAudio
    ? "Audio"
    : item.isDocument
      ? "Document"
      : item.isVideo
        ? "Video"
        : item.isMotionPhoto
          ? "Motion photo"
          : "Photo";
  const FallbackIcon =
    item.isAudio
      ? Music
      : item.isDocument
        ? FileText
        : thumbError
          ? item.isVideo
            ? Film
            : ImageIcon
          : null;
  /** Audio/docs always use glyph tile; vault media uses tile only while loading/thumb failures. */
  const showGlyphTile =
    item.isAudio || item.isDocument || (thumbError && FallbackIcon != null && !thumbUri);

  const playSize = density <= 2 ? 36 : density === 3 ? 28 : density === 4 ? 22 : 18;

  return (
    <AnimatedPressable
      onPress={onPress}
      onLongPress={onLongPress}
      style={[
        animatedStyle,
        {
          width: size,
          height: size,
          overflow: "hidden",
          backgroundColor: colors.surface,
          borderWidth: 2,
          borderColor: colors.ink,
        },
      ]}
      accessibilityRole="button"
      accessibilityLabel={`${mediaType}: ${displayVaultFileName({
        originalName: item.originalName ?? null,
        storedName: item.name ?? null,
        path: item.path,
      })}`}
      accessibilityState={{ selected: isSelected }}
    >
      {showGlyphTile && FallbackIcon ? (
        <View
          style={{
            flex: 1,
            width: size,
            height: size,
            alignItems: "center",
            justifyContent: "center",
            backgroundColor: colors.surface2,
          }}
        >
          <FallbackIcon
            size={Math.max(20, size * 0.28)}
            color={colors.ink}
            strokeWidth={2}
          />
          <Text
            style={{
              fontFamily: "JetBrainsMono",
              fontSize: 10,
              color: colors.ink2,
              marginTop: 4,
              letterSpacing: 0.4,
            }}
            numberOfLines={1}
          >
            .{item.ext}
          </Text>
        </View>
      ) : thumbUri ? (
        <Image
          source={{ uri: `file://${thumbUri}` }}
          style={{ width: size, height: size }}
          resizeMode="cover"
        />
      ) : (
        <View
          style={{
            flex: 1,
            width: size,
            height: size,
            alignItems: "center",
            justifyContent: "center",
            backgroundColor: colors.surface2,
          }}
        >
          <ActivityIndicator size="small" color={colors.ink2} />
        </View>
      )}

      {item.isVideo && (
        <>
          <View
            pointerEvents="none"
            style={{
              position: "absolute",
              left: 0,
              right: 0,
              bottom: 0,
              height: size * 0.45,
              backgroundColor: "rgba(0,0,0,0.18)",
            }}
          />
          <View
            pointerEvents="none"
            style={{
              position: "absolute",
              left: 0,
              right: 0,
              top: 0,
              bottom: 0,
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <Play
              size={playSize}
              color="white"
              fill="white"
              style={{ marginLeft: 2 }}
            />
          </View>
        </>
      )}

      {item.isMotionPhoto && !item.isVideo && (
        <View
          style={{
            position: "absolute",
            top: 6,
            right: 6,
            width: 18,
            height: 18,
            backgroundColor: "rgba(255,255,255,0.92)",
            borderWidth: 1,
            borderColor: colors.ink,
            alignItems: "center",
            justifyContent: "center",
          }}
        >
          <Play size={9} color={colors.ink} fill={colors.ink} />
        </View>
      )}

      {isSelectionMode && (
        <>
          {isSelected && (
            <Animated.View
              entering={FadeIn.duration(TIMING.fast)}
              exiting={FadeOut.duration(TIMING.fast)}
              pointerEvents="none"
              style={{
                position: "absolute",
                inset: 0,
                backgroundColor: "rgba(22,20,15,0.30)",
                borderWidth: 3,
                borderColor: colors.ink,
              }}
            />
          )}
          <Animated.View
            entering={FadeIn.duration(TIMING.fast)}
            exiting={FadeOut.duration(TIMING.fast)}
            style={{ position: "absolute", top: 6, left: 6 }}
          >
            <View
              style={{
                width: 22,
                height: 22,
                borderWidth: 2,
                borderColor: colors.ink,
                backgroundColor: isSelected ? colors.ink : colors.surface,
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              {isSelected && <Check size={13} color={colors.bg} strokeWidth={3} />}
            </View>
          </Animated.View>
        </>
      )}
    </AnimatedPressable>
  );
}

interface MediaListRowProps {
  item: MediaFile;
  isSelected: boolean;
  isSelectionMode: boolean;
  onPress: () => void;
  onLongPress: () => void;
  rowWidth: number;
}

function MediaListRow({
  item,
  isSelected,
  isSelectionMode,
  onPress,
  onLongPress,
  rowWidth,
}: MediaListRowProps) {
  const { colors } = useTheme();
  const Icon = item.isAudio ? Music : FileText;
  const title = displayVaultFileName({
    originalName: item.originalName ?? null,
    storedName: item.name ?? null,
    path: item.path,
  });
  const subtitle = `.${item.ext} · ${formatBytes(item.size)}`;

  return (
    <AnimatedPressable
      onPress={onPress}
      onLongPress={onLongPress}
      style={{
        width: rowWidth,
        flexDirection: "row",
        alignItems: "center",
        gap: 12,
        paddingVertical: 12,
        paddingHorizontal: 14,
        backgroundColor: colors.surface,
        borderWidth: 2,
        borderColor: colors.ink,
      }}
      accessibilityRole="button"
      accessibilityLabel={`${item.isAudio ? "Audio" : "Document"}: ${title}`}
      accessibilityState={{ selected: isSelected }}
    >
      <View
        style={{
          width: 44,
          height: 44,
          alignItems: "center",
          justifyContent: "center",
          backgroundColor: colors.surface2,
          borderWidth: 2,
          borderColor: colors.ink,
        }}
      >
        <Icon size={22} color={colors.ink} strokeWidth={2} />
      </View>
      <View style={{ flex: 1, minWidth: 0 }}>
        <Text
          numberOfLines={1}
          style={{
            fontFamily: "SpaceGrotesk",
            fontSize: 15,
            fontWeight: "600",
            color: colors.ink,
          }}
        >
          {title}
        </Text>
        <Text
          numberOfLines={1}
          style={{
            fontFamily: "JetBrainsMono",
            fontSize: 11,
            color: colors.ink2,
            marginTop: 4,
            letterSpacing: 0.3,
          }}
        >
          {subtitle}
        </Text>
      </View>

      {isSelectionMode && (
        <>
          {isSelected && (
            <Animated.View
              entering={FadeIn.duration(TIMING.fast)}
              exiting={FadeOut.duration(TIMING.fast)}
              pointerEvents="none"
              style={{
                position: "absolute",
                inset: 0,
                backgroundColor: "rgba(22,20,15,0.18)",
                borderWidth: 3,
                borderColor: colors.ink,
              }}
            />
          )}
          <Animated.View
            entering={FadeIn.duration(TIMING.fast)}
            exiting={FadeOut.duration(TIMING.fast)}
            style={{ position: "absolute", top: 10, left: 10 }}
          >
            <View
              style={{
                width: 22,
                height: 22,
                borderWidth: 2,
                borderColor: colors.ink,
                backgroundColor: isSelected ? colors.ink : colors.surface,
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              {isSelected && <Check size={13} color={colors.bg} strokeWidth={3} />}
            </View>
          </Animated.View>
        </>
      )}
    </AnimatedPressable>
  );
}

function EmptyState({ tab, isFake }: { tab: ContentTabKey; isFake: boolean }) {
  const { colors } = useTheme();
  const icons: Record<ContentTabKey, typeof ImageIcon> = {
    gallery: ImageIcon,
    audio: Music,
    docs: FileText,
  };
  const Icon = icons[tab];

  const titles: Record<ContentTabKey, string> = {
    gallery: "No photos or videos",
    audio: "No audio",
    docs: "No documents",
  };

  return (
    <View
      style={{
        flex: 1,
        alignItems: "center",
        justifyContent: "center",
        paddingHorizontal: 32,
        gap: 14,
      }}
    >
      <NeoSurface background={colors.surface} shadowOffset={4}>
        <View
          style={{
            width: 72,
            height: 72,
            alignItems: "center",
            justifyContent: "center",
          }}
        >
          <Icon size={32} color={colors.ink} strokeWidth={2.2} />
        </View>
      </NeoSurface>
      <Text
        style={{
          fontFamily: "BricolageGrotesque",
          fontSize: 18,
          color: colors.ink,
          marginTop: 6,
        }}
      >
        {isFake ? "Empty" : titles[tab]}
      </Text>
      {!isFake && (
        <Text
          style={{
            fontFamily: "SpaceGrotesk",
            fontSize: 13,
            color: colors.ink2,
            textAlign: "center",
          }}
        >
          Tap + to add files
        </Text>
      )}
    </View>
  );
}

type GroupedItem =
  | { type: "header"; label: string; sublabel?: string }
  | { type: "row"; items: MediaFile[] };

function startOfDay(ts: number): number {
  const d = new Date(ts);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

function formatDayHeader(dayTs: number, now: Date): { label: string; sublabel?: string } {
  const d = new Date(dayTs);
  const today = startOfDay(now.getTime());
  const yesterday = today - 86_400_000;
  if (dayTs === today) return { label: "Today" };
  if (dayTs === yesterday) return { label: "Yesterday" };

  const sameYear = d.getFullYear() === now.getFullYear();
  const weekday = d.toLocaleString("default", { weekday: "short" });
  const month = d.toLocaleString("default", { month: "short" });
  const day = d.getDate();
  if (sameYear) {
    return { label: `${weekday}, ${month} ${day}` };
  }
  return { label: `${month} ${day}, ${d.getFullYear()}`, sublabel: weekday };
}

function groupByDay(files: MediaFile[], columns: number): GroupedItem[] {
  if (files.length === 0) return [];
  const now = new Date();
  const buckets: { day: number; items: MediaFile[] }[] = [];
  let current: { day: number; items: MediaFile[] } | null = null;
  for (const file of files) {
    const day = startOfDay(file.lastModified);
    if (!current || current.day !== day) {
      current = { day, items: [] };
      buckets.push(current);
    }
    current.items.push(file);
  }
  const result: GroupedItem[] = [];
  for (const bucket of buckets) {
    const { label, sublabel } = formatDayHeader(bucket.day, now);
    result.push({ type: "header", label, sublabel });
    for (let i = 0; i < bucket.items.length; i += columns) {
      result.push({ type: "row", items: bucket.items.slice(i, i + columns) });
    }
  }
  return result;
}

const DENSITIES = [2, 3, 4, 5] as const;
const DEFAULT_DENSITY = 3;

export default function GalleryScreen() {
  const router = useRouter();
  const { mode } = useLocalSearchParams<{ mode: string }>();
  const isFake = mode === "fake";
  const insets = useSafeAreaInsets();
  const { width } = useWindowDimensions();
  const { colors } = useTheme();

  const [activeTab, setActiveTab] = useState<ContentTabKey>("gallery");
  const [allMedia, setAllMedia] = useState<MediaFile[]>([]);
  const [loading, setLoading] = useState(true);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [importing, setImporting] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");

  const selection = useGallerySelection();

  const [audioDocsViewMode, setAudioDocsViewMode] = useState<ViewMode>("grid");
  const [density, setDensity] = useState<number>(DEFAULT_DENSITY);
  const ITEM_GAP = density <= 2 ? 6 : density === 3 ? 4 : 3;
  const PADDING = 12;
  const itemSize = Math.floor(
    (width - PADDING * 2 - ITEM_GAP * (density - 1)) / density
  );

  const changeDensity = useCallback(
    (next: number) => {
      const clamped = Math.max(DENSITIES[0], Math.min(DENSITIES[DENSITIES.length - 1], next));
      if (clamped === density) return;
      Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
      setDensity(clamped);
    },
    [density]
  );

  const loadMedia = useCallback(async () => {
    setLoading(true);
    try {
      const files = await HidaNative.listMedia();
      setAllMedia(files);
    } catch (e) {
      // "Vault is locked" is the expected outcome of an idle-timer fire racing
      // with a thumbnail load; don't spam the console for benign auto-lock.
      const msg = (e as { message?: string })?.message ?? "";
      if (!msg.includes("Vault is locked")) {
        console.error("Failed to load media:", e);
      }
    }
    setLoading(false);
  }, []);

  const initialLoad = useRef(false);
  useEffect(() => {
    if (isFake) {
      setLoading(false);
      initialLoad.current = true;
      return;
    }
    loadMedia();
    initialLoad.current = true;
  }, [isFake]);

  // Reload when returning from a detail screen (image/video/audio/doc) so
  // deletions made there are reflected without a full remount.
  useFocusEffect(
    useCallback(() => {
      if (!initialLoad.current || isFake) return;
      loadMedia();
    }, [isFake, loadMedia])
  );

  const filteredMedia = useMemo(() => {
    let base: MediaFile[];
    // "gallery" merges photos AND videos so users can scroll a single timeline.
    if (activeTab === "gallery") base = allMedia.filter((f) => !f.isAudio && !f.isDocument);
    else if (activeTab === "audio") base = allMedia.filter((f) => f.isAudio);
    else if (activeTab === "docs") base = allMedia.filter((f) => f.isDocument);
    else base = [];

    const q = searchQuery.trim().toLowerCase();
    if (!q) return base;
    return base.filter((f) => {
      const name = displayVaultFileName({
        originalName: f.originalName ?? null,
        storedName: f.name ?? null,
        path: f.path,
      }).toLowerCase();
      return name.includes(q);
    });
  }, [allMedia, activeTab, searchQuery]);

  const columnsForLayout = useMemo(() => {
    if (activeTab === "gallery") return density;
    if (activeTab === "audio" || activeTab === "docs") {
      return audioDocsViewMode === "list" ? 1 : density;
    }
    return density;
  }, [activeTab, density, audioDocsViewMode]);

  const grouped = useMemo(
    () => groupByDay(filteredMedia, columnsForLayout),
    [filteredMedia, columnsForLayout]
  );

  const useListForAudioDocs =
    (activeTab === "audio" || activeTab === "docs") && audioDocsViewMode === "list";
  const listRowWidth = width - PADDING * 2;

  useEffect(() => {
    if (!selection.isSelectionMode) return;
    const sub = BackHandler.addEventListener("hardwareBackPress", () => {
      selection.clearSelection();
      return true;
    });
    return () => sub.remove();
  }, [selection.isSelectionMode, selection]);

  const handleLock = useCallback(() => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
    try {
      try {
        // lockVault wipes the in-memory ChaCha key + plaintext temp files; clearSession
        // resets the auto-lock pref state machine. Both must run for the lock button
        // to fully tear down: without lockVault, the next gallery mount would still
        // be unlocked; without clearSession, the WAS_PAUSED bit may survive.
        HidaNative.lockVault();
        HidaNative.clearSession();
      } catch {
        /* non-fatal */
      }
      router.replace("/calculator");
    } catch (e) {
      console.error("[lock] navigation failed:", e);
    }
  }, [router]);

  const importMimeTypes = useMemo(() => {
    if (activeTab === "audio") return ["audio/*"];
    if (activeTab === "docs")
      return [
        "application/*",
        "text/*",
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/msword",
        "application/vnd.ms-powerpoint",
        "application/sql",
      ];
    return [...GALLERY_IMPORT_CONTENT_TYPES];
  }, [activeTab]);

  const handleImport = useCallback(async () => {
    try {
      const result = await DocumentPicker.getDocumentAsync({
        type: importMimeTypes,
        // Large videos: copying to cache before returning URI doubles I/O; native streams from content://.
        copyToCacheDirectory: false,
      });

      if (result.canceled || !result.assets?.[0]) return;

      setImporting(true);
      const uri = result.assets[0].uri;
      await HidaNative.importMedia(uri);
      HidaNative.cleanupTempFiles();
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
      await loadMedia();
    } catch (e) {
      console.error("Import failed:", e);
    } finally {
      setImporting(false);
    }
  }, [loadMedia, importMimeTypes]);

  const handleDelete = useCallback(async () => {
    const paths = Array.from(selection.selectedPaths);
    if (paths.length === 0) return;
    await HidaNative.deleteMultipleMedia(paths);
    selection.clearSelection();
    await loadMedia();
  }, [selection, loadMedia]);

  const handleExportSelected = useCallback(async () => {
    const paths = Array.from(selection.selectedPaths);
    if (paths.length === 0) return;
    let anyOk = false;
    for (const p of paths) {
      try {
        if (await HidaNative.exportMedia(p)) anyOk = true;
      } catch {
        /* ignore single-file failures */
      }
    }
    if (anyOk) {
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    }
    selection.clearSelection();
  }, [selection]);

  const handleItemPress = useCallback(
    (item: MediaFile) => {
      if (selection.isSelectionMode) {
        Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
        selection.toggleSelection(item.path);
      } else {
        Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
        if (item.isVideo) {
          router.push(`/video-player?path=${encodeURIComponent(item.path)}`);
        } else if (item.isAudio) {
          router.push(`/audio-player?path=${encodeURIComponent(item.path)}`);
        } else if (item.isDocument) {
          router.push(`/doc-viewer?path=${encodeURIComponent(item.path)}`);
        } else {
          router.push(`/image-viewer?path=${encodeURIComponent(item.path)}`);
        }
      }
    },
    [selection, router]
  );

  const handleItemLongPress = useCallback(
    (item: MediaFile) => {
      if (!selection.isSelectionMode) {
        Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
        selection.enterSelectionMode(item.path);
      }
    },
    [selection]
  );

  const swipeGesture = useMemo(() => {
    return Gesture.Pan()
      .activeOffsetX([-30, 30])
      .failOffsetY([-20, 20])
      .onEnd((e) => {
        // Swipe cycles content tabs only; settings is reached via the tab tap.
        const contentKeys: ContentTabKey[] = TABS
          .filter((t) => t.key !== "settings")
          .map((t) => t.key as ContentTabKey);
        const currentIndex = contentKeys.indexOf(activeTab);

        const changeTab = (index: number) => {
          setActiveTab(contentKeys[index]);
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
        };

        if (e.translationX < -50 && currentIndex < contentKeys.length - 1) {
          runOnJS(changeTab)(currentIndex + 1);
        } else if (e.translationX > 50 && currentIndex > 0) {
          runOnJS(changeTab)(currentIndex - 1);
        }
      });
  }, [activeTab]);

  const pinchGesture = useMemo(() => {
    return Gesture.Pinch().onEnd((e) => {
      "worklet";
      if (e.scale > 1.15) {
        runOnJS(changeDensity)(density - 1);
      } else if (e.scale < 0.85) {
        runOnJS(changeDensity)(density + 1);
      }
    });
  }, [changeDensity, density]);

  const composedGesture = useMemo(
    () => Gesture.Race(pinchGesture, swipeGesture),
    [pinchGesture, swipeGesture]
  );

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <Header
        selection={selection}
        activeTab={activeTab}
        filteredMedia={filteredMedia}
        isFake={isFake}
        topInset={insets.top}
        searchQuery={searchQuery}
        searchOpen={searchOpen}
        onSearchOpen={() => setSearchOpen(true)}
        onSearchClose={() => {
          setSearchOpen(false);
          setSearchQuery("");
        }}
        onSearchChange={setSearchQuery}
        onLock={handleLock}
        onRequestDelete={() => setShowDeleteConfirm(true)}
        onRequestExport={handleExportSelected}
        viewMode={activeTab === "gallery" ? "grid" : audioDocsViewMode}
        onToggleViewMode={() =>
          setAudioDocsViewMode((m) => (m === "grid" ? "list" : "grid"))
        }
      />

      <GestureDetector gesture={composedGesture}>
        <View style={{ flex: 1 }}>
          {loading ? (
            <View style={{ flex: 1, alignItems: "center", justifyContent: "center" }}>
              <ActivityIndicator size="large" color={colors.ink2} />
            </View>
          ) : filteredMedia.length === 0 ? (
            <EmptyState tab={activeTab} isFake={isFake} />
          ) : (
            <FlashList
              data={grouped}
              extraData={`${density}-${columnsForLayout}-${audioDocsViewMode}-${selection.isSelectionMode}-${selection.selectedCount}`}
              style={{ backgroundColor: colors.bg }}
              contentContainerStyle={{
                padding: PADDING,
                paddingBottom: PADDING + TAB_BAR_HEIGHT + insets.bottom + 24,
              }}
              renderItem={({ item }) => {
                if (item.type === "header") {
                  return (
                    <View style={{ paddingTop: 16, paddingBottom: 6 }}>
                      <Text
                        style={{
                          fontFamily: "BricolageGrotesque",
                          fontSize: 15,
                          color: colors.ink,
                          letterSpacing: -0.2,
                        }}
                      >
                        {item.label}
                      </Text>
                      {item.sublabel ? (
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
                          {item.sublabel}
                        </Text>
                      ) : null}
                    </View>
                  );
                }

                if (useListForAudioDocs) {
                  return (
                    <View style={{ gap: ITEM_GAP, marginBottom: ITEM_GAP }}>
                      {item.items.map((media) => (
                        <MediaListRow
                          key={media.path}
                          item={media}
                          isSelected={selection.selectedPaths.has(media.path)}
                          isSelectionMode={selection.isSelectionMode}
                          onPress={() => handleItemPress(media)}
                          onLongPress={() => handleItemLongPress(media)}
                          rowWidth={listRowWidth}
                        />
                      ))}
                    </View>
                  );
                }

                return (
                  <View
                    style={{
                      flexDirection: "row",
                      gap: ITEM_GAP,
                      marginBottom: ITEM_GAP,
                    }}
                  >
                    {item.items.map((media) => (
                      <MediaGridItem
                        key={media.path}
                        item={media}
                        isSelected={selection.selectedPaths.has(media.path)}
                        isSelectionMode={selection.isSelectionMode}
                        onPress={() => handleItemPress(media)}
                        onLongPress={() => handleItemLongPress(media)}
                        size={itemSize}
                        density={density}
                      />
                    ))}
                    {item.items.length < columnsForLayout &&
                      Array.from({
                        length: columnsForLayout - item.items.length,
                      }).map((_, i) => (
                        <View
                          key={`spacer-${i}`}
                          style={{ width: itemSize, height: itemSize }}
                        />
                      ))}
                  </View>
                );
              }}
              getItemType={(item) => item.type}
              keyExtractor={(item, index) =>
                item.type === "header" ? `h-${item.label}` : `r-${index}-${columnsForLayout}`
              }
            />
          )}

          {/* FAB — neobrutalist ink square, sits just above the bottom nav. */}
          {!isFake && !selection.isSelectionMode && (
            <View
              style={{
                position: "absolute",
                right: 16,
                bottom: 12,
              }}
            >
              <NeoIconButton
                onPress={handleImport}
                disabled={importing}
                ariaLabel="Import media"
                size={56}
                background={colors.surface}
              >
                {importing ? (
                  <ActivityIndicator color={colors.ink} />
                ) : (
                  <Plus size={28} color={colors.ink} strokeWidth={2.6} />
                )}
              </NeoIconButton>
            </View>
          )}
        </View>
      </GestureDetector>

      {!selection.isSelectionMode && (
        <BottomNav
          activeTab={activeTab}
          onTabChange={setActiveTab}
          onOpenSettings={() => router.push(isFake ? "/settings?mode=fake" : "/settings")}
          bottomInset={insets.bottom}
        />
      )}

      <HidaDialog
        visible={showDeleteConfirm}
        title={`Delete ${selection.selectedCount} ${selection.selectedCount === 1 ? "item" : "items"}?`}
        message="This will permanently delete the selected items from your vault."
        confirmText="Delete"
        confirmDestructive
        onConfirm={() => {
          setShowDeleteConfirm(false);
          handleDelete();
        }}
        onDismiss={() => setShowDeleteConfirm(false)}
      />
    </View>
  );
}
