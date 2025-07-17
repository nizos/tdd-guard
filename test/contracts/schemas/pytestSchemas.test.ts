import { describe, it, expect } from 'vitest'
import { PytestResultSchema } from '../../../src/contracts/schemas/pytestSchemas'

describe('PytestResultSchema', () => {
  it('should validate pytest test result format', () => {
    const pytestResult = {
      testModules: [
        {
          moduleId: 'test_example.py',
          tests: [
            {
              name: 'test_passing',
              fullName: 'test_example.py::test_passing',
              state: 'passed' as const,
            },
          ],
        },
      ],
    }

    const result = PytestResultSchema.safeParse(pytestResult)
    expect(result.success).toBe(true)
  })
})
