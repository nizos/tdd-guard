import { describe, it, expect, beforeEach, vi } from 'vitest'
import { processHookData, defaultResult } from './processHookData'
import { testData } from '@testUtils'
import { UserPromptHandler } from './userPromptHandler'
import { GuardManager } from '../guard/GuardManager'
import { MemoryStorage } from '../storage/MemoryStorage'
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
      },
      instructions: undefined,
      testFileExists: false,
      language: 'typescript',
      languageCategory: 'interpreted',
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

  describe('Ignore patterns filtering', () => {
    it('skips validation when using default ignore patterns', async () => {
      for (const pattern of GuardManager.DEFAULT_IGNORE_PATTERNS) {
        // Convert pattern to file path (e.g., '*.md' -> '/path/to/file.md')
        const filePath = pattern.replaceAll('*', '/path/to/file')
        
        const nonCodeFileData = {
          ...EDIT_HOOK_DATA,
          tool_input: {
            file_path: filePath,
            old_string: 'old content',
            new_string: 'new content'
          }
        }

        const result = await sut.process(nonCodeFileData)
        
        expect(sut.validatorHasBeenCalled()).toBe(false)
        expect(result).toEqual(defaultResult)
      }
    })

    it.each([
      {
        description: 'files matching custom extensions',
        filePath: 'file.custom',
      },
      {
        description: 'files in ignored directories',
        filePath: 'build/output.js',
      },
      {
        description: 'files matching glob patterns',
        filePath: 'src/api/schema.generated.ts',
      },
    ])('skips validation when using custom ignore patterns for $description', async ({ filePath }) => {
      // Set up custom ignore patterns
      const customPatterns = ['*.custom', 'build/**', '**/*.generated.ts']
      await sut.storage.saveConfig(JSON.stringify({
        guardEnabled: true,
        ignorePatterns: customPatterns
      }))

      const fileData = {
        ...EDIT_HOOK_DATA,
        tool_input: {
          file_path: filePath,
          old_string: 'old content',
          new_string: 'new content'
        }
      }

      const result = await sut.process(fileData)
      
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

  describe('SessionStart handling', () => {
    let result: ValidationResult

    beforeEach(async () => {
      // Populate storage with data
      await sut.populateStorage({
        test: JSON.stringify(testData.passingTestResults()),
        todo: JSON.stringify(testData.todoWriteOperation()),
        modifications: JSON.stringify(testData.editOperation()),
        lint: JSON.stringify(testData.lintDataWithoutErrors()),
        config: JSON.stringify({ guardEnabled: true })
      })

      const sessionStartData = testData.sessionStart()
      result = await sut.process(sessionStartData)
    })

    it('should clear transient data when SessionStart event is received', async () => {
      // Verify transient data is cleared
      expect(await sut.getTest()).toBeNull()
      expect(await sut.getTodo()).toBeNull()
      expect(await sut.getModifications()).toBeNull()
      expect(await sut.getLint()).toBeNull()
    })

    it('should preserve config data when SessionStart event is received', async () => {
      expect(await sut.getConfig()).toBe(JSON.stringify({ guardEnabled: true }))
    })

    it('should return defaultResult when SessionStart event is processed', () => {
      expect(result).toEqual(defaultResult)
    })
  })

  describe('UserPromptHandler integration', () => {
    it('should enable TDD Guard when user sends "tdd-guard on"', async () => {
      const storage = new MemoryStorage()
      const guardManager = new GuardManager(storage)
      await guardManager.disable() // Ensure it starts disabled
      const userPromptHandler = new UserPromptHandler(guardManager)
      const userPromptData = testData.userPromptSubmit({ prompt: 'tdd-guard on' })
      
      await processHookData(JSON.stringify(userPromptData), { 
        userPromptHandler 
      })

      expect(await guardManager.isEnabled()).toBe(true)
    })

    it('should disable TDD Guard when user sends "tdd-guard off"', async () => {
      const storage = new MemoryStorage()
      const guardManager = new GuardManager(storage)
      await guardManager.enable() // Ensure it starts enabled
      const userPromptHandler = new UserPromptHandler(guardManager)
      const userPromptData = testData.userPromptSubmit({ prompt: 'tdd-guard off' })
      
      await processHookData(JSON.stringify(userPromptData), { 
        userPromptHandler 
      })

      expect(await guardManager.isEnabled()).toBe(false)
    })

    it('should not proceed with validation when TDD Guard is disabled', async () => {
      const storage = new MemoryStorage()
      const guardManager = new GuardManager(storage)
      await guardManager.disable() // Ensure guard is disabled
      const userPromptHandler = new UserPromptHandler(guardManager)
      const mockValidator = vi.fn()
      
      // Try to process an edit operation
      const editData = testData.editOperation()
      
      const result = await processHookData(JSON.stringify(editData), { 
        storage,
        userPromptHandler,
        validator: mockValidator
      })

      expect(mockValidator).not.toHaveBeenCalled()
      expect(result).toEqual(defaultResult)
    })

    it('should proceed with validation when TDD Guard is enabled', async () => {
      const storage = new MemoryStorage()
      const guardManager = new GuardManager(storage)
      await guardManager.enable() // Ensure guard is enabled
      const userPromptHandler = new UserPromptHandler(guardManager)
      const mockValidator = vi.fn().mockResolvedValue(BLOCK_RESULT)
      
      // Try to process an edit operation
      const editData = testData.editOperation()
      
      const result = await processHookData(JSON.stringify(editData), { 
        storage,
        userPromptHandler,
        validator: mockValidator
      })

      expect(mockValidator).toHaveBeenCalled()
      expect(result).toEqual(BLOCK_RESULT)
    })
  })
})

// Test setup helper
function createTestProcessor() {
  const storage = new MemoryStorage()
  const mockValidator = vi.fn().mockResolvedValue(BLOCK_RESULT)
  
  // Create a GuardManager and UserPromptHandler that defaults to enabled for tests
  const guardManager = new GuardManager(storage)
  const userPromptHandler = new UserPromptHandler(guardManager)
  
  // Helper to process hook data
  const process = async (hookData: unknown): Promise<ValidationResult> => {
    // Ensure TDD Guard is enabled for tests unless explicitly disabled
    await guardManager.enable()
    
    return processHookData(JSON.stringify(hookData), {
      storage, 
      validator: mockValidator,
      userPromptHandler
    })
  }
  
  // Pre-populate storage helper
  const populateStorage = async (data: { 
    modifications?: string; 
    test?: string; 
    todo?: string;
    lint?: string;
    config?: string;
  }): Promise<void> => {
    if (data.modifications) await storage.saveModifications(data.modifications)
    if (data.test) await storage.saveTest(data.test)
    if (data.todo) await storage.saveTodo(data.todo)
    if (data.lint) await storage.saveLint(data.lint)
    if (data.config) await storage.saveConfig(data.config)
  }
  
  return {
    storage,
    process,
    populateStorage,
    
    // Storage accessors
    getModifications: (): Promise<string | null> => storage.getModifications(),
    getTest: (): Promise<string | null> => storage.getTest(),
    getTodo: (): Promise<string | null> => storage.getTodo(),
    getLint: (): Promise<string | null> => storage.getLint(),
    getConfig: (): Promise<string | null> => storage.getConfig(),
    
    // Validator checks
    validatorHasBeenCalled: (): boolean => mockValidator.mock.calls.length > 0,
    getValidatorCallArgs: (): Context | null => mockValidator.mock.calls[0]?.[0] ?? null,
  }
}
