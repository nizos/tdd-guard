import { Storage } from '../storage/Storage'
import { FileStorage } from '../storage/FileStorage'
import { minimatch } from 'minimatch'
import {
  GuardConfig,
  GuardConfigSchema,
} from '../contracts/schemas/guardSchemas'

export class GuardManager {
  private readonly storage: Storage
  private readonly minimatchOptions = {
    matchBase: true, // allows *.ext to match in any directory
    nobrace: false, // enables brace expansion {a,b}
    dot: true, // allows patterns to match files/dirs starting with .
  } as const

  static readonly DEFAULT_IGNORE_PATTERNS = [
    '*.md',
    '*.txt',
    '*.log',
    '*.json',
    '*.yml',
    '*.yaml',
    '*.xml',
    '*.html',
    '*.css',
    '*.erb',
    '*.rst',
  ]

  constructor(storage?: Storage) {
    this.storage = storage ?? new FileStorage()
  }

  async isEnabled(): Promise<boolean> {
    const config = await this.getConfig()
    return config?.guardEnabled ?? true
  }

  async enable(): Promise<void> {
    await this.setGuardEnabled(true)
  }

  async disable(): Promise<void> {
    await this.setGuardEnabled(false)
  }

  async getIgnorePatterns(): Promise<string[]> {
    const config = await this.getConfig()
    return config?.ignorePatterns ?? GuardManager.DEFAULT_IGNORE_PATTERNS
  }

  async shouldIgnoreFile(filePath: string): Promise<boolean> {
    const patterns = await this.getIgnorePatterns()

    return patterns.some((pattern) =>
      minimatch(filePath, pattern, this.minimatchOptions)
    )
  }

  private async setGuardEnabled(enabled: boolean): Promise<void> {
    const existingConfig = await this.getConfig()
    const config: GuardConfig = {
      ...existingConfig,
      guardEnabled: enabled,
    }
    await this.storage.saveConfig(JSON.stringify(config))
  }

  private async getConfig(): Promise<GuardConfig | null> {
    const configString = await this.storage.getConfig()
    if (!configString) {
      return null
    }

    try {
      const parsed = JSON.parse(configString)
      return GuardConfigSchema.parse(parsed)
    } catch {
      // Return null for invalid JSON or schema validation errors
      return null
    }
  }
}
