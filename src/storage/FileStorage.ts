import { Storage } from './Storage'
import { Config } from '../config/Config'
import fs from 'fs/promises'

export class FileStorage implements Storage {
  private readonly config: Config
  private readonly filePaths: Record<string, string>

  constructor(config?: Config) {
    this.config = config ?? new Config()
    this.filePaths = {
      test: this.config.testResultsFilePath,
      todo: this.config.todosFilePath,
      modifications: this.config.modificationsFilePath,
      lint: this.config.lintFilePath,
    }
  }

  private async ensureDirectory(): Promise<void> {
    await fs.mkdir(this.config.dataDir, { recursive: true })
  }

  private async save(type: string, content: string): Promise<void> {
    await this.ensureDirectory()
    await fs.writeFile(this.filePaths[type], content)
  }

  private async get(type: string): Promise<string | null> {
    try {
      return await fs.readFile(this.filePaths[type], 'utf-8')
    } catch {
      return null
    }
  }

  async saveTest(content: string): Promise<void> {
    await this.save('test', content)
  }

  async saveTodo(content: string): Promise<void> {
    await this.save('todo', content)
  }

  async saveModifications(content: string): Promise<void> {
    await this.save('modifications', content)
  }

  async saveLint(content: string): Promise<void> {
    await this.save('lint', content)
  }

  async getTest(): Promise<string | null> {
    try {
      const stats = await fs.stat(this.filePaths.test)
      const ageMs = Date.now() - stats.mtime.getTime()
      const TWENTY_MINUTES_MS = 1200000 // 20 minutes in milliseconds
      if (ageMs > TWENTY_MINUTES_MS) return null
    } catch {
      return null
    }
    return this.get('test')
  }

  async getTodo(): Promise<string | null> {
    return this.get('todo')
  }

  async getModifications(): Promise<string | null> {
    return this.get('modifications')
  }

  async getLint(): Promise<string | null> {
    return this.get('lint')
  }
}
