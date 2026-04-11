import { GuardManager } from '../guard/GuardManager'
import { parseHookJson } from './parseHookJson'
import { ValidationResult } from '../contracts/types/ValidationResult'

export class UserPromptHandler {
  private readonly guardManager: GuardManager
  private readonly GUARD_COMMANDS = {
    ON: 'tdd-guard on',
    OFF: 'tdd-guard off'
  } as const

  constructor(guardManager?: GuardManager) {
    this.guardManager = guardManager ?? new GuardManager()
  }

  async processUserCommand(hookData: string): Promise<ValidationResult | undefined> {
    const data = parseHookJson(hookData) as Record<string, unknown>
    
    // Only process UserPromptSubmit events
    if (data.hook_event_name !== 'UserPromptSubmit') {
      return undefined
    }
    
    if (typeof data.prompt !== 'string') {
      return undefined
    }

    const command = data.prompt.toLowerCase()

    switch (command) {
      case this.GUARD_COMMANDS.ON:
        await this.guardManager.enable()
        return this.createBlockResult('TDD Guard enabled')
      
      case this.GUARD_COMMANDS.OFF:
        await this.guardManager.disable()
        return this.createBlockResult('TDD Guard disabled')
      
      default:
        return undefined
    }
  }

  private createBlockResult(message: string): ValidationResult {
    return {
      decision: undefined,
      reason: message,
      continue: false,
      stopReason: message
    }
  }

  async getDisabledResult(): Promise<ValidationResult | undefined> {
    const isEnabled = await this.guardManager.isEnabled()
    if (!isEnabled) {
      return { decision: undefined, reason: '' }
    }
    return undefined
  }
}