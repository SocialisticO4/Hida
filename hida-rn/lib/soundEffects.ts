import { useAudioPlayer, AudioSource, AudioPlayer } from "expo-audio";
import * as HidaNative from "../modules/hida-native";

export type SoundEffect = "tap" | "unlock" | "error";

const SOUND_SOURCES: Record<SoundEffect, any> = {
  tap: require("../assets/sounds/tap.wav"),
  unlock: require("../assets/sounds/unlock.wav"),
  error: require("../assets/sounds/error.wav"),
};

const playerCache = new Map<SoundEffect, AudioPlayer>();
let primePromise: Promise<void> | null = null;

function isSoundEnabled(): boolean {
  try {
    return HidaNative.isSoundEnabled();
  } catch {
    return false;
  }
}

async function getOrCreatePlayer(effect: SoundEffect): Promise<AudioPlayer | null> {
  const existing = playerCache.get(effect);
  if (existing) return existing;

  try {
    // Note: expo-audio no longer uses createAsync. We dynamically import if needed
    // or we can use the top-level Audio module if available, but for now, we'll use useAudioPlayer in components.
    // Wait, useAudioPlayer is a hook. To play programmatically outside a component, we use the `Audio` module from expo-audio (if it exists) or create a new player.
    // Actually, expo-audio exports `AudioPlayer`. But wait, in the new API we use `useAudioPlayer` or `createAudioPlayer`.
    const { createAudioPlayer } = require('expo-audio');
    const player = createAudioPlayer(SOUND_SOURCES[effect]);
    playerCache.set(effect, player);
    return player;
  } catch {
    return null;
  }
}

export async function primeSoundEffects(): Promise<void> {
  if (!isSoundEnabled()) return;
  if (primePromise) return primePromise;

  primePromise = (async () => {
    // No more ensureAudioMode() needed for basic playback typically, or handled globally
    await Promise.all(
      (Object.keys(SOUND_SOURCES) as SoundEffect[]).map(async (effect) => {
        await getOrCreatePlayer(effect);
      })
    );
  })();

  try {
    await primePromise;
  } finally {
    primePromise = null;
  }
}

export async function unloadSoundEffects(): Promise<void> {
  const players = Array.from(playerCache.values());
  playerCache.clear();
  await Promise.all(
    players.map(async (player) => {
      try {
        player.remove(); // Unloads the audio resource in expo-audio
      } catch {
        // no-op
      }
    })
  );
}

export async function playSoundEffect(effect: SoundEffect): Promise<void> {
  if (!isSoundEnabled()) return;

  const player = await getOrCreatePlayer(effect);
  if (!player) return;

  try {
    // expo-audio uses seekTo and play
    player.seekTo(0);
    player.play();
  } catch {
    try {
      player.seekTo(0);
      player.play();
    } catch {
      // no-op
    }
  }
}
