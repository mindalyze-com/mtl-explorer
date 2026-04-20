// Shared utility: random background image selection from static assets
import { USER_PREFS_KEYS, migrateLegacyKeys } from '@/utils/userPrefs';

export const backgrounds: string[] = Array.from({ length: 18 }, (_, i) =>
    `${import.meta.env.BASE_URL}backgrounds/background_${String(i + 1).padStart(3, '0')}_lowres.jpg`
);

const DISPLAYED_KEY = USER_PREFS_KEYS.backgroundsDisplayed;

function pickNextBackground(): string {
    const total = backgrounds.length;

    // Read displayed indices, dropping any that are out of range (e.g. after reducing the count)
    let displayed: number[] = [];
    try {
        migrateLegacyKeys();
        const raw = localStorage.getItem(DISPLAYED_KEY);
        if (raw) {
            const parsed = JSON.parse(raw) as unknown;
            if (Array.isArray(parsed)) {
                displayed = (parsed as unknown[])
                    .filter((v): v is number => typeof v === 'number' && v >= 0 && v < total);
            }
        }
    } catch {
        // corrupted storage — start fresh
    }

    // Compute undisplayed indices; reset cycle when all have been shown
    let available = Array.from({ length: total }, (_, i) => i).filter(i => !displayed.includes(i));
    if (available.length === 0) {
        displayed = [];
        available = Array.from({ length: total }, (_, i) => i);
    }

    const chosen = available[Math.floor(Math.random() * available.length)];
    displayed.push(chosen);

    try {
        localStorage.setItem(DISPLAYED_KEY, JSON.stringify(displayed));
    } catch {
        // storage unavailable — no-op
    }

    return backgrounds[chosen];
}

// Evaluate exactly once when the JavaScript bundle is loaded (app start)
// so LoginView and HomeView share the same image per session
const ACTIVE_BACKGROUND = pickNextBackground();

export function getRandomBackground(): string {
    return ACTIVE_BACKGROUND;
}
