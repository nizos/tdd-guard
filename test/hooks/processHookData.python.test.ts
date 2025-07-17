import { describe, it, expect } from 'vitest'
import { processHookData } from '../../src/hooks/processHookData'
import { MemoryStorage } from '../../src/storage/MemoryStorage'

describe('processHookData python support', () => {
  it('should handle pytest test results format', async () => {
    const storage = new MemoryStorage()
    const pytestResults = {
      testModules: [
        {
          moduleId: 'test_example.py',
          tests: [
            {
              name: 'test_passing',
              fullName: 'test_example.py::test_passing',
              state: 'passed'
            }
          ]
        }
      ]
    }
    
    await storage.saveTest(JSON.stringify(pytestResults))
    
    const result = await processHookData('{"hook_event_name": "PreToolUse"}', { storage })
    
    expect(result.decision).toBeUndefined()
  })

  it('should handle pytest failing test results', async () => {
    const storage = new MemoryStorage()
    const pytestResults = {
      testModules: [
        {
          moduleId: 'test_example.py',
          tests: [
            {
              name: 'test_failing',
              fullName: 'test_example.py::test_failing',
              state: 'failed',
              errors: [{ message: 'AssertionError: 1 != 2' }]
            }
          ]
        }
      ]
    }
    
    await storage.saveTest(JSON.stringify(pytestResults))
    
    const result = await processHookData('{"hook_event_name": "PreToolUse"}', { storage })
    
    expect(result.decision).toBeUndefined()
  })

  it('should detect Python files from hook data', async () => {
    const storage = new MemoryStorage()
    const hookData = {
      hook_event_name: 'PreToolUse',
      tool_input: {
        file_path: 'src/calculator.py'
      }
    }
    
    const result = await processHookData(JSON.stringify(hookData), { storage })
    
    expect(result.decision).toBeUndefined()
  })
})