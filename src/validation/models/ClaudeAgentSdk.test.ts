import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest'
import { ClaudeAgentSdk } from './ClaudeAgentSdk'
import { Config } from '../../config/Config'
import { IModelClient } from '../../contracts/types/ModelClient'
import { query, type SDKResultMessage } from '@anthropic-ai/claude-agent-sdk'
import { SYSTEM_PROMPT } from '../prompts/system-prompt'

describe('ClaudeAgentSdk', () => {
  describe('constructor', () => {
    test('implements the IModelClient interface', () => {
      const client: IModelClient = new ClaudeAgentSdk()
      expect(client.ask).toBeDefined()
    })

    test('accepts optional Config in constructor', () => {
      const config = new Config()
      const client = new ClaudeAgentSdk(config)
      expect(client['config']).toBe(config)
    })

    test('uses default Config when not provided', () => {
      const client = new ClaudeAgentSdk()
      expect(client['config']).toBeInstanceOf(Config)
    })

    test('accepts query function as second parameter', () => {
      const customQuery = vi.fn()
      const config = new Config()
      const client = new ClaudeAgentSdk(config, customQuery)
      expect(client['queryFn']).toBe(customQuery)
    })

    test('uses query from @anthropic-ai/claude-agent-sdk when not provided', () => {
      const client = new ClaudeAgentSdk()
      expect(client['queryFn']).toBe(query)
    })
  })

  describe('query invocation', () => {
    const prompt = 'test prompt'
    const message = createSDKResultMessage()
    const modelVersion = 'claude-opus-4-1'
    const config = new Config({ modelVersion })
    const { client, getUsedOptions, getUsedPrompt } = setupClient(
      message,
      config
    )

    beforeEach(async () => {
      await client.ask(prompt)
    })

    afterEach(() => {
      delete process.env.CLAUDECODE
      delete process.env.SOME_OTHER_VAR
      delete process.env.ANTHROPIC_API_KEY
    })

    test('calls queryFn with correct prompt', async () => {
      expect(getUsedPrompt()).toBe(prompt)
    })

    test('sets maxTurns to 1', async () => {
      expect(getUsedOptions().maxTurns).toBe(1)
    })

    test('sets allowedTools to empty array', async () => {
      expect(getUsedOptions().allowedTools).toEqual([])
    })

    test('sets disallowedTools to prevent file operations and other tools', async () => {
      const expectedDisallowedTools = [
        'Read',
        'Edit',
        'MultiEdit',
        'Write',
        'Grep',
        'Glob',
        'Bash',
        'WebFetch',
        'WebSearch',
        'Task',
        'TodoWrite',
      ]
      expect(getUsedOptions().disallowedTools).toEqual(expectedDisallowedTools)
    })

    test('sets maxThinkingTokens to 0', async () => {
      expect(getUsedOptions().maxThinkingTokens).toBe(0)
    })

    test('uses model version from config', async () => {
      expect(getUsedOptions().model).toBe(modelVersion)
    })

    test('sets strictMcpConfig to true', async () => {
      expect(getUsedOptions().strictMcpConfig).toBe(true)
    })

    test('uses SYSTEM_PROMPT for systemPrompt', async () => {
      expect(getUsedOptions().systemPrompt).toBe(SYSTEM_PROMPT)
    })

    test('sets cwd to config dataDir', async () => {
      // Prevents hook trigggers and keeps queries out of project history
      expect(getUsedOptions().cwd).toBe(config.dataDir)
    })

    test('passes env without CLAUDECODE to prevent nested session rejection', async () => {
      process.env.CLAUDECODE = '1'

      const freshSetup = setupClient(createSDKResultMessage(), config)
      await freshSetup.client.ask(prompt)

      expect(freshSetup.getUsedOptions().env).toBeDefined()
      expect(freshSetup.getUsedOptions().env).not.toHaveProperty('CLAUDECODE')
    })

    test('preserves other environment variables in env', async () => {
      process.env.CLAUDECODE = '1'
      process.env.SOME_OTHER_VAR = 'keep-me'

      const freshSetup = setupClient(createSDKResultMessage(), config)
      await freshSetup.client.ask(prompt)

      expect(freshSetup.getUsedOptions().env).toHaveProperty(
        'SOME_OTHER_VAR',
        'keep-me'
      )
    })

    test('strips ANTHROPIC_API_KEY from env to prevent subscription bypass', async () => {
      process.env.ANTHROPIC_API_KEY = 'sk-dummy-key'

      const freshSetup = setupClient(createSDKResultMessage(), config)
      await freshSetup.client.ask(prompt)

      expect(freshSetup.getUsedOptions().env).toBeDefined()
      expect(freshSetup.getUsedOptions().env).not.toHaveProperty(
        'ANTHROPIC_API_KEY'
      )
    })
  })

  describe('result handling', () => {
    test('returns result from successful response', async () => {
      const { client } = setupClient({ result: 'test result' })

      await expect(client.ask('test')).resolves.toBe('test result')
    })

    test('throws error when query returns error subtype', async () => {
      const { client } = setupClient({ subtype: 'error_max_turns' })

      await expect(client.ask('test')).rejects.toThrow(
        'Claude Agent SDK error: error_max_turns'
      )
    })

    test('throws error when no result message is received', async () => {
      const { client } = setupClient({ type: 'other', data: 'something' })

      await expect(client.ask('test')).rejects.toThrow(
        'Claude Agent SDK error: No result message received'
      )
    })
  })
})

// Test Helpers
function setupClient(
  messageOverrides: Partial<SDKResultMessage> = {},
  config: Config = new Config()
) {
  const customQuery = createMockQuery(messageOverrides)
  const client = new ClaudeAgentSdk(config, customQuery)

  const getLastCall = () => customQuery.mock.lastCall![0]
  const getUsedOptions = () => getLastCall().options
  const getUsedPrompt = () => getLastCall().prompt

  return {
    client,
    customQuery,
    config,
    getUsedOptions,
    getUsedPrompt,
  }
}

function createMockQuery(messageOverrides: Partial<SDKResultMessage> = {}) {
  return vi.fn().mockReturnValue({
    async *[Symbol.asyncIterator]() {
      yield createSDKResultMessage(messageOverrides)
    },
  })
}

function createSDKResultMessage(
  overrides: Partial<SDKResultMessage> = {}
): SDKResultMessage {
  return {
    type: 'result',
    subtype: 'success',
    result: 'default result',
    ...overrides,
  } as SDKResultMessage
}
