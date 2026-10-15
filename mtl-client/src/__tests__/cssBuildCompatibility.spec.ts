// @vitest-environment node
import { readFileSync } from 'node:fs';
import { compileStyle, parse } from '@vue/compiler-sfc';
import postcss from 'postcss';
import { build } from 'vite';
import { expect, it } from 'vitest';
import appConfig from '../../vite.config';

function normalizeSelector(selector: string): string {
  return selector.replace(/\[([\w-]+)=['"]([^'"]+)['"]\]/g, '[$1=$2]');
}

it('preserves standard and Safari backdrop filters in production CSS', async () => {
  const login = parse(readFileSync('src/views/LoginView.vue', 'utf8')).descriptor;
  const loginCss = compileStyle({
    source: login.styles[0].content,
    filename: 'LoginView.vue',
    id: 'data-v-login-check',
    scoped: true,
  }).code;
  const sourceCss = postcss.parse(readFileSync('src/assets/main.css', 'utf8') + loginCss);
  const glassRules = new Set<string>();
  const expectedSelectors = new Set<string>();
  sourceCss.walkDecls('-webkit-backdrop-filter', (declaration) => {
    glassRules.add(declaration.parent!.toString());
    if (declaration.parent!.type === 'rule') {
      declaration.parent!.selectors.forEach((selector) => expectedSelectors.add(normalizeSelector(selector)));
    }
  });

  const config = await appConfig({ command: 'build', mode: 'production' });
  const entry = 'virtual:glass-check.css';
  const result = await build({
    configFile: false,
    logLevel: 'silent',
    plugins: [
      {
        name: 'glass-check',
        resolveId: (id) => (id === entry ? entry : undefined),
        load: (id) => (id === entry ? [...glassRules].join('\n') : undefined),
      },
    ],
    build: {
      cssMinify: config.build?.cssMinify,
      target: config.build?.target,
      cssTarget: config.build?.cssTarget,
      write: false,
      rollupOptions: { input: entry },
    },
  });
  const outputs = Array.isArray(result) ? result : [result];
  const css = outputs
    .flatMap((output) => ('output' in output ? output.output : []))
    .filter((asset) => asset.type === 'asset' && asset.fileName.endsWith('.css'))
    .map((asset) => ('source' in asset ? String(asset.source) : ''))
    .join('\n');
  expect(css).not.toBe('');

  const checkedSelectors = new Set<string>();
  postcss.parse(css).walkDecls('-webkit-backdrop-filter', (declaration) => {
    const declarations = declaration.parent!.nodes.filter((node) => node.type === 'decl');
    const standard = declarations.find((node) => node.prop === 'backdrop-filter');
    expect(standard?.value, declaration.parent!.toString()).toBe(declaration.value);
    if (declaration.parent!.type === 'rule') {
      declaration.parent!.selectors.forEach((selector) => checkedSelectors.add(normalizeSelector(selector)));
    }
  });
  expect(checkedSelectors).toEqual(expectedSelectors);
});
