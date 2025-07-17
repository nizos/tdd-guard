import { IModelClient } from '../../contracts/types/ModelClient'
import { execFileSync } from 'child_process'
import { join } from 'path'
import { existsSync, mkdirSync } from 'fs'
import { Config } from '../../config/Config'

export class ClaudeCli implements IModelClient {
  private readonly config: Config

  constructor(config?: Config) {
    this.config = config ?? new Config()
  }

  async ask(prompt: string): Promise<string> {
    const claudeBinary = this.config.useSystemClaude
      ? 'claude'
      : `${process.env.HOME}/.claude/local/claude`

    const args = [
      '-',
      '--output-format',
      'json',
      '--max-turns',
      '2',
      '--model',
      'sonnet',
    ]
    const claudeDir = join(process.cwd(), '.claude')

    if (!existsSync(claudeDir)) {
      mkdirSync(claudeDir, { recursive: true })
    }

    const output = execFileSync(claudeBinary, args, {
      encoding: 'utf-8',
      timeout: 60000,
      input: prompt,
      cwd: claudeDir,
    })

    // Parse the Claude CLI response and extract the result field
    const response = JSON.parse(output)

    return response.result
  }
}
