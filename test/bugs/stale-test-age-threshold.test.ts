import { describe, it, expect, beforeEach } from 'vitest'
import { FileStorage } from '../../src/storage/FileStorage'
import { Config } from '../../src/config/Config'
import { resolve } from 'path'
import { rm, mkdir, utimes } from 'fs/promises'

describe('Bug: Test data should expire after 20 minutes', () => {
  const testDataPath = resolve(__dirname, './test-data-age')
  const config = new Config({ dataDir: testDataPath })
  let storage: FileStorage

  beforeEach(async () => {
    // Clean up and create fresh test directory
    await rm(testDataPath, { recursive: true, force: true })
    await mkdir(testDataPath, { recursive: true })
    storage = new FileStorage(config)
  })

  it('should return null for test data older than threshold', async () => {
    // Save test data
    await storage.saveTest(
      JSON.stringify({
        testModules: [{ moduleId: 'old_test.js' }],
      })
    )

    // Verify data was saved
    let result = await storage.getTest()
    expect(result).toContain('old_test.js')

    // Manually modify file timestamp to simulate old data (21 minutes ago)
    const testFilePath = config.testResultsFilePath
    const twentyOneMinutesAgo = new Date(Date.now() - 21 * 60 * 1000)
    await utimes(testFilePath, twentyOneMinutesAgo, twentyOneMinutesAgo)

    // Should return null for stale data
    result = await storage.getTest()
    expect(result).toBeNull()
  })
})
