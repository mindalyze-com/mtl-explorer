import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import postcss from 'postcss';

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

function themeTokens(theme: string): Record<string, string> {
  const tokens: Record<string, string> = {};
  postcss.parse(readFileSync('src/assets/base.css', 'utf8')).walkRules((rule) => {
    if (rule.selector === ':root' || (theme === 'dark' && rule.selector === "[data-theme='dark']")) {
      rule.walkDecls((declaration) => {
        tokens[declaration.prop] = declaration.value;
      });
    }
  });
  return tokens;
}

function colorChannels(color: string): number[] {
  if (color.startsWith('#')) {
    const hex = color.slice(1);
    return (hex.length === 3 ? [...hex].map((value) => value.repeat(2)).join('') : hex)
      .match(/.{2}/g)!
      .map((channel) => parseInt(channel, 16));
  }
  return color.match(/[\d.]+/g)!.map(Number);
}

function blendColor(foreground: string, background: string, alpha = colorChannels(foreground)[3] ?? 1): string {
  const back = colorChannels(background);
  return (
    '#' +
    colorChannels(foreground)
      .slice(0, 3)
      .map((channel, index) =>
        Math.round(channel * alpha + back[index] * (1 - alpha))
          .toString(16)
          .padStart(2, '0')
      )
      .join('')
  );
}

function componentStyles(path: string): postcss.Root {
  return postcss.parse(
    [...readFileSync(path, 'utf8').matchAll(/<style[^>]*>([\s\S]*?)<\/style>/g)].map((match) => match[1]).join('\n')
  );
}

function styleDeclarations(styles: postcss.Root, selector: string): Record<string, string> {
  const values: Record<string, string> = {};
  styles.walkRules((rule) => {
    if (rule.selectors.includes(selector))
      rule.walkDecls((declaration) => void (values[declaration.prop] = declaration.value));
  });
  return values;
}

describe.each(['light', 'dark'])('navigation contrast in %s application theme', (applicationTheme) => {
  const app = themeTokens(applicationTheme);
  const sheetStyles = componentStyles('src/components/ui/BottomSheet.vue');
  const viewerStyles = componentStyles('src/components/map/MediaPreview.vue');
  const mapStyles = componentStyles('src/components/map/Map2DRenderer.vue');
  const minimumControlContrast = 3;

  it.each(['light', 'dark'])('keeps every photo header control visible in %s viewer theme', (viewerTheme) => {
    const tokens = {
      ...app,
      ...styleDeclarations(viewerStyles, ':global(.sheet.media-viewer-sheet)'),
      ...(viewerTheme === 'light' ? styleDeclarations(viewerStyles, ':global(.sheet.media-viewer-sheet--light)') : {}),
    };
    const resolve = (value: string): string => value.replace(/var\((--[\w-]+)\)/g, (_, name: string) => tokens[name]);
    const header = blendColor(tokens['--mp-surface'], '#ffffff');
    for (const control of [
      'sheet-back-btn',
      'sheet-close-btn',
      'sheet-fullscreen-btn',
      'sheet-native-fullscreen-btn',
    ]) {
      for (const state of ['', ':hover']) {
        const styles = {
          ...styleDeclarations(sheetStyles, `.${control}`),
          ...styleDeclarations(viewerStyles, `:global(.sheet.media-viewer-sheet .${control})`),
          ...(state ? styleDeclarations(sheetStyles, `.${control}${state}`) : {}),
          ...(state ? styleDeclarations(viewerStyles, `:global(.sheet.media-viewer-sheet .${control}${state})`) : {}),
        };
        const surface = styles.background === 'transparent' ? header : blendColor(resolve(styles.background), header);
        const foreground = blendColor(resolve(styles.color), surface);
        expect(contrastRatio(foreground, surface), `${control}${state}`).toBeGreaterThanOrEqual(minimumControlContrast);
      }
    }
  });

  it('keeps the search marker clear control visible on its colored pin', () => {
    const resolve = (value: string) => value.replace(/var\((--[\w-]+)\)/g, (_, name: string) => app[name]);
    const normal = styleDeclarations(mapStyles, '.mtl-location-search-marker__clear');
    for (const state of ['', ':hover']) {
      const styles = {
        ...normal,
        ...(state ? styleDeclarations(mapStyles, `.mtl-location-search-marker__clear${state}`) : {}),
      };
      const surface = blendColor(resolve(styles.background), app['--accent']);
      const foreground = blendColor(resolve(styles.color), surface);
      expect(contrastRatio(foreground, surface)).toBeGreaterThanOrEqual(minimumControlContrast);
    }
  });
});

