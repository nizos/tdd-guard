import { buildContext } from '../cli/buildContext'
import { HookData, HookEvents } from './HookEvents'
import { parseHookJson } from './parseHookJson'
import { PostToolLintHandler } from './postToolLint'
import { detectFileType, isTestFile, detectLanguage, type Language } from './fileTypeDetection'
import { LinterProvider } from '../providers/LinterProvider'
import { UserPromptHandler } from './userPromptHandler'
import { SessionHandler } from './sessionHandler'
import { GuardManager } from '../guard/GuardManager'
import { Storage } from '../storage/Storage'
import { FileStorage } from '../storage/FileStorage'
import { ValidationResult } from '../contracts/types/ValidationResult'
import { Context } from '../contracts/types/Context'
import { countTestDefinitions } from './testCounter'
import {
  HookDataSchema, isTodoWriteOperation, ToolOperationSchema,
  isEditOperation, isMultiEditOperation, isWriteOperation,
  type ToolOperation
} from '../contracts/schemas/toolSchemas'
import { PytestResultSchema } from '../contracts/schemas/pytestSchemas'
import { isTestPassing, TestResultSchema } from '../contracts/schemas/reporterSchemas'
import { LintDataSchema } from '../contracts/schemas/lintSchemas'

export interface ProcessHookDataDeps {
  storage?: Storage
  validator?: (context: Context) => Promise<ValidationResult>
  userPromptHandler?: UserPromptHandler
}

export const defaultResult: ValidationResult = {
  decision: undefined,
  reason: '',
}

function extractFilePath(parsedData: unknown): string | null {
  if (!parsedData || typeof parsedData !== 'object') {
    return null
  }
  
  const data = parsedData as Record<string, unknown>
  const toolInput = data.tool_input
  
  if (!toolInput || typeof toolInput !== 'object' || !('file_path' in toolInput)) {
    return null
  }
  
  const filePath = (toolInput as Record<string, unknown>).file_path
  if (typeof filePath !== 'string') {
    return null
  }
  
  return filePath
}

export async function processHookData(
  inputData: string,
  deps: ProcessHookDataDeps = {}
): Promise<ValidationResult> {
  const parsedData = parseHookJson(inputData) as Record<string, unknown>

  // Initialize dependencies
  const storage = deps.storage ?? new FileStorage()
  const guardManager = new GuardManager(storage)
  const userPromptHandler = deps.userPromptHandler ?? new UserPromptHandler(guardManager)
  
  // Skip validation for ignored files based on patterns
  const filePath = extractFilePath(parsedData)
  if (filePath && await guardManager.shouldIgnoreFile(filePath)) {
    return defaultResult
  }
  const sessionHandler = new SessionHandler(storage)
  
  // Process SessionStart events
  if (parsedData.hook_event_name === 'SessionStart') {
    await sessionHandler.processSessionStart(inputData)
    return defaultResult
  }
  
  // Process user commands
  const stateResult = await userPromptHandler.processUserCommand(inputData)
  if (stateResult) {
    return stateResult
  }

  // Check if guard is disabled and return early if so
  const disabledResult = await userPromptHandler.getDisabledResult()
  if (disabledResult) {
    return disabledResult
  }

  // Create lintHandler with linter from provider
  const linterProvider = new LinterProvider()
  const linter = linterProvider.getLinter()
  const lintHandler = new PostToolLintHandler(storage, linter)


  const hookResult = HookDataSchema.safeParse(parsedData)
  if (!hookResult.success) {
    return defaultResult
  }

  await processHookEvent(parsedData, storage)

  // Check if this is a PostToolUse event
  if (hookResult.data.hook_event_name === 'PostToolUse') {
    return await lintHandler.handle(inputData)
  }

  if (shouldSkipValidation(hookResult.data)) {
    return defaultResult
  }

  // For PreToolUse, check if we should notify about lint issues
  if (hookResult.data.hook_event_name === 'PreToolUse') {
    const lintNotification = await checkLintNotification(storage, hookResult.data)
    if (lintNotification.decision === 'block') {
      return lintNotification
    }
  }

  if (isAllowedTestAddition(hookResult.data)) {
    return defaultResult
  }

  return await performValidation(deps)
}

