import {
  TestResultSchema as VitestTestResultSchema,
  TestResult,
  isFailingTest,
  isPassingTest,
} from '../../contracts/schemas/vitestSchemas'
import { PytestResultSchema } from '../../contracts/schemas/pytestSchemas'

export class TestResultsProcessor {
  process(jsonData: string, framework: 'vitest' | 'pytest' = 'vitest'): string {
    const parseResult = this.parseAndValidate(jsonData, framework)
    if (!parseResult.success) {
      return parseResult.error
    }

    const data = parseResult.data
    if (data.testModules.length === 0) {
      return 'No test results found.'
    }

    const counts = this.countTestsAndModules(data)
    const reasonOutput = this.formatReason(data)
    const moduleOutput = this.formatModules(data)
    const errorOutput = this.formatUnhandledErrors(data)
    const summaryOutput = this.formatSummary(counts)

    return `${reasonOutput}${moduleOutput}${errorOutput}\n${summaryOutput}`
  }

  private countTestsAndModules(data: TestResult): {
    failedCount: number
    passedCount: number
    failedModuleCount: number
    passedModuleCount: number
  } {
    let failedCount = 0
    let passedCount = 0
    let failedModuleCount = 0
    let passedModuleCount = 0

    for (const module of data.testModules) {
      const moduleCounts = this.countModuleTests(module.tests)

      failedCount += moduleCounts.failed
      passedCount += moduleCounts.passed

      if (moduleCounts.failed > 0) {
        failedModuleCount++
      } else if (module.tests.length === 0 && data.reason === 'failed') {
        // Module with no tests in a failed test run should be counted as failed
        failedModuleCount++
      } else {
        passedModuleCount++
      }
    }

    return { failedCount, passedCount, failedModuleCount, passedModuleCount }
  }

  private countModuleTests(tests: TestResult['testModules'][0]['tests']): {
    failed: number
    passed: number
  } {
    let failed = 0
    let passed = 0

    for (const test of tests) {
      if (isFailingTest(test)) {
        failed++
      } else if (isPassingTest(test)) {
        passed++
      }
    }

    return { failed, passed }
  }

  private formatModules(data: TestResult): string {
    let output = ''

    for (const module of data.testModules) {
      const testCount = module.tests.length
      const failedTests = module.tests.filter(isFailingTest)

      if (failedTests.length === 0) {
        // Check if this module should be marked as failed due to test run reason
        if (testCount === 0 && data.reason === 'failed') {
          output += ` ❯ ${module.moduleId} (${testCount} tests | 0 failed) 0ms\n`
        } else {
          output += ` ✓ ${module.moduleId} (${testCount} tests) 0ms\n`
        }
      } else {
        output += ` ❯ ${module.moduleId} (${testCount} tests | ${failedTests.length} failed) 0ms\n`
        output += this.formatFailingModuleTests(module.tests)
      }
    }

    return output
  }

  private formatFailingModuleTests(
    tests: TestResult['testModules'][0]['tests']
  ): string {
    let output = ''

    for (const test of tests) {
      output += this.formatSingleTest(test)
    }

    return output
  }

  private formatSingleTest(
    test: TestResult['testModules'][0]['tests'][0]
  ): string {
    if (isPassingTest(test)) {
      return `   ✓ ${test.fullName} 0ms\n`
    }

    if (isFailingTest(test)) {
      return this.formatFailingTest(test)
    }

    return ''
  }

  private formatFailingTest(
    test: TestResult['testModules'][0]['tests'][0]
  ): string {
    let output = `   × ${test.fullName} 0ms\n`

    if (test.errors && test.errors.length > 0) {
      output += `     → ${test.errors[0].message}\n`
    }

    return output
  }

  private formatReason(data: TestResult): string {
    if (!data.reason) {
      return ''
    }

    if (data.reason === 'failed') {
      const hasEmptyModules = data.testModules.some(
        (module) => module.tests.length === 0
      )
      if (hasEmptyModules) {
        return 'Test run failed - This is likely when an imported module can not be found and as such no tests were collected, resulting in inaccurate test run results.\n\n'
      }
    }

    return ''
  }

  private formatUnhandledErrors(data: TestResult): string {
    if (!data.unhandledErrors || data.unhandledErrors.length === 0) {
      return ''
    }

    let output = '\nUnhandled Errors:\n'
    for (const error of data.unhandledErrors) {
      if (error.name) {
        output += ` × ${error.name}: ${error.message}\n`
      } else {
        output += ` × ${error.message || error.toString()}\n`
      }

      if (error.stack) {
        output += `   Stack:\n`
        const stackLines = error.stack.split('\n')
        for (const line of stackLines) {
          if (line.trim()) {
            output += `     ${line}\n`
          }
        }
      }
    }
    return output
  }

  private formatSummary(counts: {
    failedCount: number
    passedCount: number
    failedModuleCount: number
    passedModuleCount: number
  }): string {
    const { failedCount, passedCount, failedModuleCount, passedModuleCount } =
      counts
    let output = ''

    if (failedCount > 0 || failedModuleCount > 0) {
      if (passedCount > 0) {
        output += ` Test Files  ${failedModuleCount} failed | ${passedModuleCount} passed (${failedModuleCount + passedModuleCount})\n`
        output += `      Tests  ${failedCount} failed | ${passedCount} passed (${failedCount + passedCount})\n`
      } else {
        output += ` Test Files  ${failedModuleCount} failed (${failedModuleCount})\n`
        output += `      Tests  ${failedCount} failed (${failedCount})\n`
      }
    } else {
      output += ` Test Files  ${passedModuleCount} passed (${passedModuleCount})\n`
      output += `      Tests  ${passedCount} passed (${passedCount})\n`
    }

    return output
  }

  private parseAndValidate(
    jsonData: string,
    framework: 'vitest' | 'pytest'
  ): { success: true; data: TestResult } | { success: false; error: string } {
    const parsed = this.parseJson(jsonData)
    if (!parsed.success) {
      return parsed
    }

    const schema =
      framework === 'pytest' ? PytestResultSchema : VitestTestResultSchema
    const result = schema.safeParse(parsed.data)
    if (!result.success) {
      return { success: false, error: this.getValidationError(parsed.data) }
    }

    return { success: true, data: result.data }
  }

  private parseJson(
    jsonData: string
  ): { success: true; data: unknown } | { success: false; error: string } {
    try {
      const firstParse = JSON.parse(jsonData)
      // Handle double-encoded JSON (common in both Vitest and pytest reporters)
      if (typeof firstParse === 'string') {
        const data = JSON.parse(firstParse)
        return { success: true, data }
      }
      return { success: true, data: firstParse }
    } catch {
      return { success: false, error: 'Invalid JSON format.' }
    }
  }

  private getValidationError(parsed: unknown): string {
    if (
      typeof parsed === 'object' &&
      parsed !== null &&
      !('testModules' in parsed)
    ) {
      return 'No test results found.'
    }
    return 'Invalid test result format.'
  }
}
