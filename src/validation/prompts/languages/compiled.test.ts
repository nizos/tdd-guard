import { describe, test, expect } from 'vitest'
import { COMPILED_LANGUAGE_RULES } from './compiled'

describe('COMPILED_LANGUAGE_RULES', () => {
  describe('Structural Constructs Coverage', () => {
    describe('HIGH PRIORITY constructs', () => {
      test('mentions enums as allowed structural construct', () => {
        expect(COMPILED_LANGUAGE_RULES.toLowerCase()).toContain('enum')
      })

      test('mentions records (Java 14+) as allowed structural construct', () => {
        expect(COMPILED_LANGUAGE_RULES.toLowerCase()).toContain('record')
      })

      test('mentions data classes (Kotlin) as allowed structural construct', () => {
        expect(COMPILED_LANGUAGE_RULES.toLowerCase()).toContain('data class')
      })

      test('mentions exception classes as allowed structural construct', () => {
        const rules = COMPILED_LANGUAGE_RULES.toLowerCase()
        expect(rules.includes('exception') || rules.includes('throwable')).toBe(
          true
        )
      })

      test('mentions interfaces as allowed structural construct', () => {
        expect(COMPILED_LANGUAGE_RULES.toLowerCase()).toContain('interface')
      })
    })

    describe('MEDIUM PRIORITY constructs', () => {
      test('mentions constants classes as allowed structural construct', () => {
        const rules = COMPILED_LANGUAGE_RULES.toLowerCase()
        expect(
          rules.includes('constant') || rules.includes('static final')
        ).toBe(true)
      })

      test('mentions companion objects (Kotlin) as allowed structural construct', () => {
        const rules = COMPILED_LANGUAGE_RULES.toLowerCase()
        expect(
          rules.includes('companion object') || rules.includes('companion')
        ).toBe(true)
      })

      test('mentions sealed classes/interfaces as allowed structural construct', () => {
        expect(COMPILED_LANGUAGE_RULES.toLowerCase()).toContain('sealed')
      })

      test('mentions annotation interfaces as allowed structural construct', () => {
        const rules = COMPILED_LANGUAGE_RULES.toLowerCase()
        expect(
          rules.includes('annotation') &&
            (rules.includes('@interface') || rules.includes('annotation class'))
        ).toBe(true)
      })
    })
  })

  describe('Criteria for Structural Constructs', () => {
    test('explicitly states zero conditionals allowed', () => {
      const rules = COMPILED_LANGUAGE_RULES.toLowerCase()
      expect(
        rules.includes('no conditional') ||
          rules.includes('zero conditional') ||
          rules.includes('if/else') ||
          rules.includes('switch')
      ).toBe(true)
    })

    test('explicitly states zero calculations allowed', () => {
      const rules = COMPILED_LANGUAGE_RULES.toLowerCase()
      expect(
        rules.includes('no calculation') ||
          rules.includes('zero calculation') ||
          rules.includes('arithmetic')
      ).toBe(true)
    })

    test('explicitly states simple assignments are allowed', () => {
      const rules = COMPILED_LANGUAGE_RULES.toLowerCase()
      expect(
        rules.includes('this.field = param') ||
          rules.includes('this.field = value') ||
          rules.includes('simple assignment')
      ).toBe(true)
    })
  })

  describe('Decision Tree Clarity', () => {
    test('includes decision tree for structural constructs', () => {
      expect(COMPILED_LANGUAGE_RULES).toContain('Decision Tree')
    })
  })
})
