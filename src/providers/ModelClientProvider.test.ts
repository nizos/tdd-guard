import { describe, test, expect } from 'vitest'
import { ModelClientProvider } from './ModelClientProvider'
import { Config } from '../config/Config'
import { ClaudeCli } from '../validation/models/ClaudeCli'
import { AnthropicApi } from '../validation/models/AnthropicApi'

describe('ModelClientProvider', () => {
  test('returns ClaudeCli when config modelType is claude_cli', () => {
    const config = new Config({ modelType: 'claude_cli' })

    const provider = new ModelClientProvider()
    const client = provider.getModelClient(config)

    expect(client).toBeInstanceOf(ClaudeCli)
  })

  test('returns AnthropicApi when config modelType is anthropic_api', () => {
    const config = new Config({ modelType: 'anthropic_api' })

    const provider = new ModelClientProvider()
    const client = provider.getModelClient(config)

    expect(client).toBeInstanceOf(AnthropicApi)
  })

  test('uses default config when no config is provided', () => {
    const provider = new ModelClientProvider()
    const client = provider.getModelClient()

    expect(client).toBeInstanceOf(ClaudeCli) // Default modelType is 'claude_cli'
  })

  test('passes config with API key to AnthropicApi client', () => {
    const config = new Config({
      modelType: 'anthropic_api',
      anthropicApiKey: 'test-api-key-123',
    })

    const provider = new ModelClientProvider()
    const client = provider.getModelClient(config)

    expect(client).toBeInstanceOf(AnthropicApi)
    expect((client as AnthropicApi).config.anthropicApiKey).toBe(
      'test-api-key-123'
    )
  })

  test('passes config with useSystemClaude to ClaudeCli client', () => {
    const config = new Config({
      modelType: 'claude_cli',
      useSystemClaude: true,
    })

    const provider = new ModelClientProvider()
    const client = provider.getModelClient(config)

    expect(client).toBeInstanceOf(ClaudeCli)
    expect((client as ClaudeCli).config.useSystemClaude).toBe(true)
  })
})