async function processHookEvent(parsedData: unknown, storage?: Storage): Promise<void> {
  if (storage) {
    const hookEvents = new HookEvents(storage)
    await hookEvents.processEvent(parsedData)
  }
}

function shouldSkipValidation(hookData: HookData): boolean {
  const operationResult = ToolOperationSchema.safeParse({
    ...hookData,
    tool_input: hookData.tool_input,
  })

  return !operationResult.success || isTodoWriteOperation(operationResult.data)
}

function isAllowedTestAddition(hookData: HookData): boolean {
  const operationResult = ToolOperationSchema.safeParse(hookData)
  if (!operationResult.success) return false

  const operation = operationResult.data
  if (isTodoWriteOperation(operation)) return false

  const filePath = getFilePath(operation)
  if (!filePath || !isTestFile(filePath)) return false

  const language = detectLanguage(filePath)
  if (!language) return false

  const addedTestCount = countAddedTests(operation, language)
  return addedTestCount === 1
}

function getFilePath(operation: ToolOperation): string | null {
  if ('file_path' in operation.tool_input) {
    return operation.tool_input.file_path
  }
  return null
}

function countAddedTests(operation: ToolOperation, language: Language): number {
  if (isEditOperation(operation)) {
    const newCount = countTestDefinitions(operation.tool_input.new_string, language)
    const oldCount = operation.tool_input.old_string
      ? countTestDefinitions(operation.tool_input.old_string, language)
      : 0
    return newCount - oldCount
  }
  if (isWriteOperation(operation)) {
    return countTestDefinitions(operation.tool_input.content, language)
  }
  if (isMultiEditOperation(operation)) {
    return operation.tool_input.edits.reduce((total, edit) => {
      const newCount = countTestDefinitions(edit.new_string, language)
      const oldCount = edit.old_string
        ? countTestDefinitions(edit.old_string, language)
        : 0
      return total + (newCount - oldCount)
    }, 0)
  }
  return 0
}

async function performValidation(deps: ProcessHookDataDeps): Promise<ValidationResult> {
  if (deps.validator && deps.storage) {
    const context = await buildContext(deps.storage)
    return await deps.validator(context)
  }
  
  return defaultResult
}

async function checkLintNotification(storage: Storage, hookData: HookData): Promise<ValidationResult> {
  // Get test results to check if tests are passing
  let testsPassing = false
  try {
    const testStr = await storage.getTest()
    if (testStr) {
      const fileType = detectFileType(hookData)
      const testResult = fileType === 'python' 
        ? PytestResultSchema.safeParse(JSON.parse(testStr))
        : TestResultSchema.safeParse(JSON.parse(testStr))
      if (testResult.success) {
        testsPassing = isTestPassing(testResult.data)
      }
    }
  } catch {
    testsPassing = false
  }

  // Only proceed if tests are passing
  if (!testsPassing) {
    return defaultResult
  }

  // Get lint data
  let lintData
  try {
    const lintStr = await storage.getLint()
    if (lintStr) {
      lintData = LintDataSchema.parse(JSON.parse(lintStr))
    }
  } catch {
    return defaultResult
  }

  // Only proceed if lint data exists
  if (!lintData) {
    return defaultResult
  }

  const hasIssues = lintData.errorCount > 0 || lintData.warningCount > 0

  // Block if:
  // 1. Tests are passing (already checked)
  // 2. There are lint issues
  // 3. hasNotifiedAboutLintIssues is false (not yet notified)
  if (hasIssues && !lintData.hasNotifiedAboutLintIssues) {
    // Update the notification flag and save
    const updatedLintData = {
      ...lintData,
      hasNotifiedAboutLintIssues: true
    }
    await storage.saveLint(JSON.stringify(updatedLintData))

    return {
      decision: 'block',
      reason: 'Code quality issues detected. You need to fix those first before making any other changes. Remember to exercise system thinking and design awareness to ensure continuous architectural improvements. Consider: design patterns, SOLID principles, DRY, types and interfaces, and architectural improvements. Apply equally to implementation and test code. Use test data factories, helpers, and beforeEach to better organize tests.'
    }
  }

  return defaultResult
}
