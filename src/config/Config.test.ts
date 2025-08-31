import { describe, test, expect, beforeEach, afterEach } from 'vitest'
import { Config } from './Config'
import path from 'path'

describe('Config', () => {
  const originalEnv = process.env

  beforeEach(() => {
    process.env = { ...originalEnv }
  })

  afterEach(() => {
    process.env = originalEnv
  })

  describe('dataDir', () => {
    const projectRoot = '/test/project'
    const projectDataDir = path.join(projectRoot, Config.DEFAULT_DATA_DIR)
    let config: Config

    beforeEach(() => {
      config = new Config({ projectRoot })
    })

    test('defaults to relative path when no projectRoot provided', () => {
      const defaultConfig = new Config()
      expect(defaultConfig.dataDir).toBe(Config.DEFAULT_DATA_DIR)
    })

    test('uses projectRoot to construct absolute dataDir', () => {
      expect(config.dataDir).toBe(projectDataDir)
    })

    test('testResultsFilePath returns test.json path within dataDir', () => {
      expect(config.testResultsFilePath).toBe(
        path.join(projectDataDir, 'test.json')
      )
    })

    test('todosFilePath returns todos.json path within dataDir', () => {
      expect(config.todosFilePath).toBe(path.join(projectDataDir, 'todos.json'))
    })

    test('modificationsFilePath returns modifications.json path within dataDir', () => {
      expect(config.modificationsFilePath).toBe(
        path.join(projectDataDir, 'modifications.json')
      )
    })

    test('lintFilePath returns lint.json path within dataDir', () => {
      expect(config.lintFilePath).toBe(path.join(projectDataDir, 'lint.json'))
    })

    test('configFilePath returns config.json path within dataDir', () => {
      expect(config.configFilePath).toBe(
        path.join(projectDataDir, 'config.json')
      )
    })

    test('instructionsFilePath returns instructions.md path within dataDir', () => {
      expect(config.instructionsFilePath).toBe(
        path.join(projectDataDir, 'instructions.md')
      )
    })

    describe('CLAUDE_PROJECT_DIR', () => {
      let originalCwd: typeof process.cwd

      beforeEach(() => {
        originalCwd = process.cwd
      })

      afterEach(() => {
        process.cwd = originalCwd
        delete process.env.CLAUDE_PROJECT_DIR
      })

      test('uses CLAUDE_PROJECT_DIR when available and no projectRoot provided', () => {
        const claudeProjectDir = '/claude/project/root'
        process.env.CLAUDE_PROJECT_DIR = claudeProjectDir
        process.cwd = () => '/claude/project/root/src'

        const configWithClaudeDir = new Config()

        expect(configWithClaudeDir.dataDir).toBe(
          path.join(claudeProjectDir, Config.DEFAULT_DATA_DIR)
        )
      })

      test('projectRoot option takes precedence over CLAUDE_PROJECT_DIR', () => {
        const claudeProjectDir = '/claude/project/root'
        const explicitProjectRoot = '/explicit/project/root'
        process.env.CLAUDE_PROJECT_DIR = claudeProjectDir

        const configWithBoth = new Config({ projectRoot: explicitProjectRoot })

        expect(configWithBoth.dataDir).toBe(
          path.join(explicitProjectRoot, Config.DEFAULT_DATA_DIR)
        )
      })

      test('throws error when CLAUDE_PROJECT_DIR is not an absolute path', () => {
        process.env.CLAUDE_PROJECT_DIR = 'relative/path'

        expect(() => new Config()).toThrow(
          'CLAUDE_PROJECT_DIR must be an absolute path'
        )
      })

      test('throws error when cwd is outside CLAUDE_PROJECT_DIR', () => {
        process.env.CLAUDE_PROJECT_DIR = '/project/root'
        process.cwd = () => '/some/other/path'

        expect(() => new Config()).toThrow(
          'CLAUDE_PROJECT_DIR must contain the current working directory'
        )
      })

      test('uses CLAUDE_PROJECT_DIR when cwd is deeply nested within it', () => {
        process.env.CLAUDE_PROJECT_DIR = '/project/root'
        process.cwd = () => '/project/root/src/nested/deeply'

        const configWithNestedCwd = new Config()

        expect(configWithNestedCwd.dataDir).toBe(
          '/project/root/.claude/tdd-guard/data'
        )
      })

      test('throws error when CLAUDE_PROJECT_DIR contains path traversal', () => {
        process.env.CLAUDE_PROJECT_DIR = '/some/path/../../../other'
        process.cwd = () => '/other/location'

        expect(() => new Config()).toThrow(
          'CLAUDE_PROJECT_DIR must not contain path traversal'
        )
      })
    })
  })

  describe('useSystemClaude', () => {
    test('can be set via options', () => {
      const config = new Config({ useSystemClaude: true })

      expect(config.useSystemClaude).toBe(true)
    })

    test('options take precedence over env var', () => {
      process.env.USE_SYSTEM_CLAUDE = 'false'

      const config = new Config({ useSystemClaude: true })

      expect(config.useSystemClaude).toBe(true)
    })

    test('falls back to env var when not in options', () => {
      process.env.USE_SYSTEM_CLAUDE = 'true'

      const config = new Config({})

      expect(config.useSystemClaude).toBe(true)
    })

    test('defaults to false when neither options nor env var are set', () => {
      delete process.env.USE_SYSTEM_CLAUDE

      const config = new Config()

      expect(config.useSystemClaude).toBe(false)
    })

    test('returns false for non-true env values', () => {
      // Test with 'false'
      process.env.USE_SYSTEM_CLAUDE = 'false'
      let config = new Config()
      expect(config.useSystemClaude).toBe(false)

      // Test with empty string
      process.env.USE_SYSTEM_CLAUDE = ''
      config = new Config()
      expect(config.useSystemClaude).toBe(false)
    })
  })

  describe('anthropicApiKey', () => {
    test('can be set via options', () => {
      const config = new Config({ anthropicApiKey: 'options-api-key' })

      expect(config.anthropicApiKey).toBe('options-api-key')
    })

    test('options take precedence over env var', () => {
      process.env.TDD_GUARD_ANTHROPIC_API_KEY = 'env-api-key'

      const config = new Config({ anthropicApiKey: 'options-api-key' })

      expect(config.anthropicApiKey).toBe('options-api-key')
    })

    test('falls back to env var when not in options', () => {
      process.env.TDD_GUARD_ANTHROPIC_API_KEY = 'env-api-key'

      const config = new Config()

      expect(config.anthropicApiKey).toBe('env-api-key')
    })

    test('returns undefined when neither options nor env var are set', () => {
      delete process.env.TDD_GUARD_ANTHROPIC_API_KEY

      const config = new Config()

      expect(config.anthropicApiKey).toBeUndefined()
    })
  })

  describe('modelType', () => {
    test('can be set via options', () => {
      const config = new Config({ modelType: 'anthropic_api' })

      expect(config.modelType).toBe('anthropic_api')
    })

    test('options take precedence over env vars', () => {
      process.env.MODEL_TYPE = 'claude_cli'

      const config = new Config({ modelType: 'anthropic_api' })

      expect(config.modelType).toBe('anthropic_api')
    })

    test('options take precedence even in test mode with TEST_MODEL_TYPE', () => {
      process.env.MODEL_TYPE = 'claude_cli'
      process.env.TEST_MODEL_TYPE = 'test_model'

      const config = new Config({ mode: 'test', modelType: 'anthropic_api' })

      expect(config.modelType).toBe('anthropic_api')
    })

    test('falls back to MODEL_TYPE env var in production mode', () => {
      process.env.MODEL_TYPE = 'anthropic_api'

      const config = new Config()

      expect(config.modelType).toBe('anthropic_api')
    })

    test('uses TEST_MODEL_TYPE in test mode when available', () => {
      process.env.MODEL_TYPE = 'claude_cli'
      process.env.TEST_MODEL_TYPE = 'anthropic_api'

      const config = new Config({ mode: 'test' })

      expect(config.modelType).toBe('anthropic_api')
    })

    test('falls back to MODEL_TYPE in test mode when TEST_MODEL_TYPE is not set', () => {
      process.env.MODEL_TYPE = 'anthropic_api'
      delete process.env.TEST_MODEL_TYPE

      const config = new Config({ mode: 'test' })

      expect(config.modelType).toBe('anthropic_api')
    })

    test('defaults to claude_cli when no env vars are set', () => {
      delete process.env.MODEL_TYPE
      delete process.env.TEST_MODEL_TYPE

      const config = new Config()

      expect(config.modelType).toBe('claude_cli')
    })
  })

  describe('linterType', () => {
    test('returns undefined when no configuration provided', () => {
      delete process.env.LINTER_TYPE

      const config = new Config()

      expect(config.linterType).toBeUndefined()
    })

    test('returns eslint when LINTER_TYPE env var is set to eslint', () => {
      process.env.LINTER_TYPE = 'eslint'

      const config = new Config()

      expect(config.linterType).toBe('eslint')
    })

    test('returns value from ConfigOptions when linterType is provided', () => {
      const config = new Config({ linterType: 'eslint' })

      expect(config.linterType).toBe('eslint')
    })

    test('ConfigOptions takes precedence over env var', () => {
      process.env.LINTER_TYPE = 'pylint'

      const config = new Config({ linterType: 'eslint' })

      expect(config.linterType).toBe('eslint')
    })

    test('returns undefined for empty string env var', () => {
      process.env.LINTER_TYPE = ''

      const config = new Config()

      expect(config.linterType).toBeUndefined()
    })

    test('returns future linter types when configured', () => {
      // Test that the system is extensible for future linter types
      process.env.LINTER_TYPE = 'pylint'

      const config = new Config()

      expect(config.linterType).toBe('pylint')
    })

    test('returns linterType in lowercase when env var is uppercase', () => {
      process.env.LINTER_TYPE = 'ESLINT'

      const config = new Config()

      expect(config.linterType).toBe('eslint')
    })

    test('returns linterType in lowercase when ConfigOptions is uppercase', () => {
      const config = new Config({ linterType: 'ESLINT' })

      expect(config.linterType).toBe('eslint')
    })
  })
})
