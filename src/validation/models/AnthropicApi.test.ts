import { describe, test, expect, vi, beforeEach } from 'vitest'
import { AnthropicApi } from './AnthropicApi'
import Anthropic from '@anthropic-ai/sdk'
import { Config } from '../../config/Config'
import { SYSTEM_PROMPT } from '../prompts/system-prompt'

vi.mock('@anthropic-ai/sdk')

// Test constants
const DEFAULT_TEST_PROMPT = 'test prompt'

describe('AnthropicApi', () => {
  let sut: Awaited<ReturnType<typeof createSut>>
  let client: AnthropicApi
  const apiKey = 'test-api-key'
  const modelVersion = 'claude-3-5-sonnet-20241022'

  beforeEach(() => {
    sut = createSut(apiKey, modelVersion)
    client = sut.client
  })

  test('should implement IModelClient interface', () => {
    expect(client.ask).toBeDefined()
  })

  test('should accept optional Config in constructor', () => {
    const config = new Config()
    const apiClient = new AnthropicApi(config)
    expect(apiClient).toBeDefined()
  })

  test('should create Anthropic client with API key from config', () => {
    expect(sut.wasCreatedWithApiKey(apiKey)).toBe(true)
  })

  test('should create Anthropic client with baseURL from config', () => {
    const baseUrl = 'https://api.z.ai/api/anthropic'
    const customSut = createSut(apiKey, modelVersion, baseUrl)

    expect(customSut.wasCreatedWithBaseUrl(baseUrl)).toBe(true)
  })

  test('should not include baseURL when not configured', () => {
    expect(sut.wasCreatedWithoutBaseUrl()).toBe(true)
  })

  test('uses model version from config', async () => {
    const call = await sut.askAndGetCall()
    expect(call.model).toBe(modelVersion)
  })

  test('includes system prompt in API call', async () => {
    const call = await sut.askAndGetCall()
    expect(call.system).toBe(SYSTEM_PROMPT)
  })

  test('sets max tokens to 1024', async () => {
    const call = await sut.askAndGetCall()
    expect(call.max_tokens).toBe(1024)
  })

  test('passes prompt as user message', async () => {
    const prompt = 'Does this follow TDD?'
    const call = await sut.askAndGetCall(prompt)
    expect(call.messages).toEqual([
      {
        role: 'user',
        content: prompt,
      },
    ])
  })

  test('ask method should return text from response', async () => {
    const expectedText = 'Model response text'
    const result = await sut.askWithResponse(expectedText)

    expect(result).toBe(expectedText)
  })

  test('ask method should throw error when API call fails', async () => {
    await expect(sut.askWithError('API error')).rejects.toThrow('API error')
  })

  test('ask method should handle empty content array', async () => {
    sut.mockCreate.mockResolvedValue({ content: [] })

    await expect(client.ask(DEFAULT_TEST_PROMPT)).rejects.toThrow()
  })

  test('ask method should handle missing text property', async () => {
    sut.mockCreate.mockResolvedValue({ content: [{ type: 'image' }] })

    await expect(client.ask(DEFAULT_TEST_PROMPT)).rejects.toThrow()
  })
})

// Test Helpers
interface MessageCreateParams {
  model: string
  system: string
  max_tokens: number
  messages: Array<{ role: string; content: string }>
}

function createSut(apiKey?: string, modelVersion?: string, baseUrl?: string) {
  vi.clearAllMocks()

  const mockCreate = vi.fn().mockResolvedValue({
    content: [{ text: 'Model response' }],
  })

  class MockAnthropic {
    messages = { create: mockCreate }
  }
  const mockAnthropicConstructor = vi.mocked(Anthropic)
  mockAnthropicConstructor.mockImplementation(
    MockAnthropic as unknown as typeof Anthropic
  )

  const config = new Config({
    anthropicApiKey: apiKey,
    modelVersion,
    anthropicBaseUrl: baseUrl,
  })
  const client = new AnthropicApi(config)

  const mockResponse = (text: string): void => {
    mockCreate.mockResolvedValue({
      content: [{ text }],
    })
  }

  const getLastCall = (): MessageCreateParams => {
    const lastCall = mockCreate.mock.calls[mockCreate.mock.calls.length - 1]
    const params = lastCall[0]
    return {
      model: params.model,
      system: params.system,
      max_tokens: params.max_tokens,
      messages: params.messages,
    }
  }

  const askAndGetCall = async (
    prompt = DEFAULT_TEST_PROMPT
  ): Promise<MessageCreateParams> => {
    await client.ask(prompt)
    return getLastCall()
  }

  const wasCreatedWithApiKey = (key: string): boolean => {
    return mockAnthropicConstructor.mock.calls.some(
      (call) => call[0]?.apiKey === key
    )
  }

  const wasCreatedWithBaseUrl = (url: string): boolean => {
    return mockAnthropicConstructor.mock.calls.some(
      (call) => call[0]?.baseURL === url
    )
  }

  const wasCreatedWithoutBaseUrl = (): boolean => {
    return mockAnthropicConstructor.mock.calls.some(
      (call) => !('baseURL' in (call[0] ?? {}))
    )
  }

  const askWithResponse = async (
    responseText: string
  ): Promise<string | undefined> => {
    mockResponse(responseText)
    return client.ask(DEFAULT_TEST_PROMPT)
  }

  const askWithError = async (
    errorMessage: string
  ): Promise<string | undefined> => {
    mockCreate.mockRejectedValue(new Error(errorMessage))
    return client.ask(DEFAULT_TEST_PROMPT)
  }

  return {
    client,
    mockCreate,
    mockAnthropicConstructor,
    config,
    mockResponse,
    getLastCall,
    askAndGetCall,
    wasCreatedWithApiKey,
    wasCreatedWithBaseUrl,
    wasCreatedWithoutBaseUrl,
    askWithResponse,
    askWithError,
  }
}
