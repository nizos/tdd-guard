import { ValidationResult } from '../contracts/types/ValidationResult'
import { Storage } from '../storage/Storage'
import { FileStorage } from '../storage/FileStorage'
import { Context } from '../contracts/types/Context'
import { buildContext } from '../cli/buildContext'
import { HookEvents } from './HookEvents'
import { HookDataSchema, HookData, isTodoWriteOperation, ToolOperationSchema } from '../contracts/schemas/toolSchemas'
import { PostToolLintHandler } from './postToolLint'
import { LintDataSchema } from '../contracts/schemas/lintSchemas'
import { TestResultSchema, isTestPassing } from '../contracts/schemas/vitestSchemas'
import { PytestResultSchema } from '../contracts/schemas/pytestSchemas'
import { detectFileType } from './fileTypeDetection'
import { LinterProvider } from '../providers/LinterProvider'

export interface ProcessHookDataDeps {
  storage?: Storage
  validator?: (context: Context) => Promise<ValidationResult>
}

export const defaultResult: ValidationResult = {
  decision: undefined,
  reason: '',
}

export async function processHookData(
  inputData: string,
  deps: ProcessHookDataDeps = {}
): Promise<ValidationResult> {
  const parsedData = JSON.parse(inputData)
  
  // Initialize storage if not provided
  const storage = deps.storage ?? new FileStorage()
  
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

  return await performValidation(deps, hookResult.data)
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

  if (!operationResult.success || isTodoWriteOperation(operationResult.data)) {
    return true
  }

  // Skip validation for non-code files
  const toolInput = hookData.tool_input
  if (toolInput && typeof toolInput === 'object' && 'file_path' in toolInput) {
    const filePath = toolInput.file_path
    if (typeof filePath === 'string') {
      const nonCodeExtensions = ['.md', '.txt', '.log', '.json', '.yml', '.yaml', '.xml', '.html', '.css', '.rst']
      const fileExt = filePath.toLowerCase().slice(filePath.lastIndexOf('.'))
      if (nonCodeExtensions.includes(fileExt)) {
        return true
      }
    }
  }

  return false
}

async function performValidation(deps: ProcessHookDataDeps, hookData?: unknown): Promise<ValidationResult> {
  if (deps.validator && deps.storage) {
    const context = await buildContext(deps.storage, hookData)
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
