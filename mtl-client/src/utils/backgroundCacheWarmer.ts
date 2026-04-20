import { backgrounds } from '@/utils/backgrounds';
import { USER_PREFS_KEYS, migrateLegacyKeys } from '@/utils/userPrefs';

const CACHE_NAME = 'mtl-backgrounds';
const CACHE_VERSION_KEY = USER_PREFS_KEYS.backgroundCacheVersion;

export function warmBackgroundCache(): void {
    if (!('caches' in window)) return;

    const warm = async () => {
        // 1. Cache invalidation on app update to prevent permanently stale images
        migrateLegacyKeys();
        const cachedVersion = localStorage.getItem(CACHE_VERSION_KEY);
        if (cachedVersion !== __APP_VERSION__) {
            try {
                await caches.delete(CACHE_NAME);
                localStorage.setItem(CACHE_VERSION_KEY, __APP_VERSION__);
            } catch {
                // silently continue
            }
        }

        // 2. Abort precache for slow or metered connections to save bandwidth
        if ('connection' in navigator) {
            const conn = (navigator as any).connection;
            if (conn.saveData || ['slow-2g', '2g', '3g'].includes(conn.effectiveType)) {
                return;
            }
        }

        let cache: Cache;
        try {
            cache = await caches.open(CACHE_NAME);
        } catch {
            return;
        }

        // 3. Single source of truth for background names
        for (const url of backgrounds) {
            try {
                if (await cache.match(url)) continue;
                const response = await fetch(url, { credentials: 'same-origin' });
                if (response.ok) await cache.put(url, response);
            } catch {
                // offline or transient error — skip silently
            }
        }
    };

    if ('requestIdleCallback' in window) {
        requestIdleCallback(() => void warm(), { timeout: 10000 });
    } else {
        setTimeout(() => void warm(), 3000);
    }
}
