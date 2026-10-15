/* SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2020-2026 Patrick Heusser and MTL Explorer contributors. */

const GUIDE_BASE = `${import.meta.env.BASE_URL}guide/`;

export const APP_GUIDE_URL = `${GUIDE_BASE}index.html`;

const GUIDE_TOPICS = {
  filters: 'filters',
  planning: 'planning',
  media: 'media',
  health: 'health',
  replay: 'replay',
} as const;

export function guideTopicUrl(topic: keyof typeof GUIDE_TOPICS): string {
  return `${GUIDE_BASE}${GUIDE_TOPICS[topic]}/index.html`;
}
