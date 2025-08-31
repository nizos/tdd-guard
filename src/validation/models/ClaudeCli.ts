import { execFileSync } from 'child_process'
import { join } from 'path'
import { homedir } from 'os'
import { existsSync, mkdirSync } from 'fs'
import { IModelClient } from '../../contracts/types/ModelClient'
import { Config } from '../../config/Config'

export class ClaudeCli implements IModelClient {
  private readonly config: Config

  constructor(config?: Config) {
    this.config = config ?? new Config()
  }

  async ask(prompt: string): Promise<string> {
    const claudeBinary = this.getClaudeBinary()

    const args = [
      '-',
      '--output-format',
      'json',
      '--max-turns',
      '5',
      '--model',
      'sonnet',
      '--disallowed-tools',
      'TodoWrite',
      '--strict-mcp-config',
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
      shell: process.platform === 'win32',
    })

    // Parse the Claude CLI response and extract the result field
    const response = JSON.parse(output)

    return response.result
  }

  private getClaudeBinary(): string {
    if (this.config.useSystemClaude) {
      return 'claude'
    }

    return join(homedir(), '.claude', 'local', 'claude')
  }
}
