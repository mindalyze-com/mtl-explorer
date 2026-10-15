import { isAbortLikeError } from '@/utils/errors';
import { warnSanitized } from '@/utils/safeLogging';

export function reportMapError(event: { error?: unknown }, component: string): void {
  const error = event.error;
  if (isAbortLikeError(error)) return;
  const message = error && typeof error === 'object' ? (error as { message?: unknown }).message : undefined;
  if (typeof message === 'string' && message.includes('Map archive changed; refreshing map config.')) return;
  warnSanitized('Map rendering failed', error, { topic1: 'map', topic2: component, topic3: 'render' });
}

export function reportMapContextLoss(component: string): void {
  warnSanitized('Map graphics context lost', undefined, {
    topic1: 'map',
    topic2: component,
    topic3: 'webgl-context-lost',
  });
}
