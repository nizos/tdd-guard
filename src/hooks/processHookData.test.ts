import { describe, it, expect, beforeEach, vi } from 'vitest'
import { processHookData, defaultResult } from './processHookData'
import { MemoryStorage } from '../storage/MemoryStorage'
import { testData } from '@testUtils'
import { ValidationResult } from '../contracts/types/ValidationResult'
import { Context } from '../contracts/types/Context'

const BLOCK_RESULT = {
  decision: 'block',
  reason: 'TDD violation',
} as const

const WRITE_HOOK_DATA = testData.writeOperation()
const EDIT_HOOK_DATA = testData.editOperation()
const TODO_WRITE_HOOK_DATA = testData.todoWriteOperation()

describe('processHookData', () => {
  let sut: ReturnType<typeof createTestProcessor>

  beforeEach(() => {
    sut = createTestProcessor()
  })

  it('should return a ValidationResult', async () => {
    const hookData = { type: 'test', data: 'some data' }

    const result = await sut.process(hookData)

    expect(result).toBeDefined()
    expect(result).toHaveProperty('decision')
    expect(result).toHaveProperty('reason')
  })

  it('should throw error on invalid JSON', async () => {
    const invalidJson = '{ invalid json'

    // For this test, we need to use processHookData directly since we're testing JSON parsing
    await expect(processHookData(invalidJson)).rejects.toThrow()
  })

  it('should save modifications content to storage when tool is Edit', async () => {
    await sut.process(EDIT_HOOK_DATA)

    const savedModifications = await sut.getModifications()
    const parsedModifications = JSON.parse(savedModifications!)
    expect(parsedModifications).toEqual(EDIT_HOOK_DATA)
  })

  it('should save todo content to storage when tool is TodoWrite', async () => {
    await sut.process(TODO_WRITE_HOOK_DATA)

    const savedTodo = await sut.getTodo()
    const parsedTodo = JSON.parse(savedTodo!)
    expect(parsedTodo).toEqual(TODO_WRITE_HOOK_DATA)
  })

  it('should save modifications content when tool has content field', async () => {
    await sut.process(WRITE_HOOK_DATA)

    const savedModifications = await sut.getModifications()
    const parsedModifications = JSON.parse(savedModifications!)
    expect(parsedModifications).toEqual(WRITE_HOOK_DATA)
  })

  it('should call validator with context built from storage', async () => {
    // Pre-populate storage
    await sut.populateStorage({
      modifications: 'existing modifications',
      test: 'existing test',
      todo: 'existing todo',
    })

    const result = await sut.process(EDIT_HOOK_DATA)

    const actualContext = sut.getValidatorCallArgs()
    
    // Verify the context, parsing JSON to handle formatting differences
    expect({
      ...actualContext,
      modifications: JSON.parse(actualContext!.modifications),
    }).toEqual({
      modifications: EDIT_HOOK_DATA,
      test: 'existing test',
      todo: 'existing todo',
      lint: {
        errorCount: 0,
        warningCount: 0,
        hasIssues: false,
        totalIssues: 0,
        issuesByFile: new Map(),
        summary: 'No lint data available'
      }
    })
    expect(result).toEqual(BLOCK_RESULT)
  })

  it('should not call validator for TodoWrite operations', async () => {
    // Pre-populate storage with existing edits that might cause false blocks
    await sut.populateStorage({
      modifications: 'existing modifications that might trigger validation',
    })

    const result = await sut.process(TODO_WRITE_HOOK_DATA)

    expect(sut.validatorHasBeenCalled()).toBe(false)
    expect(result).toEqual(defaultResult)
  })

  it('should handle hook data with invalid schema gracefully', async () => {
    // Invalid hook data that doesn't match either SimpleHookDataSchema or FullHookEventSchema
    const invalidHookData = {
      // This doesn't match FullHookEventSchema (missing required fields)
      // and has invalid types for SimpleHookDataSchema
      tool_name: 123, // Should be string
      tool_input: "not an object", // Should be object
    }

    const result = await sut.process(invalidHookData)

    // Should return default result without calling validator
    expect(sut.validatorHasBeenCalled()).toBe(false)
    expect(result).toEqual(defaultResult)
  })

  describe('PostToolUse hook handling', () => {
    it('should delegate to handlePostToolLint for PostToolUse events', async () => {
      const postToolUseHook = {
        ...EDIT_HOOK_DATA,
        hook_event_name: 'PostToolUse',
        tool_output: { success: true }
      }

      const result = await sut.process(postToolUseHook)

      // Should not call the validator
      expect(sut.validatorHasBeenCalled()).toBe(false)
      // Result depends on lint state, but should return a valid result
      expect(result).toHaveProperty('decision')
      expect(result).toHaveProperty('reason')
    })
  })

  describe('Non-code file filtering', () => {
    it('should skip validation for markdown files', async () => {
      const markdownFileData = {
        ...EDIT_HOOK_DATA,
        tool_input: {
          file_path: '/path/to/README.md',
          old_string: 'old content',
          new_string: 'new content'
        }
      }

      const result = await sut.process(markdownFileData)

      expect(sut.validatorHasBeenCalled()).toBe(false)
      expect(result).toEqual(defaultResult)
    })
  })

  describe('PreToolUse lint notification', () => {
    it('should block when tests pass, lint issues exist, and not yet notified', async () => {
      // Setup: passing tests
      await sut.populateStorage({
        test: JSON.stringify(testData.passingTestResults())
      })
      
      // Setup: lint issues with notification flag false
      await sut.storage.saveLint(JSON.stringify(
        testData.lintDataWithError({
          hasNotifiedAboutLintIssues: false
        })
      ))

      const result = await sut.process(EDIT_HOOK_DATA)

      expect(result.decision).toBe('block')
      expect(result.reason).toContain('Code quality issues detected')
      // Should not call the main validator
      expect(sut.validatorHasBeenCalled()).toBe(false)
    })

    it('should not block when tests are failing (red phase)', async () => {
      // Setup: failing tests
      await sut.populateStorage({
        test: JSON.stringify(testData.failedTestResults())
      })
      
      // Setup: lint issues with notification flag false
      await sut.storage.saveLint(JSON.stringify(
        testData.lintDataWithError({
          hasNotifiedAboutLintIssues: false
        })
      ))

      const result = await sut.process(EDIT_HOOK_DATA)

      // Should proceed to normal validation
      expect(result).toEqual(BLOCK_RESULT)
      expect(sut.validatorHasBeenCalled()).toBe(true)
    })

    it('should not block when no lint issues exist', async () => {
      // Setup: passing tests
      await sut.populateStorage({
        test: JSON.stringify(testData.passingTestResults())
      })
      
      // Setup: no lint issues
      await sut.storage.saveLint(JSON.stringify(
        testData.lintDataWithoutErrors({
          hasNotifiedAboutLintIssues: false
        })
      ))

      const result = await sut.process(EDIT_HOOK_DATA)

      // Should proceed to normal validation
      expect(result).toEqual(BLOCK_RESULT)
      expect(sut.validatorHasBeenCalled()).toBe(true)
    })

    it('should not block when already notified', async () => {
      // Setup: passing tests
      await sut.populateStorage({
        test: JSON.stringify(testData.passingTestResults())
      })
      
      // Setup: lint issues with notification flag true
      await sut.storage.saveLint(JSON.stringify(
        testData.lintDataWithError({
          hasNotifiedAboutLintIssues: true
        })
      ))

      const result = await sut.process(EDIT_HOOK_DATA)

      // Should proceed to normal validation
      expect(result).toEqual(BLOCK_RESULT)
      expect(sut.validatorHasBeenCalled()).toBe(true)
    })

    it('should set notification flag after blocking', async () => {
      // Setup: passing tests
      await sut.populateStorage({
        test: JSON.stringify(testData.passingTestResults())
      })
      
      // Setup: lint issues with notification flag false
      await sut.storage.saveLint(JSON.stringify(
        testData.lintDataWithError({
          hasNotifiedAboutLintIssues: false
        })
      ))

      await sut.process(EDIT_HOOK_DATA)

      // Check that the flag was updated
      const savedLint = await sut.storage.getLint()
      const parsedLint = JSON.parse(savedLint!)
      expect(parsedLint.hasNotifiedAboutLintIssues).toBe(true)
    })
  })
})

