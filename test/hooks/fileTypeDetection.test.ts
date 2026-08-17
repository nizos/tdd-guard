import { describe, it, expect } from 'vitest'
import { isTestFile, detectLanguage } from '../../src/hooks/fileTypeDetection'

describe('isTestFile', () => {
  const cases = [
    { path: 'src/calculator.test.ts', expected: true },
    { path: 'src/calculator.spec.ts', expected: true },
    { path: 'src/calculator.test.js', expected: true },
    { path: 'src/calculator.spec.js', expected: true },
    { path: 'test/calculator.ts', expected: true },
    { path: 'tests/calculator.ts', expected: true },
    { path: '__tests__/calculator.ts', expected: true },
    { path: 'src/calculator_test.go', expected: true },
    { path: 'src/CalculatorTest.php', expected: true },
    { path: 'test_calculator.py', expected: true },
    { path: 'tests/test_calculator.py', expected: true },
    { path: 'spec/calculator_spec.rb', expected: true },
    { path: 'src/calculator.rs', expected: false },
    { path: 'src/calculator.ts', expected: false },
    { path: 'src/calculator.py', expected: false },
    { path: 'src/test_utils.ts', expected: false },
  ]

  for (const { path, expected } of cases) {
    it(`should return ${expected} for "${path}"`, () => {
      expect(isTestFile(path)).toBe(expected)
    })
  }
})

describe('detectLanguage', () => {
  const cases = [
    { path: 'src/calc.ts', expected: 'typescript' },
    { path: 'src/calc.tsx', expected: 'tsx' },
    { path: 'src/calc.js', expected: 'javascript' },
    { path: 'src/calc.jsx', expected: 'javascript' },
    { path: 'src/calc.py', expected: 'python' },
    { path: 'src/calc.go', expected: 'go' },
    { path: 'src/calc.rb', expected: 'ruby' },
    { path: 'src/calc.php', expected: 'php' },
    { path: 'src/calc.rs', expected: 'rust' },
    { path: 'src/calc.swift', expected: null },
    { path: 'src/calc.java', expected: null },
    { path: 'src/calc', expected: null },
  ]

  for (const { path, expected } of cases) {
    it(`should return ${JSON.stringify(expected)} for "${path}"`, () => {
      expect(detectLanguage(path)).toBe(expected)
    })
  }
})
