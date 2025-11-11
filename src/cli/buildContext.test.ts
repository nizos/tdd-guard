import { describe, it, expect, beforeEach } from 'vitest'
import { buildContext } from './buildContext'
import { MemoryStorage } from '../storage/MemoryStorage'

describe('buildContext', () => {
  let storage: MemoryStorage

  beforeEach(() => {
    storage = new MemoryStorage()
  })

  it('should return a context with default values when storage is empty', async () => {
    const context = await buildContext(storage)

    expect(context).toEqual({
      modifications: '',
      test: '',
      todo: '',
      lint: {
        hasIssues: false,
        summary: 'No lint data available',
        issuesByFile: new Map(),
        totalIssues: 0,
        errorCount: 0,
        warningCount: 0,
      },
      instructions: undefined,
      testFileExists: false,
      language: undefined,
      languageCategory: undefined,
    })
  })

  it('should return context with values from storage', async () => {
    await storage.saveModifications('some modifications content')
    await storage.saveTest('test code')
    await storage.saveTodo('pending: implement feature')

    const context = await buildContext(storage)

    expect(context).toEqual({
      modifications: 'some modifications content',
      test: 'test code',
      todo: 'pending: implement feature',
      lint: {
        hasIssues: false,
        summary: 'No lint data available',
        issuesByFile: new Map(),
        totalIssues: 0,
        errorCount: 0,
        warningCount: 0,
      },
      testFileExists: false,
      language: undefined,
      languageCategory: undefined,
    })
  })

  it('should parse modifications JSON data when valid JSON is stored', async () => {
    const modificationsData = {
      tool_input: {
        file_path: '/src/example.ts',
        content: 'new file content',
      },
    }
    const modificationsJson = JSON.stringify(modificationsData)
    await storage.saveModifications(modificationsJson)
    await storage.saveTest('test code')
    await storage.saveTodo('pending: implement feature')

    const context = await buildContext(storage)

    expect(context).toEqual({
      modifications: JSON.stringify(modificationsData, null, 2),
      test: 'test code',
      todo: 'pending: implement feature',
      lint: {
        hasIssues: false,
        summary: 'No lint data available',
        issuesByFile: new Map(),
        totalIssues: 0,
        errorCount: 0,
        warningCount: 0,
      },
      testFileExists: false,
      language: 'typescript',
      languageCategory: 'interpreted',
    })
  })

  it('should pretty-print modifications JSON for better readability', async () => {
    const modificationsData = {
      file_path: '/src/Calculator.test.ts',
      old_string:
        "describe('Calculator', () => {\n  test('should add two numbers correctly', () => {\n    const result = calculator.add(2, 3)\n    expect(result).toBe(5)\n  })\n})",
      new_string:
        "describe('Calculator', () => {\n  test('should add two numbers correctly', () => {\n    const result = calculator.add(2, 3)\n    expect(result).toBe(5)\n  })\n  \n  test('should divide two numbers correctly', () => {\n    const result = calculator.divide(4, 2)\n    expect(result).toBe(2)\n  })\n})",
    }
    await storage.saveModifications(JSON.stringify(modificationsData))

    const context = await buildContext(storage)

    // Should be pretty-printed, not a compact JSON string
    expect(context.modifications).toBe(
      JSON.stringify(modificationsData, null, 2)
    )
  })

  it('should process valid lint data when stored', async () => {
    const lintData = {
      timestamp: '2024-01-01T00:00:00Z',
      files: ['/src/example.ts'],
      issues: [
        {
          file: '/src/example.ts',
          line: 10,
          column: 5,
          severity: 'error',
          message: 'Missing semicolon',
          rule: 'semi',
        },
      ],
      errorCount: 1,
      warningCount: 0,
      hasNotifiedAboutLintIssues: false,
    }
    await storage.saveLint(JSON.stringify(lintData))

    const context = await buildContext(storage)

    expect(context.lint).toEqual({
      hasIssues: true,
      summary: '1 lint issue found (1 error, 0 warnings)',
      issuesByFile: new Map([
        ['/src/example.ts', ['  Line 10:5 - error: Missing semicolon (semi)']],
      ]),
      totalIssues: 1,
      errorCount: 1,
      warningCount: 0,
    })
  })

  it('should handle invalid lint JSON gracefully', async () => {
    await storage.saveLint('invalid json')

    const context = await buildContext(storage)

    // Should fall back to default lint data
    expect(context.lint).toEqual({
      hasIssues: false,
      summary: 'No lint data available',
      issuesByFile: new Map(),
      totalIssues: 0,
      errorCount: 0,
      warningCount: 0,
    })
  })

  it('should handle lint data that fails schema validation', async () => {
    // Missing required fields
    const invalidLintData = {
      timestamp: '2024-01-01T00:00:00Z',
      // Missing: files, issues, errorCount, warningCount, hasNotifiedAboutLintIssues
    }
    await storage.saveLint(JSON.stringify(invalidLintData))

    const context = await buildContext(storage)

    // Should fall back to default lint data
    expect(context.lint).toEqual({
      hasIssues: false,
      summary: 'No lint data available',
      issuesByFile: new Map(),
      totalIssues: 0,
      errorCount: 0,
      warningCount: 0,
    })
  })

  it('should handle lint data with no issues', async () => {
    const lintData = {
      timestamp: '2024-01-01T00:00:00Z',
      files: ['/src/example.ts'],
      issues: [],
      errorCount: 0,
      warningCount: 0,
      hasNotifiedAboutLintIssues: false,
    }
    await storage.saveLint(JSON.stringify(lintData))

    const context = await buildContext(storage)

    expect(context.lint).toEqual({
      hasIssues: false,
      summary: 'No lint issues found',
      issuesByFile: new Map(),
      totalIssues: 0,
      errorCount: 0,
      warningCount: 0,
    })
  })

  it('should include instructions when available in storage', async () => {
    const customInstructions = '## Custom TDD Rules\n1. Write tests first'
    await storage.saveInstructions(customInstructions)

    const context = await buildContext(storage)

    expect(context.instructions).toBe(customInstructions)
  })
})
