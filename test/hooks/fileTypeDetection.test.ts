import { describe, it, expect } from 'vitest'
import { detectFileType } from '../../src/hooks/fileTypeDetection'

describe('detectFileType', () => {
  it('should detect Python files', () => {
    const hookData = {
      tool_input: {
        file_path: 'src/calculator.py'
      }
    }
    
    const result = detectFileType(hookData)
    expect(result).toBe('python')
  })

  it('should detect JavaScript files', () => {
    const hookData = {
      tool_input: {
        file_path: 'src/calculator.js'
      }
    }
    
    const result = detectFileType(hookData)
    expect(result).toBe('javascript')
  })
})