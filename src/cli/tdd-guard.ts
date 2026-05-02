#!/usr/bin/env node

import 'dotenv/config'
import { processHookData } from '../hooks/processHookData'
import { Storage } from '../storage/Storage'
import { FileStorage } from '../storage/FileStorage'
import { validator } from '../validation/validator'
import { Config } from '../config/Config'
import { ModelClientProvider } from '../providers/ModelClientProvider'
import { ValidationResult } from '../contracts/types/ValidationResult'

export async function run(
  input: string,
  config?: Config,
  storage?: Storage,
  provider?: ModelClientProvider
): Promise<ValidationResult> {
  const appConfig = config ?? new Config()
  const actualStorage = storage ?? new FileStorage(appConfig)
  const modelProvider = provider ?? new ModelClientProvider()
  const modelClient = modelProvider.getModelClient(appConfig)

  return processHookData(input, {
    storage: actualStorage,
    validator: (context) => validator(context, modelClient),
  })
}

// Only run if this is the main module
if (require.main === module) {
  let inputData = ''
  process.stdin.setEncoding('utf8')

  process.stdin.on('data', (chunk) => {
    inputData += chunk
  })

  process.stdin.on('end', async () => {
    try {
      const result = await run(inputData)
      if (
        result.decision !== undefined ||
        result.reason !== '' ||
        result.continue === false
      ) {
        console.log(JSON.stringify(result))
      }
    } catch (error) {
      console.error('Failed to parse hook data:', error)
    } finally {
      process.exit(0)
    }
  })
}
