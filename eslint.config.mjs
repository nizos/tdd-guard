import js from '@eslint/js'
import typescript from '@typescript-eslint/eslint-plugin'
import typescriptParser from '@typescript-eslint/parser'
import sonarjs from 'eslint-plugin-sonarjs'
import globals from 'globals'

export default [
  js.configs.recommended,
  {
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      parser: typescriptParser,
      parserOptions: {
        ecmaVersion: 2020,
        sourceType: 'module',
        project: './tsconfig.eslint.json',
      },
      globals: {
        ...globals.es2020,
        ...globals.node,
      },
    },
    plugins: {
      '@typescript-eslint': typescript,
      sonarjs: sonarjs,
    },
    rules: {
      ...typescript.configs.recommended.rules,
      ...sonarjs.configs.recommended.rules,

      // Prettier handling
      'no-extra-semi': 'off',
      semi: 'off',

      // === COMPLEXITY & SIZE ===
      complexity: ['error', 20],
      'sonarjs/cognitive-complexity': ['error', 20],
      'max-lines-per-function': ['warn', 80],
      'max-depth': ['error', 5],
      'max-nested-callbacks': ['error', 5],
      'max-params': ['warn', 5],
      'max-statements': ['warn', 40],
      'sonarjs/max-switch-cases': ['error', 30],

      // === CODE DUPLICATION ===
      'sonarjs/no-duplicate-string': ['error', { threshold: 5 }],
      'sonarjs/no-identical-functions': 'error',
      'sonarjs/no-duplicated-branches': 'error',
      'sonarjs/no-identical-conditions': 'error',
      'sonarjs/no-identical-expressions': 'error',

      // === CODE QUALITY & SMELLS ===
      'no-magic-numbers': [
        'warn',
        {
          ignore: [0, 1, -1, 2, 100],
          ignoreArrayIndexes: true,
          enforceConst: true,
        },
      ],
      'sonarjs/no-redundant-boolean': 'error',
      'sonarjs/no-redundant-jump': 'error',
      'sonarjs/no-redundant-assignments': 'error',
      'sonarjs/no-small-switch': 'error',
      'sonarjs/prefer-immediate-return': 'error',
      'sonarjs/no-inverted-boolean-check': 'error',
      'sonarjs/no-gratuitous-expressions': 'error',
      'sonarjs/no-unthrown-error': 'error',
      'sonarjs/prefer-object-literal': 'error',
      'sonarjs/todo-tag': 'off', // We have items called todo

      // === STRUCTURE & READABILITY ===
      'sonarjs/no-collapsible-if': 'error',
      'sonarjs/no-nested-switch': 'error',
      'sonarjs/no-nested-template-literals': 'error',
      'sonarjs/no-same-line-conditional': 'error',
      'no-nested-ternary': 'error',
      'no-else-return': 'error',

      // === MAINTAINABILITY ===
      'sonarjs/no-collection-size-mischeck': 'error',
      'sonarjs/no-unused-vars': 'error',
      'sonarjs/no-unused-function-argument': 'error',
      'no-shadow': 'error',
      'no-param-reassign': 'error',
      // 'consistent-return': 'error',

      // === MODERN JAVASCRIPT ===
      'prefer-const': 'error',
      'prefer-template': 'error',
      'object-shorthand': 'error',
      'prefer-arrow-callback': 'error',
      'prefer-object-spread': 'error',

      // === TYPESCRIPT SPECIFIC ===
      '@typescript-eslint/explicit-function-return-type': 'warn',
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/prefer-readonly': 'error',
      '@typescript-eslint/prefer-optional-chain': 'error',
      '@typescript-eslint/prefer-nullish-coalescing': 'error',
      '@typescript-eslint/no-unnecessary-condition': 'warn',
      '@typescript-eslint/switch-exhaustiveness-check': 'error',
      'sonarjs/use-type-alias': 'warn',
      'sonarjs/redundant-type-aliases': 'error',
    },
  },
  {
    files: [
      '**/*.test.ts',
      '**/*.spec.ts',
      '**/*.test.tsx',
      '**/*.spec.tsx',
      '**/factories/*',
    ],
    rules: {
      'max-lines-per-function': 'off',
      'sonarjs/no-nested-functions': 'off',
      'no-magic-numbers': 'off',
      complexity: ['error', 20],
      'sonarjs/cognitive-complexity': ['error', 20],
      'max-nested-callbacks': ['error', 10],
      '@typescript-eslint/explicit-function-return-type': 'off',
      'sonarjs/no-duplicate-string': ['error', { threshold: 20 }],
    },
  },
  {
    ignores: [
      '**/dist/**',
      '**/node_modules/**',
      'coverage/**',
      'test/artifacts/**',
      '.venv/**',
      'reporters/pytest/**',
      'reporters/phpunit/**',
      '**/*.d.ts',
      '**/*.tsbuildinfo',
    ],
  },
]
