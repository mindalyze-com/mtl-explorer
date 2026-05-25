// ESLint flat config — warnings-only baseline so we can land it without
// blocking development. Tighten rules incrementally.
import pluginVue from 'eslint-plugin-vue';
import vueTsConfig from '@vue/eslint-config-typescript';
import prettierConfig from '@vue/eslint-config-prettier';

const ONE_WORD_COMPONENT_IGNORES = [
  // PrimeVue global component names.
  'Accordion',
  'Button',
  'Card',
  'Column',
  'Dialog',
  'Message',
  'Panel',
  'Popover',
  'Row',
  'Select',
  'Slider',
  'Tab',
  'Tabs',
  'Textarea',
  'Toast',
  // Established app component names.
  'Filter',
  'Info',
  'Map',
  'Statistics',
];

export default [
  {
    ignores: [
      'dist/**',
      'dev-dist/**',
      'node_modules/**',
      'public/**',
      'doc/**',
      // Generated OpenAPI client lives under mtl-api/, not here, but ignore any
      // accidental copies just in case.
      '**/generated-sources/**',
    ],
  },
  ...pluginVue.configs['flat/recommended'],
  ...vueTsConfig(),
  prettierConfig,
  {
    rules: {
      // Baseline: surface issues as warnings, not errors. Promote to "error"
      // file-by-file as code is cleaned.
      '@typescript-eslint/no-explicit-any': 'warn',
      '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
      'vue/multi-word-component-names': ['warn', { ignores: ONE_WORD_COMPONENT_IGNORES }],
      'vue/no-reserved-component-names': ['error', { htmlElementCaseSensitive: true }],
      'vue/no-unused-vars': 'warn',
      'no-console': 'off',
      'no-debugger': 'warn',
    },
  },
  {
    files: ['src/**/__tests__/**/*.ts'],
    rules: {
      'vue/multi-word-component-names': 'off',
      'vue/one-component-per-file': 'off',
      'vue/require-default-prop': 'off',
    },
  },
];