// Test setup helper
function createTestProcessor() {
  const storage = new MemoryStorage()
  const mockValidator = vi.fn().mockResolvedValue(BLOCK_RESULT)
  
  // Helper to process hook data
  const process = async (hookData: unknown): Promise<ValidationResult> => {
    return processHookData(JSON.stringify(hookData), {
      storage, 
      validator: mockValidator
    })
  }
  
  // Pre-populate storage helper
  const populateStorage = async (data: { modifications?: string; test?: string; todo?: string }): Promise<void> => {
    if (data.modifications) await storage.saveModifications(data.modifications)
    if (data.test) await storage.saveTest(data.test)
    if (data.todo) await storage.saveTodo(data.todo)
  }
  
  return {
    storage,
    process,
    populateStorage,
    
    // Storage accessors
    getModifications: (): Promise<string | null> => storage.getModifications(),
    getTest: (): Promise<string | null> => storage.getTest(),
    getTodo: (): Promise<string | null> => storage.getTodo(),
    
    // Validator checks
    validatorHasBeenCalled: (): boolean => mockValidator.mock.calls.length > 0,
    getValidatorCallArgs: (): Context | null => mockValidator.mock.calls[0]?.[0] ?? null,
    resetValidatorMock: (): void => mockValidator.mockClear(),
  }
}
