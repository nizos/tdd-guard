import { Storage } from '../storage/Storage'
import { FileStorage } from '../storage/FileStorage'
import { parseHookJson } from './parseHookJson'
import { SessionStartSchema } from '../contracts/schemas/toolSchemas'
import { RULES } from '../validation/prompts/rules'

export class SessionHandler {
  private readonly storage: Storage

  constructor(storage?: Storage) {
    this.storage = storage ?? new FileStorage()
  }

  async processSessionStart(hookData: string): Promise<void> {
    const parsedData = parseHookJson(hookData)
    const sessionStartResult = SessionStartSchema.safeParse(parsedData)
    
    if (!sessionStartResult.success) {
      return
    }

    await this.ensureInstructionsExist()
    await this.storage.clearTransientData()
  }

  private async ensureInstructionsExist(): Promise<void> {
    const existingInstructions = await this.storage.getInstructions()
    if (!existingInstructions) {
      await this.storage.saveInstructions(RULES)
    }
  }
}