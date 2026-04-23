import { Config } from '../../config/Config'
import { query, type Options } from '@anthropic-ai/claude-agent-sdk'
import { IModelClient } from '../../contracts/types/ModelClient'
import { SYSTEM_PROMPT } from '../prompts/system-prompt'

export class ClaudeAgentSdk implements IModelClient {
  constructor(
    private readonly config: Config = new Config(),
    private readonly queryFn: typeof query = query
  ) {}

  async ask(prompt: string): Promise<string> {
    const queryResult = this.queryFn({
      prompt,
      options: this.getQueryOptions(),
    })

    for await (const message of queryResult) {
      if (message.type !== 'result') continue

      if (message.subtype === 'success') {
        return message.result
      }
      throw new Error(`Claude Agent SDK error: ${message.subtype}`)
    }

    throw new Error('Claude Agent SDK error: No result message received')
  }

  private getQueryOptions(): Options {
    return {
      maxTurns: 1,
      systemPrompt: SYSTEM_PROMPT,
      allowedTools: [],
      disallowedTools: [
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
      ],
      maxThinkingTokens: 0,
      model: this.config.modelVersion,
      strictMcpConfig: true,
      cwd: this.config.dataDir,
      env: this.getCleanEnvironment(),
    }
  }

  private getCleanEnvironment(): Record<string, string | undefined> {
    const environment = { ...process.env }
    delete environment.CLAUDECODE
    delete environment.ANTHROPIC_API_KEY
    return environment
  }
}