describe.each(['light', 'dark'])('quality and admin text contrast in %s theme', (theme) => {
  const tokens = themeTokens(theme);
  const sheet = blendColor(tokens['--surface-sheet-solid'], '#ffffff');
  const elevated = blendColor(tokens['--surface-elevated'], sheet);
  const nested = blendColor(tokens['--surface-elevated'], elevated);

  it('keeps helper and faint text readable on sheet and nested panels', () => {
    for (const token of ['--text-secondary', '--text-muted', '--text-faint']) {
      for (const surface of [sheet, elevated, nested]) {
        expect(contrastRatio(tokens[token], surface), `${token} on ${surface}`).toBeGreaterThanOrEqual(
          MINIMUM_SMALL_TEXT_CONTRAST
        );
      }
    }
  });

  it('keeps quality badges and admin status text readable', () => {
    const quality = readFileSync('src/components/trackdetails/TrackDetailQuality.vue', 'utf8');
    const styles = postcss.parse(
      quality.match(/<style scoped>([\s\S]*?)<\/style>/)![1] +
        readFileSync('src/components/admin/adminCenter.css', 'utf8')
    );
    const resolve = (value: string) => value.replace(/var\((--[\w-]+)\)/g, (_, name: string) => tokens[name]);
    const styleValue = (selector: string, property: string): string => {
      let value = '';
      styles.walkRules((rule) => {
        if (rule.selectors.includes(selector)) {
          rule.walkDecls(property, (declaration) => {
            value = resolve(declaration.value);
          });
        }
      });
      expect(value, `${selector} ${property}`).not.toBe('');
      return value;
    };

    for (const selector of [
      '.status-badge--success',
      '.status-badge--error',
      '.status-badge--warning',
      '.status-badge--neutral',
      '.source-badge--user',
      '.source-badge--failed',
      '.duplicate-info',
      '.admin-message--success',
      '.admin-message--error',
    ]) {
      const background = styleValue(selector, 'background');
      const mix = background.match(/^color-mix\(in srgb, (#[\da-f]+) ([\d.]+)%, (rgb\([^)]+\))\)$/i);
      const surface = mix ? blendColor(mix[1], mix[3], Number(mix[2]) / 100) : blendColor(background, sheet);
      expect(contrastRatio(styleValue(selector, 'color'), surface), selector).toBeGreaterThanOrEqual(
        MINIMUM_SMALL_TEXT_CONTRAST
      );
    }

    const duplicateSurface = blendColor(styleValue('.duplicate-info', 'background'), sheet);
    expect(
      contrastRatio(styleValue('.track-link', 'color'), duplicateSurface),
      'duplicate track link'
    ).toBeGreaterThanOrEqual(MINIMUM_SMALL_TEXT_CONTRAST);

    for (const selector of [
      '.admin-status-pill--success',
      '.admin-status-pill--warning',
      '.admin-status-pill--error',
      '.admin-status-row--warning .settings-row__value',
    ]) {
      expect(contrastRatio(styleValue(selector, 'color'), elevated), selector).toBeGreaterThanOrEqual(
        MINIMUM_SMALL_TEXT_CONTRAST
      );
    }
  });
});

describe('light theme text contrast tokens', () => {
  it('keeps secondary text tokens readable against white surfaces', () => {
    const tokens = ['--text-primary', '--text-secondary', '--text-muted', '--text-faint'];

    for (const token of tokens) {
      expect(contrastRatio(readRootToken(token), '#ffffff'), token).toBeGreaterThanOrEqual(MINIMUM_SMALL_TEXT_CONTRAST);
    }
  });
});

describe.each(['light', 'dark'])('map GPS sketch contrast in %s theme', (theme) => {
  it('keeps loaded strokes immediately distinct on normal, hovered and selected rows', () => {
    const tokens = themeTokens(theme);
    const component = readFileSync('src/components/map/TrackChoiceRow.vue', 'utf8');
    const renderer = readFileSync('src/components/ui/TrackShapePreview.vue', 'utf8');
    const styles = postcss.parse(
      component.match(/<style scoped>([\s\S]*?)<\/style>/)![1] + renderer.match(/<style scoped>([\s\S]*?)<\/style>/)![1]
    );
    const value = (selector: string, property: string): string => {
      let result = '';
      styles.walkRules((rule) => {
        if (rule.selectors.includes(selector)) {
          rule.walkDecls(property, (declaration) => {
            result = declaration.value;
          });
        }
      });
      return result.replace(/var\((--[\w-]+)\)/g, (_, token: string) => tokens[token]);
    };
    const shape = '.track-choice-row .track-choice-row__shape';
    const lineColor = component.match(/line-color="([^"]+)"/)![1];
    const color =
      lineColor === 'currentColor'
        ? value(shape, 'color') || value('.track-choice-row', 'color')
        : lineColor.replace(/var\((--[\w-]+)\)/g, (_, token: string) => tokens[token]);
    const mix = color.match(/^color-mix\(in srgb, (#[\da-f]+) ([\d.]+)%, (#[\da-f]+)\)$/i);
    const stroke = mix ? blendColor(mix[1], mix[3], Number(mix[2]) / 100) : color;
    const svgOpacity = Number(
      value('.track-choice-row__shape :deep(svg)', 'opacity') || value('.track-shape-preview__svg', 'opacity') || 1
    );
    const opacity = Number(value(shape, 'opacity') || 1) * svgOpacity;
    const sheet = blendColor(tokens['--surface-sheet-solid'], '#ffffff');
    for (const selector of ['.track-choice-row', '.track-choice-row:hover', '.track-choice-row--selected']) {
      const background = blendColor(value(selector, 'background'), sheet);
      const renderedStroke = blendColor(stroke, background, opacity);
      expect(contrastRatio(renderedStroke, background), selector).toBeGreaterThanOrEqual(3);
    }
  });
});

describe.each(['light', 'dark'])('track review warning in %s theme', (theme) => {
  it('keeps the actual notice text and review link readable on its warning surface', () => {
    const tokens: Record<string, string> = {};
    const baseCss = postcss.parse(readFileSync('src/assets/base.css', 'utf8'));
    baseCss.walkRules((rule) => {
      if (rule.selector === ':root' || (theme === 'dark' && rule.selector === "[data-theme='dark']")) {
        rule.walkDecls((declaration) => {
          tokens[declaration.prop] = declaration.value;
        });
      }
    });
    const component = readFileSync('src/components/trackdetails/TrackDetails.vue', 'utf8');
    const styles = postcss.parse(component.match(/<style scoped>([\s\S]*?)<\/style>/)![1]);
    const ruleValue = (selector: string, property: string): string => {
      let value = '';
      styles.walkRules(selector, (rule) => {
        rule.walkDecls(property, (declaration) => {
          value = declaration.value;
        });
      });
      expect(value, `${selector} ${property}`).not.toBe('');
      return value;
    };
    const resolve = (value: string): string => value.replace(/var\((--[\w-]+)\)/g, (_, name: string) => tokens[name]);
    const notice = '.track-source-review-notice';
    const foreground = resolve(ruleValue(notice, 'color'));
    const linkRule = ruleValue(`${notice} a`, 'color');
    const link = linkRule === 'inherit' ? foreground : resolve(linkRule);
    const rgba = resolve(ruleValue(notice, 'background'))
      .match(/[\d.]+/g)!
      .map(Number);
    const surface = tokens['--surface-sheet-solid'].match(/[\d.]+/g)!.map(Number);
    const alpha = rgba[3] ?? 1;
    const background =
      '#' +
      rgba
        .slice(0, 3)
        .map((channel, index) =>
          Math.round(channel * alpha + surface[index] * (1 - alpha))
            .toString(16)
            .padStart(2, '0')
        )
        .join('');

    for (const [label, color] of [
      ['notice', foreground],
      ['review link', link],
    ]) {
      expect(contrastRatio(color, background), label).toBeGreaterThanOrEqual(MINIMUM_SMALL_TEXT_CONTRAST);
    }
  });
});
