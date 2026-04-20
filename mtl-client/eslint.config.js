// ESLint flat config — warnings-only baseline so we can land it without
// blocking development. Tighten rules incrementally.
import pluginVue from 'eslint-plugin-vue';
import vueTsConfig from '@vue/eslint-config-typescript';
import prettierConfig from '@vue/eslint-config-prettier';

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
      'vue/multi-word-component-names': 'warn',
      'vue/no-unused-vars': 'warn',
      'no-console': 'off',
      'no-debugger': 'warn',
    },
  },
];
