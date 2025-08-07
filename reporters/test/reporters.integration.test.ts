import { describe, it, expect, beforeAll } from 'vitest'
import { mkdtempSync, writeFileSync, rmSync, copyFileSync } from 'node:fs'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import { FileStorage, Config as TDDConfig } from 'tdd-guard'
import type { ReporterConfig, TestResultData, TestScenarios } from './types'
import {
  createJestReporter,
  createVitestReporter,
  createPhpunitReporter,
  createPytestReporter,
} from './factories'

// Test data structure for each reporter
interface ReporterTestData {
  name: string
  passingResults: unknown
  failingResults: unknown
  importErrorResults: unknown
}

type ReporterName = 'jest' | 'vitest' | 'phpunit' | 'pytest'

describe('Reporters', () => {
  const reporterData: ReporterTestData[] = []

  // Run all reporters and collect their output before tests
  beforeAll(async () => {
    const reporters = [
      createJestReporter(),
      createVitestReporter(),
      createPhpunitReporter(),
      createPytestReporter(),
    ]

    // Run all reporters in parallel
    const results = await Promise.all(reporters.map(runAllScenarios))
    reporterData.push(...results)
  }, 30000)

  describe('Module Path Reporting', () => {
    describe('when assertions are passing', () => {
      const reporters: Array<{ name: ReporterName; expected: string }> = [
        { name: 'jest', expected: 'single-passing.test.js' },
        { name: 'vitest', expected: 'single-passing.test.js' },
        { name: 'phpunit', expected: 'SinglePassingTest.php' },
        { name: 'pytest', expected: 'test_single_passing.py' },
      ]

      it.each(reporters)('$name reports module path', ({ name, expected }) => {
        const moduleIds = extractValues('passingResults', extract.firstModuleId)
        expect(moduleIds[name]).toContain(expected)
      })
    })

    describe('when assertions are failing', () => {
      const reporters: Array<{ name: ReporterName; expected: string }> = [
        { name: 'jest', expected: 'single-failing.test.js' },
        { name: 'vitest', expected: 'single-failing.test.js' },
        { name: 'phpunit', expected: 'SingleFailingTest.php' },
        { name: 'pytest', expected: 'test_single_failing.py' },
      ]

      it.each(reporters)('$name reports module path', ({ name, expected }) => {
        const moduleIds = extractValues('failingResults', extract.firstModuleId)
        expect(moduleIds[name]).toContain(expected)
      })
    })

    describe('when import errors occur', () => {
      const reporters: Array<{ name: ReporterName; expected: string }> = [
        { name: 'jest', expected: 'single-import-error.test.js' },
        { name: 'vitest', expected: 'single-import-error.test.js' },
        { name: 'phpunit', expected: 'SingleImportErrorTest.php' },
        { name: 'pytest', expected: 'test_single_import_error.py' },
      ]

      it.each(reporters)('$name reports module path', ({ name, expected }) => {
        const results = extractValues(
          'importErrorResults',
          extract.firstModuleId
        )
        expect(results[name]).toContain(expected)
      })
    })
  })

  describe('Test Name Reporting', () => {
    describe('when assertions are passing', () => {
      const reporters: Array<{ name: ReporterName; expected: string }> = [
        { name: 'jest', expected: 'should add numbers correctly' },
        { name: 'vitest', expected: 'should add numbers correctly' },
        { name: 'phpunit', expected: 'testShouldAddNumbersCorrectly' },
        { name: 'pytest', expected: 'test_should_add_numbers_correctly' },
      ]

      it.each(reporters)('$name reports test name', ({ name, expected }) => {
        const testNames = extractValues('passingResults', extract.firstTestName)
        expect(testNames[name]).toBe(expected)
      })
    })

    describe('when assertions are failing', () => {
      const reporters: Array<{ name: ReporterName; expected: string }> = [
        { name: 'jest', expected: 'should add numbers correctly' },
        { name: 'vitest', expected: 'should add numbers correctly' },
        { name: 'phpunit', expected: 'testShouldAddNumbersCorrectly' },
        { name: 'pytest', expected: 'test_should_add_numbers_correctly' },
      ]

      it.each(reporters)('$name reports test name', ({ name, expected }) => {
        const testNames = extractValues('failingResults', extract.firstTestName)
        expect(testNames[name]).toBe(expected)
      })
    })

    describe('when import errors occur', () => {
      const reporters: Array<{
        name: ReporterName
        expected: string | undefined
      }> = [
        { name: 'jest', expected: 'Module failed to load (Error)' },
        { name: 'vitest', expected: undefined },
        { name: 'phpunit', expected: 'testShouldAddNumbersCorrectly' },
        {
          name: 'pytest',
          expected: 'collection_error_test_single_import_error.py',
        },
      ]

      it.each(reporters)(
        '$name handles test names for import errors',
        ({ name, expected }) => {
          const results = extractValues(
            'importErrorResults',
            extract.firstTestName
          )
          expect(results[name]).toBe(expected)
        }
      )
    })
  })

  describe('Full Test Name Reporting', () => {
    describe('when assertions are passing', () => {
      const reporters: Array<{ name: ReporterName; expected: string }> = [
        { name: 'jest', expected: 'Calculator should add numbers correctly' },
        {
          name: 'vitest',
          expected: 'Calculator > should add numbers correctly',
        },
        {
          name: 'phpunit',
          expected: 'SinglePassingTest::testShouldAddNumbersCorrectly',
        },
        {
          name: 'pytest',
          expected:
            'test_single_passing.py::TestCalculator::test_should_add_numbers_correctly',
        },
      ]

      it.each(reporters)(
        '$name reports full test name',
        ({ name, expected }) => {
          const fullNames = extractValues(
            'passingResults',
            extract.firstTestFullName
          )
          expect(fullNames[name]).toBe(expected)
        }
      )
    })

    describe('when assertions are failing', () => {
      const reporters: Array<{ name: ReporterName; expected: string }> = [
        { name: 'jest', expected: 'Calculator should add numbers correctly' },
        {
          name: 'vitest',
          expected: 'Calculator > should add numbers correctly',
        },
        {
          name: 'phpunit',
          expected: 'SingleFailingTest::testShouldAddNumbersCorrectly',
        },
        {
          name: 'pytest',
          expected:
            'test_single_failing.py::TestCalculator::test_should_add_numbers_correctly',
        },
      ]

      it.each(reporters)(
        '$name reports full test name',
        ({ name, expected }) => {
          const fullNames = extractValues(
            'failingResults',
            extract.firstTestFullName
          )
          expect(fullNames[name]).toBe(expected)
        }
      )
    })

    describe('when import errors occur', () => {
      const reporters: Array<{
        name: ReporterName
        expected: string | undefined
      }> = [
        { name: 'jest', expected: 'Module failed to load (Error)' },
        { name: 'vitest', expected: undefined },
        {
          name: 'phpunit',
          expected: 'SingleImportErrorTest::testShouldAddNumbersCorrectly',
        },
        { name: 'pytest', expected: 'test_single_import_error.py' },
      ]

      it.each(reporters)(
        '$name handles full test names for import errors',
        ({ name, expected }) => {
          const fullNames = extractValues(
            'importErrorResults',
            extract.firstTestFullName
          )
          expect(fullNames[name]).toBe(expected)
        }
      )
    })
  })

  describe('Test State Reporting', () => {
    describe('when assertions are passing', () => {
      const reporters: ReporterName[] = ['jest', 'vitest', 'phpunit', 'pytest']

      it.each(reporters)('%s reports passing state', (reporter) => {
        const testStates = extractValues(
          'passingResults',
          extract.firstTestState
        )
        expect(testStates[reporter]).toBe('passed')
      })
    })

    describe('when assertions are failing', () => {
      const reporters: ReporterName[] = ['jest', 'vitest', 'phpunit', 'pytest']

      it.each(reporters)('%s reports failing state', (reporter) => {
        const testStates = extractValues(
          'failingResults',
          extract.firstTestState
        )
        expect(testStates[reporter]).toBe('failed')
      })
    })

    describe('when import errors occur', () => {
      const reporters: Array<{
        name: ReporterName
        expected: string | undefined
      }> = [
        { name: 'jest', expected: 'failed' },
        { name: 'vitest', expected: undefined },
        { name: 'phpunit', expected: 'failed' },
        { name: 'pytest', expected: 'failed' },
      ]

      it.each(reporters)(
        '$name handles test state for import errors',
        ({ name, expected }) => {
          const testStates = extractValues(
            'importErrorResults',
            extract.firstTestState
          )
          expect(testStates[name]).toBe(expected)
        }
      )
    })
  })

  describe('Error Message Reporting', () => {
    describe('when assertions are failing', () => {
      const reporters: Array<{
        name: ReporterName
        expected: string | string[]
      }> = [
        { name: 'jest', expected: ['Expected: 6', 'Received: 5'] },
        {
          name: 'vitest',
          expected: 'expected 5 to be 6 // Object.is equality',
        },
        {
          name: 'phpunit',
          expected: 'Failed asserting that 5 matches expected 6.',
        },
        { name: 'pytest', expected: ['assert 2 + 3 == 6', 'AssertionError'] },
      ]

      it.each(reporters)(
        '$name reports error messages',
        ({ name, expected }) => {
          const errorMessages = extractValues(
            'failingResults',
            extract.firstErrorMessage
          )

          if (Array.isArray(expected)) {
            expected.forEach((exp) =>
              expect(errorMessages[name]).toContain(exp)
            )
          } else {
            expect(errorMessages[name]).toBe(expected)
          }
        }
      )
    })

    describe('when providing expected values', () => {
      const reporters: Array<{
        name: ReporterName
        expected: string | undefined
      }> = [
        { name: 'jest', expected: '6' },
        { name: 'vitest', expected: '6' },
        { name: 'phpunit', expected: undefined },
        { name: 'pytest', expected: undefined },
      ]

      it.each(reporters)(
        '$name provides expected value when available',
        ({ name, expected }) => {
          const errors = extractValues('failingResults', extract.firstError)
          expect(errors[name]?.expected).toBe(expected)
        }
      )
    })

    describe('when providing actual values', () => {
      const reporters: Array<{
        name: ReporterName
        expected: string | undefined
      }> = [
        { name: 'jest', expected: '5' },
        { name: 'vitest', expected: '5' },
        { name: 'phpunit', expected: undefined },
        { name: 'pytest', expected: undefined },
      ]

      it.each(reporters)(
        '$name provides actual value when available',
        ({ name, expected }) => {
          const errors = extractValues('failingResults', extract.firstError)
          expect(errors[name]?.actual).toBe(expected)
        }
      )
    })

    describe('when import errors occur', () => {
      const reporters: Array<{
        name: ReporterName
        expected: string[] | undefined
      }> = [
        {
          name: 'jest',
          expected: [
            "Cannot find module './non-existent-module' from 'single-import-error.test.js'",
          ],
        },
        { name: 'vitest', expected: undefined },
        { name: 'phpunit', expected: ['Class', 'not found'] },
        {
          name: 'pytest',
          expected: ['ModuleNotFoundError', 'non_existent_module'],
        },
      ]

      it.each(reporters)(
        '$name reports error messages for import errors',
        ({ name, expected }) => {
          const errorMessages = extractValues(
            'importErrorResults',
            extract.firstErrorMessage
          )

          if (expected === undefined) {
            expect(errorMessages[name]).toBeUndefined()
          } else {
            expected.forEach((exp) =>
              expect(errorMessages[name]).toContain(exp)
            )
          }
        }
      )
    })
  })

  describe('Overall Test Run Status', () => {
    describe('when all tests pass', () => {
      const reporters: Array<{
        name: ReporterName
        expected: string | undefined
      }> = [
        { name: 'jest', expected: 'passed' },
        { name: 'vitest', expected: 'passed' },
        { name: 'phpunit', expected: 'passed' },
        { name: 'pytest', expected: undefined }, // TODO: Fix
      ]

      it.each(reporters)(
        '$name reports overall status as passed',
        ({ name, expected }) => {
          const reasons = extractValues('passingResults', extract.reason)
          expect(reasons[name]).toBe(expected)
        }
      )
    })

    describe('when any test fails', () => {
      const reporters: Array<{
        name: ReporterName
        expected: string | undefined
      }> = [
        { name: 'jest', expected: 'failed' },
        { name: 'vitest', expected: 'failed' },
        { name: 'phpunit', expected: 'failed' },
        { name: 'pytest', expected: undefined }, // TODO: Fix
      ]

      it.each(reporters)(
        '$name reports overall status as failed',
        ({ name, expected }) => {
          const reasons = extractValues('failingResults', extract.reason)
          expect(reasons[name]).toBe(expected)
        }
      )
    })

    describe('when any import fails', () => {
      const reporters: Array<{
        name: ReporterName
        expected: string | undefined
      }> = [
        { name: 'jest', expected: 'failed' },
        { name: 'vitest', expected: 'failed' },
        { name: 'phpunit', expected: 'failed' },
        { name: 'pytest', expected: undefined }, // TODO: Fix
      ]

      it.each(reporters)(
        '$name reports overall status as failed',
        ({ name, expected }) => {
          const reasons = extractValues('importErrorResults', extract.reason)
          expect(reasons[name]).toBe(expected)
        }
      )
    })
  })

  // Helper to extract values from all reporters
  function extractValues<T>(
    scenario: 'passingResults' | 'failingResults' | 'importErrorResults',
    extractor: (data: unknown) => T
  ): Record<ReporterName, T | undefined> {
    const [jest, vitest, phpunit, pytest] = reporterData
    return {
      jest: safeExtract(jest[scenario], extractor),
      vitest: safeExtract(vitest[scenario], extractor),
      phpunit: safeExtract(phpunit[scenario], extractor),
      pytest: safeExtract(pytest[scenario], extractor),
    }
  }

  // Safely extract data with error handling
  function safeExtract<T>(
    data: unknown,
    extractor: (data: unknown) => T
  ): T | undefined {
    try {
      return extractor(data)
    } catch {
      return undefined
    }
  }

  // Common test data extractors
  const extract = {
    firstModuleId: (data: unknown) =>
      (data as TestResultData).testModules[0].moduleId,
    firstTestName: (data: unknown) =>
      (data as TestResultData).testModules[0].tests[0].name,
    firstTestFullName: (data: unknown) =>
      (data as TestResultData).testModules[0].tests[0].fullName,
    firstTestState: (data: unknown) =>
      (data as TestResultData).testModules[0].tests[0].state,
    firstError: (data: unknown) =>
      (data as TestResultData).testModules[0].tests[0].errors?.[0],
    firstErrorMessage: (data: unknown) =>
      (data as TestResultData).testModules[0].tests[0].errors?.[0].message,
    reason: (data: unknown) => (data as TestResultData).reason,
  }
})

