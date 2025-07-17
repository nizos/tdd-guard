import { Storage } from '../storage/Storage'
import { Context } from '../contracts/types/Context'
import { LintDataSchema } from '../contracts/schemas/lintSchemas'
import { processLintData } from '../processors/lintProcessor'

export async function buildContext(
  storage: Storage,
  hookData?: unknown
): Promise<Context> {
  const [modifications, rawTest, todo, lint] = await Promise.all([
    storage.getModifications(),
    storage.getTest(),
    storage.getTodo(),
    storage.getLint(),
  ])

  // Filter test results by framework if hookData is available
  const test =
    rawTest && hookData ? filterTestByFramework(rawTest, hookData) : rawTest

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
    test: test ?? '',
    todo: todo ?? '',
    lint: processedLintData,
  }
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

function filterTestByFramework(testData: string, hookData: unknown): string {
  try {
    const hookDataTyped = hookData as { tool_input?: { file_path?: string } }
    const operationFile = hookDataTyped.tool_input?.file_path ?? ''
    const isPythonOperation = operationFile.endsWith('.py')

    // Parse test data to filter modules
    const firstParse: unknown = JSON.parse(testData)
    const testResults =
      typeof firstParse === 'string' ? JSON.parse(firstParse) : firstParse

    if (
      !testResults ||
      typeof testResults !== 'object' ||
      !('testModules' in testResults)
    ) {
      return testData // Return original if structure is unexpected
    }

    // Filter modules based on operation type
    const filteredModules = (
      testResults as { testModules: { moduleId?: string }[] }
    ).testModules.filter((module: { moduleId?: string }) => {
      const moduleFile = module.moduleId ?? ''
      const isTypeScriptModule =
        moduleFile.includes('.test.ts') || moduleFile.includes('.test.js')
      const isPythonModule = moduleFile.endsWith('.py') && !isTypeScriptModule

      // Return only modules matching the operation type
      return isPythonOperation ? isPythonModule : isTypeScriptModule
    })

    // Return filtered results in same format
    const typedTestResults = testResults as {
      testModules: { moduleId?: string }[]
    }
    const filteredResults = {
      ...typedTestResults,
      testModules: filteredModules,
    }
    return JSON.stringify(JSON.stringify(filteredResults, null, 2))
  } catch {
    // If filtering fails, return original
    return testData
  }
}
