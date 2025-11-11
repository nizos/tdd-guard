import { Storage } from '../storage/Storage'
import { LintDataSchema } from '../contracts/schemas/lintSchemas'
import { Context } from '../contracts/types/Context'
import { processLintData } from '../processors/lintProcessor'
import { detectTestFile } from '../validation/context/testFileDetector'
import { Config } from '../config/Config'
import {
  detectFileType,
  getLanguageCategory,
  FileType,
} from '../hooks/fileTypeDetection'

export async function buildContext(
  storage: Storage,
  config?: Config
): Promise<Context> {
  const [modifications, rawTest, todo, lint, instructions] = await Promise.all([
    storage.getModifications(),
    storage.getTest(),
    storage.getTodo(),
    storage.getLint(),
    storage.getInstructions(),
  ])

  // Java support: Detect if corresponding test file exists for implementation
  // Enables allowing POJO stubs when test file exists (compilation phase of TDD)
  const testFileExists = await detectTestFileForOperation(modifications, config)

  // Java support: Detect language category to apply appropriate TDD rules
  // Compiled (Java/Go/Rust): Allow POJO patterns | Interpreted (Python/JS): Strict incremental
  const fileType = detectLanguageFromModifications(modifications)
  const language = fileType
  const languageCategory = fileType ? getLanguageCategory(fileType) : undefined

  let processedLintData
  try {
    if (lint) {
      const rawLintData = LintDataSchema.parse(JSON.parse(lint))
      processedLintData = processLintData(rawLintData)
    } else {
      processedLintData = processLintData()
    }
  } catch {
    processedLintData = processLintData()
  }

  return {
    modifications: formatModifications(modifications ?? ''),
    test: rawTest ?? '',
    todo: todo ?? '',
    lint: processedLintData,
    instructions: instructions ?? undefined,
    testFileExists,
    language,
    languageCategory,
  }
}

function detectLanguageFromModifications(
  modifications: string | null
): FileType | undefined {
  if (!modifications) return undefined

  try {
    const operation = JSON.parse(modifications)
    return detectFileType(operation)
  } catch {
    return undefined
  }
}

/**
 * Java support: Checks if a corresponding test file exists for the implementation being modified.
 * Critical for compiled languages where test.json doesn't exist during compilation failures.
 * Returns true if CustomerTest.java exists for Customer.java (even if tests won't compile yet).
 */
async function detectTestFileForOperation(
  modifications: string | null,
  config?: Config
): Promise<boolean> {
  if (!modifications) return false

  try {
    const operation = JSON.parse(modifications)
    const filePath = operation?.tool_input?.file_path

    if (!filePath) return false

    // Only check for implementation files (not test files)
    if (isTestFile(filePath)) return false

    const projectRoot =
      config?.dataDir.replace('/.claude/tdd-guard/data', '') ?? process.cwd()
    const testFile = await detectTestFile(filePath, projectRoot)

    return testFile !== null
  } catch {
    return false
  }
}

function isTestFile(filePath: string): boolean {
  const lowerPath = filePath.toLowerCase()
  return (
    lowerPath.includes('/test/') ||
    lowerPath.includes('\\test\\') ||
    lowerPath.includes('.test.') ||
    lowerPath.includes('.spec.') ||
    lowerPath.includes('_test.') ||
    lowerPath.endsWith('test.js') ||
    lowerPath.endsWith('test.ts') ||
    lowerPath.endsWith('test.java')
  )
}

function formatModifications(modifications: string): string {
  if (!modifications) {
    return ''
  }

  try {
    const parsed = JSON.parse(modifications)
    return JSON.stringify(parsed, null, 2)
  } catch {
    // If it's not valid JSON, leave it as is
    return modifications
  }
}
