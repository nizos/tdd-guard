import { describe, test, expect, beforeEach, afterEach } from 'vitest'
import { GolangciLint, buildArgs } from './GolangciLint'
import { join } from 'path'
import { hasRules, issuesFromFile } from '../../../test/utils/assertions'

// Test artifacts directory - defined once for reuse
const artifactsDir = join(process.cwd(), 'test', 'artifacts', 'go')
const configPath = join(artifactsDir, '.golangci.yml')
const withIssuesDir = join(artifactsDir, 'with-issues')
const withoutIssuesDir = join(artifactsDir, 'without-issues')

describe('GolangciLint', () => {
  let linter: GolangciLint

  beforeEach(() => {
    linter = new GolangciLint()
  })

  describe('basic functionality', () => {
    test('can be instantiated', () => {
      expect(linter).toBeDefined()
    })

    test('implements Linter interface with lint method', async () => {
      const result = await linter.lint([])

      expect(result).toBeDefined()
      expect(result.timestamp).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/)
    })

    test('returns the file paths that were passed in', async () => {
      const filePaths = ['main.go', 'pkg/handler.go']
      const result = await linter.lint(filePaths)

      expect(result.files).toEqual(filePaths)
    })
  })

  describe('linting behavior', () => {
    let originalCwd: string

    beforeEach(() => {
      originalCwd = process.cwd()
      process.chdir(withIssuesDir)
    })

    afterEach(() => {
      process.chdir(originalCwd)
    })

    test('detects issues in file with lint problems', async () => {
      const filePath = join(withIssuesDir, 'file-with-issues.go')
      const result = await linter.lint([filePath], configPath)

      expect(result.issues.length).toBeGreaterThan(0)
      expect(result.errorCount).toBeGreaterThan(0)

      // Check for specific rules that should be detected
      const expectedRules = ['typecheck']
      const ruleResults = hasRules(result.issues, expectedRules)

      ruleResults.forEach((ruleExists) => {
        expect(ruleExists).toBe(true)
      })
    })

    test('returns specific golangci-lint message for undefined variable', async () => {
      const filePath = join(withIssuesDir, 'file-with-issues.go')
      const result = await linter.lint([filePath])

      const undefinedVarIssue = result.issues.find((issue) =>
        issue.message.includes('undefined: messag')
      )
      expect(undefinedVarIssue).toMatchObject({
        rule: 'typecheck',
        message: expect.stringContaining('undefined: messag'),
      })
    })

    test('returns no issues for clean Go file', async () => {
      const filePath = join(withoutIssuesDir, 'file-without-issues.go')
      const result = await linter.lint([filePath])

      expect(result.issues.length).toBe(0)
      expect(result.errorCount).toBe(0)
    })

    test('returns exactly 2 issues for problematic file', async () => {
      const filePath = join(withIssuesDir, 'file-with-issues.go')
      const result = await linter.lint([filePath])

      expect(result.issues.length).toBe(2)
      expect(result.errorCount).toBe(2)
    })

    test('returns different results for different files', async () => {
      const problemFile = join(withIssuesDir, 'file-with-issues.go')
      const cleanFile = join(withoutIssuesDir, 'file-without-issues.go')

      const problemResult = await linter.lint([problemFile])
      const cleanResult = await linter.lint([cleanFile])

      expect(problemResult.issues.length).toBeGreaterThan(0)
      expect(cleanResult.issues.length).toBe(0)
    })

    test('processes individual files correctly', async () => {
      const problemFile = join(withIssuesDir, 'file-with-issues.go')
      const result = await linter.lint([problemFile])

      expect(result.files).toEqual([problemFile])
      expect(result.issues.length).toBeGreaterThan(0)

      // All issues should be from the problematic file
      const problemFileIssues = issuesFromFile(
        result.issues,
        'file-with-issues.go'
      )
      expect(problemFileIssues.length).toBeGreaterThan(0)
    })
  })

  describe('directory-based linting', () => {
    test('should build args for directory-based linting with current directory', () => {
      const args = buildArgs([], configPath)

      // Should contain basic golangci-lint arguments (v1.64+ format)
      expect(args).toContain('run')
      expect(args).toContain('--out-format=json')
      expect(args).toContain('--config')
      expect(args).toContain(configPath)

      // With empty filePaths, should have no directories in args
      expect(args).not.toContain('.')
      expect(args).toContain('--path-prefix=.')
    })
  })

  describe('with files containing special characters', () => {
    test.each([
      ['spaces', 'src/my file with spaces.go'],
      ['quotes', 'src/file"with"quotes.go'],
      ['semicolons', 'src/file;name.go'],
      ['backticks', 'src/file`with`backticks.go'],
      ['dollar signs', 'src/file$with$dollar.go'],
      ['pipes', 'src/file|with|pipes.go'],
      ['ampersands', 'src/file&with&ampersands.go'],
      ['parentheses', 'src/file(with)parentheses.go'],
      ['command injection attempt', 'file.go"; cat /etc/passwd; echo "'],
      ['newlines', 'src/file\nwith\nnewlines.go'],
      ['tabs', 'src/file\twith\ttabs.go'],
    ])('handles file paths with %s correctly', async (_, filePath) => {
      const result = await linter.lint([filePath])

      expect(result.files).toEqual([filePath])
      expect(result.timestamp).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/)
    })

    test.each([
      ['spaces', '/path with spaces/.golangci.yml'],
      ['quotes', '/path"with"quotes/.golangci.yml'],
      ['special chars', '/path;with&special|chars/.golangci.yml'],
    ])('handles config paths with %s correctly', async (_, configFilePath) => {
      const result = await linter.lint(['main.go'], configFilePath)

      expect(result.files).toEqual(['main.go'])
      expect(result.timestamp).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/)
    })
  })
})
