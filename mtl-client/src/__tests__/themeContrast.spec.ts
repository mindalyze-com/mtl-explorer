import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const MINIMUM_SMALL_TEXT_CONTRAST = 4.5;

function contrastRatio(foregroundHex: string, backgroundHex: string): number {
  const foreground = relativeLuminance(foregroundHex);
  const background = relativeLuminance(backgroundHex);
  const lighter = Math.max(foreground, background);
  const darker = Math.min(foreground, background);

  return (lighter + 0.05) / (darker + 0.05);
}

function relativeLuminance(hexColor: string): number {
  const [red, green, blue] = hexColor
    .replace('#', '')
    .match(/.{2}/g)!
    .map((component) => parseInt(component, 16) / 255)
    .map((channel) => (channel <= 0.03928 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4));

  return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
}

function readRootToken(tokenName: string): string {
  const baseCss = readFileSync('src/assets/base.css', 'utf8');
  const rootBlock = baseCss.match(/:root\s*{(?<body>[\s\S]*?)\n}/)?.groups?.body;
  const tokenValue = rootBlock?.match(new RegExp(`${tokenName}:\\s*(#[0-9a-fA-F]{6})`))?.[1];

  if (!tokenValue) {
    throw new Error(`Missing ${tokenName} in light theme tokens`);
  }

  return tokenValue;
}

describe('light theme text contrast tokens', () => {
  it('keeps secondary text tokens readable against white surfaces', () => {
    const tokens = ['--text-primary', '--text-secondary', '--text-muted', '--text-faint'];

    for (const token of tokens) {
      expect(contrastRatio(readRootToken(token), '#ffffff'), token).toBeGreaterThanOrEqual(MINIMUM_SMALL_TEXT_CONTRAST);
    }
  });
});
