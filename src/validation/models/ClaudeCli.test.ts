import { describe, test, expect, vi, beforeEach } from 'vitest'
import { ClaudeCli } from './ClaudeCli'
import { Config } from '../../config/Config'
import { execFileSync } from 'child_process'
import * as fs from 'fs'

vi.mock('child_process')
vi.mock('fs', { spy: true })

const mockExecFileSync = vi.mocked(execFileSync)

// Test constants
const DEFAULT_TEST_PROMPT = 'test prompt'

describe('ClaudeCli', () => {
  let sut: Awaited<ReturnType<typeof createSut>>
  let client: ClaudeCli

  beforeEach(() => {
    sut = createSut()
    client = sut.client
  })

  describe('command construction', () => {
    test('uses claude command', async () => {
      const call = await sut.askAndGetCall()
      expect(call.command).toContain('claude')
    })

    test('uses correct flags', async () => {
      const call = await sut.askAndGetCall()
      expect(call.args).toEqual([
        '-',
        '--output-format',
        'json',
        '--max-turns',
        '2',
        '--model',
        'sonnet',
      ])
    })
  })

  describe('subprocess configuration', () => {
    test('passes prompt via input option', async () => {
      const prompt = 'Does this follow TDD?'
      const call = await sut.askAndGetCall(prompt)
      expect(call.options.input).toBe(prompt)
    })

    test('uses utf-8 encoding', async () => {
      const call = await sut.askAndGetCall()
      expect(call.options.encoding).toBe('utf-8')
    })

    test('sets timeout to 60 seconds', async () => {
      const call = await sut.askAndGetCall()
      expect(call.options.timeout).toBe(60000)
    })

    test('executes claude command from .claude subdirectory', async () => {
      const call = await sut.askAndGetCall()
      expect(call.options.cwd).toContain('.claude')
    })

    test('creates .claude directory if it does not exist', async () => {
      const mockMkdirSync = vi.spyOn(fs, 'mkdirSync')
      const mockExistsSync = vi.spyOn(fs, 'existsSync')

      mockExistsSync.mockReturnValue(false)

      await client.ask('test')

      expect(mockMkdirSync).toHaveBeenCalledWith(
        expect.stringContaining('.claude'),
        { recursive: true }
      )

      mockMkdirSync.mockRestore()
      mockExistsSync.mockRestore()
    })
  })

  describe('response handling', () => {
    test('extracts and returns the result field from CLI output', async () => {
      const modelResponse = '```json\n{"approved": true}\n```'
      const cliOutput = JSON.stringify({ result: modelResponse })
      mockExecFileSync.mockReturnValue(cliOutput)

      const result = await client.ask(DEFAULT_TEST_PROMPT)

      expect(result).toBe(modelResponse)
    })

    test('extracts result field from complex CLI responses', async () => {
      const modelResponse =
        'Here is the analysis:\n```json\n{"approved": true}\n```\nThat concludes the review.'
      const cliOutput = JSON.stringify({
        result: modelResponse,
        metadata: { model: 'sonnet' },
      })
      mockExecFileSync.mockReturnValue(cliOutput)

      const result = await client.ask(DEFAULT_TEST_PROMPT)

      expect(result).toBe(modelResponse)
    })

    test('returns undefined when result field is missing', async () => {
      const cliOutput = JSON.stringify({ error: 'No result' })
      mockExecFileSync.mockReturnValue(cliOutput)

      const result = await client.ask(DEFAULT_TEST_PROMPT)

      expect(result).toBeUndefined()
    })
  })

  describe('error handling', () => {
    test('throws error when execFileSync fails', async () => {
      mockExecFileSync.mockImplementation(() => {
        throw new Error('Command failed')
      })

      await expect(client.ask('test')).rejects.toThrow('Command failed')
    })

    test('throws error when CLI output is not valid JSON', async () => {
      const rawOutput = 'invalid json or error message'
      mockExecFileSync.mockReturnValue(rawOutput)

      await expect(client.ask('test')).rejects.toThrow()
    })
  })

  describe('security', () => {
    test('uses execFileSync with system claude when useSystemClaude is true', async () => {
      const localSut = createSut({ useSystemClaude: true })
      await localSut.client.ask(DEFAULT_TEST_PROMPT)

      const call = localSut.getLastCall()
      expect(call.command).toBe('claude')
    })

    test('uses execFileSync with local claude path when useSystemClaude is false', async () => {
      const localSut = createSut({ useSystemClaude: false })
      await localSut.client.ask(DEFAULT_TEST_PROMPT)

      const call = localSut.getLastCall()
      expect(call.command).toBe(`${process.env.HOME}/.claude/local/claude`)
    })
  })
})

// Test Helpers
function createSut(options: { useSystemClaude?: boolean } = {}) {
  // Setup mocks
  vi.clearAllMocks()
  mockExecFileSync.mockReturnValue(JSON.stringify({ result: 'test' }))
  vi.spyOn(fs, 'existsSync').mockReturnValue(true)

  const config = new Config({ useSystemClaude: options.useSystemClaude })
  const client = new ClaudeCli(config)

  const mockResponse = (response: string | object): void => {
    const jsonResponse =
      typeof response === 'string'
        ? JSON.stringify({ result: response })
        : JSON.stringify(response)
    mockExecFileSync.mockReturnValue(jsonResponse)
  }

  const getLastCall = (): {
    command: string
    args: string[]
    options: Record<string, unknown>
  } => {
    const lastCall =
      mockExecFileSync.mock.calls[mockExecFileSync.mock.calls.length - 1]
    return {
      command: lastCall[0] as string,
      args: lastCall[1] as string[],
      options: lastCall[2] as Record<string, unknown>,
    }
  }

  const askAndGetCall = async (
    prompt = DEFAULT_TEST_PROMPT
  ): Promise<{
    command: string
    args: string[]
    options: Record<string, unknown>
  }> => {
    await client.ask(prompt)
    return getLastCall()
  }

  return {
    client,
    mockResponse,
    getLastCall,
    askAndGetCall,
  }
}
