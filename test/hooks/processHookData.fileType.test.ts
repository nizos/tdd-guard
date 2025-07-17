import { describe, it, expect } from 'vitest'
import { processHookData } from '../../src/hooks/processHookData'
import { MemoryStorage } from '../../src/storage/MemoryStorage'

describe('processHookData file type integration', () => {
  it('should use pytest schema for Python files', async () => {
    const storage = new MemoryStorage()
    const pytestResults = {
      testModules: [{
        moduleId: 'test_example.py',
        tests: [{
          name: 'test_passing',
          fullName: 'test_example.py::test_passing',
          state: 'passed'
        }]
      }]
    }
    
    await storage.saveTest(JSON.stringify(pytestResults))
    
    const hookData = {
      hook_event_name: 'PreToolUse',
      tool_input: { file_path: 'test_example.py' }
    }
    
    const result = await processHookData(JSON.stringify(hookData), { storage })
    
    // Should not block when pytest results are valid for Python file
    expect(result.decision).toBeUndefined()
  })

  it('should handle Python file when no test results exist', async () => {
    const storage = new MemoryStorage()
    // No test results stored
    
    const hookData = {
      hook_event_name: 'PreToolUse',
      tool_input: { file_path: 'calculator.py' }
    }
    
    const result = await processHookData(JSON.stringify(hookData), { storage })
    
    // Should not block when no test results (allows initial implementation)
    expect(result.decision).toBeUndefined()
  })
})