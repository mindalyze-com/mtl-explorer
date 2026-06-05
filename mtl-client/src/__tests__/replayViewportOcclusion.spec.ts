import { describe, expect, it } from 'vitest';
import { computeReplayViewportPadding } from '@/components/replay/replayViewportOcclusion';

function elementWithRect(rect: DOMRect): HTMLElement {
  const el = document.createElement('div');
  Object.defineProperty(el, 'getBoundingClientRect', {
    value: () => rect,
  });
  return el;
}

describe('replay viewport occlusion', () => {
  it('uses the largest visible bottom-sheet overlap, not only the explicit replay layout', () => {
    const canvas = elementWithRect(new DOMRect(0, 0, 1280, 720));
    const sheet = elementWithRect(new DOMRect(218, 360, 920, 360));
    sheet.className = 'sheet sheet--open';
    document.body.appendChild(sheet);

    try {
      const padding = computeReplayViewportPadding({
        canvas,
        baseMarginPx: 32,
        minVisibleWidthPx: 180,
        minVisibleHeightPx: 160,
        layouts: [
          {
            open: true,
            detentId: 'open',
            fullscreen: false,
            dragging: false,
            heightPx: 318,
            widthPx: 920,
            topPx: 402,
            rightPx: 1138,
            bottomPx: 720,
            leftPx: 218,
          },
        ],
      });

      expect(padding.paddingBottom).toBe(392);
    } finally {
      sheet.remove();
    }
  });

  it('ignores hidden sheets when calculating replay viewport padding', () => {
    const canvas = elementWithRect(new DOMRect(0, 0, 1280, 720));
    const sheet = elementWithRect(new DOMRect(218, 100, 920, 620));
    sheet.className = 'sheet sheet--hidden';
    document.body.appendChild(sheet);

    try {
      const padding = computeReplayViewportPadding({
        canvas,
        baseMarginPx: 32,
        minVisibleWidthPx: 180,
        minVisibleHeightPx: 160,
      });

      expect(padding.paddingBottom).toBe(32);
    } finally {
      sheet.remove();
    }
  });
});
