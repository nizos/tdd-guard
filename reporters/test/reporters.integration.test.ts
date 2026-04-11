import { describe, it, expect, beforeAll } from 'vitest'
import { mkdtempSync, rmSync } from 'node:fs'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import { FileStorage, Config as TDDConfig } from 'tdd-guard'
import type { ReporterConfig, TestResultData, TestScenarios } from './types'

type TestError = {
  message: string
  expected?: string
  actual?: string
  // Rust-specific fields
  code?: string
  location?: string
  help?: string
  note?: string
}
import {
  createJestReporter,
  createVitestReporter,
  createPhpunitReporter,
  createPytestReporter,
  createGoReporter,
  createRustReporter,
  createStorybookReporter,
  createRspecReporter,
  createMinitestReporter,
} from './factories'

// Test data structure for each reporter
interface ReporterTestData {
  name: string
  passingResults: unknown
  failingResults: unknown
  importErrorResults: unknown
}

type ReporterName =
  | 'jest'
  | 'vitest'
  | 'phpunit'
  | 'pytest'
  | 'go'
  | 'rust'
  | 'storybook'
  | 'rspec'
  | 'minitest'

describe('Reporters', () => {
  const reporterData: ReporterTestData[] = []

  // Run all reporters and collect their output before tests
  beforeAll(async () => {
    const reporters = [
      createJestReporter(),
      createVitestReporter(),
      createPhpunitReporter(),
      createPytestReporter(),
      createGoReporter(),
      createRustReporter(),
      createStorybookReporter(),
      createRspecReporter(),
      createMinitestReporter(),
    ]

    // Run all reporters in parallel, skipping any that fail (e.g., Rust not installed)
    const results = await Promise.allSettled(reporters.map(runAllScenarios))
    reporterData.push(
      ...results
        .filter(
          (r): r is PromiseFulfilledResult<ReporterTestData> =>
            r.status === 'fulfilled'
        )
        .map((r) => r.value)
    )
  }, 240000) // Increased timeout for all reporters (Storybook starts dev server + browser, others may be slower in containers)

  describe('Module Path Reporting', () => {
    describe('when assertions are passing', () => {
      const reporters: Array<{ name: ReporterName; expected: string }> = [
        { name: 'jest', expected: 'single-passing.test.js' },
        { name: 'vitest', expected: 'single-passing.test.js' },
        { name: 'phpunit', expected: 'SinglePassingTest.php' },
        { name: 'pytest', expected: 'test_single_passing.py' },
        { name: 'go', expected: 'singlePassing' },
        { name: 'rust', expected: 'single_passing' },
        { name: 'storybook', expected: 'single-passing.stories' },
        { name: 'rspec', expected: 'single_passing_spec.rb' },
        { name: 'minitest', expected: 'single_passing_test.rb' },
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
        { name: 'go', expected: 'singleFailing' },
        { name: 'rust', expected: 'single_failing' },
        { name: 'storybook', expected: 'single-failing.stories' },
        { name: 'rspec', expected: 'single_failing_spec.rb' },
        { name: 'minitest', expected: 'single_failing_test.rb' },
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
        { name: 'go', expected: 'missingImport' },
        { name: 'rust', expected: 'compilation' },
        { name: 'storybook', expected: 'single-import-error.stories' },
        { name: 'rspec', expected: 'single_import_error_spec.rb' },
        { name: 'minitest', expected: 'single_import_error_test.rb' },
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
        {
          name: 'go',
          expected: 'TestCalculator/TestShouldAddNumbersCorrectly',
        },
        {
          name: 'rust',
          expected: 'calculator_tests::should_add_numbers_correctly',
        },
        { name: 'storybook', expected: 'play-test' },
        { name: 'rspec', expected: 'should add numbers correctly' },
        { name: 'minitest', expected: 'test_should_add_numbers_correctly' },
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
        {
          name: 'go',
          expected: 'TestCalculator/TestShouldAddNumbersCorrectly',
        },
        {
          name: 'rust',
          expected: 'calculator_tests::should_add_numbers_correctly',
        },
        { name: 'storybook', expected: 'play-test' },
        { name: 'rspec', expected: 'should add numbers correctly' },
        { name: 'minitest', expected: 'test_should_add_numbers_correctly' },
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
        { name: 'vitest', expected: 'single-import-error.test.js' },
        { name: 'phpunit', expected: 'testShouldAddNumbersCorrectly' },
        {
          name: 'pytest',
          expected: 'collection_error_test_single_import_error.py',
        },
        { name: 'go', expected: 'CompilationError' },
        { name: 'rust', expected: 'build' },
        { name: 'storybook', expected: 'play-test' },
        {
          name: 'rspec',
          expected: 'LoadError: cannot load such file -- non_existent_module',
        },
        {
          name: 'minitest',
          expected: 'LoadError: cannot load such file -- non_existent_module',
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
        {
          name: 'go',
          expected:
            'singlePassingTestModule/TestCalculator/TestShouldAddNumbersCorrectly',
        },
        {
          name: 'rust',
          expected:
            'single_passing::single_passing::calculator_tests::should_add_numbers_correctly',
        },
        {
          name: 'rspec',
          expected:
            'single_passing_spec.rb::Calculator should add numbers correctly',
        },
        {
          name: 'minitest',
          expected:
            'single_passing_test.rb::CalculatorTest#test_should_add_numbers_correctly',
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
        {
          name: 'go',
          expected:
            'singleFailingTestModule/TestCalculator/TestShouldAddNumbersCorrectly',
        },
        {
          name: 'rust',
          expected:
            'single_failing::single_failing::calculator_tests::should_add_numbers_correctly',
        },
        {
          name: 'storybook',
          expected: 'Calculator Primary play-test',
        },
        {
          name: 'rspec',
          expected:
            'single_failing_spec.rb::Calculator should add numbers correctly',
        },
        {
          name: 'minitest',
          expected:
            'single_failing_test.rb::CalculatorTest#test_should_add_numbers_correctly',
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
        { name: 'vitest', expected: 'single-import-error.test.js' },
        {
          name: 'phpunit',
          expected: 'SingleImportErrorTest::testShouldAddNumbersCorrectly',
        },
        { name: 'pytest', expected: 'test_single_import_error.py' },
        { name: 'go', expected: 'missingImportModule/CompilationError' },
        { name: 'rust', expected: 'compilation::build' },
        { name: 'storybook', expected: 'Calculator Primary play-test' },
        {
          name: 'rspec',
          expected:
            'single_import_error_spec.rb::LoadError: cannot load such file -- non_existent_module',
        },
        {
          name: 'minitest',
          expected:
            'single_import_error_test.rb::LoadError: cannot load such file -- non_existent_module',
        },
      ]

      it.each(reporters)(
        '$name handles full test names for import errors',
        ({ name, expected }) => {
          const fullNames = extractValues(
            'importErrorResults',
            extract.firstTestFullName
          )
          expect(fullNames[name]).toContain(expected)
        }
      )
    })
  })

  describe('Test State Reporting', () => {
    describe('when assertions are passing', () => {
      const reporters: ReporterName[] = [
        'jest',
        'vitest',
        'phpunit',
        'pytest',
        'go',
        'rust',
        'rspec',
        'minitest',
      ]

      it.each(reporters)('%s reports passing state', (reporter) => {
        const testStates = extractValues(
          'passingResults',
          extract.firstTestState
        )
        expect(testStates[reporter]).toBe('passed')
      })
    })

    describe('when assertions are failing', () => {
      const reporters: ReporterName[] = [
        'jest',
        'vitest',
        'phpunit',
        'pytest',
        'go',
        'rust',
        'storybook',
        'rspec',
        'minitest',
      ]

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
        expected: string
      }> = [
        { name: 'jest', expected: 'failed' },
        { name: 'vitest', expected: 'failed' },
        { name: 'phpunit', expected: 'failed' },
        { name: 'pytest', expected: 'failed' },
        { name: 'go', expected: 'failed' },
        { name: 'rust', expected: 'failed' },
        { name: 'storybook', expected: 'failed' },
        { name: 'rspec', expected: 'failed' },
        { name: 'minitest', expected: 'failed' },
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
        {
          name: 'go',
          expected: 'single_failing_test.go:10: Expected 6 but got 5',
        },
        {
          name: 'rust',
          expected: [
            'calculator_tests::should_add_numbers_correctly',
            'panicked at',
            'src/lib.rs:12:9',
          ],
        },
        {
          name: 'storybook',
          expected: ['expected', '5', 'to be', '6'],
        },
        { name: 'rspec', expected: ['expected: 6', 'got: 5'] },
        { name: 'minitest', expected: ['Expected: 6', 'Actual: 5'] },
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
            expect(errorMessages[name]).toContain(expected)
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
        { name: 'go', expected: undefined },
        { name: 'rust', expected: '6' }, // Successfully extracts expected value
        { name: 'storybook', expected: undefined },
        { name: 'rspec', expected: undefined },
        { name: 'minitest', expected: undefined },
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
        { name: 'go', expected: undefined },
        { name: 'rust', expected: '5' }, // Successfully extracts actual value
        { name: 'storybook', expected: undefined },
        { name: 'rspec', expected: undefined },
        { name: 'minitest', expected: undefined },
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
        expected: string[]
      }> = [
        {
          name: 'jest',
          expected: [
            "Cannot find module './non-existent-module' from 'single-import-error.test.js'",
          ],
        },
        {
          name: 'vitest',
          expected: [
            "Cannot find module './non-existent-module'",
            'single-import-error.test.js',
          ],
        },
        { name: 'phpunit', expected: ['Class', 'not found'] },
        {
          name: 'pytest',
          expected: ['ModuleNotFoundError', 'non_existent_module'],
        },
        {
          name: 'go',
          expected: [
            'single_import_error_test.go',
            'no required module provides package',
            'github.com/non-existent/module',
          ],
        },
        {
          name: 'rust',
          expected: ['E0432', 'unresolved import', 'non_existent_module'],
        },
        {
          name: 'storybook',
          expected: [
            'Failed to fetch dynamically imported module',
            'single-import-error.stories.js',
          ],
        },
        {
          name: 'rspec',
          expected: [
            'LoadError',
            'cannot load such file -- non_existent_module',
            'single_import_error_spec.rb',
          ],
        },
        {
          name: 'minitest',
          expected: [
            'LoadError',
            'cannot load such file -- non_existent_module',
            'single_import_error_test.rb',
          ],
        },
      ]

      it.each(reporters)(
        '$name reports error messages for import errors',
        ({ name, expected }) => {
          const errorMessages = extractValues(
            'importErrorResults',
            extract.firstErrorMessage
          )

          expected.forEach((exp) => expect(errorMessages[name]).toContain(exp))
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
        { name: 'go', expected: 'passed' },
        { name: 'rust', expected: 'passed' },
        { name: 'storybook', expected: 'passed' },
        { name: 'rspec', expected: 'passed' },
        { name: 'minitest', expected: 'passed' },
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
        { name: 'go', expected: 'failed' },
        { name: 'rust', expected: 'failed' },
        { name: 'storybook', expected: 'failed' },
        { name: 'rspec', expected: 'failed' },
        { name: 'minitest', expected: 'failed' },
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
        { name: 'go', expected: 'failed' },
        { name: 'rust', expected: 'failed' },
        { name: 'storybook', expected: 'failed' },
        { name: 'rspec', expected: 'failed' },
        { name: 'minitest', expected: 'failed' },
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

  // Rust-specific enhancements tests
  describe('Rust-Specific Enhancements', () => {
    describe('compilation error details', () => {
      it('Rust reporter includes error codes', () => {
        const results = extractValues(
          'importErrorResults',
          extract.firstError
        ) as Record<ReporterName, TestError | undefined>
        const rustError = results.rust
        if (rustError?.code) {
          expect(rustError.code).toMatch(/E\d{4}/)
        }
      })

      it('Rust reporter includes file locations', () => {
        const results = extractValues(
          'importErrorResults',
          extract.firstError
        ) as Record<ReporterName, TestError | undefined>
        const rustError = results.rust
        if (rustError?.location) {
          expect(rustError.location).toContain('src/lib.rs')
        }
      })
    })
  })

  // Helper to extract values from all reporters
  function extractValues<T>(
    scenario: 'passingResults' | 'failingResults' | 'importErrorResults',
    extractor: (data: unknown) => T
  ): Record<ReporterName, T | undefined> {
    const jest = reporterData.find((r) => r.name === 'JestReporter')
    const vitest = reporterData.find((r) => r.name === 'VitestReporter')
    const phpunit = reporterData.find((r) => r.name === 'PHPUnitReporter')
    const pytest = reporterData.find((r) => r.name === 'PytestReporter')
    const go = reporterData.find((r) => r.name === 'GoReporter')
    const rust = reporterData.find((r) => r.name === 'RustReporter')
    const storybook = reporterData.find((r) => r.name === 'StorybookReporter')
    const rspec = reporterData.find((r) => r.name === 'RSpecReporter')
    const minitest = reporterData.find((r) => r.name === 'MinitestReporter')

    return {
      jest: safeExtract(jest?.[scenario], extractor),
      vitest: safeExtract(vitest?.[scenario], extractor),
      phpunit: safeExtract(phpunit?.[scenario], extractor),
      pytest: safeExtract(pytest?.[scenario], extractor),
      go: safeExtract(go?.[scenario], extractor),
      rust: safeExtract(rust?.[scenario], extractor),
      rspec: safeExtract(rspec?.[scenario], extractor),
      storybook: safeExtract(storybook?.[scenario], extractor),
      minitest: safeExtract(minitest?.[scenario], extractor),
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

    // Run the test for the given scenario
    await reporter.run(tempDir, scenario)

    // Get saved test data
    const savedData = await storage.getTest()
    return savedData ? JSON.parse(savedData) : null
  } finally {
    rmSync(tempDir, { recursive: true, force: true })
  }
}
