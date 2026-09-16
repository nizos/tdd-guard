import { Context } from '../contracts/types/Context'
import { ValidationResult } from '../contracts/types/ValidationResult'
import { ModelClient } from '../contracts/types/ModelClient'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { validator } from './validator'
import { generateDynamicContext } from './context/context'
import { testData } from '@testUtils'

vi.mock('./context/context', () => ({
  generateDynamicContext: vi.fn(),
}))

describe('validator with mock model', () => {
  const mockGenerateDynamicContext = vi.mocked(generateDynamicContext)

  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('simplified interface', () => {
    test('should only pass prompt to model client without context', async () => {
      const { result, mockModelClient, context } = await runValidator(
        JSON.stringify({ decision: null, reason: 'ok' }),
        { prompt: 'complete prompt with all context included' }
      )

      expect(mockGenerateDynamicContext).toHaveBeenCalledWith(context)
      // Should only pass the prompt, not the context
      expect(mockModelClient.ask).toHaveBeenCalledWith(
        'complete prompt with all context included'
      )
      expect(result).toEqual({
        decision: undefined,
        reason: 'ok',
      })
    })
  })

  describe('JSON extraction from model responses', () => {
    const BLOCK_JSON = '{"decision": "block", "reason": "TDD violation"}'
    const PASS_JSON = '{"decision": null, "reason": "ok"}'

    const testCases = [
      {
        name: 'should extract JSON from markdown code blocks',
        modelResponse: `\`\`\`json\n${BLOCK_JSON}\n\`\`\``,
        expected: { decision: 'block', reason: 'TDD violation' },
      },
      {
        name: 'should handle JSON with extra whitespace in code blocks',
        modelResponse: `\`\`\`json  \n\n  ${PASS_JSON}  \n\n\`\`\``,
        expected: { decision: undefined, reason: 'ok' },
      },
      {
        name: 'should parse raw JSON when no code block present',
        modelResponse: PASS_JSON,
        expected: { decision: undefined, reason: 'ok' },
      },
      {
        name: 'should extract JSON from response with surrounding text',
        modelResponse: `Here is my analysis:\n\`\`\`json\n${BLOCK_JSON}\n\`\`\`\nThat concludes the review.`,
        expected: { decision: 'block', reason: 'TDD violation' },
      },
      {
        name: 'should handle fence blocks without language specification',
        modelResponse: `\`\`\`\n${PASS_JSON}\n\`\`\``,
        expected: { decision: undefined, reason: 'ok' },
      },
      {
        name: 'should ignore fence blocks with txt language',
        modelResponse: `\`\`\`txt\nSome analysis text\n\`\`\`\n${PASS_JSON}`,
        expected: { decision: undefined, reason: 'ok' },
      },
      {
        name: 'should extract the last JSON fence block when multiple exist',
        modelResponse: `First block:\n\`\`\`json\n${BLOCK_JSON}\n\`\`\`\nSecond block:\n\`\`\`json\n${PASS_JSON}\n\`\`\``,
        expected: { decision: undefined, reason: 'ok' },
      },
      {
        name: 'should convert null decision to undefined',
        modelResponse: '{"decision": null, "reason": "No issues found"}',
        expected: { decision: undefined, reason: 'No issues found' },
      },
      {
        name: 'should extract JSON from response with detailed explanation before JSON',
        modelResponse: `Analysis paragraph one explaining the issue.

Analysis paragraph two with more details.

{
  "decision": "block",
  "reason": "Multiple test addition violation - adding 2 new tests simultaneously instead of following TDD discipline of one test at a time"
}`,
        expected: {
          decision: 'block',
          reason:
            'Multiple test addition violation - adding 2 new tests simultaneously instead of following TDD discipline of one test at a time',
        },
      },
      {
        name: 'should extract JSON with line break before opening brace',
        modelResponse: `Analysis text that gets cut off mid-sentence because it's too lo...
{
  "decision": "block",
  "reason": "Multiple test addition violation - adding 2 new tests simultaneously instead of following TDD discipline of one test at a time"
}`,
        expected: {
          decision: 'block',
          reason:
            'Multiple test addition violation - adding 2 new tests simultaneously instead of following TDD discipline of one test at a time',
        },
      },
      {
        name: 'should extract JSON when analyzing constructor error',
        modelResponse: `Analysis with code blocks:

\`\`\`
Calculator is not a constructor
\`\`\`

More analysis after the error message.

{
  "decision": "block",
  "reason": "Over-implementation: Test fails with 'Calculator is not a constructor' but you're adding both the class AND the add method. Should only create empty class first, then run test again to get proper failure for the add method."
}`,
        expected: {
          decision: 'block',
          reason:
            "Over-implementation: Test fails with 'Calculator is not a constructor' but you're adding both the class AND the add method. Should only create empty class first, then run test again to get proper failure for the add method.",
        },
      },
      {
        name: 'should extract JSON when analyzing not defined error with typescript code',
        modelResponse: `Analysis with TypeScript code block:

\`\`\`typescript
export class Calculator {
  add(a: number, b: number): number {
    return a + b;
  }
}
\`\`\`

Analysis continues after the code block.

{
  "decision": "block",
  "reason": "Over-implementation violation. Test fails with 'Calculator is not defined' but implementation adds both class AND add method. Should only create empty class first, then run test to get next failure."
}`,
        expected: {
          decision: 'block',
          reason:
            "Over-implementation violation. Test fails with 'Calculator is not defined' but implementation adds both class AND add method. Should only create empty class first, then run test to get next failure.",
        },
      },
      {
        name: 'should extract JSON when model provides extensive analysis before decision',
        modelResponse: `Extensive analysis with lists:

1. First point about the issue
2. Second point with more detail
3. Third point explaining the problem

This violates TDD principles as explained in the numbered list above.

{
  "decision": "block",
  "reason": "Multiple test addition violation - adding 2 new tests simultaneously instead of following TDD discipline of one test at a time"
}`,
        expected: {
          decision: 'block',
          reason:
            'Multiple test addition violation - adding 2 new tests simultaneously instead of following TDD discipline of one test at a time',
        },
      },
      {
        name: 'should treat a reasonless allow as an empty reason',
        modelResponse: '{"decision": null}',
        expected: { decision: undefined, reason: '' },
      },
      {
        name: 'should treat a fence-less decision-only response as an allow',
        modelResponse: 'Looks fine.\n{"decision": null}',
        expected: { decision: undefined, reason: '' },
      },
    ]

    testCases.forEach(({ name, modelResponse, expected }) => {
      test(name, async () => {
        const { result } = await runValidator(modelResponse)
        expect(result).toEqual(expected)
      })
    })
  })

  describe('prompt generation for different tools', () => {
    test('should send correct prompt for Edit operation', async () => {
      const { result, mockModelClient, context } = await runValidator(
        JSON.stringify({ decision: null, reason: 'ok' }),
        { prompt: 'mocked prompt with Edit instructions' }
      )

      expect(mockGenerateDynamicContext).toHaveBeenCalledWith(context)
      expect(mockModelClient.ask).toHaveBeenCalledWith(
        'mocked prompt with Edit instructions'
      )
      expect(result).toEqual({
        decision: undefined,
        reason: 'ok',
      })
    })
  })

  describe('error handling (fail open)', () => {
    test('fails open when model client throws error', async () => {
      const { result } = await runValidator(
        new Error('Command failed: claude not found')
      )

      expect(result.decision).toBeUndefined()
      expect(result.reason).toContain('Validation skipped')
      expect(result.reason).toContain('Command failed: claude not found')
    })

    test('fails open when model returns invalid JSON', async () => {
      const { result } = await runValidator(
        `\`\`\`json\ninvalid json content\n\`\`\``
      )

      expect(result.decision).toBeUndefined()
      expect(result.reason).toContain('Validation skipped')
    })

    test('fails open with specific message when model returns no response', async () => {
      const { result } = await runValidator('')

      expect(result.decision).toBeUndefined()
      expect(result.reason).toContain('No response from model')
      expect(result.reason).not.toContain('Validation skipped')
    })

    test('surfaces the model response when it is not valid JSON', async () => {
      const { result } = await runValidator('Credit balance too low')

      expect(result.decision).toBeUndefined()
      expect(result.reason).toContain('Credit balance too low')
    })

    test('fails open when the response has no usable decision', async () => {
      const { result } = await runValidator('{"reason": "explanation only"}')

      expect(result.decision).toBeUndefined()
      expect(result.reason).toContain('explanation only')
    })
  })

  // Test helper
  async function runValidator(
    modelResponse: string | Error,
    options?: {
      contextOverrides?: Partial<Context>
      prompt?: string
    }
  ): Promise<{
    result: ValidationResult
    mockModelClient: ModelClient
    context: Context
  }> {
    const mockModelClient: ModelClient = {
      ask:
        modelResponse instanceof Error
          ? vi.fn().mockRejectedValue(modelResponse)
          : vi.fn().mockResolvedValue(modelResponse),
    }

    const context: Context = {
      modifications: JSON.stringify(testData.editOperation()),
      ...options?.contextOverrides,
    }

    mockGenerateDynamicContext.mockReturnValue(options?.prompt ?? 'test prompt')

    const result = await validator(context, mockModelClient)

    return {
      result,
      mockModelClient,
      context,
    }
  }
})
