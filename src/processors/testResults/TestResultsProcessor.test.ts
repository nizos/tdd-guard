import { describe, it, expect, beforeEach } from 'vitest'
import { TestResultsProcessor } from './TestResultsProcessor'
import { testData } from '../../../test/utils'

describe('TestResultsProcessor', () => {
  it('should format empty test results', () => {
    const processor = new TestResultsProcessor()
    const result = processor.process('{}')

    expect(result).toBe('No test results found.')
  })

  it('should handle invalid JSON gracefully', () => {
    const processor = new TestResultsProcessor()
    const result = processor.process('invalid json')

    expect(result).toBe('Invalid JSON format.')
  })

  it('should handle invalid test result format', () => {
    const processor = new TestResultsProcessor()
    const result = processor.process('{"testModules": "not an array"}')

    expect(result).toBe('Invalid test result format.')
  })

  describe('scenarios', () => {
    it('2 passing tests in the same file', () => {
      const processor = new TestResultsProcessor()
      const testResults = testData.passingTestResults()

      const result = processor.process(JSON.stringify(testResults))

      const expected = ` ✓ /src/example.test.ts (2 tests) 0ms

 Test Files  1 passed (1)
      Tests  2 passed (2)
`

      expect(result).toBe(expected)
    })

    it('2 failing tests in the same file', () => {
      const processor = new TestResultsProcessor()
      const testResults = testData.multipleFailedTestResults()

      const result = processor.process(JSON.stringify(testResults))

      const expected = ` ❯ /src/calculator.test.ts (2 tests | 2 failed) 0ms
   × Calculator > should add two numbers 0ms
     → expected 5 to be 6
   × Calculator > should multiply two numbers 0ms
     → expected 12 to be 15

 Test Files  1 failed (1)
      Tests  2 failed (2)
`

      expect(result).toBe(expected)
    })

    it('1 passing and 1 failing test in the same file', () => {
      const processor = new TestResultsProcessor()
      const testResults = testData.mixedTestResults()

      const result = processor.process(JSON.stringify(testResults))

      const expected = ` ❯ /src/calculator.test.ts (2 tests | 1 failed) 0ms
   ✓ Calculator > should add two numbers 0ms
   × Calculator > should multiply two numbers 0ms
     → expected 12 to be 15

 Test Files  1 failed | 0 passed (1)
      Tests  1 failed | 1 passed (2)
`

      expect(result).toBe(expected)
    })

    it('2 files with a passing and a failing test pair in each', () => {
      const processor = new TestResultsProcessor()
      const testResults = testData.multipleModulesTestResults()

      const result = processor.process(JSON.stringify(testResults))

      const expected = ` ❯ /src/calculator.test.ts (2 tests | 1 failed) 0ms
   ✓ Calculator > should add two numbers 0ms
   × Calculator > should multiply two numbers 0ms
     → expected 12 to be 15
 ❯ /src/utils/formatter.test.ts (2 tests | 1 failed) 0ms
   ✓ Formatter > should format currency 0ms
   × Formatter > should format date 0ms
     → expected "2024-01-01" to be "01/01/2024"

 Test Files  2 failed | 0 passed (2)
      Tests  2 failed | 2 passed (4)
`

      expect(result).toBe(expected)
    })
  })

  it('should handle pytest format with passing tests', () => {
    const processor = new TestResultsProcessor()
    const pytestResults = {
      testModules: [
        {
          moduleId: 'test_example.py',
          tests: [
            {
              name: 'test_addition',
              fullName: 'test_example.py::test_addition',
              state: 'passed',
            },
          ],
        },
      ],
    }

    const result = processor.process(JSON.stringify(pytestResults))

    const expected = ` ✓ test_example.py (1 tests) 0ms

 Test Files  1 passed (1)
      Tests  1 passed (1)
`

    expect(result).toBe(expected)
  })

  describe('formats unhandled errors separately from test results', () => {
    let processor: TestResultsProcessor
    let result: string

    beforeEach(() => {
      processor = new TestResultsProcessor()
      const testResults = testData.createTestResults({
        testModules: [testData.createTestModule()],
        unhandledErrors: [testData.createUnhandledError()],
      })

      result = processor.process(JSON.stringify(testResults))
    })

    it('shows module as passed with 0 tests', () => {
      expect(result).toContain('✓ /src/example.test.ts (0 tests)')
    })

    it('displays unhandled errors section', () => {
      expect(result).toContain('Unhandled Errors:')
    })

    it('shows error name and message', () => {
      expect(result).toContain('× Error: Cannot find module "./helpers"')
    })

    it('includes stack trace', () => {
      expect(result).toContain('Stack:')
      expect(result).toContain('imported from')
    })

    it('summary shows passed test file', () => {
      expect(result).toContain('Test Files  1 passed (1)')
    })

    it('summary shows 0 tests', () => {
      expect(result).toContain('Tests  0 passed (0)')
    })
  })

  describe('handles test run end reason', () => {
    it('displays reason explanation when test run failed with import error', () => {
      const processor = new TestResultsProcessor()
      const testResults = testData.createTestResults({
        testModules: [testData.createTestModule()],
        unhandledErrors: [testData.createUnhandledError()],
        reason: 'failed',
      })

      const result = processor.process(JSON.stringify(testResults))

      expect(result).toContain(
        'Test run failed - This is likely when an imported module can not be found'
      )
    })

    it('does not display import error explanation when tests fail for other reasons', () => {
      const processor = new TestResultsProcessor()
      const testResults = testData.createTestResults({
        testModules: [
          testData.createTestModule({
            moduleId: '/src/calculator.test.ts',
            tests: [
              testData.createTest({
                name: 'should add two numbers',
                fullName: 'Calculator > should add two numbers',
                state: 'failed',
                errors: [
                  testData.createTestError({
                    message: 'expected 5 to be 6',
                  }),
                ],
              }),
            ],
          }),
        ],
        unhandledErrors: [],
        reason: 'failed',
      })

      const result = processor.process(JSON.stringify(testResults))

      expect(result).not.toContain(
        'This is likely when an imported module can not be found'
      )
      expect(result).toContain('expected 5 to be 6')
    })

    it('displays reason explanation when test run failed with no tests collected', () => {
      const processor = new TestResultsProcessor()
      const testResults = testData.createTestResults({
        testModules: [testData.createTestModule()],
        reason: 'failed',
      })

      const result = processor.process(JSON.stringify(testResults))

      expect(result).toContain(
        'Test run failed - This is likely when an imported module can not be found'
      )
    })

    it('shows module as failed when test run failed with no tests', () => {
      const processor = new TestResultsProcessor()
      const testResults = testData.createTestResults({
        testModules: [
          testData.createTestModule({
            moduleId: '/src/linters/eslint/helpers.test.ts',
          }),
        ],
        reason: 'failed',
      })

      const result = processor.process(JSON.stringify(testResults))

      expect(result).toContain(
        '❯ /src/linters/eslint/helpers.test.ts (0 tests | 0 failed) 0ms'
      )
    })

    it('counts module as failed in summary when test run failed with no tests', () => {
      const processor = new TestResultsProcessor()
      const testResults = testData.createTestResults({
        testModules: [testData.createTestModule()],
        reason: 'failed',
      })

      const result = processor.process(JSON.stringify(testResults))

      expect(result).toContain('Test Files  1 failed (1)')
    })
  })
})
