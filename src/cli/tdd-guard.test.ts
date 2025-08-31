import { describe, test, expect, beforeEach, afterEach } from 'vitest'
import { spawn } from 'child_process'
import path from 'path'
import fs from 'fs/promises'
import os from 'os'
import { FileStorage } from '../storage/FileStorage'
import { Config } from '../config/Config'
import { ModelClientProvider } from '../providers/ModelClientProvider'
import { run } from './tdd-guard'
import { testData } from '@testUtils'

describe('tdd-guard CLI', () => {
  const cliPath = path.join(__dirname, 'tdd-guard.ts')

  describe('CLI Behavior', () => {
    test('has shebang for direct execution', async () => {
      const content = await fs.readFile(cliPath, 'utf-8')
      const firstLine = content.split('\n')[0].trim()

      expect(firstLine).toBe('#!/usr/bin/env node')
    })

    test('exits with status 0 on invalid JSON', async () => {
      const invalidJson = '{ invalid json'

      const { exitCode } = await runCli(invalidJson)

      expect(exitCode).toBe(0)
    })

    test('logs error to stderr on invalid JSON', async () => {
      const invalidJson = '{ invalid json'

      const { stderr } = await runCli(invalidJson)

      expect(stderr).toContain('Failed to parse hook data')
    })
  })

  describe('Data Persistence', () => {
    let projectRoot: string
    let storage: FileStorage
    let testConfig: Config
    let modelProvider: ModelClientProvider
    const originalEnv = process.env

    beforeEach(async () => {
      process.env = { ...originalEnv }
      projectRoot = await fs.mkdtemp(path.join(os.tmpdir(), 'tdd-guard-test-'))
      testConfig = new Config({ projectRoot })
      storage = new FileStorage(testConfig)
      modelProvider = testData.modelClientProvider()
    })

    afterEach(async () => {
      process.env = originalEnv
      await fs.rm(projectRoot, { recursive: true, force: true })
    })

    test('uses projectRoot from Config', async () => {
      const hookData = testData.editOperation()
      await run(JSON.stringify(hookData), testConfig, storage, modelProvider)

      expect(await pathExists(testConfig.dataDir)).toBe(true)
    })

    test('saves Edit data', async () => {
      const hookData = testData.editOperation()

      await run(JSON.stringify(hookData), testConfig, storage, modelProvider)

      const savedModifications = await storage.getModifications()
      expect(JSON.parse(savedModifications!)).toStrictEqual(hookData)
    })

    test('saves Write data', async () => {
      const hookData = testData.writeOperation()

      await run(JSON.stringify(hookData), testConfig, storage, modelProvider)

      const savedModifications = await storage.getModifications()
      expect(JSON.parse(savedModifications!)).toStrictEqual(hookData)
    })

    test('saves TodoWrite data', async () => {
      const hookData = testData.todoWriteOperation()

      await run(JSON.stringify(hookData), testConfig, storage, modelProvider)

      const savedTodos = await storage.getTodo()
      expect(JSON.parse(savedTodos!)).toStrictEqual(hookData)
    })

    test('saves MultiEdit data', async () => {
      const hookData = testData.multiEditOperation()

      await run(JSON.stringify(hookData), testConfig, storage, modelProvider)

      const savedModifications = await storage.getModifications()
      expect(JSON.parse(savedModifications!)).toStrictEqual(hookData)
    })

    test('uses provided ModelClientProvider', async () => {
      await storage.saveConfig(JSON.stringify({ guardEnabled: true }))

      const hookData = testData.editOperation()

      const result = await run(
        JSON.stringify(hookData),
        testConfig,
        storage,
        modelProvider
      )

      // The mock provider always returns undefined decision
      expect(result.decision).toBe(undefined)
      expect(result.reason).toContain('Using mock model client')
    })
  })
})

// Helper to check if file/directory exists
async function pathExists(filePath: string): Promise<boolean> {
  return fs
    .access(filePath)
    .then(() => true)
    .catch(() => false)
}

// Helper function for CLI subprocess tests
async function runCli(
  input: string
): Promise<{ exitCode: number; stderr: string; stdout: string }> {
  const cliPath = path.join(__dirname, 'tdd-guard.ts')

  return new Promise((resolve) => {
    const npxPath = process.platform === 'win32' ? 'npx.cmd' : 'npx'
    const proc = spawn(npxPath, ['tsx', cliPath], {
      env: { ...process.env },
      shell: process.platform === 'win32',
    })

    let stderr = ''
    let stdout = ''
    proc.stderr.on('data', (data) => {
      stderr += data.toString()
    })
    proc.stdout.on('data', (data) => {
      stdout += data.toString()
    })

    proc.stdin.write(input)
    proc.stdin.end()

    proc.on('close', (code) => {
      resolve({ exitCode: code ?? 1, stderr, stdout })
    })
  })
}
