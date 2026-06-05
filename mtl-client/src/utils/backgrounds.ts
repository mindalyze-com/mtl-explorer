// Shared utility: random background image selection from static assets
import { readJsonStorage, STORAGE_KEYS, writeJsonStorage } from '@/utils/appStorage';

const BACKGROUND_IMAGE_COUNT = 15;
const BACKGROUND_IMAGE_EXTENSION = 'webp';

export const backgrounds: string[] = Array.from(
  { length: BACKGROUND_IMAGE_COUNT },
  (_, i) =>
    `${import.meta.env.BASE_URL}backgrounds/background_${String(i + 1).padStart(3, '0')}.${BACKGROUND_IMAGE_EXTENSION}`
);

const DISPLAYED_KEY = STORAGE_KEYS.backgroundsDisplayed;

function pickNextBackground(): string {
  const total = backgrounds.length;

  // Read displayed indices, dropping any that are out of range (e.g. after reducing the count)
  let displayed = readJsonStorage<number[]>(DISPLAYED_KEY, [], (parsed) =>
    Array.isArray(parsed) ? parsed.filter((v): v is number => typeof v === 'number' && v >= 0 && v < total) : []
  );

  // Compute undisplayed indices; reset cycle when all have been shown
  let available = Array.from({ length: total }, (_, i) => i).filter((i) => !displayed.includes(i));
  if (available.length === 0) {
    displayed = [];
    available = Array.from({ length: total }, (_, i) => i);
  }

  const chosen = available[Math.floor(Math.random() * available.length)];
  displayed.push(chosen);

  writeJsonStorage(DISPLAYED_KEY, displayed);

  return backgrounds[chosen];
}

let activeBackground: string | null = null;

export function getRandomBackground(): string {
  // Evaluate exactly once on first use so LoginView and HomeView share the
  // same image per session without cache warmup affecting the rotation.
  activeBackground ??= pickNextBackground();
  return activeBackground;
}