// Helper to run all test scenarios for a reporter
async function runAllScenarios(
  reporter: ReporterConfig
): Promise<ReporterTestData> {
  const [passingResults, failingResults, importErrorResults] =
    await Promise.all([
      runReporter(reporter, 'singlePassing'),
      runReporter(reporter, 'singleFailing'),
      runReporter(reporter, 'singleImportError'),
    ])

  return {
    name: reporter.name,
    passingResults,
    failingResults,
    importErrorResults,
  }
}

// Helper function to run a reporter and get results
async function runReporter(
  reporter: ReporterConfig,
  scenario: keyof TestScenarios
) {
  const tempDir = mkdtempSync(
    join(tmpdir(), `${reporter.name.toLowerCase()}-test-`)
  )

  try {
    // Create storage for reading test results
    const tddConfig = new TDDConfig({ projectRoot: tempDir })
    const storage = new FileStorage(tddConfig)

    // Create test framework configuration in temp directory
    const configPath = join(tempDir, reporter.configFileName)
    const reporterPath = join(__dirname, reporter.reporterPath).replace(
      /\\/g,
      '/'
    )
    writeFileSync(configPath, reporter.createConfig(tempDir, reporterPath))

    // Copy test file
    const filename = reporter.testScenarios[scenario]
    const sourcePath = join(
      __dirname,
      'artifacts',
      reporter.artifactDir,
      filename
    )
    const destPath = join(tempDir, filename)
    copyFileSync(sourcePath, destPath)

    // Run the test framework
    reporter.runCommand(tempDir, configPath, filename)

    // Get saved test data
    const savedData = await storage.getTest()
    return savedData ? JSON.parse(savedData) : null
  } finally {
    rmSync(tempDir, { recursive: true, force: true })
  }
}
