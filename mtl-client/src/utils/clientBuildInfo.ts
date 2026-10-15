import { formatDateAndTimeWithSeconds } from '@/utils/Utils';

const UNAVAILABLE = 'Unavailable';
const DEVELOPMENT = 'Development';

export function getClientBuildTime(): string | null {
  // Vite timestamps the frontend build; VITE_APP_BUILD timestamps the image build.
  return typeof __APP_VERSION__ !== 'undefined' && __APP_VERSION__ ? __APP_VERSION__ : null;
}

export function formatClientBuildTime(): string {
  const buildTime = getClientBuildTime();
  return formatDateAndTimeWithSeconds(buildTime) || buildTime || UNAVAILABLE;
}

// Release images bake their image tag into the bundle, so an installed PWA keeps
// reporting the image it was built from until it reloads the new client.
export function getClientImageVersion(): string | null {
  return (import.meta.env.VITE_APP_VERSION as string | undefined)?.trim() || null;
}

export function getClientImageRevision(): string | null {
  return (import.meta.env.VITE_APP_REVISION as string | undefined)?.trim() || null;
}

// Local builds have no image tag; their build time tells them apart.
export function formatClientImageVersion(): string {
  return getClientImageVersion() || DEVELOPMENT;
}

export function isClientImageOutdated(
  server: { version?: string | null; revision?: string | null } | null | undefined
) {
  const clientVersion = getClientImageVersion();
  const serverVersion = server?.version?.trim();
  if (!clientVersion || !serverVersion) return false;
  if (clientVersion !== serverVersion) return true;
  const clientRevision = getClientImageRevision();
  const serverRevision = server?.revision?.trim();
  return Boolean(clientRevision && serverRevision && clientRevision !== serverRevision);
}
